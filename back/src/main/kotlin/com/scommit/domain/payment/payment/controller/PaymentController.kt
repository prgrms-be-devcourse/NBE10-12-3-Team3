package com.scommit.domain.payment.payment.controller

import com.scommit.domain.payment.payment.dto.TossConfirmRequest
import com.scommit.domain.payment.payment.service.PaymentService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

import com.scommit.domain.payment.payment.dto.PaymentReadyRequest
import com.scommit.domain.payment.payment.dto.PaymentReadyResponse
import com.scommit.domain.payment.payment.dto.PaymentResponse
import com.scommit.global.security.SecurityUser
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import com.scommit.global.dto.RsData

@RestController
@RequestMapping("/api/payments")
class PaymentController(
    private val paymentService: PaymentService
) {

    @PostMapping("/toss/ready")
    fun readyPayment(
        @AuthenticationPrincipal user: SecurityUser,
        @RequestBody request: PaymentReadyRequest
    ): ResponseEntity<RsData<PaymentReadyResponse>> {
        val response = paymentService.readyPayment(user.id, request)
        return ResponseEntity.ok(RsData("200-1", "결제 준비 성공", response))
    }

    @PostMapping("/toss/confirm")
    fun confirmPayment(@RequestBody request: TossConfirmRequest): ResponseEntity<RsData<String>> {
        paymentService.confirmPayment(request)
        return ResponseEntity.ok(RsData("200-1", "결제가 성공적으로 승인되었습니다.", "OK"))
    }

    @GetMapping("/history")
    fun getMyPaymentHistory(
        @AuthenticationPrincipal user: SecurityUser
    ): ResponseEntity<RsData<List<PaymentResponse>>> {
        val history = paymentService.getMyPayments(user.id)
        return ResponseEntity.ok(RsData("200-1", "결제 내역 조회 성공", history))
    }
}
