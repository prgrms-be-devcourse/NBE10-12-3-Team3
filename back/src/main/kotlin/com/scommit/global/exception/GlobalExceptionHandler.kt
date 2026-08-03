package com.scommit.global.exception

import com.scommit.global.dto.RsData
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.validation.BindException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {
    companion object {
        private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)
    }

    /**
     * 커스텀 비즈니스 예외 처리
     */
    @ExceptionHandler(BusinessException::class)
    fun handleBusinessException(e: BusinessException): ResponseEntity<RsData<Void>> {
        log.warn("BusinessException: {}", e.message)
        val errorCode = e.errorCode
        val rsData: RsData<Void> = RsData(errorCode.code, errorCode.message)
        return ResponseEntity(rsData, errorCode.httpStatus)
    }

    /**
     * @RequestBody JSON 바인딩 에러 처리 (@Valid 유효성 검사 실패 시)
     */
    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException::class)
    fun handleMethodArgumentNotValidException(
        e: org.springframework.web.bind.MethodArgumentNotValidException,
    ): ResponseEntity<RsData<Void>> {
        val errorMessage = e.bindingResult.allErrors[0].defaultMessage
        log.warn("MethodArgumentNotValidException: {}", errorMessage)

        val errorCode = ErrorCode.INVALID_INPUT_VALUE
        val rsData: RsData<Void> =
            RsData(
                errorCode.code,
                errorMessage ?: errorCode.message,
            )
        return ResponseEntity(rsData, errorCode.httpStatus)
    }

    /**
     * @ModelAttribute 등 바인딩 에러 처리 (쿼리 스트링, 폼 데이터 유효성 검사 실패 시)
     */
    @ExceptionHandler(BindException::class)
    fun handleBindException(e: BindException): ResponseEntity<RsData<Void>> {
        val errorMessage = e.bindingResult.allErrors[0].defaultMessage
        log.warn("BindException: {}", errorMessage)

        val errorCode = ErrorCode.INVALID_INPUT_VALUE
        val rsData: RsData<Void> =
            RsData(
                errorCode.code,
                errorMessage ?: errorCode.message,
            )
        return ResponseEntity(rsData, errorCode.httpStatus)
    }

    /**
     * URL 경로 변수나 쿼리 파라미터의 데이터 타입이 일치하지 않을 때 (예: Long 자리에 String 입력)
     */
    @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException::class)
    fun handleMethodArgumentTypeMismatchException(
        e: org.springframework.web.method.annotation.MethodArgumentTypeMismatchException,
    ): ResponseEntity<RsData<Void>> {
        log.warn("TypeMismatchException: 파라미터 '{}'에 잘못된 값 '{}'가 입력되었습니다.", e.name, e.value)

        val errorCode = ErrorCode.INVALID_INPUT_VALUE
        val errorMessage = "'${e.name}' 항목에 올바르지 않은 타입의 값이 입력되었습니다."

        val rsData: RsData<Void> = RsData(errorCode.code, errorMessage)
        return ResponseEntity(rsData, errorCode.httpStatus)
    }

    /**
     * JSON 파싱 에러 처리 (클라이언트가 잘못된 JSON 포맷을 보냈을 때)
     */
    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException::class)
    fun handleHttpMessageNotReadableException(
        e: org.springframework.http.converter.HttpMessageNotReadableException,
    ): ResponseEntity<RsData<Void>> {
        log.warn("HttpMessageNotReadableException: {}", e.message)
        val errorCode = ErrorCode.INVALID_INPUT_VALUE
        val rsData: RsData<Void> = RsData(errorCode.code, "올바른 JSON 요청 형식이 아닙니다.")
        return ResponseEntity(rsData, errorCode.httpStatus)
    }

    /**
     * 핸들링 되지 않은 나머지 모든 예외 처리
     */
    @ExceptionHandler(Exception::class)
    fun handleException(e: Exception): ResponseEntity<RsData<Void>> {
        log.error("Exception", e)
        val errorCode = ErrorCode.INTERNAL_SERVER_ERROR
        val rsData: RsData<Void> = RsData(errorCode.code, errorCode.message)
        return ResponseEntity(rsData, errorCode.httpStatus)
    }
}
