create table user_accounts (
    id varchar(36) primary key,
    email varchar(320) not null,
    password_hash varchar(255) not null,
    display_name varchar(255) not null,
    created_at timestamp not null,
    constraint uq_user_accounts_email
        unique (email)
);

alter table workspaces
    add column owner_id varchar(36) null;

alter table workspaces
    add constraint fk_workspaces_owner
        foreign key (owner_id)
        references user_accounts (id);

create index idx_workspaces_owner_id_created_at
    on workspaces (owner_id, created_at);
