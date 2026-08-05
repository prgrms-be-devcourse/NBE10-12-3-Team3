create table media
(
    id         bigint auto_increment primary key,
    created_at datetime(6),
    deleted_at datetime(6),
    media_type enum ('IMAGE', 'VIDEO') not null,
    url        text
);

create table users
(
    id            bigint auto_increment primary key,
    created_at    datetime(6),
    deleted_at    datetime(6),
    email         varchar(255) not null,
    introduction  varchar(255),
    nickname      varchar(255) not null,
    password      varchar(255),
    refresh_token varchar(255),
    role          enum ('ADMIN', 'USER') not null,
    updated_at    datetime(6)  not null,
    constraint uk_users_email unique (email),
    constraint uk_users_nickname unique (nickname),
    constraint uk_users_refresh_token unique (refresh_token)
);

create table series
(
    id         bigint auto_increment primary key,
    created_at datetime(6),
    deleted_at datetime(6),
    body       varchar(255),
    title      varchar(255) not null,
    updated_at datetime(6)  not null,
    user_id    bigint       not null,
    constraint fk_series_user
        foreign key (user_id) references users (id)
);

create table posts
(
    id             bigint auto_increment primary key,
    created_at     datetime(6),
    deleted_at     datetime(6),
    access_level   enum ('FREE', 'PAID') not null,
    body           text,
    bookmark_count bigint                not null,
    like_count     bigint                not null,
    publish_status enum ('DRAFT', 'PRIVATE', 'PUBLIC') not null,
    title          varchar(255)          not null,
    updated_at     datetime(6)           not null,
    view_count     bigint                not null,
    series_id      bigint,
    user_id        bigint                not null,
    constraint fk_posts_user
        foreign key (user_id) references users (id),
    constraint fk_posts_series
        foreign key (series_id) references series (id)
);

create table comments
(
    id         bigint auto_increment primary key,
    created_at datetime(6),
    deleted_at datetime(6),
    body       text,
    updated_at datetime(6) not null,
    post_id    bigint      not null,
    user_id    bigint      not null,
    constraint fk_comments_user
        foreign key (user_id) references users (id),
    constraint fk_comments_post
        foreign key (post_id) references posts (id)
);

create table post_bookmarks
(
    id         bigint auto_increment primary key,
    created_at datetime(6),
    deleted_at datetime(6),
    post_id    bigint not null,
    user_id    bigint not null,
    constraint uk_post_bookmarks_post_user
        unique (post_id, user_id),
    constraint fk_post_bookmarks_user
        foreign key (user_id) references users (id),
    constraint fk_post_bookmarks_post
        foreign key (post_id) references posts (id)
);

create table post_likes
(
    id         bigint auto_increment primary key,
    created_at datetime(6),
    deleted_at datetime(6),
    post_id    bigint not null,
    user_id    bigint not null,
    constraint uk_post_likes_post_user
        unique (post_id, user_id),
    constraint fk_post_likes_post
        foreign key (post_id) references posts (id),
    constraint fk_post_likes_user
        foreign key (user_id) references users (id)
);

create table post_media
(
    id         bigint auto_increment primary key,
    created_at datetime(6),
    deleted_at datetime(6),
    type       enum ('THUMBNAIL', 'BODY') not null,
    media_id   bigint                     not null,
    post_id    bigint                     not null,
    constraint uk_post_media_media
        unique (media_id),
    constraint fk_post_media_post
        foreign key (post_id) references posts (id),
    constraint fk_post_media_media
        foreign key (media_id) references media (id)
);

create index idx_series_deleted_at
    on series (deleted_at);

create index idx_series_user_deleted
    on series (user_id, deleted_at);

create table series_media
(
    id         bigint auto_increment primary key,
    created_at datetime(6),
    deleted_at datetime(6),
    media_id   bigint not null,
    series_id  bigint not null,
    constraint uk_series_media_series
        unique (series_id),
    constraint uk_series_media_media
        unique (media_id),
    constraint fk_series_media_series
        foreign key (series_id) references series (id),
    constraint fk_series_media_media
        foreign key (media_id) references media (id)
);

create table subscriptions
(
    id                bigint auto_increment primary key,
    created_at        datetime(6),
    deleted_at        datetime(6),
    expired_at        date,
    started_at        date,
    subscription_tier enum ('FOLLOW', 'MEMBERSHIP') not null,
    creator_id        bigint                        not null,
    user_id           bigint                        not null,
    constraint uk_subscription_user_creator
        unique (user_id, creator_id),
    constraint fk_subscriptions_creator
        foreign key (creator_id) references users (id),
    constraint fk_subscriptions_user
        foreign key (user_id) references users (id)
);

create table user_media
(
    id         bigint auto_increment primary key,
    created_at datetime(6),
    deleted_at datetime(6),
    media_id   bigint not null,
    user_id    bigint not null,
    constraint uk_user_media_user
        unique (user_id),
    constraint uk_user_media_media
        unique (media_id),
    constraint fk_user_media_user
        foreign key (user_id) references users (id),
    constraint fk_user_media_media
        foreign key (media_id) references media (id)
);

create table coupon_policies
(
    id               bigint auto_increment primary key,
    created_at       datetime(6),
    deleted_at       datetime(6),
    title            varchar(255) not null,
    description      text,
    discount_type    enum ('PERCENT', 'FIXED') not null,
    discount_value   int          not null,
    total_quantity   int          not null,
    issued_quantity  int          not null default 0,
    start_at         datetime(6)  not null,
    end_at           datetime(6)  not null,
    expiry_type      enum ('RELATIVE', 'ABSOLUTE') not null,
    valid_days       int,
    fixed_expired_at datetime(6)
);

create table user_coupons
(
    id               bigint auto_increment primary key,
    created_at       datetime(6),
    deleted_at       datetime(6),
    user_id          bigint      not null,
    coupon_policy_id bigint      not null,
    issued_at        datetime(6) not null,
    expired_at       datetime(6) not null,
    used_at          datetime(6),
    constraint uk_user_coupons_user_policy
        unique (user_id, coupon_policy_id),
    constraint fk_user_coupons_user
        foreign key (user_id) references users (id),
    constraint fk_user_coupons_policy
        foreign key (coupon_policy_id) references coupon_policies (id)
);
