package com.scommit.domain.user.usermedia.dto

import com.scommit.domain.media.media.entity.MediaType
import com.scommit.domain.user.usermedia.entity.UserMedia

data class UserMediaResponse(
    val id: Long,
    val userId: Long,
    val url: String?,
    val mediaType: MediaType,
) {
    constructor(userMedia: UserMedia) : this(
        checkNotNull(userMedia.id),
        checkNotNull(userMedia.user.id),
        userMedia.media.url,
        userMedia.media.type,
    )
}
