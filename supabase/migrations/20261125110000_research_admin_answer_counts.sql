begin;

create or replace function public.research_admin_bank_pack(p_version text, p_pack_id text)
returns jsonb language plpgsql stable security definer set search_path = '' as $$
begin
    perform slangverse_private.require_research_admin();
    return (
        select (b.files->>p_pack_id)::jsonb || jsonb_build_object('answerCounts', coalesce((
            select jsonb_object_agg(t.question_id, jsonb_build_object('correct',t.correct,'incorrect',t.incorrect))
            from (
                select a.question_id, count(*) filter (where a.is_correct) as correct,
                    count(*) filter (where not a.is_correct) as incorrect
                from slangverse_private.research_answers a
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

commit;
