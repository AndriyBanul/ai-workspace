create table workspace_files (
    id varchar(36) primary key,
    workspace_id varchar(36) not null,
    original_filename varchar(1024) not null,
    content_type varchar(255) null,
    size_bytes bigint not null,
    storage_key varchar(1024) not null,
    checksum_sha256 varchar(64) not null,
    source_type varchar(32) not null,
    status varchar(32) not null,
    created_at timestamp not null,
    updated_at timestamp not null,
    deleted_at timestamp null,
    constraint fk_workspace_files_workspace
        foreign key (workspace_id)
        references workspaces (id)
        on delete cascade
);

create index idx_workspace_files_workspace_created_at
    on workspace_files (workspace_id, created_at);

create index idx_workspace_files_workspace_deleted_at
    on workspace_files (workspace_id, deleted_at);
