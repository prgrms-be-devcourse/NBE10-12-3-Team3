package com.scommit.domain.user.user.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class SignupRequest(
    @NotBlank(message = "이메일을 입력해주세요.")
    @Email(message = "이메일 형식이 올바르지 않습니다.")
    val email: String?,
    @NotBlank(message = "비밀번호를 입력해주세요.")
    @Size(min = 6, message = "비밀번호는 최소 6자 이상이어야 합니다.")
    val password: String,
    @NotBlank(message = "닉네임을 입력해주세요.")
    @Size(min = 2, max = 20, message = "닉네임은 최소 2자에서 최대 20자 사이입니다.")
    val nickname: String,
)
