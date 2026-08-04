package com.scommit.domain.user.usermedia.entity

import com.scommit.domain.media.media.entity.Media
import com.scommit.domain.user.user.entity.User
import com.scommit.global.base.BaseEntity
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToOne
import jakarta.persistence.Table

@Entity
@Table(name = "user_media")
class UserMedia(
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    val user: User,
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "media_id", nullable = false)
    var media: Media,
) : BaseEntity()
