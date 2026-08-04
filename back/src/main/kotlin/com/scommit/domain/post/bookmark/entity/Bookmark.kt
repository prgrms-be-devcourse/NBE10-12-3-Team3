package com.scommit.domain.post.bookmark.entity

import com.scommit.domain.post.post.entity.Post
import com.scommit.domain.user.user.entity.User
import com.scommit.global.base.BaseEntity
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "post_bookmarks",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_post_bookmarks_post_user",
            columnNames = ["post_id", "user_id"],
        ),
    ],
)
class Bookmark(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    val post: Post,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,
) : BaseEntity()
