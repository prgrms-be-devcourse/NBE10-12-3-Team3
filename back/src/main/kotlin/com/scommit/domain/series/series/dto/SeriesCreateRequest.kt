package com.scommit.domain.series.series.dto

import jakarta.validation.constraints.NotBlank

data class SeriesCreateRequest(
    @NotBlank(message = "제목은 필수입니다.")
    val title: String?,
    val body: String?,
)
