package com.scommit.global.security

import com.scommit.domain.user.user.entity.UserRole
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.userdetails.User
import com.scommit.domain.user.user.entity.User as DomainUser

class SecurityUser(
    val id: Long,
    email: String,
    val nickname: String,
    val role: UserRole,
    authorities: Collection<out GrantedAuthority>,
) : User(email, "", authorities) {
    val email: String
        get() = username

    val user: DomainUser
        get() = DomainUser(id, email, nickname, role)
}
