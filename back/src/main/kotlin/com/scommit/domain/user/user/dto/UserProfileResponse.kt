package com.scommit.domain.user.user.dto

import com.scommit.domain.user.user.entity.User

data class UserProfileResponse(
    val id: Long,
    val followerCount: Int,
    val profile: UserProfileDto,
) {
    constructor(user: User, followerCount: Int, profileImageUrl: String?) : this(
        checkNotNull(user.id),
        followerCount,
        UserProfileDto(user, profileImageUrl),
    )
}
