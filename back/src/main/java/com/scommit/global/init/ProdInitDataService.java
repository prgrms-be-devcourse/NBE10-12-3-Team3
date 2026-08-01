package com.scommit.global.init;

import com.scommit.domain.media.media.entity.Media;
import com.scommit.domain.media.media.entity.MediaType;
import com.scommit.domain.media.media.repository.MediaRepository;
import com.scommit.domain.post.comment.entity.Comment;
import com.scommit.domain.post.comment.repository.CommentRepository;
import com.scommit.domain.post.like.entity.Like;
import com.scommit.domain.post.like.repository.LikeRepository;
import com.scommit.domain.post.post.entity.Post;
import com.scommit.domain.post.post.entity.PostAccessLevel;
import com.scommit.domain.post.post.entity.PublishStatus;
import com.scommit.domain.post.post.repository.PostRepository;
import com.scommit.domain.post.postmedia.entity.PostMedia;
import com.scommit.domain.post.postmedia.entity.PostMediaType;
import com.scommit.domain.post.postmedia.repository.PostMediaRepository;
import com.scommit.domain.series.series.entity.Series;
import com.scommit.domain.series.series.repository.SeriesRepository;
import com.scommit.domain.subscription.subscription.entity.Subscription;
import com.scommit.domain.subscription.subscription.entity.SubscriptionTier;
import com.scommit.domain.subscription.subscription.repository.SubscriptionRepository;
import com.scommit.domain.user.user.entity.User;
import com.scommit.domain.user.user.entity.UserRole;
import com.scommit.domain.user.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

@Service
@Profile("prod")
@RequiredArgsConstructor
public class ProdInitDataService {

    private static final Random RNG = new Random(42);

    private static final String[] HEAVY_NICKNAMES = {
            "코드마법사", "알고리즘고수", "풀스택개발자", "데브옵스전문가", "보안전문가",
            "머신러닝엔지니어", "모바일개발자", "백엔드아키텍트", "프론트엔드리드", "클라우드전문가"
    };
    private static final String[] HEAVY_INTROS = {
            "Java와 Spring을 사랑하는 백엔드 개발자입니다. 클린 코드를 추구합니다.",
            "알고리즘과 자료구조를 연구하는 개발자입니다. 매일 PS를 즐깁니다.",
            "프론트부터 백엔드까지 전 영역을 다루는 풀스택 개발자입니다.",
            "CI/CD와 인프라 자동화를 전문으로 하는 DevOps 엔지니어입니다.",
            "보안 취약점 분석과 침투 테스트를 전문으로 합니다.",
            "딥러닝과 자연어처리를 연구하는 AI 엔지니어입니다.",
            "iOS/Android 네이티브 앱 개발을 즐기는 모바일 개발자입니다.",
            "대규모 서비스 아키텍처 설계를 전문으로 하는 백엔드 엔지니어입니다.",
            "React와 TypeScript로 사용자 경험을 만드는 프론트엔드 개발자입니다.",
            "AWS와 GCP를 활용한 클라우드 인프라 전문가입니다."
    };

    private static final String[] GENERAL_EMAILS = {
            // @dev.com (26)
            "alice@dev.com", "bob@dev.com", "charlie@dev.com", "diana@dev.com", "evan@dev.com",
            "fiona@dev.com", "george@dev.com", "hannah@dev.com", "ivan@dev.com", "julia@dev.com",
            "kevin@dev.com", "luna@dev.com", "mike@dev.com", "nina@dev.com", "oscar@dev.com",
            "petra@dev.com", "quinn@dev.com", "rose@dev.com", "sam@dev.com", "tina@dev.com",
            "uma@dev.com", "victor@dev.com", "wendy@dev.com", "xavier@dev.com", "yara@dev.com",
            "zack@dev.com",
            // @coder.com (23)
            "aaron@coder.com", "bella@coder.com", "carl@coder.com", "dora@coder.com", "eli@coder.com",
            "faith@coder.com", "greg@coder.com", "helen@coder.com", "iris@coder.com", "jack@coder.com",
            "kate@coder.com", "leo@coder.com", "mia@coder.com", "noah@coder.com", "olive@coder.com",
            "paul@coder.com", "queen@coder.com", "ryan@coder.com", "sara@coder.com", "tom@coder.com",
            "uva@coder.com", "val@coder.com", "will@coder.com",
            // @hack.io (21)
            "adam@hack.io", "betty@hack.io", "clara@hack.io", "dan@hack.io", "elena@hack.io",
            "felix@hack.io", "grace@hack.io", "hank@hack.io", "isla@hack.io", "jake@hack.io",
            "kim@hack.io", "lara@hack.io", "mason@hack.io", "nell@hack.io", "otto@hack.io",
            "pam@hack.io", "rex@hack.io", "stan@hack.io", "tara@hack.io", "ulf@hack.io",
            "vera@hack.io",
            // @tech.kr (20)
            "ana@tech.kr", "ben@tech.kr", "cora@tech.kr", "dex@tech.kr", "eve@tech.kr",
            "finn@tech.kr", "gaby@tech.kr", "hugo@tech.kr", "ike@tech.kr", "jan@tech.kr",
            "ken@tech.kr", "lily@tech.kr", "moe@tech.kr", "nan@tech.kr", "pip@tech.kr",
            "rob@tech.kr", "sue@tech.kr", "ted@tech.kr", "una@tech.kr", "zoe@tech.kr"
    };
    private static final String[] GENERAL_NICKNAMES = {
            // First 49
            "앨리스개발자", "밥코더", "찰리엔지니어", "다이애나개발", "에반테크",
            "피오나코드", "조지프로그래머", "한나개발자", "이반엔지니어", "줄리아코더",
            "케빈개발", "루나테크", "마이크코더", "니나엔지니어", "오스카개발자",
            "페트라코드", "퀸개발자", "로즈엔지니어", "샘코더", "티나개발",
            "우마테크", "빅터프로그래머", "웬디코더", "자비에르개발자", "야라엔지니어",
            "잭코드", "아론개발자", "벨라코더", "칼엔지니어", "도라개발",
            "일라이테크", "페이스코드", "그레그프로그래머", "헬렌개발자", "아이리스코더",
            "잭슨엔지니어", "케이트개발", "레오테크", "미아코더", "노아개발자",
            "올리브엔지니어", "폴코드", "퀸프로그래머", "라이언개발자", "사라코더",
            "톰엔지니어", "우바개발", "발코드", "윌프로그래머",
            // Additional 41
            "아담해커", "베티개발자", "클라라코더", "단엔지니어", "엘레나개발",
            "펠릭스테크", "그레이스코드", "행크프로그래머", "아일라개발자", "제이크코더",
            "킴엔지니어", "라라개발", "메이슨테크", "넬코드", "오토프로그래머",
            "팸개발자", "렉스코더", "스탄엔지니어", "타라개발", "울프테크",
            "베라코드", "아나개발자", "벤코더", "코라엔지니어", "덱스개발",
            "이브테크", "핀코드", "가비프로그래머", "휴고개발자", "아이크코더",
            "얀엔지니어", "켄개발", "릴리테크", "모에코드", "냔프로그래머",
            "피프개발자", "롭코더", "수에엔지니어", "테드개발", "우나테크",
            "조에코드"
    };
    private static final String[] GENERAL_INTROS = {
            "열심히 공부하는 주니어 개발자입니다.",
            "새로운 기술을 배우는 것을 즐깁니다.",
            "코드로 세상을 바꾸고 싶은 개발자입니다.",
            "문제 해결을 좋아하는 개발자입니다.",
            "협업과 소통을 중요시하는 개발자입니다.",
            "꾸준히 성장하는 소프트웨어 엔지니어입니다.",
            "사용자 중심의 서비스를 만들고 싶습니다."
    };

    private static final String[] SERIES_TITLES = {
            // user1 (Java/Spring): 0-5
            "Java 깊이 파고들기", "Spring Boot 실전 가이드", "JVM 튜닝과 최적화",
            "디자인 패턴 마스터하기", "클린 코드 실천법", "리팩토링 실전 전략",
            // user2 (Algorithm): 6-11
            "알고리즘 문제풀이 전략", "자료구조 완전 정복", "코딩 테스트 합격 가이드",
            "수학으로 보는 알고리즘", "그래프 알고리즘 심화", "동적 프로그래밍 마스터",
            // user3 (Frontend): 12-17
            "React 현대적 개발법", "TypeScript 완벽 이해", "프론트엔드 성능 최적화",
            "CSS 레이아웃 심화", "Next.js 풀스택 완전 정복", "상태 관리 패턴 비교",
            // user4 (DevOps): 18-23
            "Kubernetes 운영 실전", "Docker 컨테이너 완전 정복", "CI/CD 파이프라인 구축",
            "모니터링과 관찰 가능성", "테라폼으로 인프라 코딩", "서버리스 아키텍처 실전",
            // user5 (Security): 24-29
            "웹 보안 취약점 분석", "암호화와 인증 기술", "OWASP Top 10 대응",
            "침투 테스트 방법론", "Zero Trust 보안 모델", "API 보안 설계 가이드",
            // user6 (ML): 30-35
            "PyTorch로 배우는 딥러닝", "자연어처리 실전 프로젝트", "컴퓨터 비전 입문",
            "MLOps 파이프라인", "추천 시스템 구현", "시계열 분석 실전",
            // user7 (Mobile): 36-41
            "Swift로 만드는 iOS 앱", "Kotlin 안드로이드 개발", "크로스플랫폼 앱 개발",
            "모바일 UX 설계", "Flutter 완전 정복", "모바일 성능 최적화",
            // user8 (Architecture): 42-47
            "마이크로서비스 아키텍처", "이벤트 드리븐 시스템 설계", "데이터베이스 성능 최적화",
            "분산 시스템 기초", "메시지 큐 실전 활용", "캐싱 전략 완전 가이드",
            // user9 (Advanced Frontend): 48-53
            "웹 접근성 완전 가이드", "SEO 최적화 기술", "브라우저 렌더링 원리",
            "웹 컴포넌트 설계", "애니메이션과 인터랙션", "프론트엔드 테스팅 실전",
            // user10 (Cloud): 54-59
            "AWS 서비스 완전 정복", "비용 최적화 전략", "멀티클라우드 전략",
            "클라우드 보안 베스트 프랙티스", "데이터 엔지니어링 기초", "실시간 데이터 파이프라인",
            // General users: 60-99
            "개발자 성장 일기", "오픈소스 기여 시작하기", "Git 브랜치 전략", "코드 리뷰 잘하는 법",
            "개발 환경 세팅 가이드", "API 설계 원칙", "데이터베이스 입문", "리팩토링 실전",
            "테스트 주도 개발", "클린 코드 작성법", "HTTP 완전 이해", "Linux 기본기",
            "정규 표현식 마스터", "개발자 도구 활용법", "빌드 도구 완전 정복", "패키지 매니저 비교",
            "개발자 커리어 가이드", "기술 면접 준비", "사이드 프로젝트 시작하기", "오픈소스 라이선스 이해",
            "버전 관리 완전 정복", "마크다운 작성법", "IDE 활용 극대화", "터미널 생산성 높이기",
            "코드 문서화 전략", "기술 블로그 시작하기", "개발자 습관 만들기", "번아웃 극복하기",
            "학습 방법 최적화", "네트워크 기초 다지기", "운영체제 이해하기", "함수형 프로그래밍 입문",
            "객체지향 설계 원칙", "동시성 프로그래밍 기초", "암호화 기초 이해", "REST API 설계",
            "GraphQL 시작하기", "WebSocket 실시간 통신", "마이크로프론트엔드 입문", "컨테이너 기초 다지기"
    };
    private static final String[] SERIES_BODIES = {
            "실무에서 바로 쓸 수 있는 내용을 중심으로 정리했습니다.",
            "기초부터 차근차근 다루는 시리즈입니다.",
            "개인적으로 공부하며 정리한 내용들을 공유합니다.",
            "매주 업데이트되는 시리즈입니다. 피드백 환영합니다.",
            "심화 내용을 다루므로 기본 지식이 필요합니다."
    };
    private static final String[] POST_TITLES = {
            "처음 시작하는 법", "핵심 개념 정리", "실전 예제로 배우기", "자주 하는 실수와 해결법",
            "성능 최적화 팁", "모범 사례 소개", "입문자를 위한 가이드", "중급자를 위한 심화 내용",
            "실무에서 배운 것들", "튜토리얼 완전판", "비교 분석", "마이그레이션 가이드",
            "트러블슈팅 경험담", "코드 리뷰 사례", "아키텍처 결정 이유", "도구 선택 기준",
            "리팩토링 사례 분석", "테스트 전략 소개", "배포 자동화 경험", "보안 체크리스트",
            "심화 개념 파헤치기", "실제 프로젝트 적용기", "흔한 오해 바로잡기", "전문가 팁 모음",
            "단계별 학습 가이드", "원리 깊이 이해하기", "최신 트렌드 정리", "실전 인터뷰 질문",
            "성공 사례와 실패 사례", "커뮤니티 Q&A 정리"
    };
    private static final String[] POST_BODIES = {
            "이번 글에서는 핵심 개념을 살펴보고 실제 코드 예제와 함께 알아보겠습니다. " +
                    "처음 접하시는 분들도 이해하기 쉽게 단계별로 설명드리겠습니다. 궁금한 점은 댓글로 질문해 주세요.",
            "실무에서 직접 경험한 내용을 바탕으로 작성했습니다. " +
                    "이론보다는 실제로 써먹을 수 있는 내용 위주로 정리했으니 참고해 주세요. 피드백 언제나 환영합니다.",
            "많은 분들이 헷갈려하는 부분을 중심으로 정리해 보았습니다. " +
                    "저도 처음엔 이 부분에서 많이 헤맸는데 이 글이 도움이 됐으면 합니다. 더 좋은 방법 있으면 알려주세요.",
            "공식 문서와 여러 레퍼런스를 참고하여 정리한 내용입니다. " +
                    "완벽하지 않을 수 있으니 공식 문서도 함께 확인해 보세요. 오류 발견 시 댓글로 알려주시면 수정하겠습니다.",
            "이 글은 시리즈의 일부입니다. 이전 글을 읽고 오시면 더욱 이해가 쉽습니다. " +
                    "예제 코드는 GitHub에서 확인하실 수 있습니다. 다음 편에서는 더 심화된 내용을 다룰 예정입니다."
    };
    private static final String[] COMMENT_BODIES = {
            "좋은 글 감사합니다! 많이 배웠습니다.",
            "정말 유익한 내용이네요. 덕분에 이해가 됐습니다.",
            "이 부분이 궁금했는데 잘 설명해주셨네요.",
            "실무에서도 자주 쓰이는 내용이라 도움이 많이 됩니다.",
            "예제 코드가 이해하기 쉽게 잘 작성되어 있네요.",
            "저도 비슷한 경험이 있는데 공감이 많이 됩니다.",
            "다음 편도 기대하겠습니다!",
            "혹시 이 부분 더 자세히 설명해주실 수 있나요?",
            "다른 방법도 있는데 이 방법이 더 깔끔하네요.",
            "북마크해두고 나중에 다시 읽겠습니다.",
            "입문자인데 이해하기 쉽게 잘 써주셨어요.",
            "실제 프로젝트에 적용해봤는데 잘 동작합니다!",
            "관련 레퍼런스도 공유해주시면 감사하겠습니다.",
            "이 시리즈 정말 퀄리티가 높네요. 구독했습니다.",
            "오탈자가 있는 것 같습니다. 확인 부탁드립니다."
    };

    private final UserRepository userRepository;
    private final SeriesRepository seriesRepository;
    private final PostRepository postRepository;
    private final MediaRepository mediaRepository;
    private final PostMediaRepository postMediaRepository;
    private final CommentRepository commentRepository;
    private final LikeRepository likeRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PasswordEncoder passwordEncoder;

    private static PublishStatus randomStatus(boolean isHeavy) {
        int r = RNG.nextInt(10);
        if (isHeavy) return r < 7 ? PublishStatus.PUBLIC : r < 9 ? PublishStatus.DRAFT : PublishStatus.PRIVATE;
        return r < 5 ? PublishStatus.PUBLIC : r < 8 ? PublishStatus.DRAFT : PublishStatus.PRIVATE;
    }

    private static PostAccessLevel randomLevel(boolean isHeavy) {
        return RNG.nextInt(10) < (isHeavy ? 3 : 1) ? PostAccessLevel.PAID : PostAccessLevel.FREE;
    }

    @Transactional
    public void insertAll(List<String> thumbnailUrls, List<String> bodyUrls) {
        String pw = passwordEncoder.encode("123456");
        LocalDate today = LocalDate.now();

        // Admin
        userRepository.save(new User("admin@test.com", pw, "관리자", "사이트 관리자입니다.", UserRole.ADMIN));

        // Heavy users: user1@test.com ~ user10@test.com
        List<User> heavyUsers = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            heavyUsers.add(userRepository.save(
                    new User("user" + (i + 1) + "@test.com", pw, HEAVY_NICKNAMES[i], HEAVY_INTROS[i], UserRole.USER)));
        }

        // General users (90명)
        List<User> generalUsers = new ArrayList<>();
        for (int i = 0; i < 90; i++) {
            generalUsers.add(userRepository.save(
                    new User(GENERAL_EMAILS[i], pw, GENERAL_NICKNAMES[i], GENERAL_INTROS[i % GENERAL_INTROS.length], UserRole.USER)));
        }

        List<User> allUsers = new ArrayList<>();
        allUsers.addAll(heavyUsers);
        allUsers.addAll(generalUsers);

        // Heavy user series: 10 × 6 = 60 (SERIES_TITLES 0~59)
        List<Series> heavySeries = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 6; j++) {
                int idx = i * 6 + j;
                heavySeries.add(seriesRepository.save(Series.builder()
                        .user(heavyUsers.get(i))
                        .title(SERIES_TITLES[idx])
                        .body(SERIES_BODIES[idx % SERIES_BODIES.length])
                        .build()));
            }
        }

        // General user series: first 40 users × 1 = 40 (SERIES_TITLES 60~99)
        List<Series> generalSeries = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            generalSeries.add(seriesRepository.save(Series.builder()
                    .user(generalUsers.get(i))
                    .title(SERIES_TITLES[60 + i])
                    .body(SERIES_BODIES[(60 + i) % SERIES_BODIES.length])
                    .build()));
        }

        // Posts — globalIndex determines media and public/free override
        // globalIndex 0~49: PUBLIC+FREE, no media
        // globalIndex 50~99: PUBLIC+FREE, THUMBNAIL+BODY
        // globalIndex 100+: random status/access, no media
        List<Post> allPosts = new ArrayList<>();
        int globalIndex = 0;

        // Heavy user posts: 10 × 40 = 400 (앞 32개 시리즈 소속, 뒤 8개 standalone)
        for (int i = 0; i < 10; i++) {
            List<Series> mySeries = heavySeries.subList(i * 6, i * 6 + 6);
            for (int j = 0; j < 40; j++) {
                Series series = j < 32 ? mySeries.get(j % 6) : null;
                boolean forcePublicFree = globalIndex < 100;
                PublishStatus status = forcePublicFree ? PublishStatus.PUBLIC : randomStatus(true);
                PostAccessLevel level = forcePublicFree ? PostAccessLevel.FREE
                        : (status == PublishStatus.PUBLIC ? randomLevel(true) : PostAccessLevel.FREE);
                Post post = postRepository.save(Post.builder()
                        .user(heavyUsers.get(i)).series(series)
                        .title(POST_TITLES[(i * 40 + j) % POST_TITLES.length])
                        .body(POST_BODIES[j % POST_BODIES.length])
                        .publishStatus(status).accessLevel(level)
                        .build());
                attachMedia(post, globalIndex, thumbnailUrls, bodyUrls);
                allPosts.add(post);
                globalIndex++;
            }
        }

        // General user posts: 90 × 2~3 = 180~270
        for (int i = 0; i < 90; i++) {
            Series series = (i < 40 && RNG.nextBoolean()) ? generalSeries.get(i) : null;
            int postCount = 2 + RNG.nextInt(2);
            for (int j = 0; j < postCount; j++) {
                PublishStatus status = randomStatus(false);
                PostAccessLevel level = status == PublishStatus.PUBLIC ? randomLevel(false) : PostAccessLevel.FREE;
                allPosts.add(postRepository.save(Post.builder()
                        .user(generalUsers.get(i)).series(series)
                        .title(POST_TITLES[(i * 3 + j) % POST_TITLES.length])
                        .body(POST_BODIES[j % POST_BODIES.length])
                        .publishStatus(status).accessLevel(level)
                        .build()));
                globalIndex++;
            }
        }

        // Comments (PUBLIC 포스트만, 헤비유저 포스트 5~15개, 일반유저 포스트 0~5개)
        List<Comment> comments = new ArrayList<>();
        for (int p = 0; p < allPosts.size(); p++) {
            Post post = allPosts.get(p);
            if (post.getPublishStatus() != PublishStatus.PUBLIC) continue;
            int count = p < 400 ? 5 + RNG.nextInt(11) : RNG.nextInt(6);
            if (count == 0) continue;
            Long authorId = post.getUser().getId();
            List<User> pool = new ArrayList<>(allUsers);
            pool.removeIf(u -> u.getId().equals(authorId));
            for (int k = 0; k < count; k++) {
                comments.add(Comment.builder()
                        .post(post)
                        .user(pool.get(RNG.nextInt(pool.size())))
                        .body(COMMENT_BODIES[RNG.nextInt(COMMENT_BODIES.length)])
                        .build());
            }
        }
        commentRepository.saveAll(comments);

        // Likes (PUBLIC 포스트만, 헤비유저 포스트 20~30%, 일반유저 포스트 5~15%)
        List<Like> likes = new ArrayList<>();
        for (int p = 0; p < allPosts.size(); p++) {
            Post post = allPosts.get(p);
            if (post.getPublishStatus() != PublishStatus.PUBLIC) continue;
            double ratio = p < 400
                    ? 0.20 + RNG.nextDouble() * 0.10
                    : 0.05 + RNG.nextDouble() * 0.10;
            int likerCount = (int) (allUsers.size() * ratio);
            List<User> shuffled = new ArrayList<>(allUsers);
            Collections.shuffle(shuffled, RNG);
            Long authorId = post.getUser().getId();
            int added = 0;
            for (User u : shuffled) {
                if (added >= likerCount) break;
                if (!u.getId().equals(authorId)) {
                    likes.add(new Like(post, u));
                    post.increaseLikeCount();
                    added++;
                }
            }
        }
        likeRepository.saveAll(likes);

        // Subscriptions: 일반유저 → 헤비유저 2~4명 (MEMBERSHIP 30%, FOLLOW 70%)
        List<Subscription> subs = new ArrayList<>();
        for (User subscriber : generalUsers) {
            List<User> creators = new ArrayList<>(heavyUsers);
            int creatorCount = 2 + RNG.nextInt(3);
            for (int k = 0; k < creatorCount; k++) {
                User creator = creators.remove(RNG.nextInt(creators.size()));
                boolean isMembership = RNG.nextInt(10) < 3;
                LocalDate startedAt = isMembership
                        ? today.minusDays(RNG.nextInt(28))
                        : today.minusDays(RNG.nextInt(180));
                subs.add(Subscription.builder()
                        .user(subscriber).creator(creator)
                        .tier(isMembership ? SubscriptionTier.MEMBERSHIP : SubscriptionTier.FOLLOW)
                        .startedAt(startedAt)
                        .expiredAt(isMembership ? startedAt.plusMonths(1) : null)
                        .build());
            }
        }
        subscriptionRepository.saveAll(subs);
    }

    private void attachMedia(Post post, int globalIndex, List<String> thumbnailUrls, List<String> bodyUrls) {
        if (globalIndex < 50 || globalIndex >= 100) return;
        int imgIdx = globalIndex - 50;
        String thumbUrl = thumbnailUrls.get(imgIdx);
        String bodyUrl = bodyUrls.get(imgIdx);
        if (thumbUrl != null) {
            Media thumb = mediaRepository.save(Media.builder().url(thumbUrl).type(MediaType.IMAGE).build());
            postMediaRepository.save(PostMedia.builder().post(post).media(thumb).type(PostMediaType.THUMBNAIL).build());
        }
        if (bodyUrl != null) {
            Media body = mediaRepository.save(Media.builder().url(bodyUrl).type(MediaType.IMAGE).build());
            postMediaRepository.save(PostMedia.builder().post(post).media(body).type(PostMediaType.BODY).build());
        }
    }
}
