/**
 * 시나리오 B — 여러 사람이 같은 게시글 동시 좋아요
 * 목적: 동시 쓰기 경합에서 성능 유지 여부 확인
 *
 * [선택 이유]
 *   여러 유저가 동시에 같은 게시글에 좋아요를 누르면 DB에서 동시 INSERT 경합이 발생함
 *   이때 HikariCP 커넥션 풀이 얼마나 버티는지, 응답시간이 어떻게 변하는지 확인하기 위해 설계
 *   VU마다 다른 유저 토큰을 사용하는 이유: 같은 유저가 중복 좋아요 시 서버에서 409로 막혀
 *   실제 INSERT 경합이 발생하지 않으므로 유저를 분산해야 의미 있는 테스트가 됨
 *
 * 실행 방법:
 *   k6 run -e BASE_URL=https://api.scommit.store like-scenario2_concurrent_like.js
 *
 * [사전 준비]
 *   DB에 아래 테스트 계정들이 등록되어 있어야 함 (총 100개)
 *   setup()에서 자동으로 로그인하여 토큰을 발급받음
 *   계정이 100개라 100 VU 이하에서는 모든 유저가 각각 다른 토큰 사용
 */

import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'https://api.scommit.store';

// 모듈 스코프에서 한 번만 생성 — default function(매 iteration) 안에서 매번 새로 만들면
// VU마다 반복 생성돼 불필요한 오버헤드가 생기고, k6 버전에 따라 init 컨텍스트 밖 생성이
// 막혀 있을 수도 있어 사전 검증이 필요하다.
const likeResponseCallback = http.expectedStatuses(201, 409);

const PASSWORD = '123456';

// 총 100개 테스트 계정 (user1~10 + general 90개)
const TEST_ACCOUNTS = [
  // user1@test.com ~ user10@test.com (10개)
  ...Array.from({ length: 10 }, (_, i) => `user${i + 1}@test.com`),
  // @dev.com (26)
  'alice@dev.com', 'bob@dev.com', 'charlie@dev.com', 'diana@dev.com', 'evan@dev.com',
  'fiona@dev.com', 'george@dev.com', 'hannah@dev.com', 'ivan@dev.com', 'julia@dev.com',
  'kevin@dev.com', 'luna@dev.com', 'mike@dev.com', 'nina@dev.com', 'oscar@dev.com',
  'petra@dev.com', 'quinn@dev.com', 'rose@dev.com', 'sam@dev.com', 'tina@dev.com',
  'uma@dev.com', 'victor@dev.com', 'wendy@dev.com', 'xavier@dev.com', 'yara@dev.com',
  'zack@dev.com',
  // @coder.com (23)
  'aaron@coder.com', 'bella@coder.com', 'carl@coder.com', 'dora@coder.com', 'eli@coder.com',
  'faith@coder.com', 'greg@coder.com', 'helen@coder.com', 'iris@coder.com', 'jack@coder.com',
  'kate@coder.com', 'leo@coder.com', 'mia@coder.com', 'noah@coder.com', 'olive@coder.com',
  'paul@coder.com', 'queen@coder.com', 'ryan@coder.com', 'sara@coder.com', 'tom@coder.com',
  'uva@coder.com', 'val@coder.com', 'will@coder.com',
  // @hack.io (21)
  'adam@hack.io', 'betty@hack.io', 'clara@hack.io', 'dan@hack.io', 'elena@hack.io',
  'felix@hack.io', 'grace@hack.io', 'hank@hack.io', 'isla@hack.io', 'jake@hack.io',
  'kim@hack.io', 'lara@hack.io', 'mason@hack.io', 'nell@hack.io', 'otto@hack.io',
  'pam@hack.io', 'rex@hack.io', 'stan@hack.io', 'tara@hack.io', 'ulf@hack.io',
  'vera@hack.io',
  // @tech.kr (20)
  'ana@tech.kr', 'ben@tech.kr', 'cora@tech.kr', 'dex@tech.kr', 'eve@tech.kr',
  'finn@tech.kr', 'gaby@tech.kr', 'hugo@tech.kr', 'ike@tech.kr', 'jan@tech.kr',
  'ken@tech.kr', 'lily@tech.kr', 'moe@tech.kr', 'nan@tech.kr', 'pip@tech.kr',
  'rob@tech.kr', 'sue@tech.kr', 'ted@tech.kr', 'una@tech.kr', 'zoe@tech.kr',
].map((email) => ({ email, password: PASSWORD }));

// setup()을 사용하는 이유:
// 100개 계정 로그인을 각 VU가 개별로 하면 테스트 시작 시 100번 로그인 요청이 동시에 발생
// setup()에서 한 번에 순차적으로 처리해 로그인 자체가 성능 지표를 오염시키는 것을 방지
export function setup() {
  const tokens = TEST_ACCOUNTS.map((account) => {
    const res = http.post(
      `${BASE_URL}/api/users/login`,
      JSON.stringify(account),
      { headers: { 'Content-Type': 'application/json' } },
    );

    // 토큰은 응답 쿠키로 내려옴
    if (!res.cookies.accessToken || res.cookies.accessToken.length === 0) {
      throw new Error(`로그인 실패: ${account.email} / status: ${res.status}`);
    }
    return res.cookies.accessToken[0].value;
  });

  // 이 스크립트 전용 더미 게시글 — 계정 중 하나로 만들어서 실사용자 게시글을 건드리지 않는다.
  const postRes = http.post(
    `${BASE_URL}/api/posts`,
    JSON.stringify({
      seriesId: null,
      title: 'k6 load test dummy (like-scenario2)',
      body: 'auto-created by k6, safe to delete',
      publishStatus: 'PUBLIC',
      accessLevel: 'FREE',
    }),
    { headers: { 'Content-Type': 'application/json', Cookie: `accessToken=${tokens[0]}` } },
  );
  if (postRes.status !== 201) {
    throw new Error(`더미 게시글 생성 실패: status ${postRes.status}`);
  }

  return { tokens, postId: postRes.json('data.id') };
}

export const options = {
  stages: [
    { duration: '1m', target: 10 },  // 워밍업
    { duration: '3m', target: 50 },  // 일반 부하
    { duration: '2m', target: 100 }, // 최대 부하
    { duration: '1m', target: 0 },   // 쿨다운
  ],
  thresholds: {
    http_req_duration: ['p(95)<500'],
    http_req_failed: ['rate<0.01'],
  },
};

export default function (data) {
  // __VU % tokens.length 로 토큰을 순환하는 이유:
  // VU ID는 1부터 순차 증가하므로 나머지 연산으로 토큰 배열을 고르게 분산
  // 같은 유저가 중복 좋아요하면 서버에서 409로 막혀 INSERT 자체가 안 일어나므로
  // 경합 테스트 의미가 없어짐 — 반드시 유저를 분산해야 함
  const token = data.tokens[__VU % data.tokens.length];

  const headers = {
    'Content-Type': 'application/json',
    'Cookie': `accessToken=${token}`,
  };

  const res = http.post(`${BASE_URL}/api/posts/${data.postId}/likes`, null, {
    headers,
    responseCallback: likeResponseCallback,
  });
  check(res, {
    // 201: 좋아요 성공 / 409: 이미 좋아요 상태 (ErrorCode.ALREADY_LIKED, 토큰 순환으로 같은 유저가 겹칠 때)
    // 둘 다 서버가 의도대로 처리한 것이므로 에러로 보지 않음
    'like 201 or 409': (r) => r.status === 201 || r.status === 409,
  });

  sleep(Math.random() * 1.5 + 0.5);
}

// 측정 구간(default function)이 전부 끝난 뒤 딱 한 번 실행된다.
// 여기서 실패해도(예: 이미 취소된 계정) 다음 실행에 영향 없도록 200/404 둘 다 정상 처리한다.
export function teardown(data) {
  data.tokens.forEach((token) => {
    http.del(`${BASE_URL}/api/posts/${data.postId}/likes`, null, {
      headers: {
        'Content-Type': 'application/json',
        'Cookie': `accessToken=${token}`,
      },
      responseCallback: http.expectedStatuses(200, 404),
    });
  });

  // 더미 게시글 정리 — setup()에서 만든 게시글을 만든 계정으로 삭제한다.
  http.del(`${BASE_URL}/api/posts/${data.postId}`, null, {
    headers: { Cookie: `accessToken=${data.tokens[0]}` },
  });
}
