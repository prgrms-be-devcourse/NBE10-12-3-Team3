package com.scommit.domain.media.media.entity

import com.scommit.global.base.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table

@Entity
@Table(name = "media")
class Media(
    @Column(columnDefinition = "TEXT")
    val url: String?,
    @Enumerated(EnumType.STRING)
    @Column(name = "media_type", nullable = false)
    val type: MediaType,
) : BaseEntity()
