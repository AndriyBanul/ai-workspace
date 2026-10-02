create table ingestion_jobs (
    id varchar(36) primary key,
    workspace_id varchar(255) not null,
    status varchar(32) not null,
    created_at timestamp not null,
    updated_at timestamp not null,
    completed_at timestamp null
);

create table ingestion_job_steps (
    id varchar(36) primary key,
    job_id varchar(36) not null,
    content_type varchar(32) not null,
    status varchar(32) not null,
    started_at timestamp null,
    completed_at timestamp null,
    error_message varchar(1000) null,
    constraint fk_ingestion_job_steps_job
        foreign key (job_id)
        references ingestion_jobs (id)
        on delete cascade,
    constraint uq_ingestion_job_steps_job_content_type
        unique (job_id, content_type)
);

create index idx_ingestion_jobs_workspace_id
    on ingestion_jobs (workspace_id);

create index idx_ingestion_job_steps_job_id
    on ingestion_job_steps (job_id);
