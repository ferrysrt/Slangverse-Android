begin;
insert into auth.users (id, is_anonymous) values
    ('10000000-0000-4000-8000-000000000111',false),
    ('10000000-0000-4000-8000-000000000112',true),
    ('10000000-0000-4000-8000-000000000113',true),
    ('10000000-0000-4000-8000-000000000114',true);
insert into slangverse_private.research_admins values ('10000000-0000-4000-8000-000000000111');
insert into slangverse_private.research_participants (user_id, username) values
    ('10000000-0000-4000-8000-000000000112','sample_a'),
    ('10000000-0000-4000-8000-000000000113','sample_b'),
    ('10000000-0000-4000-8000-000000000114','qa_sample');

do $$
declare
    selected_bank slangverse_private.research_banks%rowtype;
    descriptor jsonb;
    pack_question jsonb;
    key_row slangverse_private.bank_questions%rowtype;
begin
    select * into strict selected_bank from slangverse_private.research_banks;
    descriptor := selected_bank.catalog->'packs'->0;
    pack_question := ((selected_bank.files->>(descriptor->>'packId'))::jsonb)->'questions'->0;
    select * into strict key_row from slangverse_private.bank_questions
    where pack_id=descriptor->>'packId' and pack_sha256=descriptor->>'sha256' and question_id=pack_question->>'id';

    insert into slangverse_private.bank_questions (pack_id,pack_sha256,question_id,sense_id,context_category,correct_index)
    values (key_row.pack_id,repeat('b',64),key_row.question_id,key_row.sense_id,key_row.context_category,key_row.correct_index);
    insert into public.practice_attempts (user_id,event_id,pack_id,pack_sha256,question_id,sense_id,context_category,
        selected_index,is_correct,mode,source,client_answered_at)
    select sample_id::uuid, gen_random_uuid(), key_row.pack_id, digest, key_row.question_id,
        key_row.sense_id, key_row.context_category, case when correct then key_row.correct_index
            else (key_row.correct_index + 1) % 4 end, correct, 'browse', 'practice', now()
    from (values
        ('10000000-0000-4000-8000-000000000112',key_row.pack_sha256,true),
        ('10000000-0000-4000-8000-000000000113',key_row.pack_sha256,false),
        ('10000000-0000-4000-8000-000000000114',key_row.pack_sha256,true),
        ('10000000-0000-4000-8000-000000000112',repeat('b',64),false)
    ) samples(sample_id,digest,correct);
end;
$$;

set local role authenticated;
select set_config('request.jwt.claim.sub','10000000-0000-4000-8000-000000000112',true);
do $$
begin
    begin perform public.research_admin_overview();
        raise exception 'Participant could open dashboard';
    exception when insufficient_privilege then null; end;
    begin perform * from public.research_admin_export(20,0,false);
        raise exception 'Participant could export data';
    exception when insufficient_privilege then null; end;
    begin perform public.research_admin_dataset_page('test',0);
        raise exception 'Participant could view dataset';
    exception when insufficient_privilege then null; end;
    begin perform public.research_admin_bank_pack('test','test');
        raise exception 'Participant could view questions';
    exception when insufficient_privilege then null; end;
    begin perform public.research_worker_bank('7e1e593cea34524f');
        raise exception 'Participant could get the worker snapshot';
    exception when insufficient_privilege then null; end;
end;
$$;

select set_config('request.jwt.claim.sub','10000000-0000-4000-8000-000000000111',true);
do $$
declare
    view_data jsonb := public.research_admin_overview();
    dataset_v text := view_data->'datasets'->0->>'version';
    bank_v text := view_data->'banks'->0->>'version';
    first_page jsonb := public.research_admin_dataset_page(dataset_v,0);
    second_page jsonb := public.research_admin_dataset_page(dataset_v,50);
    first_pack text := public.research_admin_content('bank',bank_v)->'packs'->0->>'packId';
    pack_details jsonb := public.research_admin_bank_pack(bank_v,first_pack);
begin
    if jsonb_array_length(first_page->'entries') is distinct from 50
        or (first_page->>'total')::integer is distinct from 148
        or first_page->'entries'->0->>'definition' is null
        or first_page->'entries'->0->>'quote' is null
        or first_page->'entries'->0 = second_page->'entries'->0
        or jsonb_array_length(second_page->'entries') is distinct from 50
        or public.research_admin_dataset_page('missing',0) is not null then
        raise exception 'Dataset viewer did not preserve versioned paging and provenance';
    end if;
    if jsonb_array_length(pack_details->'questions') is distinct from 10
        or pack_details->>'packId' is distinct from first_pack
        or pack_details->'questions'->0->>'correctIndex' is null
        or public.research_admin_bank_pack(bank_v,'missing') is not null then
        raise exception 'Bank viewer did not return the selected package';
    end if;
    if (pack_details->'answerCounts'->(pack_details->'questions'->0->>'id'))
        is distinct from '{"correct":1,"incorrect":1}'::jsonb
        or pack_details->'answerCounts' ? (pack_details->'questions'->1->>'id') then
        raise exception 'Question counts must exclude unavailable revisions and QA accounts';
    end if;
    if (view_data->'banks'->0->>'question_count')::int <> 90
        or public.research_admin_content('dataset', dataset_v)->>'total' is null then
        raise exception 'Seed summaries unavailable';
    end if;
    begin
        perform public.research_admin_reserve('20000000-0000-4000-8000-000000000111','build',repeat('a',40),null,bank_v);
        raise exception 'Untested build reservation unexpectedly succeeded';
    exception when invalid_parameter_value then null; end;
    perform public.research_admin_reserve('20000000-0000-4000-8000-000000000112','check',repeat('a',40),null,bank_v);
    perform public.research_admin_reserve('20000000-0000-4000-8000-000000000113','generate',repeat('a',40),dataset_v,null);
    begin
        perform public.research_admin_reserve('20000000-0000-4000-8000-000000000113','generate',repeat('a',40),dataset_v,null);
        raise exception 'Duplicate generation reserved twice';
    exception when unique_violation then null; end;
end;
$$;

set local role service_role;
do $$
begin
    if public.research_worker_claim('20000000-0000-4000-8000-000000000112',repeat('a',40),123)->>'operation' <> 'check'
        or public.research_worker_claim('20000000-0000-4000-8000-000000000112',repeat('a',40),124) is not null then
        raise exception 'Check must be claimed exactly once';
    end if;
    if public.research_worker_finish('20000000-0000-4000-8000-000000000112',124,true,'{}',null)
        or not public.research_worker_finish('20000000-0000-4000-8000-000000000112',123,true,'{}',null) then
        raise exception 'Only the matching worker run can record a result';
    end if;
    if public.research_worker_claim('20000000-0000-4000-8000-000000000113',repeat('b',40),125) is not null then
        raise exception 'Moved commit was allowed to generate';
    end if;
end;
$$;

set local role authenticated;
do $$
declare bank_v text := public.research_admin_overview()->'banks'->0->>'version';
begin
    perform public.research_admin_reserve('20000000-0000-4000-8000-000000000114','build',repeat('a',40),null,bank_v);
    if public.research_admin_job('20000000-0000-4000-8000-000000000113')->'summary'->>'failureCode' <> 'commit_changed' then
        raise exception 'Moved main must produce a visible result';
    end if;
end;
$$;
rollback;
select 'Research admin isolation, one-time claim, immutable bank and build gate passed' as result;
