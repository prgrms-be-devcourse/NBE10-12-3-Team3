create table MEDIA
(
    ID         BIGINT auto_increment primary key,
    CREATED_AT DATETIME,
    DELETED_AT DATETIME,
    MEDIA_TYPE ENUM ('IMAGE', 'VIDEO') not null,
    URL        TEXT
);

create table USERS
(
    ID            BIGINT auto_increment primary key,
    CREATED_AT    DATETIME,
    DELETED_AT    DATETIME,
    EMAIL         VARCHAR(255) not null,
    INTRODUCTION  VARCHAR(255),
    NICKNAME      VARCHAR(255) not null,
    PASSWORD      VARCHAR(255),
    REFRESH_TOKEN VARCHAR(255),
    ROLE          ENUM ('ADMIN', 'USER') not null,
    UPDATED_AT    DATETIME               not null,
    constraint UK_USERS_EMAIL unique (EMAIL),
    constraint UK_USERS_NICKNAME unique (NICKNAME),
    constraint UK_USERS_REFRESH_TOKEN unique (REFRESH_TOKEN)
);

create table SERIES
(
    ID         BIGINT auto_increment primary key,
    CREATED_AT DATETIME,
    DELETED_AT DATETIME,
    BODY       VARCHAR(255),
    TITLE      VARCHAR(255) not null,
    UPDATED_AT DATETIME     not null,
    USER_ID    BIGINT       not null,
    constraint FK_SERIES_USER
        foreign key (USER_ID) references USERS (ID)
);

create table POSTS
(
    ID             BIGINT auto_increment primary key,
    CREATED_AT     DATETIME,
    DELETED_AT     DATETIME,
    ACCESS_LEVEL   ENUM ('FREE', 'PAID') not null,
    BODY           TEXT,
    BOOKMARK_COUNT BIGINT                not null,
    LIKE_COUNT     BIGINT                not null,
    PUBLISH_STATUS ENUM ('DRAFT', 'PRIVATE', 'PUBLIC') not null,
    TITLE          VARCHAR(255)          not null,
    UPDATED_AT     DATETIME              not null,
    VIEW_COUNT     BIGINT                not null,
    SERIES_ID      BIGINT,
    USER_ID        BIGINT                not null,
    constraint FK_POSTS_USER
        foreign key (USER_ID) references USERS (ID),
    constraint FK_POSTS_SERIES
        foreign key (SERIES_ID) references SERIES (ID)
);

create table COMMENTS
(
    ID         BIGINT auto_increment primary key,
    CREATED_AT DATETIME,
    DELETED_AT DATETIME,
    BODY       TEXT,
    UPDATED_AT DATETIME  not null,
    POST_ID    BIGINT    not null,
    USER_ID    BIGINT    not null,
    constraint FK_COMMENTS_USER
        foreign key (USER_ID) references USERS (ID),
    constraint FK_COMMENTS_POST
        foreign key (POST_ID) references POSTS (ID)
);

create table POST_BOOKMARKS
(
    ID         BIGINT auto_increment primary key,
    CREATED_AT DATETIME,
    DELETED_AT DATETIME,
    POST_ID    BIGINT not null,
    USER_ID    BIGINT not null,
    constraint UK_POST_BOOKMARKS_POST_USER
        unique (POST_ID, USER_ID),
    constraint FK_POST_BOOKMARKS_USER
        foreign key (USER_ID) references USERS (ID),
    constraint FK_POST_BOOKMARKS_POST
        foreign key (POST_ID) references POSTS (ID)
);

create table POST_LIKES
(
    ID         BIGINT auto_increment primary key,
    CREATED_AT DATETIME,
    DELETED_AT DATETIME,
    POST_ID    BIGINT not null,
    USER_ID    BIGINT not null,
    constraint UK_POST_LIKES_POST_USER
        unique (POST_ID, USER_ID),
    constraint FK_POST_LIKES_POST
        foreign key (POST_ID) references POSTS (ID),
    constraint FK_POST_LIKES_USER
        foreign key (USER_ID) references USERS (ID)
);

create table POST_MEDIA
(
    ID         BIGINT auto_increment primary key,
    CREATED_AT DATETIME,
    DELETED_AT DATETIME,
    TYPE       ENUM ('THUMBNAIL', 'BODY') not null,
    MEDIA_ID   BIGINT                     not null,
    POST_ID    BIGINT                     not null,
    constraint UK_POST_MEDIA_MEDIA
        unique (MEDIA_ID),
    constraint FK_POST_MEDIA_POST
        foreign key (POST_ID) references POSTS (ID),
    constraint FK_POST_MEDIA_MEDIA
        foreign key (MEDIA_ID) references MEDIA (ID)
);

create index IDX_SERIES_DELETED_AT
    on SERIES (DELETED_AT);

create index IDX_SERIES_USER_DELETED
    on SERIES (USER_ID, DELETED_AT);

create table SERIES_MEDIA
(
    ID         BIGINT auto_increment primary key,
    CREATED_AT DATETIME,
    DELETED_AT DATETIME,
    MEDIA_ID   BIGINT not null,
    SERIES_ID  BIGINT not null,
    constraint UK_SERIES_MEDIA_MEDIA
        unique (MEDIA_ID),
    constraint UK_SERIES_MEDIA_SERIES
        unique (SERIES_ID),
    constraint FK_SERIES_MEDIA_SERIES
        foreign key (SERIES_ID) references SERIES (ID),
    constraint FK_SERIES_MEDIA_MEDIA
        foreign key (MEDIA_ID) references MEDIA (ID)
);

create table SUBSCRIPTIONS
(
    ID                BIGINT auto_increment primary key,
    CREATED_AT        DATETIME,
    DELETED_AT        DATETIME,
    EXPIRED_AT        DATE,
    STARTED_AT        DATE,
    SUBSCRIPTION_TIER ENUM ('FOLLOW', 'MEMBERSHIP') not null,
    CREATOR_ID        BIGINT                        not null,
    USER_ID           BIGINT                        not null,
    constraint UK_SUBSCRIPTION_USER_CREATOR
        unique (USER_ID, CREATOR_ID),
    constraint FK_SUBSCRIPTIONS_CREATOR
        foreign key (CREATOR_ID) references USERS (ID),
    constraint FK_SUBSCRIPTIONS_USER
        foreign key (USER_ID) references USERS (ID)
);

create table USER_MEDIA
(
    ID         BIGINT auto_increment primary key,
    CREATED_AT DATETIME,
    DELETED_AT DATETIME,
    MEDIA_ID   BIGINT not null,
    USER_ID    BIGINT not null,
    constraint UK_USER_MEDIA_MEDIA
        unique (MEDIA_ID),
    constraint UK_USER_MEDIA_USER
        unique (USER_ID),
    constraint FK_USER_MEDIA_USER
        foreign key (USER_ID) references USERS (ID),
    constraint FK_USER_MEDIA_MEDIA
        foreign key (MEDIA_ID) references MEDIA (ID)
);
