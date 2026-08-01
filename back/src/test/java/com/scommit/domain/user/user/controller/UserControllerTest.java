package com.scommit.domain.user.user.controller;

import tools.jackson.databind.ObjectMapper;
import com.scommit.domain.user.user.dto.LoginRequest;
import com.scommit.domain.user.user.dto.SignupRequest;
import com.scommit.domain.user.user.dto.UserDeleteRequest;
import com.scommit.domain.user.user.dto.UserPasswordUpdateRequest;
import com.scommit.domain.user.user.dto.UserUpdateRequest;
import com.scommit.domain.subscription.subscription.service.SubscriptionService;
import com.scommit.domain.user.user.entity.User;
import com.scommit.domain.user.user.entity.UserRole;
import com.scommit.domain.user.user.service.UserService;
import com.scommit.domain.user.usermedia.dto.UserMediaResponse;
import com.scommit.domain.user.usermedia.service.UserMediaService;
import com.scommit.global.exception.BusinessException;
import com.scommit.global.exception.ErrorCode;
import com.scommit.global.security.SecurityConfig;
import com.scommit.global.security.SecurityHelper;
import com.scommit.global.security.jwt.JwtFilter;
import com.scommit.global.security.jwt.JwtProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = UserController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtFilter.class)
)
@Import(SecurityConfig.class)
@AutoConfigureMockMvc(addFilters = false)
public class UserControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private UserMediaService userMediaService;

    @MockitoBean
    private SubscriptionService subscriptionService;

    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @MockitoBean
    private JwtFilter jwtFilter;

    @MockitoBean
    private JwtProvider jwtProvider;

    @MockitoBean
    private SecurityHelper securityHelper;

    @Nested
    @DisplayName("POST /api/users/signup 회원가입")
    class Signup {

        private static final String SIGNUP_URL = "/api/users/signup";
        private static final String VALID_EMAIL = "test@example.com";
        private static final String VALID_PASSWORD = "password123";
        private static final String VALID_NICKNAME = "testuser";

        @Test
        @DisplayName("성공 (201)")
        void signup_Success() throws Exception {
            User mockUser = mock(User.class);
            given(mockUser.getId()).willReturn(1L);
            given(mockUser.getEmail()).willReturn(VALID_EMAIL);
            given(mockUser.getNickname()).willReturn(VALID_NICKNAME);
            given(mockUser.getCreatedAt()).willReturn(LocalDateTime.now());
            given(userService.signUp(anyString(), anyString(), anyString())).willReturn(mockUser);

            SignupRequest request = new SignupRequest(VALID_EMAIL, VALID_PASSWORD, VALID_NICKNAME);

            mvc.perform(post(SIGNUP_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.resultCode").value("201-1"))
                    .andExpect(jsonPath("$.data.email").value(VALID_EMAIL))
                    .andExpect(jsonPath("$.data.nickname").value(VALID_NICKNAME))
                    .andExpect(jsonPath("$.data.id").isNumber());
        }

        @Test
        @DisplayName("이메일 중복 → DUPLICATE_EMAIL (409)")
        void signup_DuplicateEmail() throws Exception {
            given(userService.signUp(anyString(), anyString(), anyString()))
                    .willThrow(new BusinessException(ErrorCode.DUPLICATE_EMAIL));

            SignupRequest request = new SignupRequest(VALID_EMAIL, VALID_PASSWORD, VALID_NICKNAME);

            mvc.perform(post(SIGNUP_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.resultCode").value("409-1"))
                    .andExpect(jsonPath("$.msg").value("이미 사용중인 이메일입니다."));
        }

        @Test
        @DisplayName("닉네임 중복 → 409")
        void signup_DuplicateNickname() throws Exception {
            given(userService.signUp(anyString(), anyString(), anyString()))
                    .willThrow(new BusinessException(ErrorCode.DUPLICATE_EMAIL));

            SignupRequest request = new SignupRequest(VALID_EMAIL, VALID_PASSWORD, VALID_NICKNAME);

            mvc.perform(post(SIGNUP_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.resultCode").value("409-1"));
        }

        @Test
        @DisplayName("이메일 누락 → 400")
        void signup_BlankEmail() throws Exception {
            SignupRequest request = new SignupRequest("", VALID_PASSWORD, VALID_NICKNAME);

            mvc.perform(post(SIGNUP_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.resultCode").value("400-1"));
        }

        @Test
        @DisplayName("이메일 형식 오류 → 400")
        void signup_InvalidEmailFormat() throws Exception {
            SignupRequest request = new SignupRequest("not-an-email", VALID_PASSWORD, VALID_NICKNAME);

            mvc.perform(post(SIGNUP_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.resultCode").value("400-1"));
        }

        @Test
        @DisplayName("비밀번호 누락 → 400")
        void signup_BlankPassword() throws Exception {
            SignupRequest request = new SignupRequest(VALID_EMAIL, "", VALID_NICKNAME);

            mvc.perform(post(SIGNUP_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.resultCode").value("400-1"));
        }

        @Test
        @DisplayName("비밀번호 6자 미만 → 400")
        void signup_PasswordTooShort() throws Exception {
            SignupRequest request = new SignupRequest(VALID_EMAIL, "12345", VALID_NICKNAME);

            mvc.perform(post(SIGNUP_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.resultCode").value("400-1"));
        }

        @Test
        @DisplayName("닉네임 누락 → 400")
        void signup_BlankNickname() throws Exception {
            SignupRequest request = new SignupRequest(VALID_EMAIL, VALID_PASSWORD, "");

            mvc.perform(post(SIGNUP_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.resultCode").value("400-1"));
        }

        @Test
        @DisplayName("닉네임 2자 미만 → 400")
        void signup_NicknameTooShort() throws Exception {
            SignupRequest request = new SignupRequest(VALID_EMAIL, VALID_PASSWORD, "a");

            mvc.perform(post(SIGNUP_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.resultCode").value("400-1"));
        }

        @Test
        @DisplayName("닉네임 20자 초과 → 400")
        void signup_NicknameTooLong() throws Exception {
            SignupRequest request = new SignupRequest(VALID_EMAIL, VALID_PASSWORD, "a".repeat(21));

            mvc.perform(post(SIGNUP_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.resultCode").value("400-1"));
        }
    }

    @Nested
    @DisplayName("POST /api/users/login 로그인")
    class Login {

        private static final String LOGIN_URL = "/api/users/login";
        private static final String VALID_EMAIL = "test@example.com";
        private static final String VALID_PASSWORD = "password123";
        private static final String NICKNAME = "testuser";
        private static final String MOCK_ACCESS_TOKEN = "mocked.access.token";
        private static final String EXISTING_REFRESH_TOKEN = "22222222-2222-2222-2222-222222222222";

        private User mockUserWithRefreshToken(String refreshToken) {
            User mockUser = mock(User.class);
            given(mockUser.getId()).willReturn(1L);
            given(mockUser.getEmail()).willReturn(VALID_EMAIL);
            given(mockUser.getNickname()).willReturn(NICKNAME);
            given(mockUser.getRole()).willReturn(UserRole.USER);
            given(mockUser.getRefreshToken()).willReturn(refreshToken);
            return mockUser;
        }

        @Test
        @DisplayName("성공 (200) - 로그인 시 유저의 refreshToken을 그대로 응답에 포함한다")
        void login_Success() throws Exception {
            User mockUser = mockUserWithRefreshToken(EXISTING_REFRESH_TOKEN);
            given(userService.login(VALID_EMAIL, VALID_PASSWORD)).willReturn(mockUser);
            given(jwtProvider.generateAccessToken(1L, VALID_EMAIL, NICKNAME, UserRole.USER))
                    .willReturn(MOCK_ACCESS_TOKEN);

            LoginRequest request = new LoginRequest(VALID_EMAIL, VALID_PASSWORD);

            mvc.perform(post(LOGIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.resultCode").value("200-1"))
                    .andExpect(jsonPath("$.data.accessToken").value(MOCK_ACCESS_TOKEN))
                    .andExpect(jsonPath("$.data.refreshToken").value(EXISTING_REFRESH_TOKEN))
                    .andExpect(jsonPath("$.data.expiresIn").isNumber())
                    .andExpect(jsonPath("$.data.user.id").value(1))
                    .andExpect(jsonPath("$.data.user.email").value(VALID_EMAIL))
                    .andExpect(jsonPath("$.data.user.nickname").value(NICKNAME))
                    .andExpect(jsonPath("$.data.user.role").value("USER"));

            verify(securityHelper).setCookie("accessToken", MOCK_ACCESS_TOKEN);
            verify(securityHelper).setCookie("refreshToken", EXISTING_REFRESH_TOKEN);
        }

        @Test
        @DisplayName("실패 - 비밀번호 불일치 → 401")
        void login_WrongPassword() throws Exception {
            given(userService.login(VALID_EMAIL, VALID_PASSWORD))
                    .willThrow(new BusinessException(ErrorCode.UNAUTHORIZED));

            LoginRequest request = new LoginRequest(VALID_EMAIL, VALID_PASSWORD);

            mvc.perform(post(LOGIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.resultCode").value("401-1"))
                    .andExpect(jsonPath("$.msg").value("인증되지 않은 사용자입니다."));
        }

        @Test
        @DisplayName("실패 - 존재하지 않는 이메일 → 401 (비밀번호 불일치와 동일 처리)")
        void login_EmailNotFound() throws Exception {
            given(userService.login(VALID_EMAIL, VALID_PASSWORD))
                    .willThrow(new BusinessException(ErrorCode.UNAUTHORIZED));

            LoginRequest request = new LoginRequest(VALID_EMAIL, VALID_PASSWORD);

            mvc.perform(post(LOGIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.resultCode").value("401-1"));
        }

        @Test
        @DisplayName("이메일 누락 → 400")
        void login_BlankEmail() throws Exception {
            LoginRequest request = new LoginRequest("", VALID_PASSWORD);

            mvc.perform(post(LOGIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.resultCode").value("400-1"));
        }

        @Test
        @DisplayName("이메일 형식 오류 → 400")
        void login_InvalidEmailFormat() throws Exception {
            LoginRequest request = new LoginRequest("not-an-email", VALID_PASSWORD);

            mvc.perform(post(LOGIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.resultCode").value("400-1"));
        }

        @Test
        @DisplayName("비밀번호 누락 → 400")
        void login_BlankPassword() throws Exception {
            LoginRequest request = new LoginRequest(VALID_EMAIL, "");

            mvc.perform(post(LOGIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.resultCode").value("400-1"));
        }
    }

    @Nested
    @DisplayName("POST /api/users/logout 로그아웃")
    class Logout {

        private static final String LOGOUT_URL = "/api/users/logout";

        private User mockActor() {
            User mockUser = mock(User.class);
            given(mockUser.getId()).willReturn(1L);
            return mockUser;
        }

        @Test
        @DisplayName("성공 (200) - accessToken, refreshToken 쿠키를 삭제한다")
        void logout_Success() throws Exception {
            User actor = mockActor();
            given(securityHelper.getActor()).willReturn(actor);

            mvc.perform(post(LOGOUT_URL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.resultCode").value("200-1"))
                    .andExpect(jsonPath("$.msg").value("로그아웃에 성공했습니다."));

            verify(securityHelper).deleteCookie("accessToken");
            verify(securityHelper).deleteCookie("refreshToken");
        }
    }

    @Nested
    @DisplayName("DELETE /api/users 회원탈퇴")
    class Withdraw {

        private static final String WITHDRAW_URL = "/api/users";

        private User mockActor() {
            User mockUser = mock(User.class);
            given(mockUser.getId()).willReturn(1L);
            return mockUser;
        }

        @Test
        @DisplayName("성공 (200) - 계정을 삭제하고 accessToken, refreshToken 쿠키를 제거한다")
        void withdraw_Success() throws Exception {
            User actor = mockActor();
            given(securityHelper.getActor()).willReturn(actor);

            UserDeleteRequest request = new UserDeleteRequest("password123");

            mvc.perform(delete(WITHDRAW_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.resultCode").value("200-1"))
                    .andExpect(jsonPath("$.msg").value("회원탈퇴에 성공했습니다."));

            verify(userService).deleteUser(1L, "password123");
            verify(securityHelper).deleteCookie("accessToken");
            verify(securityHelper).deleteCookie("refreshToken");
        }

        @Test
        @DisplayName("비밀번호 누락 → 400")
        void withdraw_BlankPassword() throws Exception {
            UserDeleteRequest request = new UserDeleteRequest("");

            mvc.perform(delete(WITHDRAW_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.resultCode").value("400-1"));
        }

        @Test
        @DisplayName("실패 - 비밀번호 불일치 → 401, 쿠키는 삭제하지 않는다")
        void withdraw_WrongPassword() throws Exception {
            User actor = mockActor();
            given(securityHelper.getActor()).willReturn(actor);
            willThrow(new BusinessException(ErrorCode.UNAUTHORIZED))
                    .given(userService).deleteUser(1L, "wrongpassword");

            UserDeleteRequest request = new UserDeleteRequest("wrongpassword");

            mvc.perform(delete(WITHDRAW_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.resultCode").value("401-1"));

            verify(securityHelper, never()).deleteCookie(anyString());
        }
    }

    @Nested
    @DisplayName("GET /api/users/me 내 정보 조회")
    class GetMe {

        private static final String ME_URL = "/api/users/me";
        private static final String EMAIL = "test@example.com";
        private static final String NICKNAME = "testuser";
        private static final String INTRODUCTION = "안녕하세요";

        private User mockActor() {
            User mockUser = mock(User.class);
            given(mockUser.getId()).willReturn(1L);
            given(mockUser.getEmail()).willReturn(EMAIL);
            given(mockUser.getNickname()).willReturn(NICKNAME);
            given(mockUser.getIntroduction()).willReturn(INTRODUCTION);
            given(mockUser.getCreatedAt()).willReturn(LocalDateTime.of(2026, 1, 1, 0, 0));
            given(mockUser.getUpdatedAt()).willReturn(LocalDateTime.of(2026, 1, 2, 0, 0));
            return mockUser;
        }

        @Test
        @DisplayName("성공 (200) - 로그인한 유저 자신의 정보를 반환한다")
        void getMe_Success() throws Exception {
            User actor = mockActor();
            given(securityHelper.getActor()).willReturn(actor);
            given(userService.getUser(1L)).willReturn(actor);
            given(userMediaService.getMedia(1L)).willReturn(
                    new UserMediaResponse(1L, 1L, null, null)
            );

            mvc.perform(get(ME_URL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.resultCode").value("200-1"))
                    .andExpect(jsonPath("$.data.id").value(1))
                    .andExpect(jsonPath("$.data.email").value(EMAIL))
                    .andExpect(jsonPath("$.data.profile.nickname").value(NICKNAME))
                    .andExpect(jsonPath("$.data.profile.introduction").value(INTRODUCTION))
                    .andExpect(jsonPath("$.data.createdAt").exists())
                    .andExpect(jsonPath("$.data.updatedAt").exists());
        }

        @Test
        @DisplayName("실패 - 인증되지 않은 사용자 → 401")
        void getMe_Unauthorized() throws Exception {
            given(securityHelper.getActor()).willReturn(null);

            mvc.perform(get(ME_URL))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.resultCode").value("401-1"));
        }
    }

    @Nested
    @DisplayName("PATCH /api/users/me 내 정보 수정")
    class UpdateMe {

        private static final String ME_URL = "/api/users/me";
        private static final String EMAIL = "test@example.com";
        private static final String NEW_NICKNAME = "newnickname";
        private static final String NEW_INTRODUCTION = "수정된 소개글입니다.";

        private User mockActor() {
            User mockUser = mock(User.class);
            given(mockUser.getId()).willReturn(1L);
            return mockUser;
        }

        private MockMultipartFile requestPart(UserUpdateRequest request) throws Exception {
            return new MockMultipartFile(
                    "request", "", MediaType.APPLICATION_JSON_VALUE,
                    objectMapper.writeValueAsBytes(request)
            );
        }

        @Test
        @DisplayName("성공 (200) - 닉네임, 소개글을 수정한다")
        void updateMe_Success() throws Exception {
            User actor = mockActor();
            given(securityHelper.getActor()).willReturn(actor);

            User updatedUser = mock(User.class);
            given(updatedUser.getId()).willReturn(1L);
            given(updatedUser.getEmail()).willReturn(EMAIL);
            given(updatedUser.getNickname()).willReturn(NEW_NICKNAME);
            given(updatedUser.getIntroduction()).willReturn(NEW_INTRODUCTION);
            given(updatedUser.getCreatedAt()).willReturn(LocalDateTime.now());
            given(userService.updateUser(1L, NEW_NICKNAME, NEW_INTRODUCTION)).willReturn(updatedUser);

            UserUpdateRequest request = new UserUpdateRequest(NEW_NICKNAME, NEW_INTRODUCTION);

            mvc.perform(multipart(HttpMethod.PATCH, ME_URL)
                            .file(requestPart(request))
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.resultCode").value("200-1"))
                    .andExpect(jsonPath("$.data.profile.nickname").value(NEW_NICKNAME))
                    .andExpect(jsonPath("$.data.profile.introduction").value(NEW_INTRODUCTION));
        }

        @Test
        @DisplayName("성공 (200) - 새 이미지를 첨부하지 않으면 기존 프로필 이미지 URL을 그대로 응답한다")
        void updateMe_KeepsExistingProfileImageWhenNoneUploaded() throws Exception {
            User actor = mockActor();
            given(securityHelper.getActor()).willReturn(actor);

            User updatedUser = mock(User.class);
            given(updatedUser.getId()).willReturn(1L);
            given(updatedUser.getEmail()).willReturn(EMAIL);
            given(updatedUser.getNickname()).willReturn(NEW_NICKNAME);
            given(updatedUser.getIntroduction()).willReturn(NEW_INTRODUCTION);
            given(updatedUser.getCreatedAt()).willReturn(LocalDateTime.now());
            given(userService.updateUser(1L, NEW_NICKNAME, NEW_INTRODUCTION)).willReturn(updatedUser);
            given(userMediaService.getMedia(1L)).willReturn(
                    new UserMediaResponse(1L, 1L, "user/uuid.png", null)
            );

            UserUpdateRequest request = new UserUpdateRequest(NEW_NICKNAME, NEW_INTRODUCTION);

            mvc.perform(multipart(HttpMethod.PATCH, ME_URL)
                            .file(requestPart(request))
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.profile.profileImageUrl").value("user/uuid.png"));
        }

        @Test
        @DisplayName("실패 - 인증되지 않은 사용자 → 401")
        void updateMe_Unauthorized() throws Exception {
            given(securityHelper.getActor()).willReturn(null);

            UserUpdateRequest request = new UserUpdateRequest(NEW_NICKNAME, NEW_INTRODUCTION);

            mvc.perform(multipart(HttpMethod.PATCH, ME_URL)
                            .file(requestPart(request))
                            .with(csrf()))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.resultCode").value("401-1"));
        }

        @Test
        @DisplayName("닉네임 2자 미만 → 400")
        void updateMe_NicknameTooShort() throws Exception {
            UserUpdateRequest request = new UserUpdateRequest("a", NEW_INTRODUCTION);

            mvc.perform(multipart(HttpMethod.PATCH, ME_URL)
                            .file(requestPart(request))
                            .with(csrf()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.resultCode").value("400-1"));
        }

        @Test
        @DisplayName("닉네임 20자 초과 → 400")
        void updateMe_NicknameTooLong() throws Exception {
            UserUpdateRequest request = new UserUpdateRequest("a".repeat(21), NEW_INTRODUCTION);

            mvc.perform(multipart(HttpMethod.PATCH, ME_URL)
                            .file(requestPart(request))
                            .with(csrf()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.resultCode").value("400-1"));
        }

        @Test
        @DisplayName("닉네임이 공백으로만 이루어짐 → 400")
        void updateMe_NicknameBlank() throws Exception {
            UserUpdateRequest request = new UserUpdateRequest("  ", NEW_INTRODUCTION);

            mvc.perform(multipart(HttpMethod.PATCH, ME_URL)
                            .file(requestPart(request))
                            .with(csrf()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.resultCode").value("400-1"));
        }

        @Test
        @DisplayName("소개글 100자 초과 → 400")
        void updateMe_IntroductionTooLong() throws Exception {
            UserUpdateRequest request = new UserUpdateRequest(NEW_NICKNAME, "a".repeat(101));

            mvc.perform(multipart(HttpMethod.PATCH, ME_URL)
                            .file(requestPart(request))
                            .with(csrf()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.resultCode").value("400-1"));
        }
    }

    @Nested
    @DisplayName("PUT /api/users/me/password 비밀번호 변경")
    class UpdatePassword {

        private static final String PASSWORD_URL = "/api/users/me/password";
        private static final String EMAIL = "test@example.com";
        private static final String NICKNAME = "testuser";
        private static final String CURRENT_PASSWORD = "password123";
        private static final String NEW_PASSWORD = "newpassword456";
        private static final String MOCK_ACCESS_TOKEN = "mocked.access.token";
        private static final String REFRESH_TOKEN = "33333333-3333-3333-3333-333333333333";

        private User mockActor() {
            User mockUser = mock(User.class);
            given(mockUser.getId()).willReturn(1L);
            given(mockUser.getEmail()).willReturn(EMAIL);
            given(mockUser.getNickname()).willReturn(NICKNAME);
            given(mockUser.getRole()).willReturn(UserRole.USER);
            given(mockUser.getRefreshToken()).willReturn(REFRESH_TOKEN);
            return mockUser;
        }

        @Test
        @DisplayName("성공 (200) - 비밀번호 변경 후 새 토큰을 발급한다")
        void updatePassword_Success() throws Exception {
            User actor = mockActor();
            given(securityHelper.getActor()).willReturn(actor);
            given(userService.getUser(1L)).willReturn(actor);
            given(jwtProvider.generateAccessToken(1L, EMAIL, NICKNAME, UserRole.USER))
                    .willReturn(MOCK_ACCESS_TOKEN);

            UserPasswordUpdateRequest request = new UserPasswordUpdateRequest(CURRENT_PASSWORD, NEW_PASSWORD);

            mvc.perform(put(PASSWORD_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.resultCode").value("200-1"))
                    .andExpect(jsonPath("$.data.accessToken").value(MOCK_ACCESS_TOKEN))
                    .andExpect(jsonPath("$.data.refreshToken").value(REFRESH_TOKEN))
                    .andExpect(jsonPath("$.data.expiresIn").isNumber());
        }

        @Test
        @DisplayName("실패 - 인증되지 않은 사용자 → 401")
        void updatePassword_Unauthorized() throws Exception {
            given(securityHelper.getActor()).willReturn(null);

            UserPasswordUpdateRequest request = new UserPasswordUpdateRequest(CURRENT_PASSWORD, NEW_PASSWORD);

            mvc.perform(put(PASSWORD_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.resultCode").value("401-1"));
        }

        @Test
        @DisplayName("현재 비밀번호 누락 → 400")
        void updatePassword_BlankCurrentPassword() throws Exception {
            UserPasswordUpdateRequest request = new UserPasswordUpdateRequest("", NEW_PASSWORD);

            mvc.perform(put(PASSWORD_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.resultCode").value("400-1"));
        }

        @Test
        @DisplayName("새 비밀번호 누락 → 400")
        void updatePassword_BlankNewPassword() throws Exception {
            UserPasswordUpdateRequest request = new UserPasswordUpdateRequest(CURRENT_PASSWORD, "");

            mvc.perform(put(PASSWORD_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.resultCode").value("400-1"));
        }

        @Test
        @DisplayName("새 비밀번호 6자 미만 → 400")
        void updatePassword_NewPasswordTooShort() throws Exception {
            UserPasswordUpdateRequest request = new UserPasswordUpdateRequest(CURRENT_PASSWORD, "12345");

            mvc.perform(put(PASSWORD_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.resultCode").value("400-1"));
        }
    }

    @Nested
    @DisplayName("GET /api/users/{id} 유저 페이지 조회")
    class GetUserProfile {

        private static final String NICKNAME = "testuser";
        private static final String INTRODUCTION = "안녕하세요";

        private User mockTargetUser() {
            User mockUser = mock(User.class);
            given(mockUser.getId()).willReturn(1L);
            given(mockUser.getNickname()).willReturn(NICKNAME);
            given(mockUser.getIntroduction()).willReturn(INTRODUCTION);
            return mockUser;
        }

        @Test
        @DisplayName("성공 (200) - 프로필 이미지가 있는 경우")
        void getUserProfile_Success() throws Exception {
            User targetUser = mockTargetUser();
            given(userService.getUser(1L)).willReturn(targetUser);
            given(userMediaService.getMedia(1L)).willReturn(
                    new UserMediaResponse(1L, 1L, "user/uuid.png", com.scommit.domain.media.media.entity.MediaType.IMAGE)
            );
            given(subscriptionService.getFollowerCount(1L)).willReturn(5L);

            mvc.perform(get("/api/users/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.resultCode").value("200-1"))
                    .andExpect(jsonPath("$.data.id").value(1))
                    .andExpect(jsonPath("$.data.followerCount").value(5))
                    .andExpect(jsonPath("$.data.profile.nickname").value(NICKNAME))
                    .andExpect(jsonPath("$.data.profile.introduction").value(INTRODUCTION))
                    .andExpect(jsonPath("$.data.profile.profileImageUrl").value("user/uuid.png"));
        }

        @Test
        @DisplayName("성공 (200) - 프로필 이미지가 없는 경우")
        void getUserProfile_NoMedia() throws Exception {
            User targetUser = mockTargetUser();
            given(userService.getUser(1L)).willReturn(targetUser);
            given(userMediaService.getMedia(1L)).willReturn(null);
            given(subscriptionService.getFollowerCount(1L)).willReturn(0L);

            mvc.perform(get("/api/users/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.resultCode").value("200-1"))
                    .andExpect(jsonPath("$.data.followerCount").value(0))
                    .andExpect(jsonPath("$.data.profile.profileImageUrl").doesNotExist());
        }

        @Test
        @DisplayName("성공 (200) - 팔로워가 없는 유저를 조회하면 followerCount가 0으로 응답된다")
        void getUserProfile_ZeroFollowers() throws Exception {
            User targetUser = mockTargetUser();
            given(userService.getUser(1L)).willReturn(targetUser);
            given(userMediaService.getMedia(1L)).willReturn(null);
            given(subscriptionService.getFollowerCount(1L)).willReturn(0L);

            mvc.perform(get("/api/users/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.followerCount").value(0));
        }

        @Test
        @DisplayName("존재하지 않는 유저 → 404 (팔로워 수는 조회하지 않는다)")
        void getUserProfile_UserNotFound() throws Exception {
            given(userService.getUser(999L)).willReturn(null);

            mvc.perform(get("/api/users/999"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.resultCode").value("404-2"));

            verify(subscriptionService, never()).getFollowerCount(anyLong());
        }
    }

    @Nested
    @DisplayName("GET /api/users/{id}/medias 프로필 이미지 조회")
    class GetMedia {

        @Test
        @DisplayName("성공 (200)")
        void getMedia_Success() throws Exception {
            UserMediaResponse response = new UserMediaResponse(1L, 1L, "user/uuid.png", com.scommit.domain.media.media.entity.MediaType.IMAGE);
            given(userMediaService.getMedia(1L)).willReturn(response);

            mvc.perform(get("/api/users/1/medias"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.url").value("user/uuid.png"))
                    .andExpect(jsonPath("$.data.userId").value(1L));
        }

        @Test
        @DisplayName("유저 없음 → 404")
        void getMedia_UserNotFound() throws Exception {
            given(userMediaService.getMedia(999L))
                    .willThrow(new BusinessException(ErrorCode.USER_NOT_FOUND));

            mvc.perform(get("/api/users/999/medias"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("미디어 없음 → 200 (data: null)")
        void getMedia_MediaNotFound() throws Exception {
            given(userMediaService.getMedia(1L)).willReturn(null);

            mvc.perform(get("/api/users/1/medias"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").doesNotExist());
        }
    }

    @Nested
    @DisplayName("")
    class UploadMedia {

        @Test
        @DisplayName("성공 (201)")
        void uploadMedia_Success() throws Exception {
            User actor = mock(User.class);
            given(actor.getId()).willReturn(1L);
            given(securityHelper.getActor()).willReturn(actor);

            UserMediaResponse response = new UserMediaResponse(1L, 1L, "user/uuid.png", com.scommit.domain.media.media.entity.MediaType.IMAGE);
            MockMultipartFile file = new MockMultipartFile("file", "profile.png", "image/png", "content".getBytes());
            given(userMediaService.uploadMedia(anyLong(), any())).willReturn(response);

            mvc.perform(multipart("/api/users/me/medias")
                            .file(file)
                            .with(csrf()))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.url").value("user/uuid.png"));
        }
    }

    @Nested
    @DisplayName("DELETE /api/users/me/medias 프로필 이미지 삭제")
    class DeleteMedia {

        private User mockActor() {
            User mockUser = mock(User.class);
            given(mockUser.getId()).willReturn(1L);
            return mockUser;
        }

        @Test
        @DisplayName("성공 (200)")
        void deleteMedia_Success() throws Exception {
            User actor = mockActor();
            given(securityHelper.getActor()).willReturn(actor);

            mvc.perform(delete("/api/users/me/medias")
                            .with(csrf()))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("미디어 없음 → 404")
        void deleteMedia_MediaNotFound() throws Exception {
            User actor = mockActor();
            given(securityHelper.getActor()).willReturn(actor);
            doThrow(new BusinessException(ErrorCode.RESOURCE_NOT_FOUND))
                    .when(userMediaService).deleteMedia(anyLong());

            mvc.perform(delete("/api/users/me/medias")
                            .with(csrf()))
                    .andExpect(status().isNotFound());
        }
    }
}
