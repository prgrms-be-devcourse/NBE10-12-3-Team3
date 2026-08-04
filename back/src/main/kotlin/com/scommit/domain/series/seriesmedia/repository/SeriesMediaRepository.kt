package com.scommit.domain.series.seriesmedia.repository

import com.scommit.domain.series.series.entity.Series
import com.scommit.domain.series.seriesmedia.entity.SeriesMedia
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface SeriesMediaRepository : JpaRepository<SeriesMedia, Long> {
    fun findBySeries(series: Series): SeriesMedia?
}
