    package com.scommit.domain.user.user.service;

    import com.scommit.domain.user.user.dto.UserSearchResponse;
    import com.scommit.domain.user.user.entity.User;
    import com.scommit.domain.user.user.entity.UserRole;
    import com.scommit.domain.user.user.repository.UserRepository;
    import com.scommit.global.exception.BusinessException;
    import com.scommit.global.exception.ErrorCode;
    import org.junit.jupiter.api.DisplayName;
    import org.junit.jupiter.api.Nested;
    import org.junit.jupiter.api.Test;
    import org.junit.jupiter.api.extension.ExtendWith;
    import org.mockito.InjectMocks;
    import org.mockito.Mock;
    import org.mockito.junit.jupiter.MockitoExtension;
    import org.springframework.data.domain.Page;
    import org.springframework.data.domain.PageImpl;
    import org.springframework.data.domain.PageRequest;
    import org.springframework.data.domain.Pageable;
    import org.springframework.security.crypto.password.PasswordEncoder;
    import org.springframework.test.util.ReflectionTestUtils;

    import java.util.List;
    import java.util.Optional;

    import static org.assertj.core.api.Assertions.assertThat;
    import static org.assertj.core.api.Assertions.assertThatThrownBy;
    import static org.mockito.ArgumentMatchers.any;
    import static org.mockito.ArgumentMatchers.anyString;
    import static org.mockito.BDDMockito.given;
    import static org.mockito.Mockito.never;
    import static org.mockito.Mockito.verify;

    @ExtendWith(MockitoExtension.class)
    class UserServiceTest {

        @Mock
        private UserRepository userRepository;

        @Mock
        private PasswordEncoder passwordEncoder;

        @InjectMocks
        private UserService userService;

        @Nested
        @DisplayName("회원가입")
        class SignUp {

            private static final String EMAIL = "test@example.com";
            private static final String PASSWORD = "password123";
            private static final String NICKNAME = "testuser";

            @Test
            @DisplayName("성공: 이메일과 닉네임이 중복되지 않으면 유저를 저장하고 반환한다.")
            void signUp_Success() {
                // Given
                given(userRepository.existsByEmail(EMAIL)).willReturn(false);
                given(userRepository.existsByNickname(NICKNAME)).willReturn(false);
                given(passwordEncoder.encode(PASSWORD)).willReturn("encodedPassword");

                User savedUser = new User(EMAIL, "encodedPassword", NICKNAME, null, UserRole.USER);
                given(userRepository.save(any(User.class))).willReturn(savedUser);

                // When
                User result = userService.signUp(EMAIL, PASSWORD, NICKNAME);

                // Then
                assertThat(result.getEmail()).isEqualTo(EMAIL);
                assertThat(result.getNickname()).isEqualTo(NICKNAME);
                assertThat(result.getPassword()).isEqualTo("encodedPassword");
                verify(passwordEncoder).encode(PASSWORD);
                verify(userRepository).save(any(User.class));
            }

            @Test
            @DisplayName("성공: 저장 시 평문 비밀번호가 아닌 인코딩된 비밀번호가 사용된다.")
            void signUp_PasswordIsEncoded() {
                // Given
                given(userRepository.existsByEmail(EMAIL)).willReturn(false);
                given(userRepository.existsByNickname(NICKNAME)).willReturn(false);
                given(passwordEncoder.encode(PASSWORD)).willReturn("encodedPassword");
                given(userRepository.save(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));

                // When
                User result = userService.signUp(EMAIL, PASSWORD, NICKNAME);

                // Then
                assertThat(result.getPassword()).isNotEqualTo(PASSWORD);
                assertThat(result.getPassword()).isEqualTo("encodedPassword");
            }

            @Test
            @DisplayName("성공: 저장 전 refreshToken을 새로 발급한다.")
            void signUp_IssuesRefreshToken() {
                // Given
                given(userRepository.existsByEmail(EMAIL)).willReturn(false);
                given(userRepository.existsByNickname(NICKNAME)).willReturn(false);
                given(passwordEncoder.encode(PASSWORD)).willReturn("encodedPassword");
                given(userRepository.save(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));

                // When
                User result = userService.signUp(EMAIL, PASSWORD, NICKNAME);

                // Then
                assertThat(result.getRefreshToken()).isNotNull();
            }

            @Test
            @DisplayName("실패: 이메일이 중복되면 DUPLICATE_EMAIL 예외를 던진다.")
            void signUp_DuplicateEmail() {
                // Given
                given(userRepository.existsByEmail(EMAIL)).willReturn(true);

                // When & Then
                assertThatThrownBy(() -> userService.signUp(EMAIL, PASSWORD, NICKNAME))
                        .isInstanceOf(BusinessException.class)
                        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DUPLICATE_EMAIL);

                verify(userRepository, never()).save(any(User.class));
            }

            @Test
            @DisplayName("실패: 닉네임이 중복되면 예외를 던지고 저장하지 않는다.")
            void signUp_DuplicateNickname() {
                // Given
                given(userRepository.existsByEmail(EMAIL)).willReturn(false);
                given(userRepository.existsByNickname(NICKNAME)).willReturn(true);

                // When & Then
                assertThatThrownBy(() -> userService.signUp(EMAIL, PASSWORD, NICKNAME))
                        .isInstanceOf(BusinessException.class);

                verify(userRepository, never()).save(any(User.class));
                verify(passwordEncoder, never()).encode(anyString());
            }
        }

        @Nested
        @DisplayName("로그인")
        class Login {

            private static final String EMAIL = "test@example.com";
            private static final String PASSWORD = "password123";
            private static final String WRONG_PASSWORD = "wrongPassword";
            private static final String ENCODED_PASSWORD = "encodedPassword";
            private static final String NICKNAME = "testuser";

            private User buildUser() {
                return new User(EMAIL, ENCODED_PASSWORD, NICKNAME, null, UserRole.USER);
            }

            @Test
            @DisplayName("성공: 이메일과 비밀번호가 일치하면 유저를 반환한다.")
            void login_Success() {
                // Given
                User user = buildUser();
                given(userRepository.findByEmailAndDeletedAtIsNull(EMAIL)).willReturn(user);
                given(passwordEncoder.matches(PASSWORD, ENCODED_PASSWORD)).willReturn(true);

                // When
                User result = userService.login(EMAIL, PASSWORD);

                // Then
                assertThat(result.getEmail()).isEqualTo(EMAIL);
            }

            @Test
            @DisplayName("실패: 존재하지 않는 이메일이면 INVALID_CREDENTIALS 예외를 던진다.")
            void login_EmailNotFound() {
                // Given
                given(userRepository.findByEmailAndDeletedAtIsNull(EMAIL)).willReturn(null);

                // When & Then
                assertThatThrownBy(() -> userService.login(EMAIL, PASSWORD))
                        .isInstanceOf(BusinessException.class)
                        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_CREDENTIALS);
            }

            @Test
            @DisplayName("실패: 비밀번호가 일치하지 않으면 INVALID_CREDENTIALS 예외를 던진다.")
            void login_WrongPassword() {
                // Given
                User user = buildUser();
                given(userRepository.findByEmailAndDeletedAtIsNull(EMAIL)).willReturn(user);
                given(passwordEncoder.matches(WRONG_PASSWORD, ENCODED_PASSWORD)).willReturn(false);

                // When & Then
                assertThatThrownBy(() -> userService.login(EMAIL, WRONG_PASSWORD))
                        .isInstanceOf(BusinessException.class)
                        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_CREDENTIALS);
            }
        }

        @Nested
        @DisplayName("내 정보 수정")
        class UpdateUser {

            private static final Long USER_ID = 1L;
            private static final String NICKNAME = "testuser";
            private static final String NEW_NICKNAME = "newnickname";
            private static final String NEW_INTRODUCTION = "수정된 소개글입니다.";

            private User buildUser() {
                return new User("test@example.com", "encodedPassword", NICKNAME, null, UserRole.USER);
            }

            @Test
            @DisplayName("성공: 닉네임과 소개글을 수정한다.")
            void updateUser_Success() {
                // Given
                User user = buildUser();
                given(userRepository.findByIdAndDeletedAtIsNull(USER_ID)).willReturn(user);
                given(userRepository.existsByNickname(NEW_NICKNAME)).willReturn(false);

                // When
                User result = userService.updateUser(USER_ID, NEW_NICKNAME, NEW_INTRODUCTION);

                // Then
                assertThat(result.getNickname()).isEqualTo(NEW_NICKNAME);
                assertThat(result.getIntroduction()).isEqualTo(NEW_INTRODUCTION);
            }

            @Test
            @DisplayName("성공: 기존과 동일한 닉네임을 다시 보내도 중복 예외 없이 수정된다.")
            void updateUser_SameNicknameAsCurrent() {
                // Given
                User user = buildUser();
                given(userRepository.findByIdAndDeletedAtIsNull(USER_ID)).willReturn(user);

                // When
                User result = userService.updateUser(USER_ID, NICKNAME, NEW_INTRODUCTION);

                // Then
                assertThat(result.getNickname()).isEqualTo(NICKNAME);
                assertThat(result.getIntroduction()).isEqualTo(NEW_INTRODUCTION);
                verify(userRepository, never()).existsByNickname(any());
            }

            @Test
            @DisplayName("성공: 닉네임 없이 소개글만 수정한다.")
            void updateUser_NicknameNull() {
                // Given
                User user = buildUser();
                given(userRepository.findByIdAndDeletedAtIsNull(USER_ID)).willReturn(user);

                // When
                User result = userService.updateUser(USER_ID, null, NEW_INTRODUCTION);

                // Then
                assertThat(result.getNickname()).isEqualTo(NICKNAME);
                assertThat(result.getIntroduction()).isEqualTo(NEW_INTRODUCTION);
                verify(userRepository, never()).existsByNickname(any());
            }

            @Test
            @DisplayName("실패: 닉네임이 중복되면 예외를 던지고 수정하지 않는다.")
            void updateUser_DuplicateNickname() {
                // Given
                User user = buildUser();
                given(userRepository.findByIdAndDeletedAtIsNull(USER_ID)).willReturn(user);
                given(userRepository.existsByNickname(NEW_NICKNAME)).willReturn(true);

                // When & Then
                assertThatThrownBy(() -> userService.updateUser(USER_ID, NEW_NICKNAME, NEW_INTRODUCTION))
                        .isInstanceOf(BusinessException.class)
                        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DUPLICATE_NICKNAME);

                assertThat(user.getNickname()).isEqualTo(NICKNAME);
            }

            @Test
            @DisplayName("실패: 존재하지 않는 유저면 USER_NOT_FOUND 예외를 던진다.")
            void updateUser_UserNotFound() {
                // Given
                given(userRepository.findByIdAndDeletedAtIsNull(USER_ID)).willReturn(null);

                // When & Then
                assertThatThrownBy(() -> userService.updateUser(USER_ID, NEW_NICKNAME, NEW_INTRODUCTION))
                        .isInstanceOf(BusinessException.class)
                        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);

                verify(userRepository, never()).existsByNickname(anyString());
            }
        }

        @Nested
        @DisplayName("비밀번호 변경")
        class UpdatePassword {

            private static final Long USER_ID = 1L;
            private static final String CURRENT_PASSWORD = "password123";
            private static final String WRONG_PASSWORD = "wrongPassword";
            private static final String NEW_PASSWORD = "newPassword456";
            private static final String ENCODED_CURRENT_PASSWORD = "encodedCurrentPassword";
            private static final String ENCODED_NEW_PASSWORD = "encodedNewPassword";

            private User buildUser() {
                return new User("test@example.com", ENCODED_CURRENT_PASSWORD, "testuser", null, UserRole.USER);
            }

            @Test
            @DisplayName("성공: 비밀번호를 변경하고 refreshToken을 재발급한다.")
            void updatePassword_Success() {
                // Given
                User user = buildUser();
                given(userRepository.findByIdAndDeletedAtIsNull(USER_ID)).willReturn(user);
                given(passwordEncoder.matches(CURRENT_PASSWORD, ENCODED_CURRENT_PASSWORD)).willReturn(true);
                given(passwordEncoder.encode(NEW_PASSWORD)).willReturn(ENCODED_NEW_PASSWORD);

                // When
                userService.updatePassword(USER_ID, CURRENT_PASSWORD, NEW_PASSWORD);

                // Then
                assertThat(user.getPassword()).isEqualTo(ENCODED_NEW_PASSWORD);
                assertThat(user.getRefreshToken()).isNotNull();
            }

            @Test
            @DisplayName("실패: 현재 비밀번호가 일치하지 않으면 INVALID_PASSWORD 예외를 던지고 변경하지 않는다.")
            void updatePassword_WrongCurrentPassword() {
                // Given
                User user = buildUser();
                given(userRepository.findByIdAndDeletedAtIsNull(USER_ID)).willReturn(user);
                given(passwordEncoder.matches(WRONG_PASSWORD, ENCODED_CURRENT_PASSWORD)).willReturn(false);

                // When & Then
                assertThatThrownBy(() -> userService.updatePassword(USER_ID, WRONG_PASSWORD, NEW_PASSWORD))
                        .isInstanceOf(BusinessException.class)
                        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_PASSWORD);

                assertThat(user.getPassword()).isEqualTo(ENCODED_CURRENT_PASSWORD);
                verify(passwordEncoder, never()).encode(anyString());
            }

            @Test
            @DisplayName("실패: 존재하지 않는 유저면 USER_NOT_FOUND 예외를 던진다.")
            void updatePassword_UserNotFound() {
                // Given
                given(userRepository.findByIdAndDeletedAtIsNull(USER_ID)).willReturn(null);

                // When & Then
                assertThatThrownBy(() -> userService.updatePassword(USER_ID, CURRENT_PASSWORD, NEW_PASSWORD))
                        .isInstanceOf(BusinessException.class)
                        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
            }
        }

        @Nested
        @DisplayName("회원탈퇴")
        class DeleteUser {

            private static final Long USER_ID = 1L;
            private static final String PASSWORD = "password123";
            private static final String WRONG_PASSWORD = "wrongPassword";
            private static final String ENCODED_PASSWORD = "encodedPassword";

            private User buildUser() {
                return new User("test@example.com", ENCODED_PASSWORD, "testuser", null, UserRole.USER);
            }

            @Test
            @DisplayName("성공: 비밀번호가 일치하면 soft delete하고 refreshToken을 재발급한다.")
            void deleteUser_Success() {
                // Given
                User user = buildUser();
                given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
                given(passwordEncoder.matches(PASSWORD, ENCODED_PASSWORD)).willReturn(true);

                // When
                userService.deleteUser(USER_ID, PASSWORD);

                // Then
                assertThat(user.getDeletedAt()).isNotNull();
                assertThat(user.getRefreshToken()).isNotNull();
            }

            @Test
            @DisplayName("실패: 비밀번호가 일치하지 않으면 INVALID_PASSWORD 예외를 던지고 탈퇴하지 않는다.")
            void deleteUser_WrongPassword() {
                // Given
                User user = buildUser();
                given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
                given(passwordEncoder.matches(WRONG_PASSWORD, ENCODED_PASSWORD)).willReturn(false);

                // When & Then
                assertThatThrownBy(() -> userService.deleteUser(USER_ID, WRONG_PASSWORD))
                        .isInstanceOf(BusinessException.class)
                        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_PASSWORD);

                assertThat(user.getDeletedAt()).isNull();
            }

            @Test
            @DisplayName("실패: 존재하지 않는 유저면 USER_NOT_FOUND 예외를 던진다.")
            void deleteUser_UserNotFound() {
                // Given
                given(userRepository.findById(USER_ID)).willReturn(Optional.empty());

                // When & Then
                assertThatThrownBy(() -> userService.deleteUser(USER_ID, PASSWORD))
                        .isInstanceOf(BusinessException.class)
                        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
            }
        }

        @Nested
        @DisplayName("로그아웃")
        class Logout {

            private static final Long USER_ID = 1L;

            private User buildUser() {
                User user = new User("test@example.com", "encodedPassword", "testuser", null, UserRole.USER);
                user.resetRefreshToken();
                return user;
            }

            @Test
            @DisplayName("성공: refreshToken을 재발급한다.")
            void logout_Success() {
                // Given
                User user = buildUser();
                String oldRefreshToken = user.getRefreshToken();
                given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));

                // When
                userService.logout(USER_ID);

                // Then
                assertThat(user.getRefreshToken())
                        .isNotNull()
                        .isNotEqualTo(oldRefreshToken);
            }

            @Test
            @DisplayName("실패: 존재하지 않는 유저면 USER_NOT_FOUND 예외를 던진다.")
            void logout_UserNotFound() {
                // Given
                given(userRepository.findById(USER_ID)).willReturn(Optional.empty());

                // When & Then
                assertThatThrownBy(() -> userService.logout(USER_ID))
                        .isInstanceOf(BusinessException.class)
                        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
            }
        }

    @Nested
    @DisplayName("유저 검색 테스트")
    class SearchUsers {

        @Test
        @DisplayName("성공: 닉네임 키워드로 유저 목록을 반환한다.")
        void searchUsers_Success() {
            Pageable pageable = PageRequest.of(0, 10);
            User user = new User("a@a.com", null, "발코드", null, UserRole.USER);
            ReflectionTestUtils.setField(user, "id", 1L);
            Page<User> page = new PageImpl<>(List.of(user), pageable, 1);

            given(userRepository.findByNicknameContainingAndDeletedAtIsNull("발코드", pageable)).willReturn(page);

            Page<UserSearchResponse> result = userService.searchUsers("발코드", pageable);

            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().get(0).getNickname()).isEqualTo("발코드");
        }

        @Test
        @DisplayName("성공: 검색 결과가 없으면 빈 페이지를 반환한다.")
        void searchUsers_Empty() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<User> emptyPage = new PageImpl<>(List.of(), pageable, 0);

            given(userRepository.findByNicknameContainingAndDeletedAtIsNull("없는닉네임", pageable)).willReturn(emptyPage);

            Page<UserSearchResponse> result = userService.searchUsers("없는닉네임", pageable);

            assertThat(result.getTotalElements()).isEqualTo(0);
            assertThat(result.getContent()).isEmpty();
        }
    }
}
