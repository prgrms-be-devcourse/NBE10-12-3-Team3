package com.scommit.domain.series.seriesmedia.entity

import com.scommit.domain.media.media.entity.Media
import com.scommit.domain.series.series.entity.Series
import com.scommit.global.base.BaseEntity
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToOne
import jakarta.persistence.Table

@Entity
@Table(name = "series_media")
class SeriesMedia(
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "series_id", nullable = false, unique = true)
    val series: Series,
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "media_id", nullable = false)
    var media: Media,
) : BaseEntity()
