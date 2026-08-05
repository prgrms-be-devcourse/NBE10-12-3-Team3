package com.scommit.domain.dashboard.dashboard.controller

import com.scommit.domain.dashboard.dashboard.dto.AdminDashboard
import com.scommit.domain.dashboard.dashboard.dto.CreatorDashboard
import com.scommit.domain.dashboard.dashboard.dto.Mainpage
import com.scommit.domain.dashboard.dashboard.service.CreatorDashboardService
import com.scommit.domain.dashboard.dashboard.service.PlatformDashboardService
import com.scommit.domain.user.user.entity.UserRole
import com.scommit.global.dto.RsData
import com.scommit.global.exception.BusinessException
import com.scommit.global.exception.ErrorCode
import com.scommit.global.security.SecurityHelper
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/dashboard")
@Tag(name = "DashboardController", description = "API 대시보드 컨트롤러")
class DashboardController(
    private val creatorDashboardService: CreatorDashboardService,
    private val platformDashboardService: PlatformDashboardService,
    private val securityHelper: SecurityHelper,
) {
    @GetMapping("/user")
    @Operation(summary = "창작자 통계 대시보드 조회")
    fun getUserDashboard(
        @RequestParam(defaultValue = "30d") period: String,
    ): RsData<CreatorDashboard> {
        val actor = securityHelper.actor ?: throw BusinessException(ErrorCode.UNAUTHORIZED)

        val data = creatorDashboardService.getCreatorDashboard(checkNotNull(actor.id), period)
        return RsData("200-1", "창작자 대시보드를 조회하였습니다.", data)
    }

    @GetMapping("/admin")
    @Operation(summary = "관리자 통계 대시보드 조회")
    fun getAdminDashboard(
        @RequestParam(defaultValue = "30d") period: String,
    ): RsData<AdminDashboard> {
        val actor = securityHelper.actor ?: throw BusinessException(ErrorCode.UNAUTHORIZED)
        if (actor.role != UserRole.ADMIN) {
            throw BusinessException(ErrorCode.ACCESS_DENIED)
        }

        val data = platformDashboardService.getAdminDashboard(period)
        return RsData("200-1", "관리자 대시보드를 조회하였습니다.", data)
    }

    @GetMapping("/mainpage")
    @Operation(summary = "메인페이지 실데이터 조회")
    fun getMainpage(): RsData<Mainpage> {
        val data = platformDashboardService.getMainpage()
        return RsData("200-1", "메인페이지 데이터를 조회하였습니다.", data)
    }
}
