alter table source_recovery_tasks
    add column job_id varchar(36) null;

alter table source_recovery_tasks
    add constraint fk_source_recovery_task_job
        foreign key (job_id)
        references ingestion_jobs (id)
        on delete set null;
