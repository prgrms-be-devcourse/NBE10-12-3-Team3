package com.scommit.global.security.jwt

import com.scommit.domain.user.user.entity.User
import com.scommit.domain.user.user.service.UserService
import com.scommit.global.exception.SecurityException
import com.scommit.global.security.JsonUtility
import com.scommit.global.security.SecurityHelper
import com.scommit.global.security.SecurityUser
import io.jsonwebtoken.JwtException
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

private const val BEARER_HEADER_PART_COUNT = 3

@Component
class JwtFilter(
    private val jwtProvider: JwtProvider, // 14183의 AuthTokenService
    private val securityHelper: SecurityHelper, // 14183의 rq
    private val userService: UserService, // 14183의 memberService
    private val jsonUtility: JsonUtility,
) : OncePerRequestFilter() { // 14183의 CustomAuthenticationFilter에 해당
    public override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        logger.debug("Processing request for ${request.requestURI}")

        try {
            work(request, response, filterChain)
        } catch (e: SecurityException) {
            val rsData = e.rsData
            response.contentType = "application/json;charset=UTF-8"
            response.status = rsData.statusCode()
            response.writer.write(jsonUtility.toJson(rsData))
        }
    }

    @Suppress("ReturnCount") // 서블릿 필터의 조기 pass-through 가드절 패턴
    private fun work(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        // API 요청이 아니라면 패스
        if (!request.requestURI.startsWith("/api/")) {
            filterChain.doFilter(request, response)
            return
        }

        // 인증, 인가가 필요없는 API 요청이라면 패스
        if (request.requestURI in listOf("/api/users/login", "/api/users/signup")) {
            filterChain.doFilter(request, response)
            return
        }

        val (refreshToken, accessToken) = extractTokens()

        logger.debug("refreshToken : $refreshToken")
        logger.debug("accessToken : $accessToken")

        val isAccessTokenExists = accessToken.isNotBlank()

        if (refreshToken.isBlank() && !isAccessTokenExists) {
            filterChain.doFilter(request, response)
            return
        }

        var user = if (isAccessTokenExists) parseUserFromAccessToken(accessToken) else null
        val isAccessTokenValid = user != null

        if (user == null) {
            // 인증 실패시 익명 요청으로 다음 필터(Spring Security 인가 규칙)에 맡긴다.
            user = userService.getUserByRefreshToken(refreshToken)
            if (user == null) {
                filterChain.doFilter(request, response)
                return
            }
        }

        val actor = checkNotNull(user)

        if (isAccessTokenExists && !isAccessTokenValid) {
            reissueAccessToken(actor)
        }

        authenticate(actor)

        filterChain.doFilter(request, response)
    }

    // Authorization 헤더(Bearer 형식)가 있으면 그걸, 없으면 쿠키에서 refreshToken/accessToken을 꺼낸다.
    private fun extractTokens(): Pair<String, String> {
        val headerAuthorization = securityHelper.getHeader("Authorization", "")

        if (headerAuthorization.isBlank()) {
            return securityHelper.getCookieValue("refreshToken", "") to
                securityHelper.getCookieValue("accessToken", "")
        }

        if (!headerAuthorization.startsWith("Bearer ")) {
            throw SecurityException("401-2", "Authorization 헤더가 Bearer 형식이 아닙니다.")
        }

        val headerAuthorizationBits = headerAuthorization.split(" ", limit = BEARER_HEADER_PART_COUNT)

        return if (headerAuthorizationBits.size == BEARER_HEADER_PART_COUNT) {
            headerAuthorizationBits[1] to headerAuthorizationBits[2]
        } else {
            "" to headerAuthorizationBits[1]
        }
    }

    private fun parseUserFromAccessToken(accessToken: String): User? {
        val payload =
            try {
                jwtProvider.parseAccessToken(accessToken)
            } catch (_: JwtException) {
                null
            }

        return payload?.let { User(it.id, it.email, it.nickname, it.role) }
    }

    private fun reissueAccessToken(actor: User) {
        val actorAccessToken =
            jwtProvider.generateAccessToken(checkNotNull(actor.id), actor.email, actor.nickname, actor.role)

        securityHelper.setCookie("accessToken", actorAccessToken)
        securityHelper.setHeader("Authorization", actorAccessToken)
    }

    private fun authenticate(actor: User) {
        val userDetails: UserDetails =
            SecurityUser(
                checkNotNull(actor.id),
                actor.email, // email에 해당
                actor.nickname,
                actor.role,
                actor.authorities,
            )

        val authentication: Authentication =
            UsernamePasswordAuthenticationToken(
                userDetails,
                userDetails.password,
                userDetails.authorities,
            )

        // 이 시점 이후부터는 시큐리티가 이 요청을 인증된 사용자의 요청이다.
        SecurityContextHolder.getContext().authentication = authentication
    }
}
