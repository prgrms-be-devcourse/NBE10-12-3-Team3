package com.scommit.domain.user.user.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class UserPasswordUpdateRequest(
    @NotBlank(message = "현재 비밀번호를 입력해주세요.")
    val currentPassword: String?,
    @NotBlank(message = "새 비밀번호를 입력해주세요.")
    @Size(min = 6, message = "비밀번호는 최소 6자 이상이어야 합니다.")
    val newPassword: String,
)
