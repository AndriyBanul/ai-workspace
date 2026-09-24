create table source_index_manifests (
    source_id varchar(36) primary key,
    workspace_id varchar(36) not null,
    active_generation varchar(36) not null,
    expected_items integer not null,
    updated_at timestamp not null,
    constraint fk_index_manifest_source foreign key (source_id)
        references workspace_files (id) on delete cascade,
    constraint fk_index_manifest_workspace foreign key (workspace_id)
        references workspaces (id) on delete cascade,
    constraint chk_index_manifest_expected_items check (expected_items > 0)
);

create index idx_index_manifests_workspace on source_index_manifests (workspace_id);
