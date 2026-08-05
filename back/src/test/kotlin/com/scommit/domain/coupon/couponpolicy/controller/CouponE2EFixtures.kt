package com.scommit.domain.coupon.couponpolicy.controller

import com.scommit.domain.user.user.entity.User
import com.scommit.domain.user.user.entity.UserRole
import com.scommit.domain.user.user.repository.UserRepository
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.security.crypto.password.PasswordEncoder
import java.util.UUID

// CouponControllerE2ETest 전용 픽스처. 회원가입 API로는 UserRole.ADMIN을 만들 수 없어서
// (SignupRequest에 role이 없고 UserService.signup이 항상 USER로 고정) 이 데이터만
// 리포지토리로 직접 심는다. 그 외 데이터(쿠폰 이벤트, 발급 내역)는 전부 API로 만든다.
@TestConfiguration
class CouponE2EFixtures {
    data class AdminFixture(
        val email: String,
        val password: String,
    )

    @Bean
    fun adminFixture(
        userRepository: UserRepository,
        passwordEncoder: PasswordEncoder,
    ): AdminFixture {
        val email = "e2e-fixture-admin-${UUID.randomUUID()}@test.com"
        val rawPassword = "fixture-password"
        userRepository.save(
            User(
                email,
                passwordEncoder.encode(rawPassword),
                "e2eFxAdmin${shortId()}",
                null,
                UserRole.ADMIN,
            ),
        )
        return AdminFixture(email, rawPassword)
    }

    companion object {
        private fun shortId(): String =
            UUID
                .randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 6)
    }
}
