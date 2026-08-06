package com.scommit.global.exception

class BusinessException(
    val errorCode: ErrorCode,
    cause: Throwable? = null,
) : RuntimeException(errorCode.message, cause)
