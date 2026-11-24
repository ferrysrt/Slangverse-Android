begin;

create table slangverse_private.research_answer_resets (
    period_id uuid primary key,
    started_at timestamptz not null unique default clock_timestamp(),
    started_by uuid references auth.users(id) on delete set null
);

create view slangverse_private.current_research_answer_period as
select period_id, started_at
from slangverse_private.research_answer_resets
order by started_at desc limit 1;

create view slangverse_private.active_research_participants as
select p.* from slangverse_private.research_participants p
where p.joined_at >= coalesce(
    (select started_at from slangverse_private.current_research_answer_period), '-infinity'::timestamptz
);

create view slangverse_private.active_research_answers as
select a.* from slangverse_private.research_answers a
join slangverse_private.active_research_participants p on p.user_id=a.participant_id;

revoke all on slangverse_private.research_answer_resets, slangverse_private.current_research_answer_period,
    slangverse_private.active_research_participants, slangverse_private.active_research_answers
    from public, anon, authenticated, service_role;

create function public.research_admin_reset_answers(p_request_id uuid, p_expected_period uuid)
returns boolean language plpgsql security definer set search_path = '' as $$
declare
    current_period uuid;
begin
    perform slangverse_private.require_research_admin();
    lock table slangverse_private.research_answer_resets in share row exclusive mode;
    select period_id into current_period from slangverse_private.current_research_answer_period;
    if current_period = p_request_id then
        return true;
    end if;
    if current_period is distinct from p_expected_period or exists (
        select 1 from slangverse_private.research_answer_resets where period_id=p_request_id
    ) then
        return false;
    end if;
    insert into slangverse_private.research_answer_resets (period_id, started_by)
    values (p_request_id, auth.uid());
    return true;
end;
$$;

create or replace function public.research_admin_overview()
returns jsonb language plpgsql stable security definer set search_path = '' as $$
begin
    perform slangverse_private.require_research_admin();
    return jsonb_build_object(
        'datasets', coalesce((select jsonb_agg(to_jsonb(d) order by d.created_at desc) from (
            select version, created_at, jsonb_array_length(document->'entries') as entries
            from slangverse_private.research_datasets order by created_at desc limit 30
        ) d), '[]'::jsonb),
        'banks', coalesce((select jsonb_agg(to_jsonb(b) order by b.created_at desc) from (
            select version, created_at, question_count, jsonb_array_length(catalog->'packs') as packs
            from slangverse_private.research_banks order by created_at desc limit 30
        ) b), '[]'::jsonb),
        'jobs', coalesce((select jsonb_agg(to_jsonb(j) order by j.requested_at desc) from (
            select request_id, operation, commit_sha, dataset_version, bank_version, status, requested_at, run_id, summary
            from slangverse_private.research_jobs order by requested_at desc limit 40
        ) j), '[]'::jsonb),
        'generation', (select jsonb_build_object(
            'runs', count(*), 'passedRuns', count(*) filter (where status='success'),
            'failedRuns', count(*) filter (where status='failed'),
            'questionsPassed', coalesce(sum((summary->>'questionsPassed')::int),0),
            'questionsFailed', coalesce(sum((summary->>'questionsFailed')::int),0))
            from slangverse_private.research_jobs where operation='generate'),
        'promptStats', coalesce((select jsonb_agg(to_jsonb(p) order by p.version,p.dataset_version,p.model) from (
            select summary->>'promptVersion' as version, dataset_version, summary->>'model' as model,
                count(*) as runs, count(*) filter (where status='success') as passed,
                count(*) filter (where status='failed') as failed
            from slangverse_private.research_jobs
            where operation='generate' and summary->>'promptVersion' is not null
            group by summary->>'promptVersion', dataset_version, summary->>'model'
        ) p), '[]'::jsonb),
        'answerPeriod', (select jsonb_build_object('id', period_id, 'startedAt', started_at)
            from slangverse_private.current_research_answer_period),
        'participants', (select count(*) from slangverse_private.active_research_participants where username !~ '^qa_'),
        'answers', (select jsonb_build_object('total', count(*), 'correct', count(*) filter (where is_correct))
            from slangverse_private.active_research_answers where username !~ '^qa_'),
        'questions', coalesce((select jsonb_agg(to_jsonb(q) order by q.total desc, q.question_id) from (
            select pack_id, pack_sha256, question_id, context_category,
                   count(*) as total, count(*) filter (where is_correct) as correct
            from slangverse_private.active_research_answers where username !~ '^qa_'
            group by pack_id, pack_sha256, question_id, context_category
            order by total desc, question_id limit 50
        ) q), '[]'::jsonb),
        'recentAnswers', coalesce((select jsonb_agg(to_jsonb(a) order by a.received_at desc) from (
            select participant_id, username, question_id, context_category, is_correct, received_at
            from slangverse_private.active_research_answers where username !~ '^qa_'
            order by received_at desc, participant_id, event_id limit 20
        ) a), '[]'::jsonb)
    );
end;
$$;

create or replace function public.research_admin_export(p_limit integer, p_offset integer, p_include_qa boolean)
returns setof slangverse_private.research_answers
language plpgsql stable security definer set search_path = '' as $$
begin
    perform slangverse_private.require_research_admin();
    return query select * from slangverse_private.active_research_answers
        where p_include_qa or username !~ '^qa_'
        order by received_at, event_id, participant_id limit least(greatest(p_limit,1),500)
        offset greatest(p_offset,0);
end;
$$;

create or replace function public.research_admin_bank_pack(p_version text, p_pack_id text)
returns jsonb language plpgsql stable security definer set search_path = '' as $$
begin
    perform slangverse_private.require_research_admin();
    return (
        select (b.files->>p_pack_id)::jsonb || jsonb_build_object(
            'answerPeriodId', (select period_id from slangverse_private.current_research_answer_period),
            'answerCounts', coalesce((
            select jsonb_object_agg(t.question_id, jsonb_build_object('correct',t.correct,'incorrect',t.incorrect))
            from (
                select a.question_id, count(*) filter (where a.is_correct) as correct,
                    count(*) filter (where not a.is_correct) as incorrect
                from slangverse_private.active_research_answers a
                where a.pack_id=p_pack_id and a.pack_sha256=descriptor->>'sha256'
                    and a.username !~ '^qa_'
                group by a.question_id
            ) t
        ), '{}'::jsonb))
        from slangverse_private.research_banks b
        cross join lateral jsonb_array_elements(b.catalog->'packs') as packs(descriptor)
        where b.version=p_version and descriptor->>'packId'=p_pack_id
    );
end;
$$;

create function public.research_admin_export_period(
    p_limit integer, p_offset integer, p_include_qa boolean, p_archive boolean, p_expected_period uuid
) returns setof slangverse_private.research_answers
language plpgsql stable security definer set search_path = '' as $$
declare
    current_period uuid;
    cutoff timestamptz;
begin
    perform slangverse_private.require_research_admin();
    select period_id, started_at into current_period, cutoff
        from slangverse_private.current_research_answer_period;
    if current_period is distinct from p_expected_period then
        raise exception using errcode='40001', message='Answer period changed during export';
    end if;
    return query select a.* from slangverse_private.research_answers a
        join slangverse_private.research_participants p on p.user_id=a.participant_id
        where p_archive = (p.joined_at < coalesce(cutoff, '-infinity'::timestamptz))
            and (p_include_qa or a.username !~ '^qa_')
        order by a.received_at, a.event_id, a.participant_id limit least(greatest(p_limit,1),500)
        offset greatest(p_offset,0);
end;
$$;

revoke all on function public.research_admin_reset_answers(uuid,uuid),
    public.research_admin_export_period(integer,integer,boolean,boolean,uuid)
    from public, anon, authenticated, service_role;
grant execute on function public.research_admin_reset_answers(uuid,uuid),
    public.research_admin_export_period(integer,integer,boolean,boolean,uuid) to authenticated;

notify pgrst, 'reload schema';

commit;
