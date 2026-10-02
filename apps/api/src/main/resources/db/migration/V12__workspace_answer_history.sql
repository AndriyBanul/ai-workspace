create table workspace_answer_history (
    id varchar(36) primary key,
    workspace_id varchar(36) not null,
    question varchar(4000) not null,
    answer_json text not null,
    created_at timestamp not null,
    constraint fk_answer_history_workspace foreign key (workspace_id)
        references workspaces (id) on delete cascade
);

create index idx_answer_history_workspace_created on workspace_answer_history (workspace_id, created_at);
