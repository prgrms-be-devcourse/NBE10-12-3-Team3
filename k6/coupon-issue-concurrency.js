import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

/**
 * 시나리오  : 동시성 정합성 테스트 (부하/성능 테스트 아님)
 * 목적      : 응답 시간·처리량(RPS)은 측정하지 않는다. 서로 다른 유저 VU_COUNT명이 동시에
 *             같은 쿠폰 이벤트를 발급받으려 할 때, 실제 발급 성공 건수(issue_success)가
 *             TOTAL_QUANTITY를 넘는지만 확인한다.
 *
 *   - 비관적 락 적용 전 : issue_success가 TOTAL_QUANTITY를 초과할 수 있음 (버그 재현)
 *   - 비관적 락 적용 후 : issue_success가 정확히 TOTAL_QUANTITY로 수렴해야 함 (수정 검증)
 *
 * 락 적용 전/후 각각 실행해서 두 결과를 비교하는 용도로 쓴다.
 *
 * 실행      : k6 run k6/coupon-issue-concurrency.js
 * 다른 서버 : k6 run -e BASE_URL=https://scommit.store k6/coupon-issue-concurrency.js
 */

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const TOTAL_QUANTITY = 50; // 쿠폰 총 발급 수량
const VU_COUNT = 100; // 동시에 발급을 시도할 서로 다른 유저 수 (수량보다 넉넉히 많게)

const successCount = new Counter('issue_success');
const soldOutCount = new Counter('issue_sold_out');
const otherFailCount = new Counter('issue_other_fail');

export const options = {
  scenarios: {
    coupon_issue: {
      executor: 'per-vu-iterations',
      vus: VU_COUNT,
      iterations: 1,
      maxDuration: '30s',
    },
  },
};

function signUpAndLogin(email, password, nickname) {
  http.post(
    `${BASE_URL}/api/users/signup`,
    JSON.stringify({ email, password, nickname }),
    { headers: { 'Content-Type': 'application/json' } },
  );

  const loginRes = http.post(
    `${BASE_URL}/api/users/login`,
    JSON.stringify({ email, password }),
    { headers: { 'Content-Type': 'application/json' } },
  );

  return loginRes.json('data.accessToken');
}

// setup()은 부하 측정 대상이 아니라 순차 실행되는 준비 단계라, 여기서 회원가입/로그인/쿠폰
// 생성을 전부 미리 끝내둔다. 실제 동시성 테스트 대상은 default 함수의 발급 요청뿐이다.
export function setup() {
  const adminLoginRes = http.post(
    `${BASE_URL}/api/users/login`,
    JSON.stringify({ email: 'admin@test.com', password: '123456' }),
    { headers: { 'Content-Type': 'application/json' } },
  );
  const adminToken = adminLoginRes.json('data.accessToken');

  const now = Date.now();
  const startAt = new Date(now - 60 * 60 * 1000).toISOString();
  const endAt = new Date(now + 24 * 60 * 60 * 1000).toISOString();

  const createRes = http.post(
    `${BASE_URL}/api/admin/coupon-policies`,
    JSON.stringify({
      title: 'k6 동시성 테스트 쿠폰',
      description: 'k6 부하테스트용, 총 ' + TOTAL_QUANTITY + '개',
      discountType: 'PERCENT',
      discountValue: 10,
      totalQuantity: TOTAL_QUANTITY,
      startAt,
      endAt,
      expiryType: 'RELATIVE',
      validDays: 7,
      fixedExpiredAt: null,
    }),
    {
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${adminToken}`,
      },
    },
  );

  if (createRes.status !== 201) {
    throw new Error(`쿠폰 이벤트 생성 실패: ${createRes.status} ${createRes.body}`);
  }
  const couponPolicyId = createRes.json('data.id');

  const tokens = [];
  for (let i = 0; i < VU_COUNT; i++) {
    const email = `k6user${i}_${now}@test.com`;
    tokens.push(signUpAndLogin(email, '123456', `k6user${i}`));
  }

  return { couponPolicyId, tokens };
}

export default function (data) {
  const token = data.tokens[__VU - 1]; // k6 VU 번호는 1부터 시작

  const res = http.post(
    `${BASE_URL}/api/coupon-policies/${data.couponPolicyId}/issue`,
    null,
    { headers: { Authorization: `Bearer ${token}` } },
  );

  if (res.status === 200) {
    successCount.add(1);
  } else if (res.status === 409) {
    soldOutCount.add(1);
  } else {
    otherFailCount.add(1);
    console.error(`예상치 못한 응답: ${res.status} ${res.body}`);
  }

  check(res, {
    '200(성공) 또는 409(품절/중복)만 나온다': (r) => r.status === 200 || r.status === 409,
  });
}

export function teardown(data) {
  console.log(`쿠폰 정책 ID: ${data.couponPolicyId}`);
  console.log(`총 수량: ${TOTAL_QUANTITY}, 동시 요청 수: ${VU_COUNT}`);
  console.log('issue_success 카운트가 TOTAL_QUANTITY를 넘으면 동시성 버그가 재현된 것.');
}
