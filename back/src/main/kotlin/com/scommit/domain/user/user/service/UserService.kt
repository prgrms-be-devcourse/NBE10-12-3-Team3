package com.scommit.domain.user.user.service

import com.scommit.domain.user.user.dto.UserSearchResponse
import com.scommit.domain.user.user.entity.User
import com.scommit.domain.user.user.entity.UserRole
import com.scommit.domain.user.user.repository.UserRepository
import com.scommit.global.exception.BusinessException
import com.scommit.global.exception.ErrorCode
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.findByIdOrNull
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UserService(
    private val passwordEncoder: PasswordEncoder,
    private val userRepository: UserRepository,
) {
    @Transactional
    fun signUp(
        email: String,
        password: String,
        nickname: String,
    ): User {
        if (userRepository.existsByEmail(email)) {
            throw BusinessException(ErrorCode.DUPLICATE_EMAIL)
        }

        if (userRepository.existsByNickname(nickname)) {
            throw BusinessException(ErrorCode.DUPLICATE_NICKNAME)
        }

        val encodedPassword = passwordEncoder.encode(password)
        val user =
            User(
                email = email,
                password = encodedPassword,
                nickname = nickname,
                role = UserRole.USER,
            )
        user.resetRefreshToken()
        return userRepository.save(user)
    }

    fun getUserByRefreshToken(refreshToken: String): User? =
        userRepository.findByRefreshTokenAndDeletedAtIsNull(refreshToken)

    fun login(
        email: String,
        password: String,
    ): User {
        val user =
            userRepository.findByEmailAndDeletedAtIsNull(email)
                ?: throw BusinessException(ErrorCode.INVALID_CREDENTIALS)
        if (!passwordEncoder.matches(password, user.password)) {
            throw BusinessException(ErrorCode.INVALID_CREDENTIALS)
        }
        return user
    }

    @Transactional
    fun logout(id: Long) {
        val user = userRepository.findByIdOrNull(id) ?: throw BusinessException(ErrorCode.USER_NOT_FOUND)
        user.resetRefreshToken()
    }

    @Transactional
    fun deleteUser(
        id: Long,
        password: String,
    ) {
        val user = userRepository.findByIdOrNull(id) ?: throw BusinessException(ErrorCode.USER_NOT_FOUND)
        if (!passwordEncoder.matches(password, user.password)) {
            throw BusinessException(ErrorCode.INVALID_PASSWORD)
        }
        user.resetRefreshToken()
        user.softDelete()
    }

    fun getUser(id: Long): User? = userRepository.findByIdAndDeletedAtIsNull(id)

    fun searchUsers(
        keyword: String,
        pageable: Pageable,
    ): Page<UserSearchResponse> =
        userRepository
            .findByNicknameContainingAndDeletedAtIsNull(keyword, pageable)
            .map { UserSearchResponse(it) }

    @Transactional
    fun updateUser(
        id: Long,
        nickname: String?,
        introduction: String?,
    ): User {
        val user =
            userRepository.findByIdAndDeletedAtIsNull(id)
                ?: throw BusinessException(ErrorCode.USER_NOT_FOUND)
        val newNickname = if (user.nickname == nickname) null else nickname
        if (newNickname != null && userRepository.existsByNickname(newNickname)) {
            throw BusinessException(ErrorCode.DUPLICATE_NICKNAME)
        }
        user.update(newNickname, introduction)
        return user
    }

    @Transactional
    fun updatePassword(
        id: Long,
        oldPassword: String,
        newPassword: String,
    ) {
        val user =
            userRepository.findByIdAndDeletedAtIsNull(id)
                ?: throw BusinessException(ErrorCode.USER_NOT_FOUND)
        if (!passwordEncoder.matches(oldPassword, user.password)) {
            throw BusinessException(ErrorCode.INVALID_PASSWORD)
        }
        val encodedPassword = passwordEncoder.encode(newPassword)
        user.updatePassword(requireNotNull(encodedPassword))
        user.resetRefreshToken()
    }
}
