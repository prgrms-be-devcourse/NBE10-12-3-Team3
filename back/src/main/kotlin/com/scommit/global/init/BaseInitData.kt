package com.scommit.global.init

import com.scommit.domain.post.comment.entity.Comment
import com.scommit.domain.post.comment.repository.CommentRepository
import com.scommit.domain.post.post.entity.Post
import com.scommit.domain.post.post.entity.PostAccessLevel
import com.scommit.domain.post.post.entity.PublishStatus
import com.scommit.domain.post.post.repository.PostRepository
import com.scommit.domain.series.series.entity.Series
import com.scommit.domain.series.series.repository.SeriesRepository
import com.scommit.domain.subscription.subscription.entity.Subscription
import com.scommit.domain.subscription.subscription.entity.SubscriptionTier
import com.scommit.domain.subscription.subscription.repository.SubscriptionRepository
import com.scommit.domain.user.user.entity.User
import com.scommit.domain.user.user.entity.UserRole
import com.scommit.domain.user.user.repository.UserRepository
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Profile
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.Random

@Profile("dev")
@Component
@Suppress(
    "LongMethod",
    "LongParameterList",
    "CyclomaticComplexMethod",
    "ComplexCondition",
    "NestedBlockDepth",
    "MagicNumber",
    "LoopWithTooManyJumpStatements",
    "MaxLineLength",
)
class BaseInitData(
    private val userRepository: UserRepository,
    private val seriesRepository: SeriesRepository,
    private val postRepository: PostRepository,
    private val commentRepository: CommentRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val passwordEncoder: PasswordEncoder,
) : ApplicationRunner {
    @Transactional
    override fun run(args: ApplicationArguments) {
        if (userRepository.count() > 0 || postRepository.count() > 0 || seriesRepository.count() > 0 ||
            commentRepository.count() > 0 ||
            subscriptionRepository.count() > 0
        ) {
            return
        }

        val pw = passwordEncoder.encode("123456")
        val today = LocalDate.now()

        // Admin (no content)
        userRepository.save(
            User("admin@test.com", pw, "관리자", "사이트 관리자입니다.", UserRole.ADMIN),
        )



        // Test users: user1@test.com ~ user10@test.com (heavy users)
        val testUsers = mutableListOf<User>()
        for (i in 0..9) {
            testUsers.add(
                userRepository.save(
                    User(
                        "user${i + 1}@test.com",
                        pw,
                        TEST_USER_NICKNAMES[i],
                        TEST_USER_INTROS[i],
                        UserRole.USER,
                    ),
                ),
            )
        }

        // General users (49명)
        val generalUsers = mutableListOf<User>()
        for (i in 0..48) {
            generalUsers.add(
                userRepository.save(
                    User(
                        USER_EMAILS[i],
                        pw,
                        USER_NICKNAMES[i],
                        USER_INTROS[i % USER_INTROS.size],
                        UserRole.USER,
                    ),
                ),
            )
        }

        val allUsers = (testUsers + generalUsers).toMutableList()

        // Series for test users (4 each = 40 total)
        val testSeriesList = mutableListOf<Series>()
        for (i in 0..9) {
            val owner = testUsers[i]
            for (j in 0..3) {
                val idx = i * 4 + j
                testSeriesList.add(
                    seriesRepository.save(
                        Series(
                            owner,
                            SERIES_TITLES[idx],
                            SERIES_BODIES[idx % SERIES_BODIES.size],
                        ),
                    ),
                )
            }
        }

        // Series for first 20 general users (1 each = 20 total)
        val generalSeriesList = mutableListOf<Series>()
        for (i in 0..19) {
            generalSeriesList.add(
                seriesRepository.save(
                    Series(
                        generalUsers[i],
                        SERIES_TITLES[40 + i],
                        SERIES_BODIES[(40 + i) % SERIES_BODIES.size],
                    ),
                ),
            )
        }

        // Posts for test users (20 each = 200 total)
        // 앞 16개는 시리즈 소속, 뒤 4개는 standalone
        // PAID는 PUBLIC일 때만 설정 (DRAFT/PRIVATE 포스트는 항상 FREE)
        val allPosts = mutableListOf<Post>()
        for (i in 0..9) {
            val owner = testUsers[i]
            val mySeries = testSeriesList.subList(i * 4, i * 4 + 4)
            for (j in 0..19) {
                val series = if (j < 16) mySeries[j % 4] else null
                val status = randomStatus(true)
                val level = if (status == PublishStatus.PUBLIC) randomLevel(true) else PostAccessLevel.FREE
                allPosts.add(
                    postRepository.save(
                        Post(
                            owner,
                            series,
                            "${POST_TITLES[j % POST_TITLES.size]} ${i + 1}편",
                            POST_BODIES[j % POST_BODIES.size],
                            status,
                            level,
                        ),
                    ),
                )
            }
        }

        // Posts for general users (2-3 each)
        for (i in 0..48) {
            val owner = generalUsers[i]
            val postCount = 2 + RNG.nextInt(2) // 2 or 3
            for (j in 0..<postCount) {
                val series = if (i < 20 && RNG.nextBoolean()) generalSeriesList[i] else null
                val status = randomStatus(false)
                val level = if (status == PublishStatus.PUBLIC) randomLevel(false) else PostAccessLevel.FREE
                allPosts.add(
                    postRepository.save(
                        Post(
                            owner,
                            series,
                            POST_TITLES[(i * 3 + j) % POST_TITLES.size],
                            POST_BODIES[j % POST_BODIES.size],
                            status,
                            level,
                        ),
                    ),
                )
            }
        }

        // Comments: PUBLIC 포스트에만 달림 (DRAFT/PRIVATE는 다른 유저가 볼 수 없음)
        // 헤비유저 PUBLIC 포스트는 3-10개, 일반 PUBLIC 포스트는 0-5개
        val comments = mutableListOf<Comment>()
        for (p in allPosts.indices) {
            val post = allPosts[p]
            if (post.publishStatus != PublishStatus.PUBLIC) continue
            val isHeavyPost = p < 200
            val count = if (isHeavyPost) 3 + RNG.nextInt(8) else RNG.nextInt(6)
            if (count == 0) continue
            val authorId = post.user.id
            val pool = allUsers.toMutableList()
            pool.removeIf { it.id == authorId }
            repeat(count) {
                comments.add(
                    Comment(
                        post,
                        pool[RNG.nextInt(pool.size)],
                        COMMENT_BODIES[RNG.nextInt(COMMENT_BODIES.size)],
                    ),
                )
            }
        }
        commentRepository.saveAll(comments)

        // Subscriptions: general users → test users (2-4 creators each)
        // FOLLOW는 최대 6개월 전부터, MEMBERSHIP은 아직 유효하도록 최근 28일 내 시작
        val subs = mutableListOf<Subscription>()
        for (subscriber in generalUsers) {
            val creators = testUsers.toMutableList()
            val creatorCount = 2 + RNG.nextInt(3)
            repeat(creatorCount) {
                val creator = creators.removeAt(RNG.nextInt(creators.size))
                val isMembership = RNG.nextInt(3) == 0
                val startedAt =
                    if (isMembership) {
                        today.minusDays(RNG.nextInt(28).toLong())
                    } else {
                        today.minusDays(RNG.nextInt(180).toLong())
                    }
                subs.add(
                    Subscription(
                        subscriber,
                        creator,
                        if (isMembership) SubscriptionTier.MEMBERSHIP else SubscriptionTier.FOLLOW,
                        startedAt,
                        if (isMembership) startedAt.plusMonths(1) else null,
                    ),
                )
            }
        }
        subscriptionRepository.saveAll(subs)
    }

    companion object {
        private val RNG = Random(42)
        private val TEST_USER_NICKNAMES =
            arrayOf(
                "코드마법사",
                "알고리즘고수",
                "풀스택개발자",
                "데브옵스전문가",
                "보안전문가",
                "머신러닝엔지니어",
                "모바일개발자",
                "백엔드아키텍트",
                "프론트엔드리드",
                "클라우드전문가",
            )
        private val TEST_USER_INTROS =
            arrayOf(
                "Java와 Spring을 사랑하는 백엔드 개발자입니다. 클린 코드를 추구합니다.",
                "알고리즘과 자료구조를 연구하는 개발자입니다. 매일 PS를 즐깁니다.",
                "프론트부터 백엔드까지 전 영역을 다루는 풀스택 개발자입니다.",
                "CI/CD와 인프라 자동화를 전문으로 하는 DevOps 엔지니어입니다.",
                "보안 취약점 분석과 침투 테스트를 전문으로 합니다.",
                "딥러닝과 자연어처리를 연구하는 AI 엔지니어입니다.",
                "iOS/Android 네이티브 앱 개발을 즐기는 모바일 개발자입니다.",
                "대규모 서비스 아키텍처 설계를 전문으로 하는 백엔드 엔지니어입니다.",
                "React와 TypeScript로 사용자 경험을 만드는 프론트엔드 개발자입니다.",
                "AWS와 GCP를 활용한 클라우드 인프라 전문가입니다.",
            )
        private val USER_EMAILS =
            arrayOf(
                "alice@dev.com",
                "bob@dev.com",
                "charlie@dev.com",
                "diana@dev.com",
                "evan@dev.com",
                "fiona@dev.com",
                "george@dev.com",
                "hannah@dev.com",
                "ivan@dev.com",
                "julia@dev.com",
                "kevin@dev.com",
                "luna@dev.com",
                "mike@dev.com",
                "nina@dev.com",
                "oscar@dev.com",
                "petra@dev.com",
                "quinn@dev.com",
                "rose@dev.com",
                "sam@dev.com",
                "tina@dev.com",
                "uma@dev.com",
                "victor@dev.com",
                "wendy@dev.com",
                "xavier@dev.com",
                "yara@dev.com",
                "zack@dev.com",
                "aaron@coder.com",
                "bella@coder.com",
                "carl@coder.com",
                "dora@coder.com",
                "eli@coder.com",
                "faith@coder.com",
                "greg@coder.com",
                "helen@coder.com",
                "iris@coder.com",
                "jack@coder.com",
                "kate@coder.com",
                "leo@coder.com",
                "mia@coder.com",
                "noah@coder.com",
                "olive@coder.com",
                "paul@coder.com",
                "queen@coder.com",
                "ryan@coder.com",
                "sara@coder.com",
                "tom@coder.com",
                "uva@coder.com",
                "val@coder.com",
                "will@coder.com",
            )
        private val USER_NICKNAMES =
            arrayOf(
                "앨리스개발자",
                "밥코더",
                "찰리엔지니어",
                "다이애나개발",
                "에반테크",
                "피오나코드",
                "조지프로그래머",
                "한나개발자",
                "이반엔지니어",
                "줄리아코더",
                "케빈개발",
                "루나테크",
                "마이크코더",
                "니나엔지니어",
                "오스카개발자",
                "페트라코드",
                "퀸개발자",
                "로즈엔지니어",
                "샘코더",
                "티나개발",
                "우마테크",
                "빅터프로그래머",
                "웬디코더",
                "자비에르개발자",
                "야라엔지니어",
                "잭코드",
                "아론개발자",
                "벨라코더",
                "칼엔지니어",
                "도라개발",
                "일라이테크",
                "페이스코드",
                "그레그프로그래머",
                "헬렌개발자",
                "아이리스코더",
                "잭슨엔지니어",
                "케이트개발",
                "레오테크",
                "미아코더",
                "노아개발자",
                "올리브엔지니어",
                "폴코드",
                "퀸프로그래머",
                "라이언개발자",
                "사라코더",
                "톰엔지니어",
                "우바개발",
                "발코드",
                "윌프로그래머",
            )
        private val USER_INTROS =
            arrayOf(
                "열심히 공부하는 주니어 개발자입니다.",
                "새로운 기술을 배우는 것을 즐깁니다.",
                "코드로 세상을 바꾸고 싶은 개발자입니다.",
                "문제 해결을 좋아하는 개발자입니다.",
                "협업과 소통을 중요시하는 개발자입니다.",
                "꾸준히 성장하는 소프트웨어 엔지니어입니다.",
                "사용자 중심의 서비스를 만들고 싶습니다.",
            )
        private val SERIES_TITLES =
            arrayOf(
                "Java 깊이 파고들기",
                "Spring Boot 실전 가이드",
                "JVM 튜닝과 최적화",
                "디자인 패턴 마스터하기",
                "알고리즘 문제풀이 전략",
                "자료구조 완전 정복",
                "코딩 테스트 합격 가이드",
                "수학으로 보는 알고리즘",
                "React 현대적 개발법",
                "TypeScript 완벽 이해",
                "프론트엔드 성능 최적화",
                "CSS 레이아웃 심화",
                "Kubernetes 운영 실전",
                "Docker 컨테이너 완전 정복",
                "CI/CD 파이프라인 구축",
                "모니터링과 관찰 가능성",
                "웹 보안 취약점 분석",
                "암호화와 인증 기술",
                "OWASP Top 10 대응",
                "침투 테스트 방법론",
                "PyTorch로 배우는 딥러닝",
                "자연어처리 실전 프로젝트",
                "컴퓨터 비전 입문",
                "MLOps 파이프라인",
                "Swift로 만드는 iOS 앱",
                "Kotlin 안드로이드 개발",
                "크로스플랫폼 앱 개발",
                "모바일 UX 설계",
                "마이크로서비스 아키텍처",
                "이벤트 드리븐 시스템 설계",
                "데이터베이스 성능 최적화",
                "분산 시스템 기초",
                "Next.js 풀스택 개발",
                "GraphQL API 설계",
                "웹 접근성 완전 가이드",
                "SEO 최적화 기술",
                "AWS 서비스 완전 정복",
                "테라폼으로 인프라 코딩",
                "서버리스 아키텍처 실전",
                "비용 최적화 전략",
                "개발자 성장 일기",
                "오픈소스 기여 시작하기",
                "Git 브랜치 전략",
                "코드 리뷰 잘하는 법",
                "개발 환경 세팅 가이드",
                "API 설계 원칙",
                "데이터베이스 입문",
                "리팩토링 실전",
                "테스트 주도 개발",
                "클린 코드 작성법",
                "HTTP 완전 이해",
                "Linux 기본기",
                "정규 표현식 마스터",
                "개발자 도구 활용법",
                "빌드 도구 완전 정복",
                "패키지 매니저 비교",
                "개발자 커리어 가이드",
                "기술 면접 준비",
                "사이드 프로젝트 시작하기",
                "오픈소스 라이선스 이해",
            )
        private val SERIES_BODIES =
            arrayOf(
                "실무에서 바로 쓸 수 있는 내용을 중심으로 정리했습니다.",
                "기초부터 차근차근 다루는 시리즈입니다.",
                "개인적으로 공부하며 정리한 내용들을 공유합니다.",
                "매주 업데이트되는 시리즈입니다. 피드백 환영합니다.",
                "심화 내용을 다루므로 기본 지식이 필요합니다.",
            )
        private val POST_TITLES =
            arrayOf(
                "처음 시작하는 법",
                "핵심 개념 정리",
                "실전 예제로 배우기",
                "자주 하는 실수와 해결법",
                "성능 최적화 팁",
                "모범 사례 소개",
                "입문자를 위한 가이드",
                "중급자를 위한 심화 내용",
                "실무에서 배운 것들",
                "튜토리얼 완전판",
                "비교 분석",
                "마이그레이션 가이드",
                "트러블슈팅 경험담",
                "코드 리뷰 사례",
                "아키텍처 결정 이유",
                "도구 선택 기준",
                "리팩토링 사례 분석",
                "테스트 전략 소개",
                "배포 자동화 경험",
                "보안 체크리스트",
            )
        private val POST_BODIES =
            arrayOf(
                "이번 글에서는 핵심 개념을 살펴보고 실제 코드 예제와 함께 알아보겠습니다. " +
                    "처음 접하시는 분들도 이해하기 쉽게 단계별로 설명드리겠습니다. 궁금한 점은 댓글로 질문해 주세요.",
                "실무에서 직접 경험한 내용을 바탕으로 작성했습니다. " +
                    "이론보다는 실제로 써먹을 수 있는 내용 위주로 정리했으니 참고해 주세요. 피드백 언제나 환영합니다.",
                "많은 분들이 헷갈려하는 부분을 중심으로 정리해 보았습니다. " +
                    "저도 처음엔 이 부분에서 많이 헤맸는데 이 글이 도움이 됐으면 합니다. 더 좋은 방법 있으면 알려주세요.",
                "공식 문서와 여러 레퍼런스를 참고하여 정리한 내용입니다. " +
                    "완벽하지 않을 수 있으니 공식 문서도 함께 확인해 보세요. 오류 발견 시 댓글로 알려주시면 수정하겠습니다.",
                "이 글은 시리즈의 일부입니다. 이전 글을 읽고 오시면 더욱 이해가 쉽습니다. " +
                    "예제 코드는 GitHub에서 확인하실 수 있습니다. 다음 편에서는 더 심화된 내용을 다룰 예정입니다.",
            )
        private val COMMENT_BODIES =
            arrayOf(
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
                "오탈자가 있는 것 같습니다. 확인 부탁드립니다.",
            )

        private fun randomStatus(isHeavy: Boolean): PublishStatus {
            val r = RNG.nextInt(10)
            val (publicBound, draftBound) = if (isHeavy) 7 to 9 else 5 to 8
            return when {
                r < publicBound -> PublishStatus.PUBLIC
                r < draftBound -> PublishStatus.DRAFT
                else -> PublishStatus.PRIVATE
            }
        }

        private fun randomLevel(isHeavy: Boolean): PostAccessLevel =
            if (RNG.nextInt(10) < (if (isHeavy) 3 else 1)) PostAccessLevel.PAID else PostAccessLevel.FREE
    }
}
