alter table posts
    modify user_id bigint not null;

alter table comments
    modify user_id bigint not null;
