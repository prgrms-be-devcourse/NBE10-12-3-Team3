package com.scommit.global.exception

import com.scommit.global.dto.RsData

// TODO: BuisinessException과 통합 또는 다른 방법 강구
// Exception을 14183과 분리하기 위한 임시 예외 클래스입니다.
@Suppress("ForbiddenComment")
class SecurityException(
    private val resultCode: String,
    private val msg: String,
) : RuntimeException("$resultCode : $msg") {
    fun getRsData(): RsData<Void> = RsData(resultCode, msg, null)
}
