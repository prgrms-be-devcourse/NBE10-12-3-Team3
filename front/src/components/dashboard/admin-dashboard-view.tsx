"use client";

import React, { useEffect, useState } from "react";
import { getAdminDashboard, AdminDashboard, DashboardPeriod, getPeriodPrefix, getRankingPeriodPrefix } from "@/lib/dashboard-api";
import { MetricCard } from "./metric-card";
import { AreaChart } from "./area-chart";
import { DonutChart } from "./donut-chart";
import { PopularPostsChart } from "./popular-posts-chart";
import { Avatar } from "@/components/ui/avatar";
import { Users, FileText, BookOpen, Eye, Award, TrendingUp, Calendar } from "lucide-react";
import Link from "next/link";
import { formatDecimal } from "@/lib/format";

export function AdminDashboardView() {
  const [period, setPeriod] = useState<DashboardPeriod>("30d");
  const [data, setData] = useState<AdminDashboard | null>(null);
  const [isRefreshing, setIsRefreshing] = useState(false);

  useEffect(() => {
    setIsRefreshing(true);
    getAdminDashboard(period)
      .then(setData)
      .finally(() => setIsRefreshing(false));
  }, [period]);

  // 최초 진입(데이터 없음)일 때만 전체 화면 스켈레톤을 보여준다.
  // 기간 버튼 클릭으로 인한 재조회는 기존 화면을 유지한 채 조용히 갱신한다.
  if (!data) {
    return (
      <div className="py-20 text-center text-neutral-400 font-medium animate-pulse">
        관리자 통계 대시보드 데이터를 불러오는 중입니다...
      </div>
    );
  }

  const { metrics, signupTrend, subscriptionRatio, superCreators, topPosts } = data;

  // 전체-플로우 모델: 모든 지표가 선택한 기간 기준으로 표시되며, "전체" 선택 시 자연스럽게 lifetime 누적 총계와 같아진다.
  // (예외: 플랫폼 유료 결제 전환율 도넛은 항상 현재 시점 스냅샷 고정)
  const periodPrefix = getPeriodPrefix(period);
  const superCreatorTitle = `${getRankingPeriodPrefix(period)} 인기 창작자 TOP 5`;

  return (
    <div className="space-y-8">
      {/* Header & Period Controls (글로벌 표준 Segmented Control + Calendar Icon) */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 border-b border-neutral-200/80 pb-5">
        <div>
          <h1 className="text-2xl font-bold text-neutral-900 tracking-tight flex items-center gap-2">
            SCommit 플랫폼 관리자 대시보드
          </h1>
          <p className="text-xs font-medium text-neutral-500 mt-1">
            비즈니스 성장 지표, 구독 현황 및 서비스 랭킹 통합 모니터링
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

      {/* --- Tier 1: 4 Metric Cards (총 구축 시리즈를 총 포스트 바로 우측에 배치) --- */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <MetricCard
          title={period === "all" ? "전체 누적 회원" : `${periodPrefix} 신규 가입자`}
          value={metrics.newUsersThisPeriod}
          badgeText="유저 성장"
          badgeType="default"
          icon={Users}
          delay={0.05}
          trendText={period === "all" ? undefined : `누적 ${metrics.totalUsers.toLocaleString()}명`}
        />
        <MetricCard
          title={period === "all" ? "전체 누적 포스트" : `${periodPrefix} 작성 포스트`}
          value={metrics.newPostsThisPeriod}
          subValue={`무료 ${metrics.freePosts} / 유료 ${metrics.paidPosts}`}
          badgeText="콘텐츠"
          badgeType="default"
          icon={FileText}
          delay={0.1}
          trendText={period === "all" ? undefined : `누적 ${metrics.totalPosts.toLocaleString()}개`}
        />
        <MetricCard
          title={period === "all" ? "전체 누적 시리즈" : `${periodPrefix} 구축 시리즈`}
          value={`${metrics.newSeriesThisPeriod}개`}
          subValue={`시리즈당 평균 ${formatDecimal(metrics.avgPostsPerSeries)}개 글`}
          badgeText="시리즈"
          badgeType="default"
          icon={BookOpen}
          delay={0.15}
          trendText={period === "all" ? undefined : `누적 ${metrics.totalSeries.toLocaleString()}개`}
        />
        <MetricCard
          title={period === "all" ? "전체 누적 조회수" : `${periodPrefix} 조회수`}
          value={metrics.viewsThisPeriod}
          subValue={`포스트당 평균 ${formatDecimal(metrics.avgViewsPerPost)}회`}
          badgeText="전체 트래픽"
          badgeType="default"
          icon={Eye}
          delay={0.2}
          trendText={period === "all" ? undefined : `누적 ${metrics.totalViews.toLocaleString()}회`}
        />
      </div>

      {/* --- Tier 2: 2-column Grid (Area Chart + Donut Chart) --- */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-stretch">
        <div className="lg:col-span-7 flex flex-col">
          <AreaChart data={signupTrend} period={period} />
        </div>
        <div className="lg:col-span-5 flex flex-col">
          <DonutChart data={subscriptionRatio} />
        </div>
      </div>

      {/* --- Tier 3: 2-column Grid (Super Creators + Platform Top Posts) --- */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 items-stretch">
        {/* Super Creators Card */}
        <div className="rounded-2xl border border-neutral-200/80 bg-white p-6 shadow-sm flex flex-col justify-between h-full">
          <div>
            <div className="flex items-center justify-between mb-4">
              <div>
                <h3 className="text-base font-bold text-neutral-900 flex items-center gap-2">
                  <Award className="h-5 w-5 text-emerald-600" /> {superCreatorTitle}
                </h3>
                <p className="text-xs font-medium text-neutral-500 mt-0.5">
                  최근 팔로워 급증 기준 창작자 랭킹
                </p>
              </div>
              <span className="rounded-md bg-neutral-100 px-2.5 py-1 text-xs font-semibold text-neutral-700">
                창작자 랭킹
              </span>
            </div>

            <div className="space-y-2.5">
              {superCreators.map((creator, idx) => (
                <div
                  key={creator.id}
                  className="flex items-center justify-between p-3 rounded-xl bg-neutral-50 hover:bg-neutral-100/80 transition-colors"
                >
                  <div className="flex items-center gap-3 min-w-0">
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
                    <Avatar
                      src={creator.profileImageUrl}
                      name={creator.nickname}
                      className="h-9 w-9 border border-neutral-200 shrink-0"
                    />
                    <div className="min-w-0">
                      <Link
                        href={`/users/${creator.id}`}
                        className="font-bold text-sm text-neutral-900 hover:text-emerald-600 transition-colors truncate block"
                      >
                        {creator.nickname}
                      </Link>
                      <p className="text-xs text-neutral-500 truncate">{creator.introduction}</p>
                    </div>
                  </div>

                  <div className="text-right shrink-0">
                    <div className="text-xs font-bold text-emerald-700 flex items-center justify-end gap-0.5">
                      <TrendingUp className="h-3 w-3" /> +{creator.followerIncrease}명
                    </div>
                    <div className="text-[11px] font-medium text-neutral-500">
                      구독자 {creator.subscriberCount}명
                    </div>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>

        {/* Platform Top Posts Card (크리에이터 대시보드와 동일한 공용 컴포넌트로 규격 통일) */}
        <PopularPostsChart posts={topPosts} period={period} />
      </div>
    </div>
  );
}
