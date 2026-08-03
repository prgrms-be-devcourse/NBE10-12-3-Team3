package com.scommit.domain.user.user.controller;

import com.scommit.domain.subscription.subscription.service.SubscriptionService;
import com.scommit.domain.user.user.dto.*;
import com.scommit.domain.user.user.entity.User;
import com.scommit.domain.user.user.service.UserService;
import com.scommit.domain.user.usermedia.dto.UserMediaResponse;
import com.scommit.domain.user.usermedia.service.UserMediaService;
import com.scommit.global.dto.RsData;
import com.scommit.global.exception.BusinessException;
import com.scommit.global.exception.ErrorCode;
import com.scommit.global.security.SecurityHelper;
import com.scommit.global.security.jwt.JwtProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "UserController", description = "API 유저 컨트롤러")
public class UserController {
    private final UserService userService;
    private final JwtProvider jwtProvider;
    private final SecurityHelper securityHelper;
    private final UserMediaService userMediaService;
    private final SubscriptionService subscriptionService;

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "회원가입", description = "신규 유저가 회원가입합니다.")
    public RsData<SignupResponse> signUp(
            @Valid @RequestBody SignupRequest request
    ) {
        User user = userService.signUp(request.email(), request.password(), request.nickname());
        return new RsData<>(
                "201-1",
                "회원가입에 성공했습니다.",
                new SignupResponse(user)
        );
    }

    @PostMapping("/login")
    @Operation(summary = "로그인", description = "이메일과 비밀번호로 로그인합니다.")
    public RsData<LoginResponse> login(
            @Valid @RequestBody LoginRequest request
    ) {
        User user = userService.login(request.email(), request.password());
        String accessToken = jwtProvider.generateAccessToken(user.getId(), user.getEmail(), user.getNickname(), user.getRole());

        securityHelper.setCookie("accessToken", accessToken);
        securityHelper.setCookie("refreshToken", user.getRefreshToken());

        return new RsData<>(
                "200-1",
                "로그인에 성공했습니다.",
                new LoginResponse(accessToken, user.getRefreshToken(), securityHelper.getAccessTokenCookieExpiresInSecond(), user)
        );
    }

    @PostMapping("/logout")
    @Operation(summary = "로그아웃", description = "로그아웃합니다.")
    public RsData<Void> logout() {
        User actor = securityHelper.getActor();
        if (actor == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        securityHelper.deleteCookie("accessToken");
        securityHelper.deleteCookie("refreshToken");
        userService.logout(actor.getId());
        return new RsData<>(
                "200-1",
                "로그아웃에 성공했습니다."
        );
    }

    @DeleteMapping
    @Operation(summary = "회원탈퇴", description = "회원탈퇴합니다.")
    public RsData<Void> deleteAccount(
            @Valid @RequestBody UserDeleteRequest request
    ) {
        User actor = securityHelper.getActor();
        if (actor == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        userService.deleteUser(actor.getId(), request.password());
        securityHelper.deleteCookie("accessToken");
        securityHelper.deleteCookie("refreshToken");
        return new RsData<>(
                "200-1",
                "회원탈퇴에 성공했습니다."
        );
    }

    @GetMapping("/me")
    @Operation(summary = "내 정보 조회", description = "로그인한 유저의 정보를 조회합니다.")
    public RsData<UserMeResponse> getMyInfo() {
        User actor = securityHelper.getActor();
        if (actor == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        User user = userService.getUser(actor.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        UserMediaResponse media = userMediaService.getMedia(actor.getId());
        String profileImageUrl = media != null ? media.getUrl() : null;

        return new RsData<>(
                "200-1",
                "내 정보를 조회하였습니다.",
                new UserMeResponse(user, profileImageUrl)
        );
    }

    @PatchMapping("/me")
    @Operation(summary = "내 정보 수정", description = "로그인한 유저의 정보를 수정합니다.")
    public RsData<UserUpdateResponse> updateProfile(
            @Valid @RequestPart(value = "request") UserUpdateRequest request,
            @RequestPart(value = "profileImage", required = false) MultipartFile profileImage
    ) {
        User actor = securityHelper.getActor();
        if (actor == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        User updatedUser = userService.updateUser(actor.getId(), request.nickname(), request.introduction());
        String profileImageUrl;
        if (profileImage != null) {
            profileImageUrl = userMediaService.uploadMedia(actor.getId(), profileImage).getUrl();
        } else {
            UserMediaResponse media = userMediaService.getMedia(actor.getId());
            profileImageUrl = media != null ? media.getUrl() : null;
        }

        return new RsData<>(
                "200-1",
                "내 정보를 수정하였습니다.",
                new UserUpdateResponse(updatedUser, profileImageUrl)
        );
    }

    @PutMapping("/me/password")
    @Operation(summary = "비밀번호 변경", description = "로그인한 유저의 비밀번호를 변경합니다.")
    public RsData<UserPasswordUpdateResponse> updatePassword(
            @Valid @RequestBody UserPasswordUpdateRequest request
    ) {
        User actor = securityHelper.getActor();
        if (actor == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        userService.updatePassword(actor.getId(), request.currentPassword(), request.newPassword());

        User user = userService.getUser(actor.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        String accessToken = jwtProvider.generateAccessToken(actor.getId(), actor.getEmail(), actor.getNickname(), user.getRole());
        securityHelper.setCookie("accessToken", accessToken);
        securityHelper.setCookie("refreshToken", user.getRefreshToken());
        return new RsData<>(
                "200-1",
                "비밀번호를 변경하였습니다.",
                new UserPasswordUpdateResponse(accessToken, user.getRefreshToken(), securityHelper.getAccessTokenCookieExpiresInSecond())
        );
    }

    @GetMapping("/{id}")
    @Operation(summary = "유저 페이지 조회", description = "특정 유저의 페이지를 조회합니다.")
    public RsData<UserProfileResponse> getUserProfile(
            @PathVariable Long id
    ) {
        User user = userService.getUser(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        UserMediaResponse media = userMediaService.getMedia(id);
        int followerCount = (int) subscriptionService.getFollowerCount(id);
        String profileImageUrl = media != null ? media.getUrl() : null;
        return new RsData<>(
                "200-1",
                "유저 정보를 조회하였습니다.",
                new UserProfileResponse(user, followerCount, profileImageUrl)
        );
    }

    @PostMapping(value = "/me/medias", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "프로필 이미지 생성")
    public RsData<UserMediaResponse> uploadMedia(
            @RequestPart MultipartFile file
    ) {
        User actor = securityHelper.getActor();
        if (actor == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        Long id = actor.getId();
        UserMediaResponse response = userMediaService.uploadMedia(id, file);
        return new RsData<>("201-1", "프로필 이미지를 생성하였습니다.", response);
    }

    @GetMapping("/{id}/medias")
    @Operation(summary = "프로필 이미지 조회")
    public RsData<UserMediaResponse> getMedia(
            @PathVariable Long id
    ) {
        UserMediaResponse response = userMediaService.getMedia(id);
        return new RsData<>("200-1", "프로필 이미지를 조회하였습니다.", response);
    }

    @GetMapping("/search")
    @Operation(summary = "유저 검색", description = "닉네임에 키워드가 포함된 유저를 검색합니다.")
    public RsData<Page<UserSearchResponse>> searchUsers(
            @RequestParam(required = false, defaultValue = "") String keyword,
            @PageableDefault(size = 10, sort = "id") Pageable pageable) {
        if (keyword.isBlank()) {
            return new RsData<>("200-1", "유저 검색 결과입니다.", Page.empty(pageable));
        }
        Page<UserSearchResponse> response = userService.searchUsers(keyword, pageable);
        return new RsData<>("200-1", "유저 검색 결과입니다.", response);
    }

    @DeleteMapping("/me/medias")
    @Operation(summary = "프로필 이미지 삭제")
    public RsData<Void> deleteMedia() {
        User actor = securityHelper.getActor();
        if (actor == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        Long id = actor.getId();
        userMediaService.deleteMedia(id);
        return new RsData<>("200-1", "프로필 이미지가 삭제되었습니다.");
    }
}
