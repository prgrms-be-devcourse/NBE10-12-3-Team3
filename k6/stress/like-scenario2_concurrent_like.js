/**
 * 시나리오 B — 여러 사람이 같은 게시글 동시 좋아요 (스트레스)
 * 목적: 동시 쓰기 경합이 심해질 때 DB 락 대기 발생 시점 탐색
 *
 * [선택 이유]
 *   시나리오 A가 단일 유저 반복 락에 집중한다면, 시나리오 B는 다수 유저의 동시 INSERT 경합에 집중
 *   400 VU까지 올려 DB Connection Pool 포화 → 타임아웃 에러 발생 시점을 탐색하기 위해 설계
 *   thresholds 없음: 한계점 탐색이 목적이므로 통과/실패 기준 없이 지표 관측에 집중
 *
 * 실행 방법:
 *   k6 run -e BASE_URL=https://api.scommit.store like-scenario2_concurrent_like.js
 *
 * [사전 준비]
 *   DB에 아래 테스트 계정들이 등록되어 있어야 함 (총 100개)
 *   setup()에서 자동으로 로그인하여 토큰을 발급받음
 *   계정이 100개라 400 VU 시 일부 유저 토큰이 순환 사용됨
 */

import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'https://api.scommit.store';

// 모듈 스코프에서 한 번만 생성(이유는 performance/like-scenario1_repeat_toggle.js 상단 참고)
const likeResponseCallback = http.expectedStatuses(201, 409);

const PASSWORD = '123456';

// 총 100개 테스트 계정 (user1~10 + general 90개)
// 스트레스 테스트 최대 400 VU이므로 100개 토큰이 4번 순환됨
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
// 100개 계정 로그인을 각 VU가 개별로 하면 테스트 시작 시 대량 로그인 요청이 동시에 발생
// setup()에서 한 번에 순차 처리해 로그인 자체가 성능 지표를 오염시키는 것을 방지
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
      title: 'k6 stress test dummy (like-scenario2)',
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
    { duration: '1m', target: 10 },   // 워밍업
    { duration: '2m', target: 100 },  // 부하 증가 1
    { duration: '2m', target: 200 },  // 부하 증가 2
    { duration: '2m', target: 300 },  // 부하 증가 3
    { duration: '2m', target: 400 },  // 부하 증가 4: 커넥션 풀 포화, 에러율 급증 시점 탐색
    { duration: '1m', target: 0 },    // 쿨다운
  ],
};

export default function (data) {
  // __VU % tokens.length 로 토큰을 순환하는 이유:
  // VU ID는 1부터 순차 증가하므로 나머지 연산으로 토큰 배열을 고르게 분산
  // 같은 유저가 중복 좋아요하면 서버에서 409로 막혀 INSERT 자체가 발생하지 않으므로
  // 반드시 유저를 분산해야 동시 쓰기 경합이 실제로 일어남
  const token = data.tokens[__VU % data.tokens.length];

  const headers = {
    'Content-Type': 'application/json',
    'Cookie': `accessToken=${token}`,
  };

  // sleep을 넣지 않는 이유:
  // 400 VU가 대기 없이 동시에 같은 게시글에 INSERT를 보내 DB 커넥션 풀 포화 시점을 탐색
  const res = http.post(`${BASE_URL}/api/posts/${data.postId}/likes`, null, {
    headers,
    responseCallback: likeResponseCallback,
  });
  check(res, {
    // 201: 좋아요 성공 / 409: 토큰 순환으로 같은 유저가 겹쳐 중복 좋아요 시(ErrorCode.ALREADY_LIKED)
    // 둘 다 서버가 정상 처리한 것이므로 에러로 보지 않음
    'like status ok': (r) => r.status === 201 || r.status === 409,
  });
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
