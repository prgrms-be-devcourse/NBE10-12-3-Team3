package com.scommit.global.e2e;

import org.springframework.core.ParameterizedTypeReference;

/**
 * {@code com.scommit.global.dto.RsData} 의 테스트 전용 미러 레코드.
 *
 * <p>{@code RsData} 를 그대로 응답 타입으로 쓰면 역직렬화가 실패한다. {@code statusCode} 가
 * {@code @JsonIgnore} 라서 응답 JSON에는 나오지 않는데, record의 정규 생성자 파라미터라
 * 역직렬화 시점에는 {@code int statusCode} 에 null을 매핑하려다
 * {@code tools.jackson.databind.exc.MismatchedInputException} 이 난다.
 * {@code src/main} 을 수정할 수 없으므로 테스트에서만 쓰는 미러 레코드로 우회한다.
 *
 * <p>상세: {@code docs/user-e2e-known-issues.md} #7, {@code docs/e2e-test-convention.md}
 */
public record ApiResponse<T>(String resultCode, String msg, T data) {

    /**
     * {@code data} 를 보지 않는 응답(주로 에러 응답)용 타입 토큰.
     * {@code new ParameterizedTypeReference<ApiResponse<Void>>() {}} 는 어느 E2E에서나
     * 반복되므로 상수로 둔다. 불변이라 재사용해도 안전하다.
     */
    public static final ParameterizedTypeReference<ApiResponse<Void>> VOID_BODY =
            new ParameterizedTypeReference<>() {};
}
