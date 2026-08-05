package com.scommit.global.security

import com.scommit.global.security.jwt.AuthTokenProperties
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class SecurityHelper(
    private val req: HttpServletRequest,
    private val resp: HttpServletResponse,
    private val authTokenProperties: AuthTokenProperties,
) { // 14183의 Rq 복붙
    @Value("\${cookie.domain}")
    private lateinit var cookieDomain: String

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
