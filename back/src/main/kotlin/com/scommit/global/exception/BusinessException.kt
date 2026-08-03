package com.scommit.global.exception

class BusinessException(val errorCode: ErrorCode) : RuntimeException(errorCode.message)
