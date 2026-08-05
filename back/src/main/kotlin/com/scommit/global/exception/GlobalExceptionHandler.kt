package com.scommit.global.exception

import com.scommit.global.dto.RsData
import org.hibernate.query.sqm.UnknownPathException
import org.slf4j.LoggerFactory
import org.springframework.dao.InvalidDataAccessApiUsageException
import org.springframework.data.core.PropertyReferenceException
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.validation.BindException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

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
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleMethodArgumentNotValidException(e: MethodArgumentNotValidException): ResponseEntity<RsData<Void>> {
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
    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleMethodArgumentTypeMismatchException(
        e: MethodArgumentTypeMismatchException,
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
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleHttpMessageNotReadableException(e: HttpMessageNotReadableException): ResponseEntity<RsData<Void>> {
        log.warn("HttpMessageNotReadableException: {}", e.message)
        val errorCode = ErrorCode.INVALID_INPUT_VALUE
        val rsData: RsData<Void> = RsData(errorCode.code, "올바른 JSON 요청 형식이 아닙니다.")
        return ResponseEntity(rsData, errorCode.httpStatus)
    }

    /**
     * 존재하지 않는 정렬 프로퍼티(예: ?sort=notAField)를 요청했을 때
     * (클라이언트 입력 오류이므로 500이 아닌 400으로 응답한다)
     */
    @ExceptionHandler(PropertyReferenceException::class)
    fun handlePropertyReferenceException(e: PropertyReferenceException): ResponseEntity<RsData<Void>> {
        log.warn("PropertyReferenceException: {}", e.message)
        return invalidSortFieldResponse()
    }

    /**
     * 위와 같은 문제지만 리포지토리 메서드가 파생 쿼리가 아니라 @Query(JPQL)일 때 나오는 경로.
     * 이 경우 Spring Data의 자체 Sort 검증(PropertyReferenceException)을 안 타고, Sort가 JPQL
     * 문자열에 그대로 덧붙은 채로 Hibernate까지 넘어가 UnknownPathException으로 실패한다.
     * InvalidDataAccessApiUsageException은 이 원인 말고도 다른 JPA 오용 상황에서도 나오므로,
     * 원인이 UnknownPathException일 때만 400으로 좁혀서 응답하고 나머지는 500으로 그대로 둔다.
     * 주의: 이 경로에서 Spring의 Hibernate 예외 변환기가 cause를 채우지 않고 메시지에만
     * "org.hibernate.query.sqm.UnknownPathException: ..."을 실어 보낸다(실제 로그로 확인함) —
     * 그래서 e.cause가 아니라 메시지 문자열로 판별한다.
     */
    @ExceptionHandler(InvalidDataAccessApiUsageException::class)
    fun handleInvalidDataAccessApiUsageException(e: InvalidDataAccessApiUsageException): ResponseEntity<RsData<Void>> {
        val isUnknownPath = e.cause is UnknownPathException || e.message?.contains("UnknownPathException") == true
        if (isUnknownPath) {
            log.warn("UnknownPathException(존재하지 않는 정렬/쿼리 필드): {}", e.message)
            return invalidSortFieldResponse()
        }
        log.error("InvalidDataAccessApiUsageException", e)
        val errorCode = ErrorCode.INTERNAL_SERVER_ERROR
        return ResponseEntity(RsData(errorCode.code, errorCode.message), errorCode.httpStatus)
    }

    private fun invalidSortFieldResponse(): ResponseEntity<RsData<Void>> {
        val errorCode = ErrorCode.INVALID_INPUT_VALUE
        return ResponseEntity(RsData(errorCode.code, "존재하지 않는 정렬 필드입니다."), errorCode.httpStatus)
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
