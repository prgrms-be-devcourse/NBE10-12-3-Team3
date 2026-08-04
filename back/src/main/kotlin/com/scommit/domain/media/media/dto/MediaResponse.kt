package com.scommit.domain.media.media.dto

import com.scommit.domain.media.media.entity.Media
import com.scommit.domain.media.media.entity.MediaType

data class MediaResponse(
    val id: Long,
    val url: String?,
    val type: MediaType,
) {
    constructor(media: Media) : this(checkNotNull(media.id), media.url, media.type)
}
