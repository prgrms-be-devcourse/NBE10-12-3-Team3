package com.scommit.domain.user.user.controller

import com.scommit.domain.subscription.subscription.service.SubscriptionService
import com.scommit.domain.user.user.dto.LoginRequest
import com.scommit.domain.user.user.dto.SignupRequest
import com.scommit.domain.user.user.dto.UserDeleteRequest
import com.scommit.domain.user.user.dto.UserPasswordUpdateRequest
import com.scommit.domain.user.user.dto.UserUpdateRequest
import com.scommit.domain.user.user.entity.User
import com.scommit.domain.user.user.entity.UserRole
import com.scommit.domain.user.user.service.UserService
import com.scommit.domain.user.usermedia.dto.UserMediaResponse
import com.scommit.domain.user.usermedia.service.UserMediaService
import com.scommit.global.exception.BusinessException
import com.scommit.global.exception.ErrorCode
import com.scommit.global.security.JsonUtility
import com.scommit.global.security.SecurityConfig
import com.scommit.global.security.SecurityHelper
import com.scommit.global.security.currentUserWithoutSecurityFilter
import com.scommit.global.security.jwt.JwtFilter
import com.scommit.global.security.jwt.JwtProvider
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.willThrow
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.FilterType
import org.springframework.context.annotation.Import
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.multipart.MultipartFile
import tools.jackson.databind.ObjectMapper
import java.time.LocalDateTime
import com.scommit.domain.media.media.entity.MediaType as MediaFileType

// any() returns null at runtime, which fails Kotlin's non-null check on Kotlin-declared repository params.
private fun <T> anyOfType(): T {
    any<T>()
    @Suppress("UNCHECKED_CAST")
    return null as T
}

@WebMvcTest(
    controllers = [UserController::class],
    excludeFilters = [ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [JwtFilter::class])],
)
@Import(SecurityConfig::class)
@AutoConfigureMockMvc(addFilters = false)
class UserControllerTest {
    @Autowired
    private lateinit var mvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockitoBean
    private lateinit var userService: UserService

    @MockitoBean
    private lateinit var userMediaService: UserMediaService

    @MockitoBean
    private lateinit var subscriptionService: SubscriptionService

    @MockitoBean
    @Suppress("UnusedPrivateProperty")
    private lateinit var jpaMetamodelMappingContext: JpaMetamodelMappingContext

    @MockitoBean
    @Suppress("UnusedPrivateProperty")
    private lateinit var jwtFilter: JwtFilter

    @MockitoBean
    private lateinit var jwtProvider: JwtProvider

    @MockitoBean
    private lateinit var securityHelper: SecurityHelper

    @MockitoBean
    @Suppress("UnusedPrivateProperty")
    private lateinit var jsonUtility: JsonUtility

    @AfterEach
    fun clearSecurityContext() {
        SecurityContextHolder.clearContext()
    }

    @Nested
    @DisplayName("POST /api/users/signup 회원가입")
    inner class Signup {
        private val signupUrl = "/api/users/signup"
        private val validEmail = "test@example.com"
        private val validPassword = "password123"
        private val validNickname = "testuser"

        @Test
        @DisplayName("성공 (201)")
        fun signup_Success() {
            val mockUser =
                mock(User::class.java).apply {
                    given(id).willReturn(1L)
                    given(email).willReturn(validEmail)
                    given(nickname).willReturn(validNickname)
                    given(createdAt).willReturn(LocalDateTime.now())
                }
            given(userService.signUp(anyString(), anyString(), anyString())).willReturn(mockUser)

            val request = SignupRequest(validEmail, validPassword, validNickname)

            mvc
                .perform(
                    post(signupUrl)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)),
                ).andExpect(status().isCreated())
                .andExpect(jsonPath("$.resultCode").value("201-1"))
                .andExpect(jsonPath("$.data.email").value(validEmail))
                .andExpect(jsonPath("$.data.nickname").value(validNickname))
                .andExpect(jsonPath("$.data.id").isNumber())
        }

        @Test
        @DisplayName("이메일 중복 → DUPLICATE_EMAIL (409)")
        fun signup_DuplicateEmail() {
            given(userService.signUp(anyString(), anyString(), anyString()))
                .willThrow(BusinessException(ErrorCode.DUPLICATE_EMAIL))

            val request = SignupRequest(validEmail, validPassword, validNickname)

            mvc
                .perform(
                    post(signupUrl)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)),
                ).andExpect(status().isConflict())
                .andExpect(jsonPath("$.resultCode").value("409-1"))
                .andExpect(jsonPath("$.msg").value("이미 사용중인 이메일입니다."))
        }

        @Test
        @DisplayName("닉네임 중복 → 409")
        fun signup_DuplicateNickname() {
            given(userService.signUp(anyString(), anyString(), anyString()))
                .willThrow(BusinessException(ErrorCode.DUPLICATE_EMAIL))

            val request = SignupRequest(validEmail, validPassword, validNickname)

            mvc
                .perform(
                    post(signupUrl)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)),
                ).andExpect(status().isConflict())
                .andExpect(jsonPath("$.resultCode").value("409-1"))
        }

        @Test
        @DisplayName("이메일 누락 → 400")
        fun signup_BlankEmail() {
            val request = SignupRequest("", validPassword, validNickname)

            mvc
                .perform(
                    post(signupUrl)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)),
                ).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.resultCode").value("400-1"))
        }

        @Test
        @DisplayName("이메일 형식 오류 → 400")
        fun signup_InvalidEmailFormat() {
            val request = SignupRequest("not-an-email", validPassword, validNickname)

            mvc
                .perform(
                    post(signupUrl)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)),
                ).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.resultCode").value("400-1"))
        }

        @Test
        @DisplayName("비밀번호 누락 → 400")
        fun signup_BlankPassword() {
            val request = SignupRequest(validEmail, "", validNickname)

            mvc
                .perform(
                    post(signupUrl)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)),
                ).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.resultCode").value("400-1"))
        }

        @Test
        @DisplayName("비밀번호 6자 미만 → 400")
        fun signup_PasswordTooShort() {
            val request = SignupRequest(validEmail, "12345", validNickname)

            mvc
                .perform(
                    post(signupUrl)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)),
                ).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.resultCode").value("400-1"))
        }

        @Test
        @DisplayName("닉네임 누락 → 400")
        fun signup_BlankNickname() {
            val request = SignupRequest(validEmail, validPassword, "")

            mvc
                .perform(
                    post(signupUrl)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)),
                ).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.resultCode").value("400-1"))
        }

        @Test
        @DisplayName("닉네임 2자 미만 → 400")
        fun signup_NicknameTooShort() {
            val request = SignupRequest(validEmail, validPassword, "a")

            mvc
                .perform(
                    post(signupUrl)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)),
                ).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.resultCode").value("400-1"))
        }

        @Test
        @DisplayName("닉네임 20자 초과 → 400")
        fun signup_NicknameTooLong() {
            val request = SignupRequest(validEmail, validPassword, "a".repeat(21))

            mvc
                .perform(
                    post(signupUrl)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)),
                ).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.resultCode").value("400-1"))
        }
    }

    @Nested
    @DisplayName("POST /api/users/login 로그인")
    inner class Login {
        private val loginUrl = "/api/users/login"
        private val validEmail = "test@example.com"
        private val validPassword = "password123"
        private val nickname = "testuser"
        private val mockAccessToken = "mocked.access.token"
        private val existingRefreshToken = "22222222-2222-2222-2222-222222222222"

        private fun mockUserWithRefreshToken(token: String): User {
            val mockUser = mock(User::class.java)
            given(mockUser.id).willReturn(1L)
            given(mockUser.email).willReturn(validEmail)
            given(mockUser.nickname).willReturn(nickname)
            given(mockUser.role).willReturn(UserRole.USER)
            given(mockUser.refreshToken).willReturn(token)
            return mockUser
        }

        @Test
        @DisplayName("성공 (200) - 로그인 시 유저의 refreshToken을 그대로 응답에 포함한다")
        fun login_Success() {
            val mockUser = mockUserWithRefreshToken(existingRefreshToken)
            given(userService.login(validEmail, validPassword)).willReturn(mockUser)
            given(jwtProvider.generateAccessToken(1L, validEmail, nickname, UserRole.USER))
                .willReturn(mockAccessToken)

            val request = LoginRequest(validEmail, validPassword)

            mvc
                .perform(
                    post(loginUrl)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)),
                ).andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.data.accessToken").value(mockAccessToken))
                .andExpect(jsonPath("$.data.refreshToken").value(existingRefreshToken))
                .andExpect(jsonPath("$.data.expiresIn").isNumber())
                .andExpect(jsonPath("$.data.user.id").value(1))
                .andExpect(jsonPath("$.data.user.email").value(validEmail))
                .andExpect(jsonPath("$.data.user.nickname").value(nickname))
                .andExpect(jsonPath("$.data.user.role").value("USER"))

            verify(securityHelper).setCookie("accessToken", mockAccessToken)
            verify(securityHelper).setCookie("refreshToken", existingRefreshToken)
        }

        @Test
        @DisplayName("실패 - 비밀번호 불일치 → 401")
        fun login_WrongPassword() {
            given(userService.login(validEmail, validPassword))
                .willThrow(BusinessException(ErrorCode.UNAUTHORIZED))

            val request = LoginRequest(validEmail, validPassword)

            mvc
                .perform(
                    post(loginUrl)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)),
                ).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.resultCode").value("401-1"))
                .andExpect(jsonPath("$.msg").value("인증되지 않은 사용자입니다."))
        }

        @Test
        @DisplayName("실패 - 존재하지 않는 이메일 → 401 (비밀번호 불일치와 동일 처리)")
        fun login_EmailNotFound() {
            given(userService.login(validEmail, validPassword))
                .willThrow(BusinessException(ErrorCode.UNAUTHORIZED))

            val request = LoginRequest(validEmail, validPassword)

            mvc
                .perform(
                    post(loginUrl)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)),
                ).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.resultCode").value("401-1"))
        }

        @Test
        @DisplayName("이메일 누락 → 400")
        fun login_BlankEmail() {
            val request = LoginRequest("", validPassword)

            mvc
                .perform(
                    post(loginUrl)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)),
                ).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.resultCode").value("400-1"))
        }

        @Test
        @DisplayName("이메일 형식 오류 → 400")
        fun login_InvalidEmailFormat() {
            val request = LoginRequest("not-an-email", validPassword)

            mvc
                .perform(
                    post(loginUrl)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)),
                ).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.resultCode").value("400-1"))
        }

        @Test
        @DisplayName("비밀번호 누락 → 400")
        fun login_BlankPassword() {
            val request = LoginRequest(validEmail, "")

            mvc
                .perform(
                    post(loginUrl)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)),
                ).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.resultCode").value("400-1"))
        }
    }

    @Nested
    @DisplayName("POST /api/users/logout 로그아웃")
    inner class Logout {
        private val logoutUrl = "/api/users/logout"

        private fun mockActor(): User = User(1L, "test@example.com", "테스터")

        @Test
        @DisplayName("성공 (200) - accessToken, refreshToken 쿠키를 삭제한다")
        fun logout_Success() {
            val actor = mockActor()

            mvc
                .perform(post(logoutUrl).with(currentUserWithoutSecurityFilter(actor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.msg").value("로그아웃에 성공했습니다."))

            verify(securityHelper).deleteCookie("accessToken")
            verify(securityHelper).deleteCookie("refreshToken")
        }
    }

    @Nested
    @DisplayName("DELETE /api/users 회원탈퇴")
    inner class Withdraw {
        private val withdrawUrl = "/api/users"

        private fun mockActor(): User = User(1L, "test@example.com", "테스터")

        @Test
        @DisplayName("성공 (200) - 계정을 삭제하고 accessToken, refreshToken 쿠키를 제거한다")
        fun withdraw_Success() {
            val actor = mockActor()

            val request = UserDeleteRequest("password123")

            mvc
                .perform(
                    delete(withdrawUrl)
                        .with(currentUserWithoutSecurityFilter(actor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)),
                ).andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.msg").value("회원탈퇴에 성공했습니다."))

            verify(userService).deleteUser(1L, "password123")
            verify(securityHelper).deleteCookie("accessToken")
            verify(securityHelper).deleteCookie("refreshToken")
        }

        @Test
        @DisplayName("비밀번호 누락 → 400")
        fun withdraw_BlankPassword() {
            val request = UserDeleteRequest("")

            mvc
                .perform(
                    delete(withdrawUrl)
                        .with(currentUserWithoutSecurityFilter(mockActor()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)),
                ).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.resultCode").value("400-1"))
        }

        @Test
        @DisplayName("실패 - 비밀번호 불일치 → 401, 쿠키는 삭제하지 않는다")
        fun withdraw_WrongPassword() {
            val actor = mockActor()
            willThrow(BusinessException(ErrorCode.UNAUTHORIZED))
                .given(userService)
                .deleteUser(1L, "wrongpassword")

            val request = UserDeleteRequest("wrongpassword")

            mvc
                .perform(
                    delete(withdrawUrl)
                        .with(currentUserWithoutSecurityFilter(actor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)),
                ).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.resultCode").value("401-1"))

            verify(securityHelper, never()).deleteCookie(anyString())
        }
    }

    @Nested
    @DisplayName("GET /api/users/me 내 정보 조회")
    inner class GetMe {
        private val meUrl = "/api/users/me"
        private val email = "test@example.com"
        private val nickname = "testuser"
        private val introduction = "안녕하세요"

        private fun mockActor(): User {
            val mockUser = mock(User::class.java)
            given(mockUser.id).willReturn(1L)
            given(mockUser.email).willReturn(email)
            given(mockUser.nickname).willReturn(nickname)
            given(mockUser.introduction).willReturn(introduction)
            given(mockUser.createdAt).willReturn(LocalDateTime.of(2026, 1, 1, 0, 0))
            given(mockUser.updatedAt).willReturn(LocalDateTime.of(2026, 1, 2, 0, 0))
            return mockUser
        }

        @Test
        @DisplayName("성공 (200) - 로그인한 유저 자신의 정보를 반환한다")
        fun getMe_Success() {
            val actor = mockActor()
            given(userService.getUser(1L)).willReturn(actor)
            given(userMediaService.getMedia(1L)).willReturn(
                UserMediaResponse(1L, 1L, null, MediaFileType.IMAGE),
            )

            mvc
                .perform(get(meUrl).with(currentUserWithoutSecurityFilter(actor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(jsonPath("$.data.profile.nickname").value(nickname))
                .andExpect(jsonPath("$.data.profile.introduction").value(introduction))
                .andExpect(jsonPath("$.data.createdAt").exists())
                .andExpect(jsonPath("$.data.updatedAt").exists())
        }
    }

    @Nested
    @DisplayName("PATCH /api/users/me 내 정보 수정")
    inner class UpdateMe {
        private val meUrl = "/api/users/me"
        private val email = "test@example.com"
        private val newNickname = "newnickname"
        private val newIntroduction = "수정된 소개글입니다."

        private fun mockActor(): User = User(1L, email, "테스터")

        private fun requestPart(request: UserUpdateRequest): MockMultipartFile =
            MockMultipartFile(
                "request",
                "",
                MediaType.APPLICATION_JSON_VALUE,
                objectMapper.writeValueAsBytes(request),
            )

        @Test
        @DisplayName("성공 (200) - 닉네임, 소개글을 수정한다")
        fun updateMe_Success() {
            val actor = mockActor()

            val updatedUser = mock(User::class.java)
            given(updatedUser.id).willReturn(1L)
            given(updatedUser.email).willReturn(email)
            given(updatedUser.nickname).willReturn(newNickname)
            given(updatedUser.introduction).willReturn(newIntroduction)
            given(updatedUser.createdAt).willReturn(LocalDateTime.now())
            given(userService.updateUser(1L, newNickname, newIntroduction)).willReturn(updatedUser)

            val request = UserUpdateRequest(newNickname, newIntroduction)

            mvc
                .perform(
                    multipart(HttpMethod.PATCH, meUrl)
                        .file(requestPart(request))
                        .with(currentUserWithoutSecurityFilter(actor))
                        .with(csrf()),
                ).andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.data.profile.nickname").value(newNickname))
                .andExpect(jsonPath("$.data.profile.introduction").value(newIntroduction))
        }

        @Test
        @DisplayName("성공 (200) - 새 이미지를 첨부하지 않으면 기존 프로필 이미지 URL을 그대로 응답한다")
        fun updateMe_KeepsExistingProfileImageWhenNoneUploaded() {
            val actor = mockActor()

            val updatedUser = mock(User::class.java)
            given(updatedUser.id).willReturn(1L)
            given(updatedUser.email).willReturn(email)
            given(updatedUser.nickname).willReturn(newNickname)
            given(updatedUser.introduction).willReturn(newIntroduction)
            given(updatedUser.createdAt).willReturn(LocalDateTime.now())
            given(userService.updateUser(1L, newNickname, newIntroduction)).willReturn(updatedUser)
            given(userMediaService.getMedia(1L)).willReturn(
                UserMediaResponse(1L, 1L, "user/uuid.png", MediaFileType.IMAGE),
            )

            val request = UserUpdateRequest(newNickname, newIntroduction)

            mvc
                .perform(
                    multipart(HttpMethod.PATCH, meUrl)
                        .file(requestPart(request))
                        .with(currentUserWithoutSecurityFilter(actor))
                        .with(csrf()),
                ).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profile.profileImageUrl").value("user/uuid.png"))
        }

        @Test
        @DisplayName("닉네임 2자 미만 → 400")
        fun updateMe_NicknameTooShort() {
            val request = UserUpdateRequest("a", newIntroduction)

            mvc
                .perform(
                    multipart(HttpMethod.PATCH, meUrl)
                        .file(requestPart(request))
                        .with(currentUserWithoutSecurityFilter(mockActor()))
                        .with(csrf()),
                ).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.resultCode").value("400-1"))
        }

        @Test
        @DisplayName("닉네임 20자 초과 → 400")
        fun updateMe_NicknameTooLong() {
            val request = UserUpdateRequest("a".repeat(21), newIntroduction)

            mvc
                .perform(
                    multipart(HttpMethod.PATCH, meUrl)
                        .file(requestPart(request))
                        .with(currentUserWithoutSecurityFilter(mockActor()))
                        .with(csrf()),
                ).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.resultCode").value("400-1"))
        }

        @Test
        @DisplayName("닉네임이 공백으로만 이루어짐 → 400")
        fun updateMe_NicknameBlank() {
            val request = UserUpdateRequest("  ", newIntroduction)

            mvc
                .perform(
                    multipart(HttpMethod.PATCH, meUrl)
                        .file(requestPart(request))
                        .with(currentUserWithoutSecurityFilter(mockActor()))
                        .with(csrf()),
                ).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.resultCode").value("400-1"))
        }

        @Test
        @DisplayName("소개글 100자 초과 → 400")
        fun updateMe_IntroductionTooLong() {
            val request = UserUpdateRequest(newNickname, "a".repeat(101))

            mvc
                .perform(
                    multipart(HttpMethod.PATCH, meUrl)
                        .file(requestPart(request))
                        .with(currentUserWithoutSecurityFilter(mockActor()))
                        .with(csrf()),
                ).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.resultCode").value("400-1"))
        }
    }

    @Nested
    @DisplayName("PUT /api/users/me/password 비밀번호 변경")
    inner class UpdatePassword {
        private val passwordUrl = "/api/users/me/password"
        private val email = "test@example.com"
        private val nickname = "testuser"
        private val currentPassword = "password123"
        private val newPassword = "newpassword456"
        private val mockAccessToken = "mocked.access.token"
        private val refreshToken = "33333333-3333-3333-3333-333333333333"

        private fun mockActor(): User {
            val mockUser = mock(User::class.java)
            given(mockUser.id).willReturn(1L)
            given(mockUser.email).willReturn(email)
            given(mockUser.nickname).willReturn(nickname)
            given(mockUser.role).willReturn(UserRole.USER)
            given(mockUser.refreshToken).willReturn(refreshToken)
            return mockUser
        }

        @Test
        @DisplayName("성공 (200) - 비밀번호 변경 후 새 토큰을 발급한다")
        fun updatePassword_Success() {
            val actor = mockActor()
            given(userService.getUser(1L)).willReturn(actor)
            given(jwtProvider.generateAccessToken(1L, email, nickname, UserRole.USER))
                .willReturn(mockAccessToken)

            val request = UserPasswordUpdateRequest(currentPassword, newPassword)

            mvc
                .perform(
                    put(passwordUrl)
                        .with(currentUserWithoutSecurityFilter(actor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)),
                ).andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.data.accessToken").value(mockAccessToken))
                .andExpect(jsonPath("$.data.refreshToken").value(refreshToken))
                .andExpect(jsonPath("$.data.expiresIn").isNumber())
        }

        @Test
        @DisplayName("현재 비밀번호 누락 → 400")
        fun updatePassword_BlankCurrentPassword() {
            val request = UserPasswordUpdateRequest("", newPassword)

            mvc
                .perform(
                    put(passwordUrl)
                        .with(currentUserWithoutSecurityFilter(mockActor()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)),
                ).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.resultCode").value("400-1"))
        }

        @Test
        @DisplayName("새 비밀번호 누락 → 400")
        fun updatePassword_BlankNewPassword() {
            val request = UserPasswordUpdateRequest(currentPassword, "")

            mvc
                .perform(
                    put(passwordUrl)
                        .with(currentUserWithoutSecurityFilter(mockActor()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)),
                ).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.resultCode").value("400-1"))
        }

        @Test
        @DisplayName("새 비밀번호 6자 미만 → 400")
        fun updatePassword_NewPasswordTooShort() {
            val request = UserPasswordUpdateRequest(currentPassword, "12345")

            mvc
                .perform(
                    put(passwordUrl)
                        .with(currentUserWithoutSecurityFilter(mockActor()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)),
                ).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.resultCode").value("400-1"))
        }
    }

    @Nested
    @DisplayName("GET /api/users/{id} 유저 페이지 조회")
    inner class GetUserProfile {
        private val nickname = "testuser"
        private val introduction = "안녕하세요"

        private fun mockTargetUser(): User {
            val mockUser = mock(User::class.java)
            given(mockUser.id).willReturn(1L)
            given(mockUser.nickname).willReturn(nickname)
            given(mockUser.introduction).willReturn(introduction)
            return mockUser
        }

        @Test
        @DisplayName("성공 (200) - 프로필 이미지가 있는 경우")
        fun getUserProfile_Success() {
            val targetUser = mockTargetUser()
            given(userService.getUser(1L)).willReturn(targetUser)
            given(userMediaService.getMedia(1L)).willReturn(
                UserMediaResponse(1L, 1L, "user/uuid.png", MediaFileType.IMAGE),
            )
            given(subscriptionService.getFollowerCount(1L)).willReturn(5L)

            mvc
                .perform(get("/api/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.followerCount").value(5))
                .andExpect(jsonPath("$.data.profile.nickname").value(nickname))
                .andExpect(jsonPath("$.data.profile.introduction").value(introduction))
                .andExpect(jsonPath("$.data.profile.profileImageUrl").value("user/uuid.png"))
        }

        @Test
        @DisplayName("성공 (200) - 프로필 이미지가 없는 경우")
        fun getUserProfile_NoMedia() {
            val targetUser = mockTargetUser()
            given(userService.getUser(1L)).willReturn(targetUser)
            given(userMediaService.getMedia(1L)).willReturn(null)
            given(subscriptionService.getFollowerCount(1L)).willReturn(0L)

            mvc
                .perform(get("/api/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.data.followerCount").value(0))
                .andExpect(jsonPath("$.data.profile.profileImageUrl").doesNotExist())
        }

        @Test
        @DisplayName("성공 (200) - 팔로워가 없는 유저를 조회하면 followerCount가 0으로 응답된다")
        fun getUserProfile_ZeroFollowers() {
            val targetUser = mockTargetUser()
            given(userService.getUser(1L)).willReturn(targetUser)
            given(userMediaService.getMedia(1L)).willReturn(null)
            given(subscriptionService.getFollowerCount(1L)).willReturn(0L)

            mvc
                .perform(get("/api/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.followerCount").value(0))
        }

        @Test
        @DisplayName("존재하지 않는 유저 → 404 (팔로워 수는 조회하지 않는다)")
        fun getUserProfile_UserNotFound() {
            given(userService.getUser(999L)).willReturn(null)

            mvc
                .perform(get("/api/users/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.resultCode").value("404-2"))

            verify(subscriptionService, never()).getFollowerCount(anyLong())
        }
    }

    @Nested
    @DisplayName("GET /api/users/{id}/medias 프로필 이미지 조회")
    inner class GetMedia {
        @Test
        @DisplayName("성공 (200)")
        fun getMedia_Success() {
            val response = UserMediaResponse(1L, 1L, "user/uuid.png", MediaFileType.IMAGE)
            given(userMediaService.getMedia(1L)).willReturn(response)

            mvc
                .perform(get("/api/users/1/medias"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.url").value("user/uuid.png"))
                .andExpect(jsonPath("$.data.userId").value(1L))
        }

        @Test
        @DisplayName("유저 없음 → 404")
        fun getMedia_UserNotFound() {
            given(userMediaService.getMedia(999L))
                .willThrow(BusinessException(ErrorCode.USER_NOT_FOUND))

            mvc
                .perform(get("/api/users/999/medias"))
                .andExpect(status().isNotFound())
        }

        @Test
        @DisplayName("미디어 없음 → 200 (data: null)")
        fun getMedia_MediaNotFound() {
            given(userMediaService.getMedia(1L)).willReturn(null)

            mvc
                .perform(get("/api/users/1/medias"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist())
        }
    }

    @Nested
    @DisplayName("POST /api/users/me/medias 프로필 이미지 업로드")
    inner class UploadMedia {
        @Test
        @DisplayName("성공 (201)")
        fun uploadMedia_Success() {
            val actor = User(1L, "test@example.com", "테스터")

            val response = UserMediaResponse(1L, 1L, "user/uuid.png", MediaFileType.IMAGE)
            val file = MockMultipartFile("file", "profile.png", "image/png", "content".toByteArray())
            given(userMediaService.uploadMedia(anyLong(), anyOfType<MultipartFile>())).willReturn(response)

            mvc
                .perform(
                    multipart("/api/users/me/medias")
                        .file(file)
                        .with(currentUserWithoutSecurityFilter(actor))
                        .with(csrf()),
                ).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.url").value("user/uuid.png"))
        }
    }

    @Nested
    @DisplayName("DELETE /api/users/me/medias 프로필 이미지 삭제")
    inner class DeleteMedia {
        private fun mockActor(): User = User(1L, "test@example.com", "테스터")

        @Test
        @DisplayName("성공 (200)")
        fun deleteMedia_Success() {
            val actor = mockActor()

            mvc
                .perform(
                    delete("/api/users/me/medias")
                        .with(currentUserWithoutSecurityFilter(actor))
                        .with(csrf()),
                ).andExpect(status().isOk())
        }

        @Test
        @DisplayName("미디어 없음 → 404")
        fun deleteMedia_MediaNotFound() {
            val actor = mockActor()
            doThrow(BusinessException(ErrorCode.RESOURCE_NOT_FOUND))
                .`when`(userMediaService)
                .deleteMedia(anyLong())

            mvc
                .perform(
                    delete("/api/users/me/medias")
                        .with(currentUserWithoutSecurityFilter(actor))
                        .with(csrf()),
                ).andExpect(status().isNotFound())
        }
    }
}
