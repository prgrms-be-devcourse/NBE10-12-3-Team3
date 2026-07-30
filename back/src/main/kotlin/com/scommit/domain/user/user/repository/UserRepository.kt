package com.scommit.domain.user.user.repository

import com.scommit.domain.user.user.entity.User
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface UserRepository : JpaRepository<User, Long> {
    fun existsByEmail(email: String): Boolean

    fun existsByNickname(nickname: String): Boolean

    fun findByRefreshTokenAndDeletedAtIsNull(refreshToken: String): User?

    fun findByIdAndDeletedAtIsNull(id: Long): User?

    fun findByEmailAndDeletedAtIsNull(email: String): User?

    // 닉네임 키워드 검색
    fun findByNicknameContainingAndDeletedAtIsNull(
        nickname: String,
        pageable: Pageable,
    ): Page<User>
}
