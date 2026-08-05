alter table post_likes
    add index idx_post_likes_post (post_id);

alter table post_likes
    drop index uk_post_likes_post_user;

alter table post_likes
    add constraint uk_post_likes_post_user
        unique (user_id, post_id);

alter table post_bookmarks
    add index idx_post_bookmarks_post (post_id);

alter table post_bookmarks
    drop index uk_post_bookmarks_post_user;

alter table post_bookmarks
    add constraint uk_post_bookmarks_post_user
        unique (user_id, post_id);
