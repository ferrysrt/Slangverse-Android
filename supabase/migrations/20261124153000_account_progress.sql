begin;

create schema slangverse_private;
revoke all on schema slangverse_private from public, anon, authenticated;

create table slangverse_private.bank_questions (
    pack_id text not null check (char_length(pack_id) between 1 and 100),
    pack_sha256 text not null check (pack_sha256 ~ '^[0-9a-f]{64}$'),
    question_id text not null check (char_length(question_id) between 1 and 140),
    sense_id text not null check (char_length(sense_id) between 1 and 140),
    context_category text not null check (char_length(context_category) between 1 and 40),
    correct_index smallint not null check (correct_index between 0 and 3),
    primary key (pack_id, pack_sha256, question_id)
);
revoke all on slangverse_private.bank_questions from public, anon, authenticated;

create table public.practice_attempts (
    user_id uuid not null references auth.users(id) on delete cascade,
    event_id uuid not null,
    pack_id text not null,
    pack_sha256 text not null,
    question_id text not null,
    sense_id text not null,
    context_category text not null,
    selected_index smallint not null check (selected_index between 0 and 3),
    is_correct boolean not null,
    xp_awarded smallint generated always as (case when is_correct then 80 else 15 end) stored,
    mode text not null check (mode in ('browse', 'adaptive', 'fixed')),
    source text not null check (source in ('practice', 'guest_import')),
    response_ms integer check (response_ms >= 0),
    hints_viewed smallint check (hints_viewed between 0 and 5),
    received_at timestamptz not null default clock_timestamp(),
    check (source <> 'guest_import' or (response_ms is null and hints_viewed is null)),
    primary key (user_id, event_id),
    unique (user_id, pack_id, pack_sha256, question_id),
    foreign key (pack_id, pack_sha256, question_id)
        references slangverse_private.bank_questions (pack_id, pack_sha256, question_id)
);

alter table public.practice_attempts enable row level security;
revoke all on public.practice_attempts from public, anon, authenticated;
grant select on public.practice_attempts to authenticated;

create policy own_practice_history on public.practice_attempts
    for select to authenticated using (user_id = (select auth.uid()));

create function public.submit_practice_attempt(
    p_event_id uuid,
    p_pack_id text,
    p_pack_sha256 text,
    p_question_id text,
    p_selected_index integer,
    p_mode text,
    p_source text default 'practice',
    p_response_ms integer default null,
    p_hints_viewed integer default null
)
returns public.practice_attempts
language plpgsql
security definer
set search_path = ''
as $$
declare
    account_id uuid := auth.uid();
    question slangverse_private.bank_questions%rowtype;
    recorded public.practice_attempts%rowtype;
begin
    if account_id is null then
        raise exception using errcode = '28000', message = 'Authentication required';
    end if;

    if p_event_id is null or p_pack_id is null or char_length(p_pack_id) not between 1 and 100
        or p_pack_sha256 is null or p_pack_sha256 !~ '^[0-9a-f]{64}$'
        or p_question_id is null or char_length(p_question_id) not between 1 and 140
        or p_selected_index is null or p_selected_index not between 0 and 3
        or p_mode is null or p_mode not in ('browse', 'adaptive', 'fixed')
        or p_source is null or p_source not in ('practice', 'guest_import')
        or p_response_ms < 0 or p_hints_viewed not between 0 and 5
        or (p_source = 'guest_import' and (p_response_ms is not null or p_hints_viewed is not null)) then
        raise exception using errcode = '22023', message = 'Invalid answer metadata';
    end if;

    select bank.* into question
    from slangverse_private.bank_questions as bank
    where bank.pack_id = p_pack_id and bank.pack_sha256 = p_pack_sha256 and bank.question_id = p_question_id;
    if not found then
        raise exception using errcode = '22023', message = 'Unknown question revision';
    end if;

    insert into public.practice_attempts (
        user_id, event_id, pack_id, pack_sha256, question_id, sense_id, context_category,
        selected_index, is_correct, mode, source, response_ms, hints_viewed
    ) values (
        account_id, p_event_id, question.pack_id, question.pack_sha256, question.question_id,
        question.sense_id, question.context_category, p_selected_index,
        p_selected_index = question.correct_index, p_mode, p_source, p_response_ms, p_hints_viewed
    ) on conflict do nothing returning * into recorded;
    if found then return recorded; end if;

    select attempt.* into recorded
    from public.practice_attempts as attempt
    where attempt.user_id = account_id and attempt.event_id = p_event_id;
    if found then
        if row(recorded.pack_id, recorded.pack_sha256, recorded.question_id, recorded.selected_index,
               recorded.mode, recorded.source, recorded.response_ms, recorded.hints_viewed)
            is distinct from row(p_pack_id, p_pack_sha256, p_question_id, p_selected_index,
                                 p_mode, p_source, p_response_ms, p_hints_viewed) then
            raise exception using errcode = '22023', message = 'Event ID already used for a different submission';
        end if;
        return recorded;
    end if;

    select attempt.* into strict recorded
    from public.practice_attempts as attempt
    where attempt.user_id = account_id and attempt.pack_id = p_pack_id
        and attempt.pack_sha256 = p_pack_sha256 and attempt.question_id = p_question_id;
    return recorded;
end;
$$;

create function public.my_practice_progress()
returns table (answered bigint, correct bigint, xp bigint)
language sql stable
security invoker
set search_path = ''
as $$
    select count(*), count(*) filter (where is_correct), coalesce(sum(xp_awarded), 0)
    from public.practice_attempts
    where user_id = (select auth.uid());
$$;

revoke all on function public.submit_practice_attempt(uuid, text, text, text, integer, text, text, integer, integer)
    from public, anon, authenticated;
revoke all on function public.my_practice_progress() from public, anon, authenticated;
grant execute on function public.submit_practice_attempt(uuid, text, text, text, integer, text, text, integer, integer)
    to authenticated;
grant execute on function public.my_practice_progress() to authenticated;

commit;
