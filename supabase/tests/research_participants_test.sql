begin;

insert into auth.users (id, is_anonymous) values
    ('00000000-0000-4000-8000-000000000011', true),
    ('00000000-0000-4000-8000-000000000012', true),
    ('00000000-0000-4000-8000-000000000013', false);
insert into slangverse_private.bank_questions values ('test', repeat('b',64), 'q1', 'sense', 'sports', 1);

set local role anon;
do $$
begin
    perform public.register_research_participant('guest123');
    raise exception 'Unauthenticated register unexpectedly succeeded';
exception when insufficient_privilege then null;
end;
$$;
set local role authenticated;
select set_config('request.jwt.claim.sub', '00000000-0000-4000-8000-000000000013', true);
do $$
begin
    perform public.register_research_participant('learner');
    raise exception 'Email account unexpectedly registered as a research participant';
exception when insufficient_privilege then null;
end;
$$;

select set_config('request.jwt.claim.sub', '00000000-0000-4000-8000-000000000011', true);
select public.register_research_participant('guest123');
select public.register_research_participant('guest123');
select public.record_research_answer('10000000-0000-4000-8000-000000000011', 'test', repeat('b',64), 'q1', 1, 'browse', '2026-11-24T01:00:00Z');
select public.record_research_answer('10000000-0000-4000-8000-000000000011', 'test', repeat('b',64), 'q1', 1, 'browse', '2026-11-24T01:00:00Z');
select public.record_research_answer('10000000-0000-4000-8000-000000000012', 'test', repeat('b',64), 'q1', 0, 'browse', '2026-11-24T02:00:00Z');

select set_config('request.jwt.claim.sub', '00000000-0000-4000-8000-000000000012', true);
select public.register_research_participant('guest123');
do $$
begin
    if exists (select 1 from public.practice_attempts) then raise exception 'Participant saw another profile'; end if;
    begin
        perform * from slangverse_private.research_answers;
        raise exception 'Participant could access the owner export';
    exception when insufficient_privilege then null;
    end;
end;
$$;
select public.record_research_answer('10000000-0000-4000-8000-000000000011', 'test', repeat('b',64), 'q1', 0, 'browse', '2026-11-24T03:00:00Z');

reset role;
do $$
begin
    if (select count(*) from slangverse_private.research_answers where username = 'guest123') <> 2
       or (select count(distinct participant_id) from slangverse_private.research_answers) <> 2
       or (select sum(xp_awarded) from slangverse_private.research_answers) <> 95
       or not exists (select 1 from slangverse_private.research_answers
                      where is_correct and client_answered_at = '2026-11-24T01:00:00Z') then
        raise exception 'Participant export, idempotence, grading or offline timestamp failed';
    end if;
end;
$$;

rollback;
select 'Research registration, answer replay, account isolation and owner export passed' as result;
