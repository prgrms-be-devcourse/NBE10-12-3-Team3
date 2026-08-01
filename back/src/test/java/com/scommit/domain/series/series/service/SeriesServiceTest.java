package com.scommit.domain.series.series.service;

import com.scommit.domain.post.post.repository.PostRepository;
import com.scommit.domain.series.series.dto.SeriesListResponse;
import com.scommit.domain.series.series.dto.SeriesResponse;
import com.scommit.domain.series.series.entity.Series;
import com.scommit.domain.series.series.repository.SeriesRepository;
import com.scommit.domain.user.user.entity.User;
import com.scommit.domain.user.user.entity.UserRole;
import com.scommit.domain.user.user.repository.UserRepository;
import com.scommit.global.exception.BusinessException;
import com.scommit.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SeriesServiceTest {

    @Mock
    private SeriesRepository seriesRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PostRepository postRepository;

    @InjectMocks
    private SeriesService seriesService;

    private User mockUser;

    @BeforeEach
    void setUp() {
        mockUser = new User("test@example.com", null, "테스터", null, UserRole.USER);
        ReflectionTestUtils.setField(mockUser, "id", 1L);
    }

    private Series buildSeries(Long id, String title, String body) {
        Series series = Series.builder()
                .user(mockUser)
                .title(title)
                .body(body)
                .build();
        ReflectionTestUtils.setField(series, "id", id);
        return series;
    }

    private SeriesListResponse buildListResponse(Long id, String title) {
        return new SeriesListResponse(id, mockUser.getId(), mockUser.getNickname(), title, "내용", 3L, null, null, null);
    }

    @Nested
    @DisplayName("시리즈 생성 테스트")
    class CreateSeries {

        @Test
        @DisplayName("성공: 올바른 요청인 경우 시리즈를 정상 저장한다.")
        void create_Success() {
            Series series = buildSeries(100L, "시리즈 제목", "시리즈 설명");

            when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
            when(seriesRepository.save(any(Series.class))).thenReturn(series);

            SeriesResponse result = seriesService.createSeries("시리즈 제목", "시리즈 설명", 1L);

            assertThat(result).isNotNull();
            assertThat(result.id()).isEqualTo(100L);
            assertThat(result.title()).isEqualTo("시리즈 제목");
            assertThat(result.userId()).isEqualTo(1L);
            verify(seriesRepository, times(1)).save(any(Series.class));
        }

        @Test
        @DisplayName("실패: 유저가 존재하지 않으면 USER_NOT_FOUND 예외를 던진다.")
        void create_UserNotFound() {
            when(userRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> seriesService.createSeries("시리즈 제목", "시리즈 설명", 999L))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);

            verify(seriesRepository, never()).save(any(Series.class));
        }
    }

    @Nested
    @DisplayName("시리즈 전체 조회 테스트 (무한 스크롤)")
    class FindAllSeries {

        @Test
        @DisplayName("성공: 삭제되지 않은 전체 시리즈를 postCount 포함 Slice로 반환한다.")
        void findAll_Slice() {
            List<SeriesListResponse> list = List.of(buildListResponse(1L, "제목1"));
            Slice<SeriesListResponse> slice = new SliceImpl<>(list);
            when(seriesRepository.findAllWithPostCount(any(Pageable.class))).thenReturn(slice);

            Pageable pageable = PageRequest.of(0, 10, Sort.by("id").descending());
            Slice<SeriesListResponse> result = seriesService.getSeriesSlice(pageable);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().getFirst().title()).isEqualTo("제목1");
            verify(seriesRepository, times(1)).findAllWithPostCount(any(Pageable.class));
        }
    }

    @Nested
    @DisplayName("유저별 시리즈 조회 테스트")
    class FindSeriesByUser {

        @Test
        @DisplayName("성공: creatorId로 해당 유저의 시리즈를 postCount 포함 Page로 반환한다.")
        void findByCreatorId() {
            Page<SeriesListResponse> page = new PageImpl<>(List.of(buildListResponse(1L, "제목1")));
            when(seriesRepository.findByUserIdWithPostCount(eq(1L), any(Pageable.class))).thenReturn(page);

            Pageable pageable = PageRequest.of(0, 10, Sort.by("id").descending());
            Page<SeriesListResponse> result = seriesService.getSeriesList(1L, pageable);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().getFirst().postCount()).isEqualTo(3L);
            verify(seriesRepository, times(1)).findByUserIdWithPostCount(eq(1L), any(Pageable.class));
        }
    }

    @Nested
    @DisplayName("시리즈 제목 검색 테스트")
    class SearchSeries {

        @Test
        @DisplayName("성공: 키워드가 포함된 제목의 시리즈를 postCount 포함 Page로 반환한다.")
        void search_Success() {
            Page<SeriesListResponse> page = new PageImpl<>(List.of(buildListResponse(1L, "Spring 입문")));
            when(seriesRepository.findByTitleContainingWithPostCount(eq("Spring"), any(Pageable.class))).thenReturn(page);

            Pageable pageable = PageRequest.of(0, 10, Sort.by("id").descending());
            Page<SeriesListResponse> result = seriesService.searchSeries("Spring", pageable);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().getFirst().title()).isEqualTo("Spring 입문");
            verify(seriesRepository, times(1)).findByTitleContainingWithPostCount(eq("Spring"), any(Pageable.class));
        }

        @Test
        @DisplayName("성공: 키워드에 매칭되는 시리즈가 없으면 빈 Page를 반환한다.")
        void search_Empty() {
            when(seriesRepository.findByTitleContainingWithPostCount(eq("없는키워드"), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of()));

            Pageable pageable = PageRequest.of(0, 10, Sort.by("id").descending());
            Page<SeriesListResponse> result = seriesService.searchSeries("없는키워드", pageable);

            assertThat(result.getContent()).isEmpty();
        }

        @Test
        @DisplayName("실패: 키워드가 빈 문자열이면 INVALID_INPUT_VALUE 예외를 던진다.")
        void search_BlankKeyword() {
            assertThatThrownBy(() -> seriesService.searchSeries("", PageRequest.of(0, 10)))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);

            verify(seriesRepository, never()).findByTitleContainingWithPostCount(any(), any());
        }

        @Test
        @DisplayName("실패: 키워드가 공백만 있으면 INVALID_INPUT_VALUE 예외를 던진다.")
        void search_WhitespaceKeyword() {
            assertThatThrownBy(() -> seriesService.searchSeries("   ", PageRequest.of(0, 10)))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);

            verify(seriesRepository, never()).findByTitleContainingWithPostCount(any(), any());
        }
    }

    @Nested
    @DisplayName("시리즈 상세 조회 테스트")
    class FindSeriesById {

        @Test
        @DisplayName("성공: 삭제되지 않은 시리즈인 경우 데이터를 올바르게 반환한다.")
        void findById_Success() {
            Series series = buildSeries(1L, "제목1", "내용1");
            when(seriesRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(series));

            SeriesResponse result = seriesService.getSeries(1L);

            assertThat(result).isNotNull();
            assertThat(result.id()).isEqualTo(1L);
            assertThat(result.title()).isEqualTo("제목1");
        }

        @Test
        @DisplayName("실패: 존재하지 않는 시리즈 ID인 경우 SERIES_NOT_FOUND 예외를 던진다.")
        void findById_NotFound() {
            when(seriesRepository.findByIdAndDeletedAtIsNull(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> seriesService.getSeries(999L))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SERIES_NOT_FOUND);
        }

        @Test
        @DisplayName("실패: 삭제된 시리즈인 경우 SERIES_NOT_FOUND 예외를 던진다.")
        void findById_SoftDeleted() {
            when(seriesRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> seriesService.getSeries(1L))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SERIES_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("시리즈 수정 테스트")
    class UpdateSeries {

        @Test
        @DisplayName("성공: 활성 상태의 시리즈인 경우 제목과 본문을 업데이트한다.")
        void update_Success() {
            Series series = buildSeries(1L, "기존 제목", "기존 설명");
            when(seriesRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(series));

            SeriesResponse result = seriesService.updateSeries(1L, "수정된 제목", "수정된 설명", 1L, UserRole.USER);

            assertThat(result.title()).isEqualTo("수정된 제목");
            assertThat(result.body()).isEqualTo("수정된 설명");
        }

        @Test
        @DisplayName("실패: 이미 삭제된 시리즈를 수정하려고 시도하면 SERIES_NOT_FOUND 예외를 던진다.")
        void update_SoftDeleted() {
            when(seriesRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> seriesService.updateSeries(1L, "수정된 제목", "수정된 설명", 1L, UserRole.USER))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SERIES_NOT_FOUND);
        }

        @Test
        @DisplayName("실패: 다른 유저의 시리즈를 수정하려 하면 ACCESS_DENIED 예외를 던진다.")
        void update_Forbidden() {
            Series series = buildSeries(1L, "기존 제목", "기존 설명");
            when(seriesRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(series));

            assertThatThrownBy(() -> seriesService.updateSeries(1L, "수정된 제목", "수정된 설명", 99L, UserRole.USER))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCESS_DENIED);
        }

        @Test
        @DisplayName("성공: 어드민은 타인 시리즈도 수정할 수 있다.")
        void update_AdminCanUpdateOthers() {
            Series series = buildSeries(1L, "기존 제목", "기존 설명");
            when(seriesRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(series));

            SeriesResponse result = seriesService.updateSeries(1L, "수정된 제목", "수정된 설명", 99L, UserRole.ADMIN);

            assertThat(result.title()).isEqualTo("수정된 제목");
        }
    }

    @Nested
    @DisplayName("시리즈 삭제 테스트")
    class DeleteSeries {

        @Test
        @DisplayName("성공: 활성 상태의 시리즈인 경우 deletedAt 필드를 세팅해 삭제 처리하고, 포스트의 시리즈 참조를 제거한다.")
        void delete_Success() {
            Series series = buildSeries(1L, "시리즈 제목", "시리즈 설명");
            when(seriesRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(series));
            when(postRepository.findBySeriesIdAndDeletedAtIsNull(1L)).thenReturn(List.of());

            seriesService.deleteSeries(1L, 1L, UserRole.USER);

            assertThat(series.getDeletedAt()).isNotNull();
            verify(postRepository, times(1)).findBySeriesIdAndDeletedAtIsNull(1L);
        }

        @Test
        @DisplayName("실패: 이미 삭제된 시리즈를 삭제하려고 시도하면 SERIES_NOT_FOUND 예외를 던진다.")
        void delete_AlreadyDeleted() {
            when(seriesRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> seriesService.deleteSeries(1L, 1L, UserRole.USER))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SERIES_NOT_FOUND);
        }

        @Test
        @DisplayName("실패: 다른 유저의 시리즈를 삭제하려 하면 ACCESS_DENIED 예외를 던진다.")
        void delete_Forbidden() {
            Series series = buildSeries(1L, "시리즈 제목", "시리즈 설명");
            when(seriesRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(series));

            assertThatThrownBy(() -> seriesService.deleteSeries(1L, 99L, UserRole.USER))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCESS_DENIED);
        }

        @Test
        @DisplayName("성공: 어드민은 타인 시리즈도 삭제할 수 있다.")
        void delete_AdminCanDeleteOthers() {
            Series series = buildSeries(1L, "시리즈 제목", "시리즈 설명");
            when(seriesRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(series));

            seriesService.deleteSeries(1L, 99L, UserRole.ADMIN);

            assertThat(series.getDeletedAt()).isNotNull();
        }
    }
}
