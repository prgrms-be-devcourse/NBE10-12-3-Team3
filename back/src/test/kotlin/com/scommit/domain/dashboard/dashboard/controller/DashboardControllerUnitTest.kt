package com.scommit.domain.dashboard.dashboard.controller

import com.scommit.domain.dashboard.dashboard.service.CreatorDashboardService
import com.scommit.domain.dashboard.dashboard.service.PlatformDashboardService
import com.scommit.global.exception.BusinessException
import com.scommit.global.exception.ErrorCode
import com.scommit.global.security.SecurityHelper
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock

// DashboardControllerTest(실제 Spring Security 체인 통합 테스트)는 SecurityHelper를 목킹하지 않으므로
// actor가 null인 방어 분기(정상적인 HTTP 요청으로는 도달 불가능)를 검증할 수 없다.
// 이 클래스는 그 가드 로직만 스프링 컨텍스트 없이 순수 단위 테스트로 검증한다.
@DisplayName("DashboardController 단위 테스트 - 인증 가드")
class DashboardControllerUnitTest {
    private val creatorDashboardService = mock(CreatorDashboardService::class.java)
    private val platformDashboardService = mock(PlatformDashboardService::class.java)
    private val securityHelper = mock(SecurityHelper::class.java)

    private val controller = DashboardController(creatorDashboardService, platformDashboardService, securityHelper)

    @Test
    @DisplayName("getUserDashboard - actor가 없으면 UNAUTHORIZED 예외를 던진다")
    fun `getUserDashboard - actor가 없으면 UNAUTHORIZED`() {
        given(securityHelper.actor).willReturn(null)

        assertThatThrownBy { controller.getUserDashboard("30d") }
            .isInstanceOf(BusinessException::class.java)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNAUTHORIZED)
    }

    @Test
    @DisplayName("getAdminDashboard - actor가 없으면 UNAUTHORIZED 예외를 던진다")
    fun `getAdminDashboard - actor가 없으면 UNAUTHORIZED`() {
        given(securityHelper.actor).willReturn(null)

        assertThatThrownBy { controller.getAdminDashboard("30d") }
            .isInstanceOf(BusinessException::class.java)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNAUTHORIZED)
    }
}
