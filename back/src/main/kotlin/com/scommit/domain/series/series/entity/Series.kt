package com.scommit.domain.series.series.entity

import com.scommit.domain.user.user.entity.User
import com.scommit.global.base.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.springframework.data.annotation.LastModifiedDate
import java.time.LocalDateTime

@Entity
@Table(
    name = "series",
    indexes = [
        Index(name = "idx_series_user_deleted", columnList = "user_id, deleted_at"),
        Index(name = "idx_series_deleted_at", columnList = "deleted_at"),
    ],
)
class Series(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User,
    @Column(nullable = false)
    var title: String,
    var body: String? = null,
) : BaseEntity() {
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime? = null
        protected set

    fun update(
        title: String,
        body: String?,
    ) {
        this.title = title
        this.body = body
    }
}
