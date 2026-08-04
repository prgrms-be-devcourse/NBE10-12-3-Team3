package com.scommit.domain.payment.payment.entity

enum class PaymentStatus {
    READY,          // 결제 생성 후 승인 대기
    IN_PROGRESS,    // 결제 진행 중 (토스 인증 완료 상태)
    DONE,           // 최종 승인 완료
    CANCELED,       // 결제 취소 (환불)
    ABORTED         // 결제 실패 또는 사용자 취소
}
