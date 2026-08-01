package com.scommit.global.security.jwt;

import com.scommit.domain.user.user.entity.User;
import com.scommit.domain.user.user.service.UserService;
import com.scommit.global.dto.RsData;
import com.scommit.global.security.JsonUtility;
import com.scommit.global.security.SecurityHelper;
import com.scommit.global.security.SecurityUser;
import com.scommit.global.exception.SecurityException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtFilter extends OncePerRequestFilter { // 14183의 CustomAuthenticationFilter에 해당
    private final JwtProvider jwtProvider; // 14183의 AuthTokenService
    private final UserService userService; // 14183의 memberService
    private final SecurityHelper securityHelper; // 14183의 rq

    public JwtFilter(JwtProvider jwtProvider, SecurityHelper securityHelper, UserService userService) {
        this.jwtProvider = jwtProvider;
        this.securityHelper = securityHelper;
        this.userService = userService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        logger.debug("Processing request for " + request.getRequestURI());

        try {
            work(request, response, filterChain);
        } catch (SecurityException e) {
            RsData<Void> rsData = e.getRsData();
            response.setContentType("application/json;charset=UTF-8");
            response.setStatus(rsData.statusCode());
            response.getWriter().write(
                    JsonUtility.toString(rsData)
            );
        }
    }

    private void work(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        // API 요청이 아니라면 패스
        if (!request.getRequestURI().startsWith("/api/")) {
            filterChain.doFilter(request, response);
            return;
        }

        // 인증, 인가가 필요없는 API 요청이라면 패스
        if (List.of("/api/users/login", "/api/users/signup").contains(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }

        String refreshToken;
        String accessToken;

        String headerAuthorization = securityHelper.getHeader("Authorization", "");

        if (!headerAuthorization.isBlank()) {
            if (!headerAuthorization.startsWith("Bearer "))
                throw new SecurityException("401-2", "Authorization 헤더가 Bearer 형식이 아닙니다.");

            String[] headerAuthorizationBits = headerAuthorization.split(" ", 3);

            if (headerAuthorizationBits.length == 3) {
                refreshToken = headerAuthorizationBits[1];
                accessToken = headerAuthorizationBits[2];
            } else {
                refreshToken = "";
                accessToken = headerAuthorizationBits[1];
            }
        } else {
            refreshToken = securityHelper.getCookieValue("refreshToken", "");
            accessToken = securityHelper.getCookieValue("accessToken", "");
        }

        logger.debug("refreshToken : " + refreshToken);
        logger.debug("accessToken : " + accessToken);

        boolean isRefreshTokenExists = !refreshToken.isBlank();
        boolean isAccessTokenExists = !accessToken.isBlank();

        if (!isRefreshTokenExists && !isAccessTokenExists) {
            filterChain.doFilter(request, response);
            return;
        }

        User user = null;
        boolean isAccessTokenValid = false;

        if (isAccessTokenExists) {
            JwtProvider.AccessTokenPayload payload;
            try {
                payload = jwtProvider.parseAccessToken(accessToken);
            } catch (JwtException e) {
                payload = null;
            }

            if (payload != null) {
                user = new User(payload.id(), payload.email(), payload.nickname(), payload.role());
                isAccessTokenValid = true;
            }
        }

        if (user == null) {
            // 인증 실패시 익명 요청으로 다음 필터(Spring Security 인가 규칙)에 맡긴다.
            User refreshedUser = userService.getUserByRefreshToken(refreshToken);
            if (refreshedUser == null) {
                filterChain.doFilter(request, response);
                return;
            }
            user = refreshedUser;
        }

        if (isAccessTokenExists && !isAccessTokenValid) {
            String actorAccessToken = jwtProvider.generateAccessToken(user.getId(), user.getEmail(), user.getNickname(), user.getRole());

            securityHelper.setCookie("accessToken", actorAccessToken);
            securityHelper.setHeader("Authorization", actorAccessToken);
        }

        UserDetails userDetails = new SecurityUser(
                user.getId(),
                user.getEmail(), // email에 해당
                user.getNickname(),
                user.getAuthorities()
        );

        Authentication authentication = new UsernamePasswordAuthenticationToken(
                userDetails,
                userDetails.getPassword(),
                userDetails.getAuthorities()
        );

        // 이 시점 이후부터는 시큐리티가 이 요청을 인증된 사용자의 요청이다.
        SecurityContextHolder
                .getContext()
                .setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }
}
