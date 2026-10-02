create table user_daily_quota_usage (
    id varchar(80) primary key,
    user_id varchar(36) not null,
    action varchar(24) not null,
    window_start timestamp not null,
    consumed integer not null,
    constraint fk_quota_user foreign key (user_id)
        references user_accounts (id) on delete cascade,
    constraint chk_quota_consumed check (consumed >= 0)
);

create index idx_quota_window on user_daily_quota_usage (window_start);
