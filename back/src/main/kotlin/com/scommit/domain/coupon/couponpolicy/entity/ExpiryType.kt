package com.scommit.domain.coupon.couponpolicy.entity

enum class ExpiryType {
    // 발급일 기준 validDays일 후 만료 (예: 발급 후 7일)
    RELATIVE,

    // 정책에 지정된 고정 날짜에 만료 (예: 2026-08-31까지)
    ABSOLUTE,
}
