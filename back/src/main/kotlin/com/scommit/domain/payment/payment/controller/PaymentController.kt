package com.scommit.domain.payment.payment.controller

import com.scommit.domain.payment.payment.dto.PaymentReadyRequest
import com.scommit.domain.payment.payment.dto.PaymentReadyResponse
import com.scommit.domain.payment.payment.dto.PaymentResponse
import com.scommit.domain.payment.payment.dto.TossConfirmRequest
import com.scommit.domain.payment.payment.service.PaymentService
import com.scommit.global.dto.RsData
import com.scommit.global.security.SecurityUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Payment", description = "토스 페이먼츠 결제 API")
@RestController
@RequestMapping("/api/payments")
class PaymentController(
    private val paymentService: PaymentService,
) {
    @Operation(summary = "결제 준비", description = "주문번호를 생성하고 서버가 확정한 결제 금액을 반환합니다.")
    @PostMapping("/toss/ready")
    fun readyPayment(
        @AuthenticationPrincipal user: SecurityUser,
        @RequestBody @Valid request: PaymentReadyRequest,
    ): ResponseEntity<RsData<PaymentReadyResponse>> {
        val response = paymentService.readyPayment(user.id, request)
        return ResponseEntity.ok(RsData("200-1", "결제 준비 성공", response))
    }

    @Operation(summary = "결제 승인", description = "토스 승인 API를 호출하고 멤버십으로 승급합니다.")
    @PostMapping("/toss/confirm")
    fun confirmPayment(
        @AuthenticationPrincipal user: SecurityUser,
        @RequestBody @Valid request: TossConfirmRequest,
    ): ResponseEntity<RsData<String>> {
        paymentService.confirmPayment(user.id, request.paymentKey, request.orderId)
        return ResponseEntity.ok(RsData("200-1", "결제가 성공적으로 승인되었습니다.", "OK"))
    }

    @Operation(summary = "내 결제 내역 조회")
    @GetMapping("/history")
    fun getMyPaymentHistory(
        @AuthenticationPrincipal user: SecurityUser,
    ): ResponseEntity<RsData<List<PaymentResponse>>> {
        val history = paymentService.getMyPayments(user.id)
        return ResponseEntity.ok(RsData("200-1", "결제 내역 조회 성공", history))
    }
}
