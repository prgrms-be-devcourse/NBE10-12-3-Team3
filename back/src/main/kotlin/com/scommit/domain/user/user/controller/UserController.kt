package com.scommit.domain.user.user.controller

import com.scommit.domain.subscription.subscription.service.SubscriptionService
import com.scommit.domain.user.user.dto.LoginRequest
import com.scommit.domain.user.user.dto.LoginResponse
import com.scommit.domain.user.user.dto.SignupRequest
import com.scommit.domain.user.user.dto.SignupResponse
import com.scommit.domain.user.user.dto.UserDeleteRequest
import com.scommit.domain.user.user.dto.UserMeResponse
import com.scommit.domain.user.user.dto.UserPasswordUpdateRequest
import com.scommit.domain.user.user.dto.UserPasswordUpdateResponse
import com.scommit.domain.user.user.dto.UserProfileResponse
import com.scommit.domain.user.user.dto.UserSearchResponse
import com.scommit.domain.user.user.dto.UserUpdateRequest
import com.scommit.domain.user.user.dto.UserUpdateResponse
import com.scommit.domain.user.user.service.UserService
import com.scommit.domain.user.usermedia.dto.UserMediaResponse
import com.scommit.domain.user.usermedia.service.UserMediaService
import com.scommit.global.dto.RsData
import com.scommit.global.exception.BusinessException
import com.scommit.global.exception.ErrorCode
import com.scommit.global.security.SecurityHelper
import com.scommit.global.security.jwt.JwtProvider
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/api/users")
@Tag(name = "UserController", description = "API 유저 컨트롤러")
@Suppress("TooManyFunctions")
class UserController(
    private val userService: UserService,
    private val jwtProvider: JwtProvider,
    private val securityHelper: SecurityHelper,
    private val userMediaService: UserMediaService,
    private val subscriptionService: SubscriptionService,
) {
    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "회원가입", description = "신규 유저가 회원가입합니다.")
    fun signUp(
        @Valid @RequestBody request: SignupRequest,
    ): RsData<SignupResponse> {
        val user = userService.signUp(request.email, request.password, request.nickname)
        return RsData(
            "201-1",
            "회원가입에 성공했습니다.",
            SignupResponse(user),
        )
    }

    @PostMapping("/login")
    @Operation(summary = "로그인", description = "이메일과 비밀번호로 로그인합니다.")
    fun login(
        @Valid @RequestBody request: LoginRequest,
    ): RsData<LoginResponse> {
        val user = userService.login(request.email, request.password)
        val accessToken = jwtProvider.generateAccessToken(user.id, user.email, user.nickname, user.role)

        securityHelper.setCookie("accessToken", accessToken)
        securityHelper.setCookie("refreshToken", user.refreshToken)

        return RsData(
            "200-1",
            "로그인에 성공했습니다.",
            LoginResponse(accessToken, user.refreshToken, securityHelper.accessTokenCookieExpiresInSecond, user),
        )
    }

    @PostMapping("/logout")
    @Operation(summary = "로그아웃", description = "로그아웃합니다.")
    fun logout(): RsData<Unit> {
        val actor = securityHelper.actor ?: throw BusinessException(ErrorCode.UNAUTHORIZED)
        securityHelper.deleteCookie("accessToken")
        securityHelper.deleteCookie("refreshToken")
        userService.logout(actor.id)
        return RsData("200-1", "로그아웃에 성공했습니다.")
    }

    @DeleteMapping
    @Operation(summary = "회원탈퇴", description = "회원탈퇴합니다.")
    fun deleteAccount(
        @Valid @RequestBody request: UserDeleteRequest,
    ): RsData<Unit> {
        val actor = securityHelper.actor ?: throw BusinessException(ErrorCode.UNAUTHORIZED)
        userService.deleteUser(actor.id, request.password)
        securityHelper.deleteCookie("accessToken")
        securityHelper.deleteCookie("refreshToken")
        return RsData("200-1", "회원탈퇴에 성공했습니다.")
    }

    @GetMapping("/me")
    @Operation(summary = "내 정보 조회", description = "로그인한 유저의 정보를 조회합니다.")
    fun getMyInfo(): RsData<UserMeResponse> {
        val actor = securityHelper.actor ?: throw BusinessException(ErrorCode.UNAUTHORIZED)
        val user = userService.getUser(actor.id) ?: throw BusinessException(ErrorCode.USER_NOT_FOUND)
        val profileImageUrl = userMediaService.getMedia(actor.id)?.url()

        return RsData(
            "200-1",
            "내 정보를 조회하였습니다.",
            UserMeResponse(user, profileImageUrl),
        )
    }

    @PatchMapping("/me")
    @Operation(summary = "내 정보 수정", description = "로그인한 유저의 정보를 수정합니다.")
    fun updateProfile(
        @Valid @RequestPart(value = "request") request: UserUpdateRequest,
        @RequestPart(value = "profileImage", required = false) profileImage: MultipartFile?,
    ): RsData<UserUpdateResponse> {
        val actor = securityHelper.actor ?: throw BusinessException(ErrorCode.UNAUTHORIZED)

        val updatedUser = userService.updateUser(actor.id, request.nickname, request.introduction)
        val profileImageUrl =
            if (profileImage != null) {
                userMediaService.uploadMedia(actor.id, profileImage).url()
            } else {
                userMediaService.getMedia(actor.id)?.url()
            }

        return RsData("200-1", "내 정보를 수정하였습니다.", UserUpdateResponse(updatedUser, profileImageUrl))
    }

    @PutMapping("/me/password")
    @Operation(summary = "비밀번호 변경", description = "로그인한 유저의 비밀번호를 변경합니다.")
    fun updatePassword(
        @Valid @RequestBody request: UserPasswordUpdateRequest,
    ): RsData<UserPasswordUpdateResponse> {
        val actor = securityHelper.actor ?: throw BusinessException(ErrorCode.UNAUTHORIZED)
        userService.updatePassword(actor.id, request.currentPassword, request.newPassword)

        val user = userService.getUser(actor.id) ?: throw BusinessException(ErrorCode.USER_NOT_FOUND)
        val accessToken = jwtProvider.generateAccessToken(actor.id, actor.email, actor.nickname, user.role)
        securityHelper.setCookie("accessToken", accessToken)
        securityHelper.setCookie("refreshToken", user.refreshToken)
        return RsData(
            "200-1",
            "비밀번호를 변경하였습니다.",
            UserPasswordUpdateResponse(
                accessToken,
                requireNotNull(user.refreshToken),
                securityHelper.accessTokenCookieExpiresInSecond,
            ),
        )
    }

    @GetMapping("/{id}")
    @Operation(summary = "유저 페이지 조회", description = "특정 유저의 페이지를 조회합니다.")
    fun getUserProfile(
        @PathVariable id: Long,
    ): RsData<UserProfileResponse> {
        val user = userService.getUser(id) ?: throw BusinessException(ErrorCode.USER_NOT_FOUND)
        val followerCount = subscriptionService.getFollowerCount(id).toInt()
        val profileImageUrl = userMediaService.getMedia(id)?.url()
        return RsData(
            "200-1",
            "유저 정보를 조회하였습니다.",
            UserProfileResponse(user, followerCount, profileImageUrl),
        )
    }

    @GetMapping("/search")
    @Operation(summary = "유저 검색", description = "닉네임에 키워드가 포함된 유저를 검색합니다.")
    fun searchUsers(
        @RequestParam(required = false, defaultValue = "") keyword: String,
        @PageableDefault(size = 10, sort = ["id"]) pageable: Pageable,
    ): RsData<Page<UserSearchResponse>> {
        if (keyword.isBlank()) {
            return RsData("200-1", "유저 검색 결과입니다.", Page.empty(pageable))
        }
        val response = userService.searchUsers(keyword, pageable)
        return RsData("200-1", "유저 검색 결과입니다.", response)
    }

    @PostMapping(value = ["/me/medias"], consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "프로필 이미지 생성")
    fun uploadMedia(
        @RequestPart file: MultipartFile,
    ): RsData<UserMediaResponse> {
        val actor = securityHelper.actor ?: throw BusinessException(ErrorCode.UNAUTHORIZED)
        val response = userMediaService.uploadMedia(actor.id, file)
        return RsData("201-1", "프로필 이미지를 생성하였습니다.", response)
    }

    @GetMapping("/{id}/medias")
    @Operation(summary = "프로필 이미지 조회")
    fun getMedia(
        @PathVariable id: Long,
    ): RsData<UserMediaResponse> {
        val response = userMediaService.getMedia(id)
        return RsData("200-1", "프로필 이미지를 조회하였습니다.", response)
    }

    @DeleteMapping("/me/medias")
    @Operation(summary = "프로필 이미지 삭제")
    fun deleteMedia(): RsData<Unit> {
        val actor = securityHelper.actor ?: throw BusinessException(ErrorCode.UNAUTHORIZED)
        userMediaService.deleteMedia(actor.id)
        return RsData("200-1", "프로필 이미지가 삭제되었습니다.")
    }
}
