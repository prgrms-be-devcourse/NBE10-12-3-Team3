package com.scommit.domain.series.series.dto

import com.scommit.domain.series.series.entity.Series
import java.time.LocalDateTime

data class SeriesResponse(
    val id: Long,
    val userId: Long,
    val nickname: String,
    val title: String,
    val body: String?,
    val createdAt: LocalDateTime?,
    val updatedAt: LocalDateTime?,
) {
    constructor(series: Series) : this(
        id = checkNotNull(series.id),
        userId = checkNotNull(series.user.id),
        nickname = series.user.nickname,
        title = series.title,
        body = series.body,
        createdAt = series.createdAt,
        updatedAt = series.updatedAt,
    )
}
