begin;

create table slangverse_private.research_admins (
    user_id uuid primary key references auth.users(id) on delete cascade
);

create table slangverse_private.research_datasets (
    version text primary key,
    document jsonb not null check (jsonb_typeof(document->'entries') = 'array'),
    created_at timestamptz not null default now()
);

create table slangverse_private.research_banks (
    version text primary key,
    catalog jsonb not null check (jsonb_typeof(catalog->'packs') = 'array'),
    files jsonb not null check (jsonb_typeof(files) = 'object'),
    question_count integer not null check (question_count > 0),
    created_at timestamptz not null default now()
);

create table slangverse_private.research_jobs (
    request_id uuid primary key,
    requested_by uuid references auth.users(id) on delete set null,
    operation text not null check (operation in ('generate','check','build')),
    commit_sha text not null check (commit_sha ~ '^[a-f0-9]{40}$'),
    dataset_version text references slangverse_private.research_datasets(version),
    bank_version text references slangverse_private.research_banks(version),
    status text not null default 'queued' check (status in ('queued','running','success','failed')),
    requested_at timestamptz not null default now(),
    completed_at timestamptz,
    run_id bigint,
    summary jsonb not null default '{}'::jsonb,
    evidence jsonb,
    check ((operation = 'generate' and dataset_version is not null and bank_version is null) or
           (operation in ('check','build') and bank_version is not null and dataset_version is null))
);

create function slangverse_private.require_research_admin()
returns void language plpgsql stable set search_path = '' as $$
begin
    if not exists (select 1 from slangverse_private.research_admins where user_id = auth.uid()) then
        raise exception using errcode = '42501', message = 'Research admin access required';
    end if;
end;
$$;

create function public.research_admin_overview()
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
        'participants', (select count(*) from slangverse_private.research_participants where username !~ '^qa_'),
        'answers', (select jsonb_build_object('total', count(*), 'correct', count(*) filter (where is_correct))
            from slangverse_private.research_answers where username !~ '^qa_'),
        'questions', coalesce((select jsonb_agg(to_jsonb(q) order by q.total desc, q.question_id) from (
            select pack_id, pack_sha256, question_id, context_category,
                   count(*) as total, count(*) filter (where is_correct) as correct
            from slangverse_private.research_answers where username !~ '^qa_'
            group by pack_id, pack_sha256, question_id, context_category
            order by total desc, question_id limit 50
        ) q), '[]'::jsonb),
        'recentAnswers', coalesce((select jsonb_agg(to_jsonb(a) order by a.received_at desc) from (
            select participant_id, username, question_id, context_category, is_correct, received_at
            from slangverse_private.research_answers where username !~ '^qa_'
            order by received_at desc, participant_id, event_id limit 20
        ) a), '[]'::jsonb)
    );
end;
$$;

create function public.research_admin_job(p_request_id uuid)
returns jsonb language plpgsql stable security definer set search_path = '' as $$
begin
    perform slangverse_private.require_research_admin();
    return (select to_jsonb(j) - 'requested_by'
        from slangverse_private.research_jobs j where j.request_id = p_request_id);
end;
$$;

create function public.research_admin_export(p_limit integer, p_offset integer, p_include_qa boolean)
returns setof slangverse_private.research_answers
language plpgsql stable security definer set search_path = '' as $$
begin
    perform slangverse_private.require_research_admin();
    return query select * from slangverse_private.research_answers
        where p_include_qa or username !~ '^qa_'
        order by received_at, event_id, participant_id limit least(greatest(p_limit,1),500)
        offset greatest(p_offset,0);
end;
$$;

create function public.research_admin_content(p_kind text, p_version text)
returns jsonb language plpgsql stable security definer set search_path = '' as $$
begin
    perform slangverse_private.require_research_admin();
    if p_kind = 'dataset' then
        return (select jsonb_build_object('version', d.version, 'total', jsonb_array_length(d.document->'entries'),
            'entries', (select coalesce(jsonb_agg(jsonb_build_object(
                'slang', e->>'term', 'definition', e->>'normalizedDefinition',
                'date', e->'sources'->0->>'sourcePublishedAt', 'source', e->'sources'->0->>'url')), '[]'::jsonb)
                from (select e from jsonb_array_elements(d.document->'entries') e limit 200) selection))
            from slangverse_private.research_datasets d where d.version=p_version);
    end if;
    if p_kind = 'bank' then
        return (select jsonb_build_object('version', b.version, 'total', b.question_count, 'packs', b.catalog->'packs')
            from slangverse_private.research_banks b where b.version=p_version);
    end if;
    return null;
end;
$$;

create function public.research_admin_reserve(
    p_request_id uuid, p_operation text, p_commit_sha text, p_dataset_version text, p_bank_version text
) returns void language plpgsql security definer set search_path = '' as $$
begin
    perform slangverse_private.require_research_admin();
    if p_operation = 'build' and not exists (select 1 from slangverse_private.research_jobs
        where operation = 'check' and status = 'success' and commit_sha = p_commit_sha
          and bank_version = p_bank_version) then
        raise exception using errcode = '22023', message = 'A passed check for the same commit and bank is required';
    end if;
    insert into slangverse_private.research_jobs (request_id, requested_by, operation, commit_sha, dataset_version, bank_version)
    values (p_request_id, auth.uid(), p_operation, p_commit_sha, p_dataset_version, p_bank_version);
end;
$$;

create function public.research_admin_dispatch_failed(p_request_id uuid)
returns void language plpgsql security definer set search_path = '' as $$
begin
    perform slangverse_private.require_research_admin();
    update slangverse_private.research_jobs set status='failed', completed_at=now(),
        summary=jsonb_build_object('failureCode','dispatch_failed')
    where request_id = p_request_id and requested_by = auth.uid() and status = 'queued';
end;
$$;

create function public.research_worker_claim(p_request_id uuid, p_commit_sha text, p_run_id bigint)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare value jsonb;
begin
    update slangverse_private.research_jobs set status='running', run_id=p_run_id
    where request_id=p_request_id and commit_sha=p_commit_sha and status='queued'
    returning jsonb_build_object('operation', operation, 'dataset_version', dataset_version, 'bank_version', bank_version) into value;
    if found then return value; end if;
    update slangverse_private.research_jobs set status='failed', run_id=p_run_id, completed_at=now(),
        summary=jsonb_build_object('failureCode','commit_changed')
    where request_id=p_request_id and commit_sha<>p_commit_sha and status='queued';
    return null;
end;
$$;

create function public.research_worker_finish(
    p_request_id uuid, p_run_id bigint, p_success boolean, p_summary jsonb, p_evidence jsonb
) returns boolean language plpgsql security definer set search_path = '' as $$
begin
    update slangverse_private.research_jobs set status=case when p_success then 'success' else 'failed' end,
        completed_at=now(), summary=p_summary, evidence=p_evidence
    where request_id=p_request_id and run_id=p_run_id and status='running';
    return found;
end;
$$;

create function public.research_worker_dataset(p_version text)
returns jsonb language sql stable security definer set search_path = '' as $$
    select document from slangverse_private.research_datasets where version=p_version;
$$;

create function public.research_worker_bank(p_version text)
returns jsonb language sql stable security definer set search_path = '' as $$
    select jsonb_build_object('catalog',catalog,'files',files)
    from slangverse_private.research_banks where version=p_version;
$$;

create function public.research_worker_import(p_dataset_version text, p_document jsonb, p_bank_version text,
    p_catalog jsonb, p_files jsonb, p_question_count integer)
returns void language plpgsql security definer set search_path = '' as $$
begin
    if p_dataset_version is not null then
        insert into slangverse_private.research_datasets(version,document) values (p_dataset_version,p_document)
        on conflict (version) do nothing;
        if not exists (select 1 from slangverse_private.research_datasets
            where version=p_dataset_version and document=p_document) then
            raise exception using errcode='22023', message='Dataset version content mismatch';
        end if;
    end if;
    if p_bank_version is not null then
        insert into slangverse_private.research_banks(version,catalog,files,question_count)
        values (p_bank_version,p_catalog,p_files,p_question_count) on conflict (version) do nothing;
        if not exists (select 1 from slangverse_private.research_banks
            where version=p_bank_version and catalog=p_catalog and files=p_files and question_count=p_question_count) then
            raise exception using errcode='22023', message='Bank version content mismatch';
        end if;
    end if;
end;
$$;

revoke all on function public.research_admin_overview(), public.research_admin_job(uuid), public.research_admin_content(text,text),
    public.research_admin_export(integer,integer,boolean), public.research_admin_reserve(uuid,text,text,text,text),
    public.research_admin_dispatch_failed(uuid)
    from public, anon, authenticated, service_role;
grant execute on function public.research_admin_overview(), public.research_admin_job(uuid), public.research_admin_content(text,text),
    public.research_admin_export(integer,integer,boolean), public.research_admin_reserve(uuid,text,text,text,text),
    public.research_admin_dispatch_failed(uuid) to authenticated;

revoke all on function public.research_worker_claim(uuid,text,bigint),
    public.research_worker_finish(uuid,bigint,boolean,jsonb,jsonb), public.research_worker_dataset(text),
    public.research_worker_bank(text), public.research_worker_import(text,jsonb,text,jsonb,jsonb,integer)
    from public, anon, authenticated, service_role;
grant execute on function public.research_worker_claim(uuid,text,bigint),
    public.research_worker_finish(uuid,bigint,boolean,jsonb,jsonb), public.research_worker_dataset(text),
    public.research_worker_bank(text), public.research_worker_import(text,jsonb,text,jsonb,jsonb,integer)
    to service_role;

commit;
