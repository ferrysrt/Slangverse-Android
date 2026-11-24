begin;
insert into auth.users (id, is_anonymous) values
    ('10000000-0000-4000-8000-000000000111',false),
    ('10000000-0000-4000-8000-000000000112',true);
insert into slangverse_private.research_admins values ('10000000-0000-4000-8000-000000000111');

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
begin
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
