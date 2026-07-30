package com.scommit.domain.user.user.entity

import com.scommit.global.base.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "users")
class User(
    email: String,
    password: String? = null,
    nickname: String,
    introduction: String? = null,
    role: UserRole,
) : BaseEntity() {
    @Column(unique = true, nullable = false)
    var email: String = email
        protected set

    var password: String? = password
        protected set

    @Column(unique = true, nullable = false)
    var nickname: String = nickname
        protected set

    var introduction: String? = introduction
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var role: UserRole = role
        protected set

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime? = null
        protected set

    @Column(name = "refresh_token", unique = true)
    var refreshToken: String? = null
        protected set

    constructor(id: Long, email: String, nickname: String) : this(
        email = email,
        nickname = nickname,
        role = UserRole.USER,
    ) {
        setId(id)
    }

    constructor(id: Long, email: String, nickname: String, role: UserRole) : this(id, email, nickname) {
        this.role = role
    }

    val authorities: Collection<GrantedAuthority>
        get() = authoritiesAsStringList().map { SimpleGrantedAuthority(it) }

    private fun authoritiesAsStringList(): List<String> =
        listOfNotNull(if (role == UserRole.ADMIN) "ROLE_ADMIN" else null)

    fun resetRefreshToken() {
        refreshToken = UUID.randomUUID().toString()
    }

    fun update(
        nickname: String?,
        introduction: String?,
    ) {
        if (!nickname.isNullOrBlank()) {
            this.nickname = nickname
        }
        introduction?.let { this.introduction = it }
    }

    fun updatePassword(password: String) {
        this.password = password
    }
}
