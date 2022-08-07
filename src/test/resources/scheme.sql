create table scheduled_jobs
(
    id char(36) primary key,
    scheduled_at timestamp with time zone,
    trigger_at timestamp with time zone,
    status smallint ,
    updated_at timestamp with time zone,
    payload jsonb
);

create table scheduled_jobs_change_log(
    id char(36) primary key ,
    job_id char(36),
    status smallint ,
    created_at timestamp with time zone,
    payload jsonb
);