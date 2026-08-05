package com.scommit.global.security

import com.scommit.domain.user.user.entity.User
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.test.web.servlet.request.RequestPostProcessor

private fun authenticationOf(user: User): UsernamePasswordAuthenticationToken {
    val securityUser = SecurityUser(checkNotNull(user.id), user.email, user.nickname, user.role, user.authorities)
    return UsernamePasswordAuthenticationToken(securityUser, null, securityUser.authorities)
}

// @CurrentUser/@AuthenticationPrincipal이 주입받는 SecurityUser를 실제 Authentication으로 만들어 요청에 붙인다.
// Security 필터 체인이 살아있는 @WebMvcTest 슬라이스에서 사용한다 (필터가 TestSecurityContextHolder를 실제 SecurityContextHolder로 복사해준다).
fun currentUser(user: User): RequestPostProcessor =
    SecurityMockMvcRequestPostProcessors.authentication(authenticationOf(user))

// @AutoConfigureMockMvc(addFilters = false)처럼 Security 필터 체인 자체가 꺼져 있어서
// TestSecurityContextHolder → SecurityContextHolder 복사가 일어나지 않는 슬라이스에서 사용한다.
// SecurityContextHolder를 직접 채우므로, 호출한 테스트 클래스에서 @AfterEach로 SecurityContextHolder.clearContext()를 호출해 정리해야 한다.
fun currentUserWithoutSecurityFilter(user: User): RequestPostProcessor {
    val authentication = authenticationOf(user)
    return RequestPostProcessor { request ->
        SecurityContextHolder.getContext().authentication = authentication
        request
    }
}
