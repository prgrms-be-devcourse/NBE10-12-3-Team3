package com.scommit.global.init

import com.cloudinary.Cloudinary
import com.cloudinary.utils.ObjectUtils
import com.scommit.domain.post.comment.repository.CommentRepository
import com.scommit.domain.post.post.repository.PostRepository
import com.scommit.domain.series.series.repository.SeriesRepository
import com.scommit.domain.subscription.subscription.repository.SubscriptionRepository
import com.scommit.domain.user.user.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Profile("prod")
@Component
@Suppress(
    "LongParameterList",
    "MagicNumber",
    "MaxLineLength",
    "ComplexCondition",
)
class ProdInitData(
    private val userRepository: UserRepository,
    private val postRepository: PostRepository,
    private val seriesRepository: SeriesRepository,
    private val commentRepository: CommentRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val cloudinary: Cloudinary,
    private val prodInitDataService: ProdInitDataService,
) : ApplicationRunner {
    // Cloudinary 업로드 1건 실패로 전체 시딩이 죽지 않도록 의도적으로 넓게 잡고 건너뛴다.
    @Suppress("TooGenericExceptionCaught")
    override fun run(args: ApplicationArguments) {
        if (userRepository.count() > 0 || postRepository.count() > 0 || seriesRepository.count() > 0 || commentRepository.count() > 0 ||
            subscriptionRepository.count() > 0
        ) {
            return
        }

        log.info("[ProdInitData] Cloudinary에 이미지 100장 업로드 시작...")
        val thumbnailUrls = mutableListOf<String?>()
        val bodyUrls = mutableListOf<String?>()

        for (i in 0..49) {
            try {
                val thumbResult =
                    cloudinary.uploader().upload(
                        "https://picsum.photos/seed/${i + 1}/1200/630",
                        ObjectUtils.asMap("folder", "posts", "resource_type", "image"),
                    )
                thumbnailUrls.add(thumbResult["secure_url"] as String?)
            } catch (e: Exception) {
                log.warn("[ProdInitData] 썸네일 업로드 실패 (seed {}), 건너뜀", i + 1, e)
                thumbnailUrls.add(null)
            }
            try {
                val bodyResult =
                    cloudinary.uploader().upload(
                        "https://picsum.photos/seed/${i + 51}/800/600",
                        ObjectUtils.asMap("folder", "posts", "resource_type", "image"),
                    )
                bodyUrls.add(bodyResult["secure_url"] as String?)
            } catch (e: Exception) {
                log.warn("[ProdInitData] 본문 이미지 업로드 실패 (seed {}), 건너뜀", i + 51, e)
                bodyUrls.add(null)
            }
        }

        log.info("[ProdInitData] Cloudinary 업로드 완료. DB 데이터 삽입 시작...")
        prodInitDataService.insertAll(thumbnailUrls, bodyUrls)
        log.info("[ProdInitData] 초기 데이터 삽입 완료.")
    }

    companion object {
        private val log = LoggerFactory.getLogger(ProdInitData::class.java)
    }
}
