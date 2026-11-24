begin;

create or replace function public.research_admin_dataset_page(p_version text, p_offset integer)
returns jsonb language plpgsql stable security definer set search_path = '' as $$
begin
    perform slangverse_private.require_research_admin();
    return (select jsonb_build_object(
        'version', d.version,
        'total', jsonb_array_length(d.document->'entries'),
        'extraction', d.document->>'extraction',
        'entries', (select coalesce(jsonb_agg(jsonb_build_object(
            'slang', e->>'term', 'definition', e->>'normalizedDefinition',
            'date', e->'sources'->0->>'sourcePublishedAt',
            'source', e->'sources'->0->>'url', 'quote', e->'sources'->0->>'quote'
        ) order by position), '[]'::jsonb)
        from (select e, position
              from jsonb_array_elements(d.document->'entries') with ordinality as entries(e, position)
              order by position offset greatest(p_offset,0) limit 50) selection))
    from slangverse_private.research_datasets d where d.version=p_version);
end;
$$;

create or replace function public.research_admin_bank_pack(p_version text, p_pack_id text)
returns jsonb language plpgsql stable security definer set search_path = '' as $$
begin
    perform slangverse_private.require_research_admin();
    return (select (b.files->>p_pack_id)::jsonb from slangverse_private.research_banks b
        where b.version=p_version and exists (
            select 1 from jsonb_array_elements(b.catalog->'packs') pack
            where pack->>'packId'=p_pack_id
        ));
end;
$$;

revoke all on function public.research_admin_dataset_page(text,integer),
    public.research_admin_bank_pack(text,text) from public, anon, authenticated, service_role;
grant execute on function public.research_admin_dataset_page(text,integer),
    public.research_admin_bank_pack(text,text) to authenticated;

notify pgrst, 'reload schema';

commit;
