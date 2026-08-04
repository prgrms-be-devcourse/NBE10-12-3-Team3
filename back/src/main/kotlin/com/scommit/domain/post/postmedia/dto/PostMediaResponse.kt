package com.scommit.domain.post.postmedia.dto

import com.scommit.domain.media.media.entity.MediaType
import com.scommit.domain.post.postmedia.entity.PostMedia
import com.scommit.domain.post.postmedia.entity.PostMediaType

// @JvmRecord: 원래 Java record였고, PostControllerE2ETest 등 Java 호출부가
// xxx() record 접근자를 그대로 쓴다.
@JvmRecord
data class PostMediaResponse(
    val id: Long?,
    val postId: Long?,
    val url: String?,
    val mediaType: MediaType,
    val type: PostMediaType,
) {
    constructor(postMedia: PostMedia) : this(
        postMedia.id,
        postMedia.post.id,
        postMedia.media.url,
        postMedia.media.type,
        postMedia.type,
    )
}
