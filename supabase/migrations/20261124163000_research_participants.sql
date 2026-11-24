begin;

create table slangverse_private.research_participants (
    user_id uuid primary key references auth.users(id) on delete cascade,
    username text not null check (username ~ '^[A-Za-z0-9_-]{1,24}$'),
    joined_at timestamptz not null default clock_timestamp()
);
revoke all on slangverse_private.research_participants from public, anon, authenticated;

alter table public.practice_attempts add column client_answered_at timestamptz;

create function public.register_research_participant(p_username text)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
    account_id uuid := auth.uid();
begin
    if not exists (select 1 from auth.users where id = account_id and is_anonymous) then
        raise exception using errcode = '42501', message = 'An anonymous participant session is required';
    end if;
    if p_username is null or p_username !~ '^[A-Za-z0-9_-]{1,24}$' then
        raise exception using errcode = '22023', message = 'Invalid participant username';
    end if;

    insert into slangverse_private.research_participants (user_id, username)
    values (account_id, p_username) on conflict (user_id) do nothing;
    if not exists (select 1 from slangverse_private.research_participants
                   where user_id = account_id and username = p_username) then
        raise exception using errcode = '22023', message = 'Participant username already registered';
    end if;
end;
$$;

create function public.record_research_answer(
    p_event_id uuid, p_pack_id text, p_pack_sha256 text, p_question_id text,
    p_selected_index integer, p_mode text, p_answered_at timestamptz
)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
    account_id uuid := auth.uid();
    recorded public.practice_attempts%rowtype;
begin
    if not exists (select 1 from slangverse_private.research_participants where user_id = account_id) then
        raise exception using errcode = '42501', message = 'Register a participant before submitting answers';
    end if;
    if p_answered_at is null or not isfinite(p_answered_at) then
        raise exception using errcode = '22023', message = 'Invalid answer timestamp';
    end if;

    recorded := public.submit_practice_attempt(
        p_event_id, p_pack_id, p_pack_sha256, p_question_id, p_selected_index, p_mode
    );
    if recorded.event_id = p_event_id then
        update public.practice_attempts set client_answered_at = p_answered_at
        where user_id = account_id and event_id = p_event_id and client_answered_at is null;
    end if;
end;
$$;

create view slangverse_private.research_answers as
select participant.user_id as participant_id, participant.username, attempt.event_id,
       attempt.pack_id, attempt.pack_sha256, attempt.question_id, attempt.sense_id, attempt.context_category,
       attempt.selected_index, attempt.is_correct, attempt.xp_awarded, attempt.mode,
       attempt.client_answered_at, attempt.received_at
from slangverse_private.research_participants as participant
join public.practice_attempts as attempt on attempt.user_id = participant.user_id
where attempt.client_answered_at is not null;
revoke all on slangverse_private.research_answers from public, anon, authenticated;

revoke all on function public.register_research_participant(text) from public, anon, authenticated;
revoke all on function public.record_research_answer(uuid, text, text, text, integer, text, timestamptz)
    from public, anon, authenticated;
grant execute on function public.register_research_participant(text) to authenticated;
grant execute on function public.record_research_answer(uuid, text, text, text, integer, text, timestamptz)
    to authenticated;

commit;
