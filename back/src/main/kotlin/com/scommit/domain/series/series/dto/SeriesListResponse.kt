package com.scommit.domain.series.series.dto

import java.time.LocalDateTime

data class SeriesListResponse(
    val id: Long,
    val userId: Long,
    val nickname: String,
    val title: String,
    val body: String?,
    val postCount: Long,
    val thumbnailUrl: String?,
    val createdAt: LocalDateTime?,
    val updatedAt: LocalDateTime?,
)
