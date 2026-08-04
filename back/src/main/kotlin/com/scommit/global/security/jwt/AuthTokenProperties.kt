package com.scommit.global.security.jwt

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "auth-token")
data class AuthTokenProperties(
    val accessToken: AccessToken,
    val refreshToken: RefreshToken,
) {
    data class AccessToken(
        val secretKey: String,
        val expiration: Duration,
        val cookieMaxAge: Duration,
    )

    data class RefreshToken(
        val cookieMaxAge: Duration,
    )
}
