package com.scommit.domain.series.series.repository

import com.scommit.domain.series.series.dto.SeriesListResponse
import com.scommit.domain.series.series.entity.Series
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
interface SeriesRepository : JpaRepository<Series, Long> {
    fun findByIdAndDeletedAtIsNull(id: Long): Series?

    @Query(
        (
            "SELECT new com.scommit.domain.series.series.dto.SeriesListResponse(" +
                "s.id, s.user.id, s.user.nickname, s.title, s.body, " +
                "(SELECT COUNT(p.id) FROM Post p WHERE p.series = s AND p.deletedAt IS NULL), " +
                "(SELECT m.url FROM SeriesMedia sm JOIN sm.media m WHERE sm.series = s), " +
                "s.createdAt, s.updatedAt) " +
                "FROM Series s WHERE s.deletedAt IS NULL"
        ),
    )
    fun findAllWithPostCount(pageable: Pageable): Slice<SeriesListResponse>

    @Query(
        (
            "SELECT new com.scommit.domain.series.series.dto.SeriesListResponse(" +
                "s.id, s.user.id, s.user.nickname, s.title, s.body, " +
                "(SELECT COUNT(p.id) FROM Post p WHERE p.series = s AND p.deletedAt IS NULL), " +
                "(SELECT m.url FROM SeriesMedia sm JOIN sm.media m WHERE sm.series = s), " +
                "s.createdAt, s.updatedAt) " +
                "FROM Series s WHERE s.user.id = :userId AND s.deletedAt IS NULL"
        ),
    )
    fun findByUserIdWithPostCount(
        userId: Long,
        pageable: Pageable,
    ): Page<SeriesListResponse>

    @Query(
        (
            "SELECT new com.scommit.domain.series.series.dto.SeriesListResponse(" +
                "s.id, s.user.id, s.user.nickname, s.title, s.body, " +
                "(SELECT COUNT(p.id) FROM Post p WHERE p.series = s AND p.deletedAt IS NULL), " +
                "(SELECT m.url FROM SeriesMedia sm JOIN sm.media m WHERE sm.series = s), " +
                "s.createdAt, s.updatedAt) " +
                "FROM Series s WHERE s.title LIKE %:keyword% AND s.deletedAt IS NULL"
        ),
    )
    fun findByTitleContainingWithPostCount(
        keyword: String,
        pageable: Pageable,
    ): Page<SeriesListResponse>

    // 대시보드 통계용 쿼리
    fun countByUserIdAndDeletedAtIsNull(userId: Long): Long

    fun countByUserIdAndCreatedAtGreaterThanEqualAndDeletedAtIsNull(
        userId: Long,
        createdAt: LocalDateTime,
    ): Long

    @Query(
        "SELECT s FROM Series s WHERE s.user.id = :userId AND s.createdAt >= :createdAt AND s.deletedAt IS NULL " +
            "ORDER BY (SELECT SUM(p.viewCount) FROM Post p WHERE p.series = s AND p.deletedAt IS NULL) DESC",
    )
    fun findTop5SeriesByUserIdAndPeriod(
        @Param("userId") userId: Long,
        @Param("createdAt") createdAt: LocalDateTime,
        pageable: Pageable,
    ): List<Series>

    // 전체 통계
    fun countByDeletedAtIsNull(): Long

    fun countByCreatedAtGreaterThanEqualAndDeletedAtIsNull(createdAt: LocalDateTime): Long
}
