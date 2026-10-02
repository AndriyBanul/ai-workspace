alter table workspace_files alter column storage_key drop not null;
alter table workspace_files alter column checksum_sha256 drop not null;
alter table workspace_files add column source_url varchar(2048) null;

create index idx_workspace_files_source_url
    on workspace_files (workspace_id, source_url);
