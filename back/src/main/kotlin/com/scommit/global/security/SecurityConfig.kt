package com.scommit.global.security

import com.scommit.global.dto.RsData
import com.scommit.global.security.jwt.AuthTokenProperties
import com.scommit.global.security.jwt.JwtFilter
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableConfigurationProperties(AuthTokenProperties::class)
class SecurityConfig(
    private val jwtFilter: JwtFilter,
    private val jsonUtility: JsonUtility,
) {
    @Value("\${cors.allowed-origins}")
    private lateinit var allowedOrigins: String

    @Bean
    @Suppress("LongMethod") // 원본 URL 매처 선언부(주석 포함)를 그대로 보존하기 위해 분리하지 않음
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .cors { it.configurationSource(corsConfigurationSource()) }
            .formLogin { it.disable() }
            .httpBasic { it.disable() }
            .logout { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .headers { headers -> headers.frameOptions { it.sameOrigin() } }
            .exceptionHandling { exceptionHandling ->
                exceptionHandling
                    .authenticationEntryPoint { _, response, _ ->
                        writeSecurityErrorResponse(
                            response,
                            HttpServletResponse.SC_UNAUTHORIZED,
                            RsData("401-1", "로그인 후 이용해주세요."),
                        )
                    }.accessDeniedHandler { _, response, _ ->
                        writeSecurityErrorResponse(
                            response,
                            HttpServletResponse.SC_FORBIDDEN,
                            RsData("403-1", "권한이 없습니다."),
                        )
                    }
            }.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter::class.java)
            .authorizeHttpRequests { auth ->
                auth
                    // 개발
                    .requestMatchers("/h2-console/**")
                    .permitAll()
                    // 모니터링
                    .requestMatchers("/actuator/**")
                    .permitAll()
                    // 정적 리소스
                    .requestMatchers(
                        "/favicon.ico",
                        "/css/**",
                        "/js/**",
                        "/images/**",
                        "/static/**",
                        "/media/**",
                    ).permitAll()
                    // 문서
                    .requestMatchers(
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/v3/api-docs/**",
                        "/api-docs/**",
                    ).permitAll()
                    // 인증 이전에 호출되는 API
                    .requestMatchers(
                        "/api/users/signup",
                        "/api/users/login",
                    ).permitAll()
                    // 공개 조회 API — 비로그인 사용자도 볼 수 있는 GET 요청들
                    .requestMatchers(
                        HttpMethod.GET,
                        "/api/users/{id:\\d+}",
                        "/api/users/{id:\\d+}/medias",
                        "/api/series",
                        "/api/series/search",
                        "/api/series/{id:\\d+}",
                        "/api/series/{id:\\d+}/posts",
                        "/api/series/{id:\\d+}/medias",
                        "/api/series/users/{userId:\\d+}",
                        "/api/posts",
                        "/api/posts/search",
                        "/api/posts/users/{userId:\\d+}",
                        "/api/posts/{id:\\d+}",
                        "/api/posts/{id:\\d+}/comments",
                        "/api/posts/{id:\\d+}/medias",
                        "/api/posts/{id:\\d+}/medias/thumbnail",
                        "/api/users/search",
                        "/api/coupon-policies/active",
                        "/api/dashboard/mainpage",
                    ).permitAll()
                    // 일반 인증 필요 경로
                    .requestMatchers("/api/**")
                    .authenticated()
                    // api가 아닌 명시되지 않은 경로는 인증 요구
                    .anyRequest()
                    .authenticated()
            }

        return http.build()
    }

    private fun writeSecurityErrorResponse(
        response: HttpServletResponse,
        status: Int,
        rsData: RsData<Void>,
    ) {
        response.contentType = "application/json;charset=UTF-8"
        response.status = status
        response.writer.write(jsonUtility.toJson(rsData))
    }

    @Bean
    fun corsConfigurationSource(): UrlBasedCorsConfigurationSource {
        val configuration =
            CorsConfiguration().apply {
                allowedOriginPatterns = this@SecurityConfig.allowedOrigins.split(",").map { it.trim() }
                allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                allowedHeaders = listOf("*")
                allowCredentials = true
            }

        return UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/api/**", configuration)
        }
    }
}
