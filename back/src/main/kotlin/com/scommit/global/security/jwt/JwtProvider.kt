package com.scommit.global.security.jwt

import com.scommit.domain.user.user.entity.UserRole
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.io.Decoders
import io.jsonwebtoken.security.Keys
import org.springframework.stereotype.Component
import java.util.Date
import javax.crypto.SecretKey

@Component
class JwtProvider(
    authTokenProperties: AuthTokenProperties,
) {
    private val signingKey: SecretKey =
        Keys.hmacShaKeyFor(Decoders.BASE64.decode(authTokenProperties.accessToken.secretKey))
    private val accessTokenExpiration = authTokenProperties.accessToken.expiration

    fun generateAccessToken(
        userId: Long,
        email: String,
        nickname: String,
        role: UserRole,
    ): String {
        val now = Date()
        val expirationDate = Date(now.time + accessTokenExpiration.toMillis())
        return Jwts
            .builder()
            .claim("id", userId)
            .claim("email", email)
            .claim("nickname", nickname)
            .claim("role", role.name)
            .issuedAt(now)
            .expiration(expirationDate)
            .signWith(signingKey)
            .compact()
    }

    @JvmRecord
    data class AccessTokenPayload(
        val id: Long,
        val email: String,
        val nickname: String,
        val role: UserRole,
    )

    fun parseAccessToken(token: String): AccessTokenPayload {
        val claims =
            Jwts
                .parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .payload

        return AccessTokenPayload(
            id = claims.get("id", java.lang.Long::class.java) as Long,
            email = claims.get("email", String::class.java),
            nickname = claims.get("nickname", String::class.java),
            role = UserRole.valueOf(claims.get("role", String::class.java)),
        )
    }
}
