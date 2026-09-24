alter table source_recovery_tasks
    add column lease_token varchar(36) null;

update source_recovery_tasks
set status = 'SCHEDULED', lease_expires_at = null, next_attempt_at = current_timestamp
where status = 'RUNNING';
