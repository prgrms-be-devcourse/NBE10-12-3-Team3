package com.scommit.global.dto

import org.springframework.data.domain.Page

data class PageResponse<T : Any>(
    val content: List<T>,
    val pageNumber: Int,
    val pageSize: Int,
    val totalElements: Long,
    val totalPages: Int,
    val isLast: Boolean,
) {
    constructor(page: Page<T>) : this(
        page.content,
        page.number,
        page.size,
        page.totalElements,
        page.totalPages,
        page.isLast,
    )
}
