create table workspaces (
    id varchar(36) primary key,
    name varchar(255) not null,
    created_at timestamp not null,
    updated_at timestamp not null
);

create index idx_workspaces_created_at
    on workspaces (created_at);
