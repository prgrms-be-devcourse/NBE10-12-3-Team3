    package com.scommit.domain.user.user.service

import com.scommit.domain.user.user.dto.UserSearchResponse
import com.scommit.domain.user.user.entity.User
import com.scommit.domain.user.user.entity.UserRole
import com.scommit.domain.user.user.repository.UserRepository
import com.scommit.global.exception.BusinessException
import com.scommit.global.exception.ErrorCode
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.BDDMockito.given
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.util.ReflectionTestUtils
import java.util.Optional

// any() returns null at runtime, which fails Kotlin's non-null check on Kotlin-declared repository params.
private fun <T> anyOfType(): T {
    any<T>()
    @Suppress("UNCHECKED_CAST")
    return null as T
}

@ExtendWith(MockitoExtension::class)
class UserServiceTest {
    @Mock
    private lateinit var userRepository: UserRepository

    @Mock
    private lateinit var passwordEncoder: PasswordEncoder

    @InjectMocks
    private lateinit var userService: UserService

    @Nested
    @DisplayName("회원가입")
    inner class SignUp {
        private val email = "test@example.com"
        private val password = "password123"
        private val nickname = "testuser"

        @Test
        @DisplayName("성공: 이메일과 닉네임이 중복되지 않으면 유저를 저장하고 반환한다.")
        fun signUp_Success() {
            // Given
            given(userRepository.existsByEmail(email)).willReturn(false)
            given(userRepository.existsByNickname(nickname)).willReturn(false)
            given(passwordEncoder.encode(password)).willReturn("encodedPassword")

            val savedUser = User(email = email, password = "encodedPassword", nickname = nickname, role = UserRole.USER)
            given(userRepository.save(any(User::class.java))).willReturn(savedUser)

            // When
            val result = userService.signUp(email, password, nickname)

            // Then
            assertThat(result.email).isEqualTo(email)
            assertThat(result.nickname).isEqualTo(nickname)
            assertThat(result.password).isEqualTo("encodedPassword")
            verify(passwordEncoder).encode(password)
            verify(userRepository).save(any(User::class.java))
        }

        @Test
        @DisplayName("성공: 저장 시 평문 비밀번호가 아닌 인코딩된 비밀번호가 사용된다.")
        fun signUp_PasswordIsEncoded() {
            // Given
            given(userRepository.existsByEmail(email)).willReturn(false)
            given(userRepository.existsByNickname(nickname)).willReturn(false)
            given(passwordEncoder.encode(password)).willReturn("encodedPassword")
            given(userRepository.save(any(User::class.java))).willAnswer { it.getArgument<User>(0) }

            // When
            val result = userService.signUp(email, password, nickname)

            // Then
            assertThat(result.password).isNotEqualTo(password)
            assertThat(result.password).isEqualTo("encodedPassword")
        }

        @Test
        @DisplayName("성공: 저장 전 refreshToken을 새로 발급한다.")
        fun signUp_IssuesRefreshToken() {
            // Given
            given(userRepository.existsByEmail(email)).willReturn(false)
            given(userRepository.existsByNickname(nickname)).willReturn(false)
            given(passwordEncoder.encode(password)).willReturn("encodedPassword")
            given(userRepository.save(any(User::class.java))).willAnswer { it.getArgument<User>(0) }

            // When
            val result = userService.signUp(email, password, nickname)

            // Then
            assertThat(result.refreshToken).isNotNull()
        }

        @Test
        @DisplayName("실패: 이메일이 중복되면 DUPLICATE_EMAIL 예외를 던진다.")
        fun signUp_DuplicateEmail() {
            // Given
            given(userRepository.existsByEmail(email)).willReturn(true)

            // When & Then
            assertThatThrownBy { userService.signUp(email, password, nickname) }
                .isInstanceOf(BusinessException::class.java)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DUPLICATE_EMAIL)

            verify(userRepository, never()).save(any(User::class.java))
        }

        @Test
        @DisplayName("실패: 닉네임이 중복되면 예외를 던지고 저장하지 않는다.")
        fun signUp_DuplicateNickname() {
            // Given
            given(userRepository.existsByEmail(email)).willReturn(false)
            given(userRepository.existsByNickname(nickname)).willReturn(true)

            // When & Then
            assertThatThrownBy { userService.signUp(email, password, nickname) }
                .isInstanceOf(BusinessException::class.java)

            verify(userRepository, never()).save(any(User::class.java))
            verify(passwordEncoder, never()).encode(anyString())
        }
    }

    @Nested
    @DisplayName("로그인")
    inner class Login {
        private val email = "test@example.com"
        private val password = "password123"
        private val wrongPassword = "wrongPassword"
        private val encodedPassword = "encodedPassword"
        private val nickname = "testuser"

        private fun buildUser(): User =
            User(email = email, password = encodedPassword, nickname = nickname, role = UserRole.USER)

        @Test
        @DisplayName("성공: 이메일과 비밀번호가 일치하면 유저를 반환한다.")
        fun login_Success() {
            // Given
            val user = buildUser()
            given(userRepository.findByEmailAndDeletedAtIsNull(email)).willReturn(user)
            given(passwordEncoder.matches(password, encodedPassword)).willReturn(true)

            // When
            val result = userService.login(email, password)

            // Then
            assertThat(result.email).isEqualTo(email)
        }

        @Test
        @DisplayName("실패: 존재하지 않는 이메일이면 INVALID_CREDENTIALS 예외를 던진다.")
        fun login_EmailNotFound() {
            // Given
            given(userRepository.findByEmailAndDeletedAtIsNull(email)).willReturn(null)

            // When & Then
            assertThatThrownBy { userService.login(email, password) }
                .isInstanceOf(BusinessException::class.java)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_CREDENTIALS)
        }

        @Test
        @DisplayName("실패: 비밀번호가 일치하지 않으면 INVALID_CREDENTIALS 예외를 던진다.")
        fun login_WrongPassword() {
            // Given
            val user = buildUser()
            given(userRepository.findByEmailAndDeletedAtIsNull(email)).willReturn(user)
            given(passwordEncoder.matches(wrongPassword, encodedPassword)).willReturn(false)

            // When & Then
            assertThatThrownBy { userService.login(email, wrongPassword) }
                .isInstanceOf(BusinessException::class.java)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_CREDENTIALS)
        }
    }

    @Nested
    @DisplayName("내 정보 수정")
    inner class UpdateUser {
        private val userId = 1L
        private val nickname = "testuser"
        private val newNickname = "newnickname"
        private val newIntroduction = "수정된 소개글입니다."

        private fun buildUser(): User =
            User(email = "test@example.com", password = "encodedPassword", nickname = nickname, role = UserRole.USER)

        @Test
        @DisplayName("성공: 닉네임과 소개글을 수정한다.")
        fun updateUser_Success() {
            // Given
            val user = buildUser()
            given(userRepository.findByIdAndDeletedAtIsNull(userId)).willReturn(user)
            given(userRepository.existsByNickname(newNickname)).willReturn(false)

            // When
            val result = userService.updateUser(userId, newNickname, newIntroduction)

            // Then
            assertThat(result.nickname).isEqualTo(newNickname)
            assertThat(result.introduction).isEqualTo(newIntroduction)
        }

        @Test
        @DisplayName("성공: 기존과 동일한 닉네임을 다시 보내도 중복 예외 없이 수정된다.")
        fun updateUser_SameNicknameAsCurrent() {
            // Given
            val user = buildUser()
            given(userRepository.findByIdAndDeletedAtIsNull(userId)).willReturn(user)

            // When
            val result = userService.updateUser(userId, nickname, newIntroduction)

            // Then
            assertThat(result.nickname).isEqualTo(nickname)
            assertThat(result.introduction).isEqualTo(newIntroduction)
            verify(userRepository, never()).existsByNickname(anyOfType())
        }

        @Test
        @DisplayName("성공: 닉네임 없이 소개글만 수정한다.")
        fun updateUser_NicknameNull() {
            // Given
            val user = buildUser()
            given(userRepository.findByIdAndDeletedAtIsNull(userId)).willReturn(user)

            // When
            val result = userService.updateUser(userId, null, newIntroduction)

            // Then
            assertThat(result.nickname).isEqualTo(nickname)
            assertThat(result.introduction).isEqualTo(newIntroduction)
            verify(userRepository, never()).existsByNickname(anyOfType())
        }

        @Test
        @DisplayName("실패: 닉네임이 중복되면 예외를 던지고 수정하지 않는다.")
        fun updateUser_DuplicateNickname() {
            // Given
            val user = buildUser()
            given(userRepository.findByIdAndDeletedAtIsNull(userId)).willReturn(user)
            given(userRepository.existsByNickname(newNickname)).willReturn(true)

            // When & Then
            assertThatThrownBy { userService.updateUser(userId, newNickname, newIntroduction) }
                .isInstanceOf(BusinessException::class.java)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DUPLICATE_NICKNAME)

            assertThat(user.nickname).isEqualTo(nickname)
        }

        @Test
        @DisplayName("실패: 존재하지 않는 유저면 USER_NOT_FOUND 예외를 던진다.")
        fun updateUser_UserNotFound() {
            // Given
            given(userRepository.findByIdAndDeletedAtIsNull(userId)).willReturn(null)

            // When & Then
            assertThatThrownBy { userService.updateUser(userId, newNickname, newIntroduction) }
                .isInstanceOf(BusinessException::class.java)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND)

            verify(userRepository, never()).existsByNickname(anyString())
        }
    }

    @Nested
    @DisplayName("비밀번호 변경")
    inner class UpdatePassword {
        private val userId = 1L
        private val currentPassword = "password123"
        private val wrongPassword = "wrongPassword"
        private val newPassword = "newPassword456"
        private val encodedCurrentPassword = "encodedCurrentPassword"
        private val encodedNewPassword = "encodedNewPassword"

        private fun buildUser(): User =
            User(
                email = "test@example.com",
                password = encodedCurrentPassword,
                nickname = "testuser",
                role = UserRole.USER,
            )

        @Test
        @DisplayName("성공: 비밀번호를 변경하고 refreshToken을 재발급한다.")
        fun updatePassword_Success() {
            // Given
            val user = buildUser()
            given(userRepository.findByIdAndDeletedAtIsNull(userId)).willReturn(user)
            given(passwordEncoder.matches(currentPassword, encodedCurrentPassword)).willReturn(true)
            given(passwordEncoder.encode(newPassword)).willReturn(encodedNewPassword)

            // When
            userService.updatePassword(userId, currentPassword, newPassword)

            // Then
            assertThat(user.password).isEqualTo(encodedNewPassword)
            assertThat(user.refreshToken).isNotNull()
        }

        @Test
        @DisplayName("실패: 현재 비밀번호가 일치하지 않으면 INVALID_PASSWORD 예외를 던지고 변경하지 않는다.")
        fun updatePassword_WrongCurrentPassword() {
            // Given
            val user = buildUser()
            given(userRepository.findByIdAndDeletedAtIsNull(userId)).willReturn(user)
            given(passwordEncoder.matches(wrongPassword, encodedCurrentPassword)).willReturn(false)

            // When & Then
            assertThatThrownBy { userService.updatePassword(userId, wrongPassword, newPassword) }
                .isInstanceOf(BusinessException::class.java)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_PASSWORD)

            assertThat(user.password).isEqualTo(encodedCurrentPassword)
            verify(passwordEncoder, never()).encode(anyString())
        }

        @Test
        @DisplayName("실패: 존재하지 않는 유저면 USER_NOT_FOUND 예외를 던진다.")
        fun updatePassword_UserNotFound() {
            // Given
            given(userRepository.findByIdAndDeletedAtIsNull(userId)).willReturn(null)

            // When & Then
            assertThatThrownBy { userService.updatePassword(userId, currentPassword, newPassword) }
                .isInstanceOf(BusinessException::class.java)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND)
        }
    }

    @Nested
    @DisplayName("회원탈퇴")
    inner class DeleteUser {
        private val userId = 1L
        private val password = "password123"
        private val wrongPassword = "wrongPassword"
        private val encodedPassword = "encodedPassword"

        private fun buildUser(): User =
            User(email = "test@example.com", password = encodedPassword, nickname = "testuser", role = UserRole.USER)

        @Test
        @DisplayName("성공: 비밀번호가 일치하면 soft delete하고 refreshToken을 재발급한다.")
        fun deleteUser_Success() {
            // Given
            val user = buildUser()
            given(userRepository.findById(userId)).willReturn(Optional.of(user))
            given(passwordEncoder.matches(password, encodedPassword)).willReturn(true)

            // When
            userService.deleteUser(userId, password)

            // Then
            assertThat(user.deletedAt).isNotNull()
            assertThat(user.refreshToken).isNotNull()
        }

        @Test
        @DisplayName("실패: 비밀번호가 일치하지 않으면 INVALID_PASSWORD 예외를 던지고 탈퇴하지 않는다.")
        fun deleteUser_WrongPassword() {
            // Given
            val user = buildUser()
            given(userRepository.findById(userId)).willReturn(Optional.of(user))
            given(passwordEncoder.matches(wrongPassword, encodedPassword)).willReturn(false)

            // When & Then
            assertThatThrownBy { userService.deleteUser(userId, wrongPassword) }
                .isInstanceOf(BusinessException::class.java)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_PASSWORD)

            assertThat(user.deletedAt).isNull()
        }

        @Test
        @DisplayName("실패: 존재하지 않는 유저면 USER_NOT_FOUND 예외를 던진다.")
        fun deleteUser_UserNotFound() {
            // Given
            given(userRepository.findById(userId)).willReturn(Optional.empty())

            // When & Then
            assertThatThrownBy { userService.deleteUser(userId, password) }
                .isInstanceOf(BusinessException::class.java)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND)
        }
    }

    @Nested
    @DisplayName("로그아웃")
    inner class Logout {
        private val userId = 1L

        private fun buildUser(): User =
            User(
                email = "test@example.com",
                password = "encodedPassword",
                nickname = "testuser",
                role = UserRole.USER,
            ).apply { resetRefreshToken() }

        @Test
        @DisplayName("성공: refreshToken을 재발급한다.")
        fun logout_Success() {
            // Given
            val user = buildUser()
            val oldRefreshToken = user.refreshToken
            given(userRepository.findById(userId)).willReturn(Optional.of(user))

            // When
            userService.logout(userId)

            // Then
            assertThat(user.refreshToken)
                .isNotNull()
                .isNotEqualTo(oldRefreshToken)
        }

        @Test
        @DisplayName("실패: 존재하지 않는 유저면 USER_NOT_FOUND 예외를 던진다.")
        fun logout_UserNotFound() {
            // Given
            given(userRepository.findById(userId)).willReturn(Optional.empty())

            // When & Then
            assertThatThrownBy { userService.logout(userId) }
                .isInstanceOf(BusinessException::class.java)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND)
        }
    }

    @Nested
    @DisplayName("유저 검색 테스트")
    inner class SearchUsers {
        @Test
        @DisplayName("성공: 닉네임 키워드로 유저 목록을 반환한다.")
        fun searchUsers_Success() {
            val pageable: Pageable = PageRequest.of(0, 10)
            val user =
                User(email = "a@a.com", nickname = "발코드", role = UserRole.USER)
                    .also { ReflectionTestUtils.setField(it, "id", 1L) }
            val page: Page<User> = PageImpl(listOf(user), pageable, 1)

            given(userRepository.findByNicknameContainingAndDeletedAtIsNull("발코드", pageable)).willReturn(page)

            val result: Page<UserSearchResponse> = userService.searchUsers("발코드", pageable)

            assertThat(result.totalElements).isEqualTo(1)
            assertThat(result.content[0].nickname).isEqualTo("발코드")
        }

        @Test
        @DisplayName("성공: 검색 결과가 없으면 빈 페이지를 반환한다.")
        fun searchUsers_Empty() {
            val pageable: Pageable = PageRequest.of(0, 10)
            val emptyPage: Page<User> = PageImpl(emptyList(), pageable, 0)

            given(userRepository.findByNicknameContainingAndDeletedAtIsNull("없는닉네임", pageable)).willReturn(emptyPage)

            val result: Page<UserSearchResponse> = userService.searchUsers("없는닉네임", pageable)

            assertThat(result.totalElements).isEqualTo(0)
            assertThat(result.content).isEmpty()
        }
    }
}
