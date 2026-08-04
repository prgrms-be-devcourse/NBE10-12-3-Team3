package com.scommit.global.security

import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.userdetails.User

class SecurityUser(
    val id: Long,
    email: String,
    val nickname: String,
    authorities: Collection<out GrantedAuthority>,
) : User(email, "", authorities) {
    val email: String
        get() = username
}
