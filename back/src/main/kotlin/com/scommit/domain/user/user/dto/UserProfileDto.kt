package com.scommit.domain.user.user.dto

import com.scommit.domain.user.user.entity.User

data class UserProfileDto(
    val nickname: String,
    val profileImageUrl: String?,
    val introduction: String?,
) {
    constructor(user: User, profileImageUrl: String?) : this(user.nickname, profileImageUrl, user.introduction)
}
