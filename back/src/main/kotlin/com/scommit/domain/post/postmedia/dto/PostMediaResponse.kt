package com.scommit.domain.post.postmedia.dto

import com.scommit.domain.media.media.entity.MediaType
import com.scommit.domain.post.postmedia.entity.PostMedia
import com.scommit.domain.post.postmedia.entity.PostMediaType

data class PostMediaResponse(
    val id: Long,
    val postId: Long,
    val url: String?,
    val mediaType: MediaType,
    val type: PostMediaType,
) {
    constructor(postMedia: PostMedia) : this(
        checkNotNull(postMedia.id),
        checkNotNull(postMedia.post.id),
        postMedia.media.url,
        postMedia.media.type,
        postMedia.type,
    )
}
