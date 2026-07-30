package com.scommit.domain.series.series.service

import com.scommit.domain.post.post.repository.PostRepository
import com.scommit.domain.series.series.dto.SeriesListResponse
import com.scommit.domain.series.series.entity.Series
import com.scommit.domain.series.series.repository.SeriesRepository
import com.scommit.domain.user.user.entity.User
import com.scommit.domain.user.user.entity.UserRole
import com.scommit.domain.user.user.repository.UserRepository
import com.scommit.global.exception.BusinessException
import com.scommit.global.exception.ErrorCode
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.BDDMockito.given
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.SliceImpl
import org.springframework.data.domain.Sort
import org.springframework.test.util.ReflectionTestUtils
import java.util.Optional

@Suppress("UNCHECKED_CAST")
private fun <T> anyKotlin(type: Class<T>): T {
    Mockito.any(type)
    return createMockInstance(type)
}

@Suppress("UNCHECKED_CAST")
private fun <T> createMockInstance(type: Class<T>): T =
    when {
        type == String::class.java -> "" as T
        Pageable::class.java.isAssignableFrom(type) -> PageRequest.of(0, 10) as T
        else -> null as T
    }

private fun <T> eqKotlin(value: T): T {
    eq(value)
    return value
}

@ExtendWith(MockitoExtension::class)
class SeriesServiceTest {
    @Mock
    private lateinit var seriesRepository: SeriesRepository

    @Mock
    private lateinit var userRepository: UserRepository

    @Mock
    private lateinit var postRepository: PostRepository

    @InjectMocks
    private lateinit var seriesService: SeriesService

    private lateinit var mockUser: User

    @BeforeEach
    fun setUp() {
        mockUser =
            User
                .builder()
                .email("test@example.com")
                .nickname("테스터")
                .role(UserRole.USER)
                .build()
        ReflectionTestUtils.setField(mockUser, "id", 1L)
    }

    private fun buildSeries(
        id: Long,
        title: String,
        body: String,
    ): Series {
        val series =
            Series
                .builder()
                .user(mockUser)
                .title(title)
                .body(body)
                .build()
        ReflectionTestUtils.setField(series, "id", id)
        return series
    }

    private fun buildListResponse(
        id: Long,
        title: String,
    ): SeriesListResponse = SeriesListResponse(id, mockUser.id!!, mockUser.nickname, title, "내용", 3L, null, null, null)

    @Nested
    @DisplayName("시리즈 생성 테스트")
    inner class CreateSeries {
        @Test
        @DisplayName("성공: 올바른 요청인 경우 시리즈를 정상 저장한다.")
        fun create_Success() {
            val series = buildSeries(100L, "시리즈 제목", "시리즈 설명")

            given(userRepository.findById(1L)).willReturn(Optional.of(mockUser))
            given(seriesRepository.save(org.mockito.ArgumentMatchers.any(Series::class.java))).willReturn(series)

            val result = seriesService.createSeries("시리즈 제목", "시리즈 설명", 1L)

            assertThat(result).isNotNull
            assertThat(result.id).isEqualTo(100L)
            assertThat(result.title).isEqualTo("시리즈 제목")
            assertThat(result.userId).isEqualTo(1L)
            verify(seriesRepository, times(1)).save(org.mockito.ArgumentMatchers.any(Series::class.java))
        }

        @Test
        @DisplayName("실패: 유저가 존재하지 않으면 USER_NOT_FOUND 예외를 던진다.")
        fun create_UserNotFound() {
            given(userRepository.findById(999L)).willReturn(Optional.empty())

            assertThatThrownBy { seriesService.createSeries("시리즈 제목", "시리즈 설명", 999L) }
                .isInstanceOf(BusinessException::class.java)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND)

            verify(seriesRepository, never()).save(anyKotlin(Series::class.java))
        }
    }

    @Nested
    @DisplayName("시리즈 전체 조회 테스트 (무한 스크롤)")
    inner class FindAllSeries {
        @Test
        @DisplayName("성공: 삭제되지 않은 전체 시리즈를 postCount 포함 Slice로 반환한다.")
        fun findAll_Slice() {
            val list = listOf(buildListResponse(1L, "제목1"))
            val slice = SliceImpl(list)
            given(seriesRepository.findAllWithPostCount(anyKotlin(Pageable::class.java))).willReturn(slice)

            val pageable: Pageable = PageRequest.of(0, 10, Sort.by("id").descending())
            val result = seriesService.getSeriesSlice(pageable)

            assertThat(result.content).hasSize(1)
            assertThat(result.content.first().title).isEqualTo("제목1")
            verify(seriesRepository, times(1)).findAllWithPostCount(anyKotlin(Pageable::class.java))
        }
    }

    @Nested
    @DisplayName("유저별 시리즈 조회 테스트")
    inner class FindSeriesByUser {
        @Test
        @DisplayName("성공: creatorId로 해당 유저의 시리즈를 postCount 포함 Page로 반환한다.")
        fun findByCreatorId() {
            val page = PageImpl(listOf(buildListResponse(1L, "제목1")))
            given(seriesRepository.findByUserIdWithPostCount(eq(1L), anyKotlin(Pageable::class.java))).willReturn(page)

            val pageable: Pageable = PageRequest.of(0, 10, Sort.by("id").descending())
            val result = seriesService.getSeriesList(1L, pageable)

            assertThat(result.content).hasSize(1)
            assertThat(result.content.first().postCount).isEqualTo(3L)
            verify(seriesRepository, times(1)).findByUserIdWithPostCount(eq(1L), anyKotlin(Pageable::class.java))
        }
    }

    @Nested
    @DisplayName("시리즈 제목 검색 테스트")
    inner class SearchSeries {
        @Test
        @DisplayName("성공: 키워드가 포함된 제목의 시리즈를 postCount 포함 Page로 반환한다.")
        fun search_Success() {
            val page = PageImpl(listOf(buildListResponse(1L, "Spring 입문")))
            given(
                seriesRepository.findByTitleContainingWithPostCount(
                    eqKotlin("Spring"),
                    anyKotlin(Pageable::class.java),
                ),
            ).willReturn(page)

            val pageable: Pageable = PageRequest.of(0, 10, Sort.by("id").descending())
            val result = seriesService.searchSeries("Spring", pageable)

            assertThat(result.content).hasSize(1)
            assertThat(result.content.first().title).isEqualTo("Spring 입문")
            verify(seriesRepository, times(1)).findByTitleContainingWithPostCount(
                eqKotlin("Spring"),
                anyKotlin(Pageable::class.java),
            )
        }

        @Test
        @DisplayName("성공: 키워드에 매칭되는 시리즈가 없으면 빈 Page를 반환한다.")
        fun search_Empty() {
            given(
                seriesRepository.findByTitleContainingWithPostCount(
                    eqKotlin("없는키워드"),
                    anyKotlin(Pageable::class.java),
                ),
            ).willReturn(PageImpl(listOf()))

            val pageable: Pageable = PageRequest.of(0, 10, Sort.by("id").descending())
            val result = seriesService.searchSeries("없는키워드", pageable)

            assertThat(result.content).isEmpty()
        }

        @Test
        @DisplayName("실패: 키워드가 빈 문자열이면 INVALID_INPUT_VALUE 예외를 던진다.")
        fun search_BlankKeyword() {
            assertThatThrownBy { seriesService.searchSeries("", PageRequest.of(0, 10)) }
                .isInstanceOf(BusinessException::class.java)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE)

            verify(seriesRepository, never()).findByTitleContainingWithPostCount(
                anyKotlin(String::class.java),
                anyKotlin(Pageable::class.java),
            )
        }

        @Test
        @DisplayName("실패: 키워드가 공백만 있으면 INVALID_INPUT_VALUE 예외를 던진다.")
        fun search_WhitespaceKeyword() {
            assertThatThrownBy { seriesService.searchSeries("   ", PageRequest.of(0, 10)) }
                .isInstanceOf(BusinessException::class.java)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE)

            verify(seriesRepository, never()).findByTitleContainingWithPostCount(
                anyKotlin(String::class.java),
                anyKotlin(Pageable::class.java),
            )
        }
    }

    @Nested
    @DisplayName("시리즈 상세 조회 테스트")
    inner class FindSeriesById {
        @Test
        @DisplayName("성공: 삭제되지 않은 시리즈인 경우 데이터를 올바르게 반환한다.")
        fun findById_Success() {
            val series = buildSeries(1L, "제목1", "내용1")
            given(seriesRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(series))

            val result = seriesService.getSeries(1L)

            assertThat(result).isNotNull
            assertThat(result.id).isEqualTo(1L)
            assertThat(result.title).isEqualTo("제목1")
        }

        @Test
        @DisplayName("실패: 존재하지 않는 시리즈 ID인 경우 SERIES_NOT_FOUND 예외를 던진다.")
        fun findById_NotFound() {
            given(seriesRepository.findByIdAndDeletedAtIsNull(999L)).willReturn(Optional.empty())

            assertThatThrownBy { seriesService.getSeries(999L) }
                .isInstanceOf(BusinessException::class.java)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SERIES_NOT_FOUND)
        }

        @Test
        @DisplayName("실패: 삭제된 시리즈인 경우 SERIES_NOT_FOUND 예외를 던진다.")
        fun findById_SoftDeleted() {
            given(seriesRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.empty())

            assertThatThrownBy { seriesService.getSeries(1L) }
                .isInstanceOf(BusinessException::class.java)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SERIES_NOT_FOUND)
        }
    }

    @Nested
    @DisplayName("시리즈 수정 테스트")
    inner class UpdateSeries {
        @Test
        @DisplayName("성공: 활성 상태의 시리즈인 경우 제목과 본문을 업데이트한다.")
        fun update_Success() {
            val series = buildSeries(1L, "기존 제목", "기존 설명")
            given(seriesRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(series))

            val result = seriesService.updateSeries(1L, "수정된 제목", "수정된 설명", 1L, UserRole.USER)

            assertThat(result.title).isEqualTo("수정된 제목")
            assertThat(result.body).isEqualTo("수정된 설명")
        }

        @Test
        @DisplayName("실패: 이미 삭제된 시리즈를 수정하려고 시도하면 SERIES_NOT_FOUND 예외를 던진다.")
        fun update_SoftDeleted() {
            given(seriesRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.empty())

            assertThatThrownBy { seriesService.updateSeries(1L, "수정된 제목", "수정된 설명", 1L, UserRole.USER) }
                .isInstanceOf(BusinessException::class.java)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SERIES_NOT_FOUND)
        }

        @Test
        @DisplayName("실패: 다른 유저의 시리즈를 수정하려 하면 ACCESS_DENIED 예외를 던진다.")
        fun update_Forbidden() {
            val series = buildSeries(1L, "기존 제목", "기존 설명")
            given(seriesRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(series))

            assertThatThrownBy { seriesService.updateSeries(1L, "수정된 제목", "수정된 설명", 99L, UserRole.USER) }
                .isInstanceOf(BusinessException::class.java)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCESS_DENIED)
        }

        @Test
        @DisplayName("성공: 어드민은 타인 시리즈도 수정할 수 있다.")
        fun update_AdminCanUpdateOthers() {
            val series = buildSeries(1L, "기존 제목", "기존 설명")
            given(seriesRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(series))

            val result = seriesService.updateSeries(1L, "수정된 제목", "수정된 설명", 99L, UserRole.ADMIN)

            assertThat(result.title).isEqualTo("수정된 제목")
        }
    }

    @Nested
    @DisplayName("시리즈 삭제 테스트")
    inner class DeleteSeries {
        @Test
        @DisplayName("성공: 활성 상태의 시리즈인 경우 deletedAt 필드를 세팅해 삭제 처리하고, 포스트의 시리즈 참조를 제거한다.")
        fun delete_Success() {
            val series = buildSeries(1L, "시리즈 제목", "시리즈 설명")
            given(seriesRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(series))
            given(postRepository.findBySeriesIdAndDeletedAtIsNull(1L)).willReturn(listOf())

            seriesService.deleteSeries(1L, 1L, UserRole.USER)

            assertThat(series.deletedAt).isNotNull
            verify(postRepository, times(1)).findBySeriesIdAndDeletedAtIsNull(1L)
        }

        @Test
        @DisplayName("실패: 이미 삭제된 시리즈를 삭제하려고 시도하면 SERIES_NOT_FOUND 예외를 던진다.")
        fun delete_AlreadyDeleted() {
            given(seriesRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.empty())

            assertThatThrownBy { seriesService.deleteSeries(1L, 1L, UserRole.USER) }
                .isInstanceOf(BusinessException::class.java)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SERIES_NOT_FOUND)
        }

        @Test
        @DisplayName("실패: 다른 유저의 시리즈를 삭제하려 하면 ACCESS_DENIED 예외를 던진다.")
        fun delete_Forbidden() {
            val series = buildSeries(1L, "시리즈 제목", "시리즈 설명")
            given(seriesRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(series))

            assertThatThrownBy { seriesService.deleteSeries(1L, 99L, UserRole.USER) }
                .isInstanceOf(BusinessException::class.java)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCESS_DENIED)
        }

        @Test
        @DisplayName("성공: 어드민은 타인 시리즈도 삭제할 수 있다.")
        fun delete_AdminCanDeleteOthers() {
            val series = buildSeries(1L, "시리즈 제목", "시리즈 설명")
            given(seriesRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(series))

            seriesService.deleteSeries(1L, 99L, UserRole.ADMIN)

            assertThat(series.deletedAt).isNotNull
        }
    }
}
