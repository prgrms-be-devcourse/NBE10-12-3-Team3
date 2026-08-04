package com.scommit.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 400 Bad Request
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "400-1", "올바르지 않은 입력값입니다."),
    INVALID_PASSWORD(HttpStatus.BAD_REQUEST, "400-2", "현재 비밀번호가 일치하지 않습니다."),
    SELF_SUBSCRIPTION_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "400-3", "자기 자신을 구독할 수 없습니다."),
    EMPTY_FILE(HttpStatus.BAD_REQUEST, "400-4", "업로드할 파일이 비어 있습니다."),
    
    // 401 Unauthorized
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "401-1", "인증되지 않은 사용자입니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "401-2", "이메일 또는 비밀번호가 일치하지 않습니다."),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "401-3", "액세스 토큰이 만료되었습니다."),
    TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "401-4", "액세스 토큰이 유효하지 않습니다."),
    REFRESH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "401-5", "리프레시 토큰이 만료되었습니다. 다시 로그인해 주세요."),

    // 403 Forbidden
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "403-1", "접근 권한이 없습니다."),

    // 404 Not Found
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "404-1", "요청한 리소스를 찾을 수 없습니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "404-2", "사용자를 찾을 수 없습니다."),
    POST_NOT_FOUND(HttpStatus.NOT_FOUND, "404-3", "게시글을 찾을 수 없습니다."),
    SUBSCRIPTION_NOT_FOUND(HttpStatus.NOT_FOUND, "404-4", "구독 정보를 찾을 수 없습니다."),
    SERIES_NOT_FOUND(HttpStatus.NOT_FOUND, "404-5", "시리즈를 찾을 수 없습니다."),
    COMMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "404-6", "댓글을 찾을 수 없습니다."),
    MEDIA_NOT_FOUND(HttpStatus.NOT_FOUND, "404-7", "미디어를 찾을 수 없습니다."),
    BOOKMARK_NOT_FOUND(HttpStatus.NOT_FOUND, "404-8", "북마크를 찾을 수 없습니다."),
    LIKE_NOT_FOUND(HttpStatus.NOT_FOUND, "404-9", "좋아요 기록이 없습니다."),
    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "404-10", "결제 정보를 찾을 수 없습니다."),
    
    // 409 Conflict
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "409-1", "이미 사용중인 이메일입니다."),
    ALREADY_SUBSCRIBED(HttpStatus.CONFLICT, "409-2", "이미 구독중인 창작자입니다."),
    DUPLICATE_NICKNAME(HttpStatus.CONFLICT, "409-3", "이미 사용중인 닉네임입니다."),
    MEMBERSHIP_ACTIVE_CANCEL_REQUIRED(HttpStatus.CONFLICT, "409-4", "멤버십 가입 중에는 구독 취소가 불가합니다. 멤버십 해지 후 다시 시도해주세요."),
    ALREADY_JOINED_MEMBERSHIP(HttpStatus.CONFLICT, "409-5", "이미 멤버십에 가입되어 있습니다."),
    NOT_MEMBERSHIP_SUBSCRIBER(HttpStatus.CONFLICT, "409-6", "멤버십에 가입되어 있지 않습니다."),
    ALREADY_LIKED(HttpStatus.CONFLICT, "409-7", "이미 좋아요를 누른 게시글입니다."),
    ALREADY_BOOKMARKED(HttpStatus.CONFLICT, "409-8", "이미 북마크한 게시글입니다."),

    // 415 Unsupported Media Type
    UNSUPPORTED_FILE_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "415-1", "지원하지 않는 파일 형식입니다."),

    // 500 Internal Server Error
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "500-1", "서버 내부 오류가 발생했습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
