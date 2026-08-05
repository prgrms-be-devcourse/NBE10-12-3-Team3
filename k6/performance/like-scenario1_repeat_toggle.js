/**
 * 시나리오 A — 한 사람이 같은 게시글 반복 좋아요/취소
 * 목적: 빠른 토글 시 성능 저하 여부 확인
 *
 * [선택 이유]
 *   좋아요 로직은 INSERT/DELETE 순서로 동일 row에 반복 접근함
 *   한 명이 빠르게 토글하면 같은 row에 락이 반복되어 DB 병목이 생길 수 있는지 확인하기 위해 설계
 *
 * [목표 수치 기준]
 *   p95 500ms: Google 연구 기준 200ms 이상이면 느리다고 인식, 500ms를 허용 상한으로 설정
 *   에러율 1%: 100명 중 1명 이상 에러 발생 시 서비스 신뢰도 문제로 판단
 *
 * 실행 방법:
 *   k6 run -e BASE_URL=https://api.scommit.store like-scenario1_repeat_toggle.js
 *
 * [사전 준비]
 *   DB에 아래 테스트 계정이 등록되어 있어야 함
 *   setup()에서 자동으로 로그인하여 토큰을 발급받음
 */

import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'https://api.scommit.store';

// like/cancel 각각 "성공"과 "충돌(이미 처리된 상태)"을 구분해서 세는 이유:
// http_req_failed 하나만으로는 두 상태가 섞여 보여서 문제 여부를 구분할 수 없다.
const likeCreated = new Counter('like_created'); // 201
const likeConflict = new Counter('like_conflict'); // 409
const cancelOk = new Counter('cancel_ok'); // 200
const cancelMissing = new Counter('cancel_missing'); // 404

// 매 iteration마다 새로 만들지 않도록 모듈 스코프에서 한 번만 생성한다(init 컨텍스트 밖,
// 즉 default function 안에서 매번 새로 만들면 VU마다 반복 생성돼 불필요한 오버헤드가 생기고,
// k6 버전에 따라 init 컨텍스트 밖 생성이 막혀 있을 수도 있어 미리 검증 필요).
const likeResponseCallback = http.expectedStatuses(201, 409);
const cancelResponseCallback = http.expectedStatuses(200, 404);

// 단일 테스트 계정 — 동일 유저가 반복 토글하는 상황 재현
const TEST_ACCOUNT = { email: 'user1@test.com', password: '123456' };

// setup()을 사용하는 이유:
// default function은 VU 수만큼 병렬로 실행되므로, 각 VU가 로그인하면 100번 로그인 요청이 발생함
// setup()은 테스트 시작 전 단 1번만 실행되므로 불필요한 로그인 부하를 줄이기 위해 사용
export function setup() {
  const res = http.post(
    `${BASE_URL}/api/users/login`,
    JSON.stringify(TEST_ACCOUNT),
    { headers: { 'Content-Type': 'application/json' } },
  );

  // 토큰은 응답 쿠키로 내려옴
  if (!res.cookies.accessToken || res.cookies.accessToken.length === 0) {
    throw new Error(`로그인 실패: ${TEST_ACCOUNT.email} / status: ${res.status}`);
  }
  const token = res.cookies.accessToken[0].value;

  // 이 스크립트 전용 더미 게시글 — 실사용자 게시글을 건드리지 않기 위해 매 실행마다 새로 만든다.
  const postRes = http.post(
    `${BASE_URL}/api/posts`,
    JSON.stringify({
      seriesId: null,
      title: 'k6 load test dummy (like-scenario1)',
      body: 'auto-created by k6, safe to delete',
      publishStatus: 'PUBLIC',
      accessLevel: 'FREE',
    }),
    { headers: { 'Content-Type': 'application/json', Cookie: `accessToken=${token}` } },
  );
  if (postRes.status !== 201) {
    throw new Error(`더미 게시글 생성 실패: status ${postRes.status}`);
  }

  return { token, postId: postRes.json('data.id') };
}

// 측정 구간이 전부 끝난 뒤 딱 한 번 실행 — setup()에서 만든 더미 게시글을 정리한다.
export function teardown(data) {
  http.del(`${BASE_URL}/api/posts/${data.postId}`, null, {
    headers: { Cookie: `accessToken=${data.token}` },
  });
}

export const options = {
  stages: [
    { duration: '1m', target: 10 },  // 워밍업: JVM JIT 컴파일, 커넥션 풀 초기화
    { duration: '3m', target: 50 },  // 일반 부하: 목표 성능 충족 여부 확인
    { duration: '2m', target: 100 }, // 최대 부하: 한계 근처에서의 성능 확인
    { duration: '1m', target: 0 },   // 쿨다운: 서버 자원 회복 확인
  ],
  thresholds: {
    // p95 500ms 초과 시 테스트 실패로 표시
    http_req_duration: ['p(95)<500'],
    // 에러율 1% 초과 시 테스트 실패로 표시
    http_req_failed: ['rate<0.01'],
  },
};

export default function (data) {
  const headers = {
    'Content-Type': 'application/json',
    'Cookie': `accessToken=${data.token}`,
  };

  // 좋아요 → 취소를 연속으로 보내는 이유:
  // 한 명이 빠르게 좋아요/취소를 반복할 때 DB row 락이 연속으로 걸리는 상황을 재현하기 위함
  const likeRes = http.post(`${BASE_URL}/api/posts/${data.postId}/likes`, null, {
    headers,
    responseCallback: likeResponseCallback,
  });
  check(likeRes, {
    // 201: 좋아요 성공 / 409: 이미 좋아요 상태 (ErrorCode.ALREADY_LIKED, 서버 중복 방지 로직)
    // 둘 다 정상 응답으로 처리 — 서버가 의도대로 동작하는 것이므로 에러로 보지 않음
    'like 201 or 409': (r) => r.status === 201 || r.status === 409,
  });
  if (likeRes.status === 201) likeCreated.add(1);
  if (likeRes.status === 409) likeConflict.add(1);

  const cancelRes = http.del(`${BASE_URL}/api/posts/${data.postId}/likes`, null, {
    headers,
    responseCallback: cancelResponseCallback,
  });
  check(cancelRes, {
    // 200: 취소 성공 / 404: 이미 취소된 상태 (ErrorCode.LIKE_NOT_FOUND, like가 없을 때)
    'cancel 200 or 404': (r) => r.status === 200 || r.status === 404,
  });
  if (cancelRes.status === 200) cancelOk.add(1);
  if (cancelRes.status === 404) cancelMissing.add(1);

  // sleep을 넣지 않는 이유:
  // 실제 유저는 클릭 후 대기하지만, 이 시나리오는 "빠른 토글로 인한 DB 부하"를 보는 것이므로
  // 대기 없이 연속 요청을 보내 최대한 가혹한 상황을 재현
}
