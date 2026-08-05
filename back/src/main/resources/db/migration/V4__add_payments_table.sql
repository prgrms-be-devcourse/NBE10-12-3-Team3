create table payments
(
    id                bigint auto_increment primary key,
    created_at        datetime(6),
    deleted_at        datetime(6),
    user_id           bigint       not null,
    order_id          varchar(255) not null,
    order_name        varchar(255) not null,
    target_creator_id bigint       not null,
    amount            bigint       not null,
    status            enum ('READY', 'IN_PROGRESS', 'DONE', 'CANCELED', 'ABORTED') not null,
    payment_key       varchar(255),
    constraint uk_payments_order_id
        unique (order_id),
    constraint fk_payments_user
        foreign key (user_id) references users (id)
);
