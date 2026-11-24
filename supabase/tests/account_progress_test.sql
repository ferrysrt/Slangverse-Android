begin;

insert into auth.users (id) values
    ('00000000-0000-4000-8000-000000000001'), ('00000000-0000-4000-8000-000000000002');

insert into slangverse_private.bank_questions
    (pack_id, pack_sha256, question_id, sense_id, context_category, correct_index)
values
    ('fixture-pack', repeat('a', 64), 'fixture-question', 'fixture-sense', 'education', 2),
    ('fixture-pack', repeat('b', 64), 'fixture-question', 'fixture-sense', 'work', 1);

set local role anon;
do $$
begin
    begin
        perform public.my_practice_progress();
        raise exception 'Anonymous progress access unexpectedly succeeded';
    exception when insufficient_privilege then null;
    end;
    begin
        perform public.submit_practice_attempt('00000000-0000-4000-8000-000000000010',
            'fixture-pack', repeat('a', 64), 'fixture-question', 2, 'browse');
        raise exception 'Anonymous submission unexpectedly succeeded';
    exception when insufficient_privilege then null;
    end;
end;
$$;

set local role authenticated;
select set_config('request.jwt.claim.sub', '00000000-0000-4000-8000-000000000001', true);
do $$
declare
    first_attempt public.practice_attempts;
    repeated_attempt public.practice_attempts;
    progress record;
begin
    first_attempt := public.submit_practice_attempt('00000000-0000-4000-8000-000000000010',
        'fixture-pack', repeat('a', 64), 'fixture-question', 2, 'adaptive', 'practice', 3250, 1);
    if first_attempt.xp_awarded <> 80 or not first_attempt.is_correct or first_attempt.response_ms <> 3250
        or first_attempt.hints_viewed <> 1 or first_attempt.context_category <> 'education' then
        raise exception 'First response was graded or stored incorrectly';
    end if;

    repeated_attempt := public.submit_practice_attempt('00000000-0000-4000-8000-000000000010',
        'fixture-pack', repeat('a', 64), 'fixture-question', 2, 'adaptive', 'practice', 3250, 1);
    if repeated_attempt is distinct from first_attempt then raise exception 'Retry did not return the original row'; end if;

    repeated_attempt := public.submit_practice_attempt('00000000-0000-4000-8000-000000000011',
        'fixture-pack', repeat('a', 64), 'fixture-question', 0, 'fixed', 'practice', 5100, 0);
    if repeated_attempt is distinct from first_attempt then
        raise exception 'Same question revision was treated as a new answer';
    end if;

    begin
        perform public.submit_practice_attempt('00000000-0000-4000-8000-000000000010',
            'fixture-pack', repeat('b', 64), 'fixture-question', 1, 'adaptive');
        raise exception 'Conflicting reuse of event ID unexpectedly succeeded';
    exception when sqlstate '22023' then null;
    end;
    begin
        perform public.submit_practice_attempt('00000000-0000-4000-8000-000000000012',
            'fixture-pack', repeat('c', 64), 'fixture-question', 1, 'fixed');
        raise exception 'Unknown revision unexpectedly accepted';
    exception when sqlstate '22023' then null;
    end;
    begin
        perform public.submit_practice_attempt('00000000-0000-4000-8000-000000000012',
            'fixture-pack', repeat('b', 64), 'fixture-question', 6, 'fixed');
        raise exception 'Invalid answer index unexpectedly accepted';
    exception when sqlstate '22023' then null;
    end;
    begin
        perform public.submit_practice_attempt('00000000-0000-4000-8000-000000000012',
            'fixture-pack', repeat('b', 64), 'fixture-question', 2, 'browse', 'guest_import', 1000);
        raise exception 'Invented timing on guest import unexpectedly accepted';
    exception when sqlstate '22023' then null;
    end;

    repeated_attempt := public.submit_practice_attempt('00000000-0000-4000-8000-000000000012',
        'fixture-pack', repeat('b', 64), 'fixture-question', 2, 'browse', 'guest_import');
    if repeated_attempt.xp_awarded <> 15 or repeated_attempt.is_correct or repeated_attempt.response_ms is not null
        or repeated_attempt.hints_viewed is not null or repeated_attempt.source <> 'guest_import' then
        raise exception 'Guest import or revision-specific grading failed';
    end if;

    select * into progress from public.my_practice_progress();
    if row(progress.answered, progress.correct, progress.xp) is distinct from row(2::bigint, 1::bigint, 95::bigint) then
        raise exception 'XP was duplicated or totals are incorrect';
    end if;

    begin
        update public.practice_attempts set is_correct = true;
        raise exception 'Direct editing of recorded correctness unexpectedly succeeded';
    exception when insufficient_privilege then null;
    end;
    begin
        perform correct_index from slangverse_private.bank_questions;
        raise exception 'Private grading data is readable by a player';
    exception when insufficient_privilege then null;
    end;
end;
$$;

select set_config('request.jwt.claim.sub', '00000000-0000-4000-8000-000000000002', true);
do $$
declare
    second_attempt public.practice_attempts;
    progress record;
begin
    if exists (select 1 from public.practice_attempts) then raise exception 'Second user saw first user history'; end if;
    select * into progress from public.my_practice_progress();
    if row(progress.answered, progress.correct, progress.xp) is distinct from row(0::bigint, 0::bigint, 0::bigint) then
        raise exception 'Second user saw first user totals';
    end if;
    second_attempt := public.submit_practice_attempt('00000000-0000-4000-8000-000000000010',
        'fixture-pack', repeat('a', 64), 'fixture-question', 0, 'fixed');
    if second_attempt.xp_awarded <> 15 or second_attempt.user_id <> auth.uid()
        or (select count(*) from public.practice_attempts) <> 1 then
        raise exception 'Second user submission interfered with the first user';
    end if;
end;
$$;

select set_config('request.jwt.claim.sub', '00000000-0000-4000-8000-000000000001', true);
do $$
begin
    if (select xp from public.my_practice_progress()) <> 95 or (select count(*) from public.practice_attempts) <> 2 then
        raise exception 'First user now sees second user data';
    end if;
end;
$$;

rollback;

select 'Account permissions, grading, revision locking, retry and XP tests passed' as result;
