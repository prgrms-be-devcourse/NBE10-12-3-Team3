package com.scommit.domain.post.post.entity

import com.scommit.domain.post.postmedia.entity.PostMedia
import com.scommit.domain.series.series.entity.Series
import com.scommit.domain.user.user.entity.User
import com.scommit.global.base.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import org.springframework.data.annotation.LastModifiedDate
import java.time.LocalDateTime

@Entity
@Table(name = "posts")
class Post(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "series_id")
    var series: Series?,
    // Kotlin 타입은 nullable이지만 @Column(nullable = false)로 DB 제약은 그대로 유지한다.
    // title=null로 들어오면 Bean Validation이 없어 그대로 INSERT까지 가서 DB 제약 위반(500-1)이
    // 나는 게 실제(버그) 동작이라, 여기서 String으로 막아버리면 그 동작을 재현할 수 없다.
    @Column(nullable = false)
    var title: String?,
    @Column(columnDefinition = "TEXT")
    var body: String?,
    @Enumerated(EnumType.STRING)
    @Column(name = "publish_status", nullable = false)
    var publishStatus: PublishStatus,
    @Enumerated(EnumType.STRING)
    @Column(name = "access_level", nullable = false)
    var accessLevel: PostAccessLevel,
) : BaseEntity() {
    @Column(name = "view_count", nullable = false)
    var viewCount: Long = 0L
        protected set

    @Column(name = "like_count", nullable = false)
    var likeCount: Long = 0L
        protected set

    @Column(name = "bookmark_count", nullable = false)
    var bookmarkCount: Long = 0L
        protected set

    @OneToMany(mappedBy = "post", fetch = FetchType.LAZY)
    val medias: MutableList<PostMedia> = mutableListOf()

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime? = null
        protected set

    fun update(
        title: String?,
        body: String?,
        publishStatus: PublishStatus,
        accessLevel: PostAccessLevel,
        series: Series?,
    ) {
        this.title = title
        this.body = body
        this.publishStatus = publishStatus
        this.accessLevel = accessLevel
        this.series = series
    }

    fun updateSeries(series: Series?) {
        this.series = series
    }

    fun increaseViewCount() {
        viewCount++
    }

    fun increaseLikeCount() {
        likeCount++
    }

    fun decreaseLikeCount() {
        if (likeCount > 0) likeCount--
    }

    fun increaseBookmarkCount() {
        bookmarkCount++
    }

    fun decreaseBookmarkCount() {
        if (bookmarkCount > 0) bookmarkCount--
    }
}
