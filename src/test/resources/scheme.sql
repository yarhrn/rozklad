create table scheduled_tasks
(
    id char(36) primary key not null ,
    scheduled_at timestamp with time zone not null ,
    trigger_at timestamp with time zone not null ,
    status smallint not null ,
    updated_at timestamp with time zone not null ,
    failed_reason smallint ,
    payload jsonb not null
);

create table scheduled_tasks_change_log(
    id char(36) primary key not null ,
    task_id char(36) not null ,
    status smallint not null ,
    created_at timestamp with time zone not null ,
    failed_reason smallint ,
    payload jsonb not null
);

create unique index scheduled_tasks_trigger_at_index
    on scheduled_tasks(trigger_at);