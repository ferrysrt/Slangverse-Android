begin;

insert into auth.users (id,is_anonymous) values
    ('60000000-0000-4000-8000-000000000001',false),
    ('60000000-0000-4000-8000-000000000002',true),
    ('60000000-0000-4000-8000-000000000003',true),
    ('60000000-0000-4000-8000-000000000004',true);
insert into slangverse_private.research_admins values ('60000000-0000-4000-8000-000000000001');

create temporary table period_questions as
select b.version, q.pack_id, q.pack_sha256, q.question_id, q.correct_index,
    row_number() over (order by q.question_id) as position
from slangverse_private.research_banks b
join slangverse_private.bank_questions q on q.pack_id=b.catalog->'packs'->0->>'packId'
    and q.pack_sha256=b.catalog->'packs'->0->>'sha256';
grant select on period_questions to authenticated;

set local role authenticated;
select set_config('request.jwt.claim.sub','60000000-0000-4000-8000-000000000002',true);
select public.register_research_participant('test_before_reset');
select public.record_research_answer(gen_random_uuid(),pack_id,pack_sha256,question_id,correct_index,'browse',now())
    from period_questions where position=1;
do $$
begin
    begin
        perform public.research_admin_reset_answers('70000000-0000-4000-8000-000000000001',null);
        raise exception 'Participant was allowed to reset';
    exception when insufficient_privilege then null; end;
    begin
        perform public.research_admin_export_period(10,0,false,true,null);
        raise exception 'Participant was allowed to read archived answers';
    exception when insufficient_privilege then null; end;
end;
$$;

select set_config('request.jwt.claim.sub','60000000-0000-4000-8000-000000000001',true);
do $$
declare
    period uuid := '70000000-0000-4000-8000-000000000001';
    summary jsonb;
    pack jsonb;
begin
    summary := public.research_admin_overview();
    if (summary->'answers'->>'total')::int is distinct from 1 or summary->>'answerPeriod' is not null then
        raise exception 'Initial migration hid existing answers';
    end if;
    if public.research_admin_reset_answers(period,null) is not true
        or public.research_admin_reset_answers(period,null) is not true
        or public.research_admin_reset_answers('70000000-0000-4000-8000-000000000002',null) is not false then
        raise exception 'Reset did not reject a stale form or handle retries';
    end if;
    summary := public.research_admin_overview();
    select public.research_admin_bank_pack(version,pack_id) into pack from period_questions limit 1;
    if (summary->'answerPeriod'->>'id')::uuid is distinct from period
        or (summary->'participants')::int is distinct from 0
        or (summary->'answers'->>'total')::int is distinct from 0
        or summary->'recentAnswers' is distinct from '[]'::jsonb
        or (pack->>'answerPeriodId')::uuid is distinct from period
        or pack->'answerCounts' is distinct from '{}'::jsonb
        or exists (select * from public.research_admin_export(10,0,false))
        or exists (select * from public.research_admin_export_period(10,0,false,false,period)) then
        raise exception 'Reset left data in an active view';
    end if;
    begin
        perform public.research_admin_export_period(10,0,false,false,null);
        raise exception 'CSV could continue with a stale period';
    exception when serialization_failure then null; end;
end;
$$;

-- A delayed sync from an older account stays in the archive, even after a reset.
select set_config('request.jwt.claim.sub','60000000-0000-4000-8000-000000000002',true);
select public.record_research_answer(gen_random_uuid(),pack_id,pack_sha256,question_id,correct_index,'browse',now())
    from period_questions where position=2;
select set_config('request.jwt.claim.sub','60000000-0000-4000-8000-000000000003',true);
select public.register_research_participant('test_after_reset');
select public.record_research_answer(gen_random_uuid(),pack_id,pack_sha256,question_id,(correct_index+1)%4,'browse',now())
    from period_questions where position=1;
select set_config('request.jwt.claim.sub','60000000-0000-4000-8000-000000000004',true);
select public.register_research_participant('qa_after_reset');
select public.record_research_answer(gen_random_uuid(),pack_id,pack_sha256,question_id,correct_index,'browse',now())
    from period_questions where position=1;

select set_config('request.jwt.claim.sub','60000000-0000-4000-8000-000000000001',true);
do $$
declare
    period uuid := '70000000-0000-4000-8000-000000000001';
    summary jsonb := public.research_admin_overview();
    counts jsonb;
begin
    select public.research_admin_bank_pack(version,pack_id)->'answerCounts'->question_id
    into counts from period_questions where position=1;
    if (summary->'participants')::int is distinct from 1
        or (summary->'answers'->>'total')::int is distinct from 1
        or counts is distinct from '{"correct":0,"incorrect":1}'::jsonb
        or (select count(*) from public.research_admin_export_period(10,0,false,false,period)
            where username='test_after_reset') <> 1
        or (select count(*) from public.research_admin_export_period(10,0,true,false,period)) <> 2
        or (select count(*) from public.research_admin_export_period(10,0,false,true,period)
            where username='test_before_reset') <> 2 then
        raise exception 'Active answers, QA or archived answers did not stay in their intended period';
    end if;
end;
$$;

rollback;
select 'Answer period reset, late sync and archive isolation passed' as result;
