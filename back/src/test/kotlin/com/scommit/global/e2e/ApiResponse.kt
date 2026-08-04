package com.scommit.global.e2e

/**
 * [com.scommit.global.dto.RsData] 의 테스트 전용 미러 클래스.
 *
 * `RsData` 를 그대로 응답 타입으로 쓰면 역직렬화가 실패한다. `statusCode` 가
 * `@JsonIgnore` 라서 응답 JSON에는 나오지 않는데, 주 생성자 파라미터라
 * 역직렬화 시점에는 `Int statusCode` 에 null을 매핑하려다
 * `tools.jackson.databind.exc.MismatchedInputException` 이 난다.
 * `src/main` 을 수정할 수 없으므로 테스트에서만 쓰는 미러 클래스로 우회한다.
 *
 * 상세: `docs/user-e2e-known-issues.md` #7, `docs/e2e-test-convention.md`
 */
data class ApiResponse<T>(
    val resultCode: String,
    val msg: String,
    val data: T,
)
