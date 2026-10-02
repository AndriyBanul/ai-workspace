create table source_recovery_tasks (
    source_id varchar(36) primary key,
    workspace_id varchar(36) not null,
    operation_type varchar(32) not null,
    status varchar(32) not null,
    attempt_count integer not null default 0,
    next_attempt_at timestamp null,
    lease_expires_at timestamp null,
    last_error_code varchar(100) null,
    last_error_message varchar(1000) null,
    created_at timestamp not null,
    updated_at timestamp not null,
    constraint fk_source_recovery_task_source
        foreign key (source_id)
        references workspace_files (id)
        on delete cascade,
    constraint fk_source_recovery_task_workspace
        foreign key (workspace_id)
        references workspaces (id)
        on delete cascade,
    constraint chk_source_recovery_attempt_count
        check (attempt_count >= 0)
);

create index idx_source_recovery_due
    on source_recovery_tasks (status, next_attempt_at);

create index idx_source_recovery_lease
    on source_recovery_tasks (status, lease_expires_at);

insert into source_recovery_tasks (
    source_id, workspace_id, operation_type, status, attempt_count,
    next_attempt_at, lease_expires_at, created_at, updated_at
)
select id,
       workspace_id,
       'PROCESS',
       case
           when status = 'PROCESSED' then 'COMPLETED'
           when status in ('UPLOADED', 'PROCESSING') then 'SCHEDULED'
           else 'DEAD_LETTER'
       end,
       0,
       case when status in ('UPLOADED', 'PROCESSING', 'PROCESSED') then current_timestamp else null end,
       null,
       created_at,
       current_timestamp
from workspace_files
where deleted_at is null;
