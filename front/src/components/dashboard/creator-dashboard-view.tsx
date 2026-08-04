"use client";

import React, { useEffect, useState } from "react";
import { getCreatorDashboard, CreatorDashboard, DashboardPeriod, getPeriodPrefix, getRankingPeriodPrefix } from "@/lib/dashboard-api";
import { ActivityHeatmap } from "./activity-heatmap";
import { ActivityRadarChart } from "./radar-chart";
import { PopularPostsChart } from "./popular-posts-chart";
import { MetricCard } from "./metric-card";
import { FileText, BookOpen, Eye, Heart, Bookmark, Users, Layers, Sparkles, PenTool, Calendar, BarChart2 } from "lucide-react";
import Link from "next/link";
import { formatDecimal } from "@/lib/format";

export function CreatorDashboardView() {
  const [period, setPeriod] = useState<DashboardPeriod>("30d");
  const [data, setData] = useState<CreatorDashboard | null>(null);
  const [isRefreshing, setIsRefreshing] = useState(false);

  useEffect(() => {
    setIsRefreshing(true);
    getCreatorDashboard(period)
      .then(setData)
      .finally(() => setIsRefreshing(false));
  }, [period]);

  // 최초 진입(데이터 없음)일 때만 전체 화면 스켈레톤을 보여준다.
  // 기간 버튼 클릭으로 인한 재조회는 기존 화면을 유지한 채 조용히 갱신한다.
  if (!data) {
    return (
      <div className="py-20 text-center text-neutral-400 font-medium animate-pulse">
        통계 대시보드 데이터를 불러오는 중입니다...
      </div>
    );
  }

  const { metrics, heatmap, radar, topPosts, topSeries } = data;

  // 전체-플로우 모델: 모든 지표가 선택한 기간 기준으로 표시되며, "전체" 선택 시 자연스럽게 lifetime 누적 총계와 같아진다.
  // (예외: 잔디밭·오방진 활동 성향 차트, 유료 멤버십 전환율은 항상 기간 무관 고정)
  const periodPrefix = getPeriodPrefix(period);
  const topSeriesTitle = `${getRankingPeriodPrefix(period)} 인기 시리즈 TOP 5`;

  return (
    <div className="space-y-8">
      {/* Header & Period Controls (글로벌 표준 Segmented Control) */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 border-b border-neutral-200/80 pb-5">
        <div>
          <h2 className="text-xl font-bold text-neutral-900 tracking-tight flex items-center gap-2">
            <BarChart2 className="h-5 w-5 text-emerald-600" /> 크리에이터 지식 성장 대시보드
          </h2>
          <p className="text-xs font-medium text-neutral-500 mt-1">
            내 콘텐츠 트래픽, 유저 반응 지수 및 1년 커밋 이력을 통합 모니터링합니다.
          </p>
        </div>

        {/* Period Selector (macOS / Stripe 스타일 Segmented Control 캡슐) */}
        <div className="flex items-center gap-2">
          {isRefreshing && (
            <div className="h-3.5 w-3.5 rounded-full border-2 border-neutral-300 border-t-emerald-600 animate-spin" />
          )}
          <div className="flex items-center gap-1 rounded-xl border border-neutral-200/80 bg-neutral-100/90 p-1 shadow-xs">
            <Calendar className="h-3.5 w-3.5 text-neutral-500 ml-1.5 mr-0.5" />
            {(["7d", "30d", "all"] as const).map((p) => (
              <button
                key={p}
                onClick={() => setPeriod(p)}
                className={`rounded-lg px-3 py-1 text-xs font-semibold transition-all ${
                  period === p
                    ? "bg-white text-neutral-900 shadow-sm border border-neutral-200/60 font-bold"
                    : "text-neutral-500 hover:text-neutral-900"
                }`}
              >
                {p === "7d" ? "최근 7일" : p === "30d" ? "최근 30일" : "전체"}
              </button>
            ))}
          </div>
        </div>
      </div>

      {/* Empty State Onboarding Banner (포스트 0개 신규 크리에이터 안내) */}
      {metrics.totalPosts === 0 && (
        <div className="rounded-2xl border border-emerald-200 bg-gradient-to-r from-emerald-50 via-teal-50 to-white p-6 shadow-sm flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
          <div className="flex items-center gap-4">
            <div className="p-3 rounded-2xl bg-emerald-600 text-white shadow-sm shrink-0">
              <Sparkles className="h-6 w-6" />
            </div>
            <div>
              <h3 className="text-base font-bold text-neutral-900">
                반갑습니다! 첫 포스트를 작성하고 대시보드 잔디를 심어보세요 🚀
              </h3>
              <p className="text-xs text-neutral-600 mt-1">
                개발 지식을 공유하고 구독자 팬덤을 형성하면 실시간 성장 통계가 이곳에 기록됩니다.
              </p>
            </div>
          </div>
          <Link
            href="/posts/new"
            className="shrink-0 inline-flex items-center gap-2 rounded-xl bg-emerald-600 px-4 py-2.5 text-xs font-bold text-white shadow-sm hover:bg-emerald-700 transition-colors"
          >
            <PenTool className="h-4 w-4" /> 첫 포스트 작성하기
          </Link>
        </div>
      )}

      {/* --- Tier 1: 상단 잔디밭 & 오방진 레이다 차트 (위아래 위치 교환 - GitHub 개발자 감성 최우선배치) --- */}
      <p className="text-[11px] font-medium text-neutral-400">
        ※ 잔디밭·활동 성향 차트는 선택한 기간과 무관하게 항상 최근 1년 기준으로 표시됩니다
      </p>
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-stretch">
        <div className="lg:col-span-8 flex flex-col">
          <ActivityHeatmap data={heatmap} />
        </div>
        <div className="lg:col-span-4 flex flex-col">
          <ActivityRadarChart data={radar} />
        </div>
      </div>

      {/* --- Tier 2: 6-Card Grid (동일 높이 3x2 백색 프리미엄 지표 카드) --- */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <MetricCard
          title={period === "all" ? "전체 누적 팔로워" : `${periodPrefix} 신규 팔로워`}
          value={`${metrics.newFollowersThisPeriod}명`}
          subValue={`유료 멤버십 ${metrics.paidMembershipCount}명 (${formatDecimal(metrics.membershipConversionRate)}%)`}
          badgeText="팬덤"
          badgeType="default"
          colorVariant="teal"
          icon={Users}
          delay={0.05}
          trendText={period === "all" ? undefined : `누적 ${metrics.totalFollowers}명`}
        />

        <MetricCard
          title={period === "all" ? "전체 누적 포스트" : `${periodPrefix} 작성 포스트`}
          value={`${metrics.newPostsThisPeriod}개`}
          subValue={`공개 ${metrics.freePosts}개 · 멤버십 ${metrics.paidPosts}개`}
          badgeText="콘텐츠"
          colorVariant="emerald"
          icon={FileText}
          delay={0.1}
          trendText={period === "all" ? undefined : `누적 ${metrics.totalPosts}개`}
        />

        <MetricCard
          title={period === "all" ? "전체 누적 시리즈" : `${periodPrefix} 구축 시리즈`}
          value={`${metrics.newSeriesThisPeriod}개`}
          subValue={`시리즈당 평균 ${metrics.totalSeries > 0 ? formatDecimal(metrics.totalPosts / metrics.totalSeries) : "0.0"}개 글`}
          badgeText="시리즈"
          colorVariant="indigo"
          icon={BookOpen}
          delay={0.15}
          trendText={period === "all" ? undefined : `누적 ${metrics.totalSeries}개`}
        />

        <MetricCard
          title={period === "all" ? "전체 누적 조회수" : `${periodPrefix} 조회수`}
          value={`${metrics.viewsThisPeriod.toLocaleString()}회`}
          subValue={`포스트당 평균 ${formatDecimal(metrics.avgViewsPerPost)}회`}
          badgeText="트래픽"
          colorVariant="blue"
          icon={Eye}
          delay={0.2}
          trendText={period === "all" ? undefined : `누적 ${metrics.totalViews.toLocaleString()}회`}
        />

        <MetricCard
          title={period === "all" ? "전체 누적 좋아요" : `${periodPrefix} 좋아요`}
          value={`${metrics.likesThisPeriod.toLocaleString()}개`}
          subValue={`포스트당 평균 ${formatDecimal(metrics.avgLikesPerPost)}개`}
          badgeText="공감 지수"
          colorVariant="rose"
          icon={Heart}
          delay={0.25}
          trendText={period === "all" ? undefined : `누적 ${metrics.totalLikes.toLocaleString()}개`}
        />

        <MetricCard
          title={period === "all" ? "전체 누적 북마크" : `${periodPrefix} 북마크`}
          value={`${metrics.bookmarksThisPeriod.toLocaleString()}개`}
          subValue={`포스트당 평균 ${formatDecimal(metrics.avgBookmarksPerPost)}개`}
          badgeText="소장 지수"
          colorVariant="amber"
          icon={Bookmark}
          delay={0.3}
          trendText={period === "all" ? undefined : `누적 ${metrics.totalBookmarks.toLocaleString()}개`}
        />
      </div>

      {/* --- Tier 3: 2-column Half-Width Grid (관리자 대시보드와 100% 동일한 50% 2컬럼 레이아웃) --- */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 items-stretch">
        {/* Popular Posts Card (50% Column - 좁고 정갈한 컴팩트 스타일) */}
        <PopularPostsChart posts={topPosts} period={period} />

        {/* Series Top 3 Companion Card (50% Column - 정갈한 대칭 레이아웃) */}
        <div className="rounded-2xl border border-neutral-200/80 bg-white p-6 shadow-sm flex flex-col justify-between h-full">
          <div>
            <div className="flex items-center justify-between mb-4">
              <div>
                <h3 className="text-base font-bold text-neutral-900 flex items-center gap-2">
                  <Layers className="h-5 w-5 text-emerald-600" /> {topSeriesTitle}
                </h3>
                <p className="text-xs font-medium text-neutral-500 mt-0.5">
                  {period === "all" ? "전체 기간 누적 시리즈별 포스트 구성 및 조회 반응" : `${periodPrefix} 기준 시리즈별 포스트 구성 및 조회 반응`}
                </p>
              </div>
              <span className="rounded-md bg-neutral-100 px-2.5 py-1 text-xs font-semibold text-neutral-700">
                시리즈 랭킹
              </span>
            </div>

            <div className="space-y-2.5">
              {topSeries.slice(0, 5).map((series, idx) => (
                <div
                  key={series.id}
                  className="flex items-center justify-between p-3 rounded-xl bg-neutral-50 hover:bg-neutral-100/80 transition-colors"
                >
                  <div className="flex items-center gap-3 min-w-0 flex-1">
                    <span
                      className={`text-sm font-bold w-4 text-center shrink-0 ${
                        idx === 0
                          ? "text-emerald-700 font-extrabold"
                          : idx === 1
                          ? "text-neutral-800"
                          : "text-neutral-400"
                      }`}
                    >
                      {idx + 1}
                    </span>
                    <div className="min-w-0 flex-1">
                      <Link
                        href={`/series/${series.id}`}
                        className="font-bold text-sm text-neutral-900 hover:text-emerald-600 transition-colors truncate block"
                      >
                        {series.title}
                      </Link>
                    </div>
                  </div>

                  <div className="text-right shrink-0 ml-3 flex items-center gap-4 text-xs font-medium text-neutral-600">
                    <span className="text-neutral-500">{series.postCount}개 포스트</span>
                    <span className="flex items-center gap-1 text-neutral-800 font-bold">
                      <Eye className="h-3.5 w-3.5 text-neutral-400" />
                      {series.viewCount.toLocaleString()}회
                    </span>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
