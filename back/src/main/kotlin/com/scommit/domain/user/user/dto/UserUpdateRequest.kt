package com.scommit.domain.user.user.dto

import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class UserUpdateRequest(
    @Size(min = 2, max = 20, message = "닉네임은 최소 2자에서 최대 20자 사이입니다.")
    @Pattern(regexp = ".*\\S.*", message = "닉네임은 공백으로만 이루어질 수 없습니다.")
    val nickname: String?,
    @Size(max = 100, message = "소개글은 100자 이내로 입력하셔야 합니다.")
    val introduction: String?,
)
