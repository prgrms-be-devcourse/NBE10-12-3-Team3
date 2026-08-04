package com.scommit.domain.series.seriesmedia.dto

import com.scommit.domain.media.media.entity.MediaType
import com.scommit.domain.series.seriesmedia.entity.SeriesMedia

data class SeriesMediaResponse(
    val id: Long,
    val seriesId: Long,
    val url: String?,
    val mediaType: MediaType,
) {
    constructor(seriesMedia: SeriesMedia) : this(
        checkNotNull(seriesMedia.id),
        checkNotNull(seriesMedia.series.id),
        seriesMedia.media.url,
        seriesMedia.media.type,
    )
}
