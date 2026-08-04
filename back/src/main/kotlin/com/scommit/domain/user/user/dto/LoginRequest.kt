package com.scommit.domain.user.user.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank

data class LoginRequest(
    @Email(message = "이메일 형식이 올바르지 않습니다.")
    @NotBlank(message = "이메일을 입력해주세요.")
    val email: String,
    @NotBlank(message = "비밀번호를 입력해주세요.")
    val password: String?,
)
