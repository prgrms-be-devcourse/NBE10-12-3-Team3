package com.scommit.domain.user.user.dto

import jakarta.validation.constraints.NotBlank

data class UserDeleteRequest(
    @NotBlank(message = "비밀번호를 입력해주세요.")
    val password: String?,
)
