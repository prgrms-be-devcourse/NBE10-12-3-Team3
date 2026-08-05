package com.scommit.global.security

import com.scommit.domain.user.user.entity.User
import com.scommit.global.security.jwt.AuthTokenProperties
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

@Component
class SecurityHelper(
    private val req: HttpServletRequest,
    private val resp: HttpServletResponse,
    private val authTokenProperties: AuthTokenProperties,
) { // 14183의 Rq 복붙
    @Value("\${cookie.domain}")
    private lateinit var cookieDomain: String

    // TRIPLES-48에서 @CurrentUser로 대체하는 중. main에 아직 없는 쿠폰 도메인이 이 프로퍼티를 계속 쓰고 있어서
    // 쿠폰 브랜치가 머지되어 전환되기 전까지는 삭제할 수 없다.
    val actor: User?
        get() =
            (SecurityContextHolder.getContext().authentication?.principal as? SecurityUser)
                ?.let { User(it.id, it.username, it.nickname) }

    fun getHeader(
        name: String,
        defaultValue: String,
    ): String = req.getHeader(name)?.takeIf { it.isNotBlank() } ?: defaultValue

    fun setHeader(
        name: String,
        value: String?,
    ) {
        val safeValue = value ?: ""

        if (safeValue.isBlank()) {
            req.removeAttribute(name)
        } else {
            resp.setHeader(name, safeValue)
        }
    }

    fun getCookieValue(
        name: String,
        defaultValue: String,
    ): String =
        req.cookies
            .orEmpty()
            .firstOrNull { it.name == name && !it.value.isNullOrBlank() }
            ?.value
            ?: defaultValue

    val accessTokenCookieExpiresInSecond: Int
        get() =
            authTokenProperties.accessToken.cookieMaxAge
                .toSeconds()
                .toInt()

    @Suppress("ForbiddenComment") // 원본에 있던 미해결 TODO를 그대로 보존
    fun setCookie(
        name: String,
        value: String?,
    ) {
        val safeValue = value ?: ""

        val cookie =
            Cookie(name, safeValue).apply {
                path = "/"
                isHttpOnly = true
                domain = cookieDomain
                secure = true
                setAttribute("SameSite", "Lax") // TODO: 포트 다를 때 Strict로도 되는지 확인
                maxAge =
                    if (safeValue.isBlank()) {
                        0
                    } else {
                        val maxAgeDuration =
                            if (name == "refreshToken") {
                                authTokenProperties.refreshToken.cookieMaxAge
                            } else {
                                authTokenProperties.accessToken.cookieMaxAge
                            }
                        maxAgeDuration.toSeconds().toInt()
                    }
            }

        resp.addCookie(cookie)
    }

    fun deleteCookie(name: String) = setCookie(name, null)
}
