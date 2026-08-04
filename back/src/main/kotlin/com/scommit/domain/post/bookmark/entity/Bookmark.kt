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
            // user_id를 선두로 두는 이유: post_id+user_id 동등조건 조회는 컬럼 순서와 무관하게 이
            // 인덱스를 그대로 타지만, findByUserIdAndPostDeletedAtIsNull("내 북마크 목록")처럼
            // user_id만으로 거는 조회는 선두 컬럼이 아니면 인덱스를 못 탄다.
            columnNames = ["user_id", "post_id"],
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
