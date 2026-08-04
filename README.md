# SCommit (Team 3 - Triple S)

<div align="center">
  <a href="https://www.scommit.store/">
    <img src="front/src/app/icon.jpg" alt="SCommit Logo" width="160" />
  </a>
  <br><br>
  <h3>개발자의 학습을 커밋하다</h3>
  <p>개발자를 위한 지식 공유 구독 플랫폼</p>
</div>

---

## 🚀 Live Demo

| 서비스                  | URL                                                                                                |
|:---------------------|:---------------------------------------------------------------------------------------------------|
| **SCommit 서비스**      | [https://www.scommit.store](https://www.scommit.store)                                             |
| **API 문서 (Swagger)** | [https://api.scommit.store/swagger-ui/index.html](https://api.scommit.store/swagger-ui/index.html) |

---

## 📝 프로젝트 소개

국내 개발 콘텐츠는 유튜브·벨로그·인프런에 분산되어 있습니다. 창작자는 수익화 도구가 부족하고, 구독자는 흩어진 콘텐츠를 찾아다녀야 합니다. **SCommit**은 이 문제를 해결하는 개발자 특화 지식 구독
플랫폼입니다.

창작자는 포스트를 시리즈(course) 단위로 묶어 발행하고, 포스트마다 공개 범위(`DRAFT` / `PRIVATE` / `PUBLIC`)와 접근 등급(`FREE` / `PAID`)을 독립적으로 설정합니다.
구독자는 창작자를 팔로우(무료)하거나 멤버십(유료)에 가입해 프리미엄 콘텐츠에 접근하고, 새 게시글이 올라오면 SSE 알림을 즉시 받습니다.

---

## 📸 Preview

![Preview](docs/preview.png)

---

## 주요 기능 (Key Features)

### 1. 포스트 · 시리즈 CRUD

TipTap 리치텍스트 에디터로 글·이미지·동영상을 작성합니다. 포스트는 발행 상태(`DRAFT` 임시저장 / `PRIVATE` 비공개 / `PUBLIC` 공개)와 접근 등급(`FREE` / `PAID`)을 각각
설정할 수 있습니다. 포스트를 시리즈로 묶으면 코스형 학습 콘텐츠로 구성됩니다. 홈은 Slice 기반 무한 스크롤, 창작자 프로필은 Page 기반 번호 페이지네이션으로 조회합니다. 제목·본문 키워드 검색을
지원합니다.

### 2. 구독 등급 기반 접근 제어

구독 상태는 `FOLLOW`(무료 팔로우) · `MEMBERSHIP`(유료 멤버십) 2단계이며, 미구독자는 `NONE`으로 간주합니다. `PAID` 포스트에 `MEMBERSHIP` 구독자가 아닌 유저가 접근하면
서버가 본문을 잠근 부분 응답을 반환하고 클라이언트는 잠금 화면을 표시합니다. `FOLLOW`는 콘텐츠 접근 권한이 아닌 창작자와의 팔로우 관계로, 팔로워 수 집계에 사용됩니다.

### 3. SSE 실시간 알림

포스트 발행·댓글 작성·구독 이벤트가 발생하면 해당 유저에게 알림을 즉시 전달합니다. 클라이언트는 별도 폴링 없이 서버 이벤트를 수신하며, 연결 해제 시 브라우저가 자동 재연결합니다.

### 4. 미디어 업로드

`multipart/form-data`로 파일을 전송하면 백엔드가 Cloudinary에 직접 저장하고, DB에는 URL만 기록합니다. 이미지·동영상 MIME 타입만 허용하며 그 외 파일 형식은 서버에서 거부하고,
prod 환경에서는 단일 파일 100MB · 요청 전체 100MB 제한이 적용됩니다. dev 환경에서는 로컬 파일 시스템(`back/src/main/resources/static/media/`)을 사용합니다.

### 5. 좋아요 · 북마크 · 댓글

포스트에 좋아요와 북마크를 남길 수 있으며, 댓글은 10개 단위 페이지네이션으로 조회합니다. 북마크한 포스트는 마이페이지에서 다시 확인할 수 있습니다. 댓글 작성은 포스트 작성자에게 SSE 알림의 트리거가 됩니다.

### 6. 유저 · 팔로우 시스템

이메일 회원가입·로그인, 프로필 이미지 업로드, 닉네임 검색을 지원합니다. 인증은 HTTP-only 쿠키(`accessToken` + `refreshToken`)로 처리되어 JavaScript에서 토큰에 접근할 수
없습니다. 창작자 프로필 페이지에는 팔로워 수와 발행 포스트 목록이 함께 노출됩니다.

---

## 기술 하이라이트 (Tech Highlights)

### SSE로 구현한 실시간 알림

HTTP 폴링이나 WebSocket 대신 SSE를 선택했습니다. 알림은 서버→클라이언트 단방향 이벤트로 충분하고, SSE는 HTTP 기반이라 별도 프로토콜 업그레이드 없이 Spring MVC `SseEmitter`로
바로 구현할 수 있습니다. 유저 ID를 키로 `SseEmitter`를 `SseEmitterRepository`에 보관하고, 포스트 발행(`PostService`) · 댓글(`CommentService`) · 구독(
`SubscriptionService`) 이벤트 발생 시 서비스 레이어에서 즉시 푸시합니다. 연결은 30분 타임아웃 후 자동 해제되며, 클라이언트 재연결은 브라우저가 처리합니다.

### HTTP-only 쿠키 JWT 인증 + 구독 등급 접근 제어

세션리스 아키텍처를 위해 JWT를 선택했습니다. 토큰은 `accessToken` + `refreshToken`을 HTTP-only 쿠키에 담아 발급해 JavaScript에서 접근할 수 없도록 XSS 경로의 토큰
탈취를 방지합니다. 포스트마다 `PostAccessLevel(FREE/PAID)`을 설정하고, Spring Security 필터가 쿠키의 JWT로 유저를 인증한 뒤 `PostService`가
`SubscriptionTier`를 조회해 `PAID` 포스트 본문 노출 여부를 결정합니다.

### Cloudinary CDN 미디어 파이프라인

이미지·동영상을 애플리케이션 서버에 두지 않고 Cloudinary CDN에 직접 저장합니다. 백엔드는 업로드 후 반환된 URL만 DB에 기록하며 서버 디스크 I/O가 없습니다. dev 환경은 로컬 파일 서비스로
대체해 Cloudinary 계정 없이 개발할 수 있습니다. 프로파일(`dev` / `prod`)로 미디어 서비스 구현체를 교체합니다.

### 풀 모니터링 스택 구축 (EC2 2-tier 분리)

앱 EC2와 모니터링 EC2를 분리해 운영 부하가 관측 시스템에 영향을 주지 않도록 구성했습니다.

- **앱 EC2**: Spring Boot 앱 · MySQL · **Promtail**(컨테이너 로그 수집 후 Loki로 푸시) · **MySQL Exporter**(DB 메트릭 노출)
- **모니터링 EC2**: **Prometheus**(메트릭 수집) · **Grafana**(대시보드) · **Loki**(로그 집계) · **Node Exporter**(OS 메트릭)

Spring Actuator의 `/actuator/prometheus` 엔드포인트로 JVM 메모리·GC·HikariCP 커넥션 풀·HTTP 요청 p95/p99 레이턴시를 수집하고, 애플리케이션·OS·DB·로그를 단일
Grafana 대시보드에서 확인합니다.

### JaCoCo 커버리지 게이트

라인 커버리지 70%, 브랜치 커버리지 60% 기준을 JaCoCo로 설정해 CI에서 자동으로 강제합니다. 기준 미달 시 빌드가 실패하므로 테스트 없는 코드는 `main`에 머지될 수 없습니다. 현재 275개 단위
테스트로 전체 라인 커버리지 76%를 달성했습니다.

---

## 기술 스택 (Tech Stack)

### Backend

![Java](https://img.shields.io/badge/Java_25-ED8B00?style=flat-square&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot_4.1-6DB33F?style=flat-square&logo=springboot&logoColor=white)
![Spring Security](https://img.shields.io/badge/Spring_Security-6DB33F?style=flat-square&logo=springsecurity&logoColor=white)
![Spring Data JPA](https://img.shields.io/badge/Spring_Data_JPA-6DB33F?style=flat-square&logo=spring&logoColor=white)
![JWT](https://img.shields.io/badge/JWT-000000?style=flat-square&logo=jsonwebtokens&logoColor=white)
![SSE](https://img.shields.io/badge/SSE-FF6600?style=flat-square&logoColor=white)
![Swagger](https://img.shields.io/badge/Swagger_UI-85EA2D?style=flat-square&logo=swagger&logoColor=black)
![JUnit5](https://img.shields.io/badge/JUnit_5-25A162?style=flat-square&logo=junit5&logoColor=white)
![JaCoCo](https://img.shields.io/badge/JaCoCo-C71A36?style=flat-square&logoColor=white)

### Frontend

![Next.js](https://img.shields.io/badge/Next.js_16-000000?style=flat-square&logo=nextdotjs&logoColor=white)
![React](https://img.shields.io/badge/React_19-61DAFB?style=flat-square&logo=react&logoColor=black)
![TypeScript](https://img.shields.io/badge/TypeScript-3178C6?style=flat-square&logo=typescript&logoColor=white)
![Tailwind CSS](https://img.shields.io/badge/Tailwind_CSS_v4-06B6D4?style=flat-square&logo=tailwindcss&logoColor=white)
![TipTap](https://img.shields.io/badge/TipTap-5C5C8A?style=flat-square&logoColor=white)
![Framer Motion](https://img.shields.io/badge/Framer_Motion-0055FF?style=flat-square&logo=framer&logoColor=white)

### Infra & DevOps

![MySQL](https://img.shields.io/badge/MySQL_8.0-4479A1?style=flat-square&logo=mysql&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-2496ED?style=flat-square&logo=docker&logoColor=white)
![GitHub Actions](https://img.shields.io/badge/GitHub_Actions-2088FF?style=flat-square&logo=githubactions&logoColor=white)
![Amazon EC2](https://img.shields.io/badge/Amazon_EC2-FF9900?style=flat-square&logo=amazonec2&logoColor=white)
![Vercel](https://img.shields.io/badge/Vercel-000000?style=flat-square&logo=vercel&logoColor=white)
![Cloudinary](https://img.shields.io/badge/Cloudinary-3448C5?style=flat-square&logo=cloudinary&logoColor=white)

### Monitoring & Testing

![K6](https://img.shields.io/badge/K6-7D64FF?style=flat-square&logo=k6&logoColor=white)
![Prometheus](https://img.shields.io/badge/Prometheus-E6522C?style=flat-square&logo=prometheus&logoColor=white)
![Grafana](https://img.shields.io/badge/Grafana-F46800?style=flat-square&logo=grafana&logoColor=white)
![Loki](https://img.shields.io/badge/Loki-F5A623?style=flat-square&logoColor=white)

---

## 성능 테스트 (K6)

`performance/`(정상 부하)와 `stress/`(한계 부하) 두 세트로 포스트 조회 3종·좋아요 2종 시나리오를 구성했습니다. 아래는 프로덕션 서버(`api.scommit.store`)에서 측정한 대표
2개 결과입니다.

| 시나리오             | 내용                                | VU  |   p(95)   | 에러율 |   처리량    |
|:-----------------|:----------------------------------|:---:|:---------:|:---:|:--------:|
| **로그인 유저 순차 조회** | JWT 인증 + 개인화 쿼리 포함, 1~100번 포스트 순회 | 100 | **158ms** | 0%  | 28 req/s |
| **동시 좋아요**       | 100명이 동일 포스트에 동시 INSERT 경합        | 100 | **268ms** | 0%  | 28 req/s |

> 동일 100 VU 조건에서 비로그인 조회와 JWT 로그인 조회의 p95 차이는 **3ms 미만**으로, Spring Security 인증 필터 오버헤드가 사실상 0에 수렴합니다.  
> 동시 좋아요 시나리오는 HikariCP 기본 풀(10개)만으로 100 VU 동시 쓰기 경합을 처리했습니다.

![Grafana Dashboard](docs/grafana.png)

---

## CI/CD (GitHub Actions)

3개의 워크플로우로 코드 품질 검증, 자동 배포, AI 코드 리뷰를 처리합니다.

### CI — 테스트 & 커버리지 검증 (`ci.yml`)

`main` 브랜치에 push 또는 PR이 열릴 때마다 실행됩니다.

1. JDK 25 Temurin 설치
2. `./gradlew check` — JUnit5 단위 테스트 + JaCoCo 커버리지 검증 (라인 70% · 브랜치 60% 미달 시 빌드 실패)
3. JaCoCo HTML 리포트를 GitHub Actions 아티팩트로 업로드

### CD — 빌드 & 배포 (`cd.yml`)

`main`에 push될 때 백엔드 관련 파일(`back/**`, `docker-compose.yml`, `promtail-config.yml`)이 변경된 경우에만 실행됩니다. 프론트엔드 변경은 Vercel이 자동
처리합니다.

1. Docker Buildx로 이미지 빌드 (GHA 레이어 캐시 사용)
2. GHCR(`ghcr.io/prgrms-be-devcourse/nbe10-12-3-team3:latest`)에 이미지 푸시
3. SCP로 `docker-compose.yml`, `promtail-config.yml`을 EC2로 전송
4. EC2 SSH 접속 → `.env` 파일 생성 → `mysql-exporter.cnf` 생성 → `docker compose pull` + `docker compose up -d`

### AI 코드 리뷰 (`code-review.yml`)

`main` 대상 PR이 열리거나 업데이트될 때마다 실행됩니다. (초안 PR 제외)

- `*.lock`, `*.svg` 등을 제외한 diff를 **Gemini 2.5 Flash**에 전달
- **P1 (Critical) / P2 (Major) / P3 (Minor)** 등급으로 분류된 인라인 코멘트와 요약 코멘트를 PR에 자동 게시
- 동일 PR의 기존 리뷰 코멘트는 덮어써 중복 게시를 방지

```
[Push to main]
      │
      ├─ CI ──▶ JUnit5 테스트 + JaCoCo 커버리지 검증
      │
      ├─ CD ──▶ Docker 빌드 ──▶ GHCR 푸시 ──▶ EC2 배포 (백엔드 변경 시에만)
      │
      └─ Vercel ─▶ 프론트엔드 자동 배포

[PR to main]
      └─ AI Code Review ──▶ Gemini 2.5 Flash ──▶ P1/P2/P3 인라인 코멘트
```

---

## 시스템 구성도 (Architecture)

![Architecture](docs/architecture.png)

---

## 데이터베이스 스키마 (ERD)

![ERD](docs/erd.png)

---

## 🛠 시작하기 (Getting Started)

로컬 개발 환경은 H2 파일 기반 데이터베이스를 사용합니다. DB를 별도로 설치하지 않아도 됩니다.

### 1. 환경변수 준비

| 변수               | 용도                        |
|:-----------------|:--------------------------|
| `JWT_SECRET_KEY` | JWT 서명 비밀키 (256bit 이상 권장) |

> dev 프로파일이 기본값으로 적용됩니다. H2 파일 DB(`./back/triples`)와 로컬 미디어 저장소를 사용하므로 DB 인증 정보나 Cloudinary 키는 필요하지 않습니다.

### 2. 백엔드 실행

```bash
cd back
JWT_SECRET_KEY=your-secret-key ./gradlew bootRun
```

### 3. 프론트엔드 실행

```bash
cd front
pnpm install
pnpm dev
```

| 엔드포인트      | URL                                         |
|:-----------|:--------------------------------------------|
| 서비스        | http://localhost:3000                       |
| Swagger UI | http://localhost:8080/swagger-ui/index.html |
| H2 콘솔      | http://localhost:8080/h2-console            |

### 테스트 및 커버리지

```bash
# 단위 테스트 실행 + JaCoCo 리포트 생성
cd back && ./gradlew test

# 커버리지 리포트 확인
open back/build/reports/jacoco/test/html/index.html
```

---

## 프로젝트 구조 (Project Structure)

### Backend Structure

```
back/
├── src/main/java/com/scommit/
│   ├── domain/
│   │   ├── user/           # 유저 도메인 (user, usermedia)
│   │   ├── post/           # 포스트 도메인 (post, like, bookmark, comment, postmedia)
│   │   ├── series/         # 시리즈 도메인 (series, seriesmedia)
│   │   ├── subscription/   # 구독 도메인
│   │   ├── notification/   # SSE 알림 도메인
│   │   └── media/          # 미디어 공통 도메인
│   └── global/
│       ├── config/         # 전역 설정 (Security, JPA 등)
│       ├── security/       # JWT 필터 및 인증/인가 구현
│       ├── exception/      # 전역 예외 처리
│       ├── dto/            # 공통 응답 객체
│       ├── base/           # BaseEntity 등 공통 추상 클래스
│       └── init/           # 초기 데이터 설정
└── src/main/resources/     # application.yml 등 환경별 설정 파일
```

### Frontend Structure

```
front/
├── src/
│   ├── app/                # Next.js App Router 페이지
│   │   ├── posts/          # 게시글 관련 페이지
│   │   ├── series/         # 시리즈 관련 페이지
│   │   ├── users/          # 유저 프로필 페이지
│   │   ├── mypage/         # 마이페이지
│   │   ├── bookmarks/      # 북마크 페이지
│   │   └── search/         # 검색 페이지
│   ├── components/         # 재사용 가능한 UI 컴포넌트
│   │   ├── ui/             # 기본 UI 컴포넌트
│   │   ├── common/         # 공통 레이아웃 컴포넌트
│   │   ├── editor/         # TipTap 리치텍스트 에디터
│   │   ├── comment/        # 댓글 컴포넌트
│   │   └── mypage/         # 마이페이지 컴포넌트
│   ├── hooks/              # 커스텀 React 훅
│   ├── lib/                # 유틸리티 및 API 클라이언트
│   └── providers/          # React Context Provider
├── next.config.ts          # Next.js 설정
└── package.json
```

---

## 팀원 (Team)

| 이름  | 역할                                           | GitHub                                                                                                                              |
|:----|:---------------------------------------------|:------------------------------------------------------------------------------------------------------------------------------------|
| 남효림 | 유저 도메인, Spring Security, 유저 프론트              | [![GitHub](https://img.shields.io/badge/GitHub-181717?style=flat-square&logo=github&logoColor=white)](https://github.com/EuniceNam) |
| 오준서 | 시리즈·미디어 도메인, 배포·인프라, SSE 알림, 공통 컴포넌트·시리즈 프론트 | [![GitHub](https://img.shields.io/badge/GitHub-181717?style=flat-square&logo=github&logoColor=white)](https://github.com/piker0925) |
| 최선진 | 구독 도메인, K6 부하테스트, 구독 프론트                     | [![GitHub](https://img.shields.io/badge/GitHub-181717?style=flat-square&logo=github&logoColor=white)](https://github.com/Ant1Ch3aT) |
| 한철완 | 와이어프레임 설계, 포스트·댓글 도메인, 포스트·댓글 프론트, K6 부하테스트  | [![GitHub](https://img.shields.io/badge/GitHub-181717?style=flat-square&logo=github&logoColor=white)](https://github.com/Mungwani)  |
