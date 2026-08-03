package com.scommit.domain.post.postmedia.entity

import com.scommit.domain.media.media.entity.Media
import com.scommit.domain.post.post.entity.Post
import com.scommit.global.base.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToOne
import jakarta.persistence.Table

@Entity
@Table(name = "post_media")
class PostMedia(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    val post: Post,
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "media_id", nullable = false)
    var media: Media,
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    val type: PostMediaType,
) : BaseEntity()
