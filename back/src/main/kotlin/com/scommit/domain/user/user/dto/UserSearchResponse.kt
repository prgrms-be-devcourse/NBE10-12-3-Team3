package com.scommit.domain.user.user.dto

import com.scommit.domain.user.user.entity.User

data class UserSearchResponse(
    val id: Long,
    val nickname: String,
    val introduction: String?,
) {
    constructor(user: User) : this(checkNotNull(user.id), user.nickname, user.introduction)
}
