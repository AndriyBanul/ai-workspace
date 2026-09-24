create table orchestration_submission_requests (
    id varchar(64) primary key,
    owner_id varchar(36) not null,
    workspace_id varchar(36) not null,
    fingerprint varchar(64) not null,
    response_json text null,
    created_at timestamp not null,
    constraint fk_submission_workspace foreign key (workspace_id)
        references workspaces (id) on delete cascade
);

create index idx_submission_owner_created on orchestration_submission_requests (owner_id, created_at);
