package com.scommit.domain.payment.payment.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/**
 * 토스 페이먼츠 결제 승인 API 응답 중 검증에 필요한 필드만 매핑한다.
 * 응답 스펙이 변경되어 필드가 추가되더라도 역직렬화가 깨지지 않도록 미지의 필드는 무시한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class TossConfirmResponse(
    val paymentKey: String? = null,
    val orderId: String? = null,
    val status: String? = null,
    val totalAmount: Long? = null,
    val method: String? = null,
) {
    /**
     * 실제로 대금이 수납 완료된 상태인지 여부.
     * 가상계좌(WAITING_FOR_DEPOSIT)처럼 승인 API가 200을 반환해도
     * 입금이 끝나지 않은 상태가 존재하므로 DONE 여부를 반드시 확인해야 한다.
     */
    fun isSettled(): Boolean = status == STATUS_DONE

    companion object {
        const val STATUS_DONE = "DONE"
    }
}
