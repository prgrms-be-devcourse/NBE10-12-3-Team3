import { apiFetch } from "@/lib/api";

export type DashboardPeriod = "7d" | "30d" | "all";

// 기간 선택 문구를 한 곳에서만 관리한다 — "이달의"를 "월간"으로 바꾸는 것처럼
// 표현을 바꿀 일이 생기면 여기 두 함수만 고치면 관리자/크리에이터 대시보드 및
// 인기 포스트 카드 등 이 함수를 쓰는 모든 곳에 동시에 반영된다.
export function getPeriodPrefix(period: DashboardPeriod): string {
  return period === "7d" ? "최근 7일" : period === "30d" ? "최근 30일" : "전체";
}

export function getRankingPeriodPrefix(period: DashboardPeriod): string {
  return period === "7d" ? "이주의" : period === "30d" ? "이달의" : "역대";
}

// Stock(누적 총계) + Delta(선택 기간 내 증가분) 모델:
// total*/avg* 필드는 기간 선택과 무관한 lifetime 누적값(Stock)이고,
// *ThisPeriod 필드만 기간 선택에 따라 달라지는 값(Delta)이다.
export interface CreatorDashboardMetrics {
  totalPosts: number;
  freePosts: number;
  paidPosts: number;
  newPostsThisPeriod: number;
  totalSeries: number;
  newSeriesThisPeriod: number;
  totalViews: number;
  avgViewsPerPost: number;
  viewsThisPeriod: number;
  totalLikes: number;
  avgLikesPerPost: number;
  likesThisPeriod: number;
  totalBookmarks: number;
  avgBookmarksPerPost: number;
  bookmarksThisPeriod: number;
  totalFollowers: number;
  newFollowersThisPeriod: number;
  membershipConversionRate: number; // 유료 멤버십 전환율 (%) — 기간 무관 스냅샷 지표
  paidMembershipCount: number;
}

export interface HeatmapPoint {
  date: string; // YYYY-MM-DD
  count: number;
}

export interface RadarDataPoint {
  label: string;
  value: number; // 0 ~ 100
  fullMark: number;
}

export interface CreatorRadarChart {
  postWriteRate: number;     // 포스트 작성 %
  seriesBuildRate: number;   // 시리즈 구축 %
  commentRate: number;       // 댓글 소통 %
  reactionRate: number;      // 반응(좋아요/북마크) %
  subscriptionRate: number;  // 구독/팔로우 %
}

export interface TopPost {
  id: number;
  title: string;
  accessLevel: "FREE" | "PAID";
  viewCount: number;
  likeCount: number;
  bookmarkCount: number;
  createdAt?: string;
  authorId?: number;
  authorNickname?: string;
}

export interface TopSeries {
  id: number;
  title: string;
  postCount: number;
  viewCount: number;
}

export interface CreatorDashboard {
  metrics: CreatorDashboardMetrics;
  heatmap: HeatmapPoint[];
  radar: CreatorRadarChart;
  topPosts: TopPost[];
  topSeries: TopSeries[];
}

export interface SignupTrendPoint {
  label: string; // 기간에 따라 일/주/월 단위 라벨이 됨. e.g. "7/25", "7월 1주", "2월", ...
  users: number;
}

export interface SubscriptionRatio {
  followCount: number;
  membershipCount: number;
  followPercentage: number;
  membershipPercentage: number;
}

export interface SuperCreator {
  id: number;
  nickname: string;
  subscriberCount: number;
  followerIncrease: number; // 금주/최근 7일/30일 증가 수
  profileImageUrl?: string;
  introduction?: string;
}

export interface AdminDashboard {
  metrics: {
    // Stock(전체 누적, 기간 무관) + Delta(*ThisWeek/*ThisPeriod, 선택 기간 내 증가분) 모델
    totalUsers: number;
    newUsersThisPeriod: number;
    totalPosts: number;
    freePosts: number;
    paidPosts: number;
    newPostsThisPeriod: number;
    totalSeries: number;
    avgPostsPerSeries: number;
    newSeriesThisPeriod: number;
    totalViews: number;
    avgViewsPerPost: number;
    viewsThisPeriod: number;
  };
  signupTrend: SignupTrendPoint[];
  subscriptionRatio: SubscriptionRatio;
  superCreators: SuperCreator[];
  topPosts: TopPost[];
}

// --- Mock Data Generators (정확히 1년 = 52주 x 7일 = 364일 잔디 데이터) ---
function generateMockHeatmap(): HeatmapPoint[] {
  const result: HeatmapPoint[] = [];
  const today = new Date();
  for (let i = 363; i >= 0; i--) {
    const d = new Date(today);
    d.setDate(d.getDate() - i);
    const dateStr = d.toISOString().split("T")[0];

    const dayOfWeek = d.getDay();
    let count = 0;
    const rand = Math.random();
    if (dayOfWeek !== 0 && dayOfWeek !== 6) {
      if (rand > 0.45) count = Math.floor(Math.random() * 3) + 1;
      if (rand > 0.85) count = Math.floor(Math.random() * 5) + 3;
    } else {
      if (rand > 0.75) count = Math.floor(Math.random() * 2) + 1;
    }

    result.push({ date: dateStr, count });
  }
  return result;
}

// 기간(7일/30일/전체)에 따라 상단 지표·인기 포스트 수치만 달라짐 — 잔디밭/오방진은 항상 최근 1년 롤링 윈도우 기준이라 기간 선택과 무관
const CREATOR_DASHBOARD_HEATMAP = generateMockHeatmap();
const CREATOR_DASHBOARD_RADAR = {
  postWriteRate: 35,
  seriesBuildRate: 15,
  commentRate: 20,
  reactionRate: 20,
  subscriptionRate: 10,
};

// Stock 필드(총 작성 포스트/시리즈/조회수/좋아요/북마크/구독자, 유료 전환 지표)는
// lifetime 누적값이라 기간 선택과 무관하게 세 목업이 전부 동일하고,
// *ThisPeriod 필드와 topPosts/topSeries 랭킹만 기간별로 달라진다.
const CREATOR_DASHBOARD_STOCK = {
  totalPosts: 52,
  freePosts: 41,
  paidPosts: 11,
  totalSeries: 7,
  totalViews: 38200,
  avgViewsPerPost: 735,
  totalLikes: 2480,
  avgLikesPerPost: 47.7,
  totalBookmarks: 960,
  avgBookmarksPerPost: 18.5,
  totalFollowers: 184,
  membershipConversionRate: 14.2,
  paidMembershipCount: 26,
};

export const MOCK_CREATOR_DASHBOARD_7D: CreatorDashboard = {
  metrics: {
    ...CREATOR_DASHBOARD_STOCK,
    newPostsThisPeriod: 1,
    newSeriesThisPeriod: 0,
    viewsThisPeriod: 620,
    likesThisPeriod: 48,
    bookmarksThisPeriod: 21,
    newFollowersThisPeriod: 6,
  },
  heatmap: CREATOR_DASHBOARD_HEATMAP,
  radar: CREATOR_DASHBOARD_RADAR,
  topPosts: [
    { id: 101, title: "Next.js 16과 React 19로 대규모 실무 서비스 구축하기", accessLevel: "PAID", viewCount: 620, likeCount: 48, bookmarkCount: 21, createdAt: "2026-07-20" },
    { id: 102, title: "스프링 부트 3.3 JPA N+1 문제 완벽 해결 가이드", accessLevel: "FREE", viewCount: 480, likeCount: 35, bookmarkCount: 16, createdAt: "2026-07-15" },
    { id: 103, title: "실무에서 바로 쓰는 Docker & Kubernetes CI/CD 파이프라인", accessLevel: "PAID", viewCount: 390, likeCount: 27, bookmarkCount: 12, createdAt: "2026-07-10" },
    { id: 104, title: "개발자를 위한 커리어 이직 가이드 및 연봉 협상 팁", accessLevel: "FREE", viewCount: 310, likeCount: 21, bookmarkCount: 6, createdAt: "2026-07-02" },
    { id: 105, title: "TypeScript 5.x 고급 타입 시스템 마스터하기", accessLevel: "FREE", viewCount: 180, likeCount: 14, bookmarkCount: 3, createdAt: "2026-06-25" },
  ],
  topSeries: [
    { id: 1, title: "Next.js 16 실무 완벽 가이드", postCount: 1, viewCount: 620 },
    { id: 2, title: "스프링 부트 3.3 JPA 마스터 클래스", postCount: 1, viewCount: 390 },
    { id: 3, title: "Docker & Kubernetes CI/CD 시리즈", postCount: 1, viewCount: 180 },
    { id: 4, title: "TypeScript 5.x 고급 타입 시스템 시리즈", postCount: 1, viewCount: 140 },
    { id: 5, title: "커리어 & 이직 가이드 시리즈", postCount: 1, viewCount: 95 },
  ],
};

export const MOCK_CREATOR_DASHBOARD_30D: CreatorDashboard = {
  metrics: {
    ...CREATOR_DASHBOARD_STOCK,
    newPostsThisPeriod: 4,
    newSeriesThisPeriod: 1,
    viewsThisPeriod: 3840,
    likesThisPeriod: 245,
    bookmarksThisPeriod: 112,
    newFollowersThisPeriod: 26,
  },
  heatmap: CREATOR_DASHBOARD_HEATMAP,
  radar: CREATOR_DASHBOARD_RADAR,
  topPosts: [
    { id: 101, title: "Next.js 16과 React 19로 대규모 실무 서비스 구축하기", accessLevel: "PAID", viewCount: 3840, likeCount: 245, bookmarkCount: 112, createdAt: "2026-07-20" },
    { id: 102, title: "스프링 부트 3.3 JPA N+1 문제 완벽 해결 가이드", accessLevel: "FREE", viewCount: 2910, likeCount: 198, bookmarkCount: 95, createdAt: "2026-07-15" },
    { id: 103, title: "실무에서 바로 쓰는 Docker & Kubernetes CI/CD 파이프라인", accessLevel: "PAID", viewCount: 2450, likeCount: 162, bookmarkCount: 84, createdAt: "2026-07-10" },
    { id: 104, title: "개발자를 위한 커리어 이직 가이드 및 연봉 협상 팁", accessLevel: "FREE", viewCount: 2100, likeCount: 140, bookmarkCount: 61, createdAt: "2026-07-02" },
    { id: 105, title: "TypeScript 5.x 고급 타입 시스템 마스터하기", accessLevel: "FREE", viewCount: 1750, likeCount: 115, bookmarkCount: 48, createdAt: "2026-06-25" },
  ],
  topSeries: [
    { id: 1, title: "Next.js 16 실무 완벽 가이드", postCount: 5, viewCount: 3840 },
    { id: 2, title: "스프링 부트 3.3 JPA 마스터 클래스", postCount: 4, viewCount: 2450 },
    { id: 3, title: "Docker & Kubernetes CI/CD 시리즈", postCount: 3, viewCount: 1750 },
    { id: 4, title: "TypeScript 5.x 고급 타입 시스템 시리즈", postCount: 2, viewCount: 980 },
    { id: 5, title: "커리어 & 이직 가이드 시리즈", postCount: 2, viewCount: 720 },
  ],
};

export const MOCK_CREATOR_DASHBOARD_ALL: CreatorDashboard = {
  metrics: {
    ...CREATOR_DASHBOARD_STOCK,
    newPostsThisPeriod: CREATOR_DASHBOARD_STOCK.totalPosts,
    newSeriesThisPeriod: CREATOR_DASHBOARD_STOCK.totalSeries,
    viewsThisPeriod: CREATOR_DASHBOARD_STOCK.totalViews,
    likesThisPeriod: CREATOR_DASHBOARD_STOCK.totalLikes,
    bookmarksThisPeriod: CREATOR_DASHBOARD_STOCK.totalBookmarks,
    newFollowersThisPeriod: CREATOR_DASHBOARD_STOCK.totalFollowers,
  },
  heatmap: CREATOR_DASHBOARD_HEATMAP,
  radar: CREATOR_DASHBOARD_RADAR,
  topPosts: [
    { id: 101, title: "Next.js 16과 React 19로 대규모 실무 서비스 구축하기", accessLevel: "PAID", viewCount: 9420, likeCount: 680, bookmarkCount: 340, createdAt: "2026-07-20" },
    { id: 102, title: "스프링 부트 3.3 JPA N+1 문제 완벽 해결 가이드", accessLevel: "FREE", viewCount: 8150, likeCount: 590, bookmarkCount: 290, createdAt: "2026-07-15" },
    { id: 103, title: "실무에서 바로 쓰는 Docker & Kubernetes CI/CD 파이프라인", accessLevel: "PAID", viewCount: 6730, likeCount: 480, bookmarkCount: 210, createdAt: "2026-07-10" },
    { id: 104, title: "개발자를 위한 커리어 이직 가이드 및 연봉 협상 팁", accessLevel: "FREE", viewCount: 5920, likeCount: 420, bookmarkCount: 195, createdAt: "2026-07-02" },
    { id: 105, title: "TypeScript 5.x 고급 타입 시스템 마스터하기", accessLevel: "FREE", viewCount: 4890, likeCount: 360, bookmarkCount: 170, createdAt: "2026-06-25" },
  ],
  topSeries: [
    { id: 1, title: "Next.js 16 실무 완벽 가이드", postCount: 8, viewCount: 7850 },
    { id: 2, title: "스프링 부트 3.3 JPA 마스터 클래스", postCount: 12, viewCount: 5420 },
    { id: 3, title: "Docker & Kubernetes CI/CD 시리즈", postCount: 5, viewCount: 3180 },
    { id: 4, title: "TypeScript 5.x 고급 타입 시스템 시리즈", postCount: 4, viewCount: 2340 },
    { id: 5, title: "커리어 & 이직 가이드 시리즈", postCount: 3, viewCount: 1680 },
  ],
};

// --- Admin Dashboard Period-Specific Mock Datasets ---
// Stock 필드(총 회원/포스트/시리즈/조회수)는 lifetime 누적값이라 세 목업이 전부 동일하고,
// *ThisWeek/*ThisPeriod 필드와 signupTrend/superCreators/topPosts만 기간별로 달라진다.
const ADMIN_DASHBOARD_STOCK = {
  totalUsers: 2480,
  totalPosts: 620,
  freePosts: 410,
  paidPosts: 210,
  totalSeries: 84,
  avgPostsPerSeries: 7.4,
  totalViews: 482900,
  avgViewsPerPost: 778,
};

export const MOCK_ADMIN_DASHBOARD_7D: AdminDashboard = {
  metrics: {
    ...ADMIN_DASHBOARD_STOCK,
    newUsersThisPeriod: 127,
    newPostsThisPeriod: 8,
    newSeriesThisPeriod: 1,
    viewsThisPeriod: 42800,
  },
  signupTrend: [
    { label: "7/25", users: 12 },
    { label: "7/26", users: 18 },
    { label: "7/27", users: 15 },
    { label: "7/28", users: 22 },
    { label: "7/29", users: 19 },
    { label: "7/30", users: 25 },
    { label: "7/31", users: 28 },
  ],
  subscriptionRatio: {
    followCount: 1640,
    membershipCount: 840,
    followPercentage: 66.1,
    membershipPercentage: 33.9,
  },
  superCreators: [
    { id: 2, nickname: "김백엔드", subscriberCount: 450, followerIncrease: 18, introduction: "MSA와 스프링 부트 아키텍처를 작성합니다." },
    { id: 3, nickname: "이프론트", subscriberCount: 380, followerIncrease: 14, introduction: "React, Next.js 모던 웹 프론트엔드 노하우 공유" },
    { id: 4, nickname: "박데브옵스", subscriberCount: 310, followerIncrease: 12, introduction: "쿠버네티스와 Cloud Native 아키텍트" },
    { id: 5, nickname: "최AI랩", subscriberCount: 275, followerIncrease: 9, introduction: "LLM, RAG 및 엔터프라이즈 AI 서비스 개발" },
    { id: 6, nickname: "정보안", subscriberCount: 220, followerIncrease: 7, introduction: "웹 보안, 취약점 점검 및 DevSecOps" },
  ],
  topPosts: [
    { id: 201, title: "2026년 백엔드 개발자 필수 로드맵 & 기술 스택", accessLevel: "FREE", viewCount: 1240, likeCount: 95, bookmarkCount: 42 },
    { id: 202, title: "대용량 트래픽 처리 시스템 설계 핵심 노하우 10가지", accessLevel: "PAID", viewCount: 1080, likeCount: 82, bookmarkCount: 38 },
    { id: 203, title: "주니어 개발자가 자주 실수하는 5가지 디자인 패턴", accessLevel: "FREE", viewCount: 890, likeCount: 65, bookmarkCount: 28 },
    { id: 204, title: "Spring Cloud 기반 MSA 트랜잭션 완벽 제어 패턴", accessLevel: "PAID", viewCount: 760, likeCount: 54, bookmarkCount: 24 },
    { id: 205, title: "Redis 캐싱 전략과 메모리 누수 방지 실무 팁", accessLevel: "FREE", viewCount: 640, likeCount: 48, bookmarkCount: 19 },
  ],
};

export const MOCK_ADMIN_DASHBOARD_30D: AdminDashboard = {
  metrics: {
    ...ADMIN_DASHBOARD_STOCK,
    newUsersThisPeriod: 480,
    newPostsThisPeriod: 34,
    newSeriesThisPeriod: 3,
    viewsThisPeriod: 184200,
  },
  signupTrend: [
    { label: "7월 1주", users: 90 },
    { label: "7월 2주", users: 110 },
    { label: "7월 3주", users: 130 },
    { label: "7월 4주", users: 150 },
  ],
  subscriptionRatio: {
    followCount: 1640,
    membershipCount: 840,
    followPercentage: 66.1,
    membershipPercentage: 33.9,
  },
  superCreators: [
    { id: 2, nickname: "김백엔드", subscriberCount: 450, followerIncrease: 42, introduction: "MSA와 스프링 부트 아키텍처를 작성합니다." },
    { id: 3, nickname: "이프론트", subscriberCount: 380, followerIncrease: 35, introduction: "React, Next.js 모던 웹 프론트엔드 노하우 공유" },
    { id: 4, nickname: "박데브옵스", subscriberCount: 310, followerIncrease: 28, introduction: "쿠버네티스와 Cloud Native 아키텍트" },
    { id: 5, nickname: "최AI랩", subscriberCount: 275, followerIncrease: 24, introduction: "LLM, RAG 및 엔터프라이즈 AI 서비스 개발" },
    { id: 6, nickname: "정보안", subscriberCount: 220, followerIncrease: 19, introduction: "웹 보안, 취약점 점검 및 DevSecOps" },
  ],
  topPosts: [
    { id: 201, title: "2026년 백엔드 개발자 필수 로드맵 & 기술 스택", accessLevel: "FREE", viewCount: 4850, likeCount: 380, bookmarkCount: 190 },
    { id: 202, title: "대용량 트래픽 처리 시스템 설계 핵심 노하우 10가지", accessLevel: "PAID", viewCount: 4120, likeCount: 310, bookmarkCount: 155 },
    { id: 203, title: "주니어 개발자가 자주 실수하는 5가지 디자인 패턴", accessLevel: "FREE", viewCount: 3450, likeCount: 250, bookmarkCount: 120 },
    { id: 204, title: "Spring Cloud 기반 MSA 트랜잭션 완벽 제어 패턴", accessLevel: "PAID", viewCount: 2980, likeCount: 210, bookmarkCount: 98 },
    { id: 205, title: "Redis 캐싱 전략과 메모리 누수 방지 실무 팁", accessLevel: "FREE", viewCount: 2450, likeCount: 180, bookmarkCount: 85 },
  ],
};

export const MOCK_ADMIN_DASHBOARD: AdminDashboard = {
  metrics: {
    ...ADMIN_DASHBOARD_STOCK,
    newUsersThisPeriod: ADMIN_DASHBOARD_STOCK.totalUsers,
    newPostsThisPeriod: ADMIN_DASHBOARD_STOCK.totalPosts,
    newSeriesThisPeriod: ADMIN_DASHBOARD_STOCK.totalSeries,
    viewsThisPeriod: ADMIN_DASHBOARD_STOCK.totalViews,
  },
  signupTrend: [
    { label: "2월", users: 180 },
    { label: "3월", users: 290 },
    { label: "4월", users: 420 },
    { label: "5월", users: 650 },
    { label: "6월", users: 1100 },
    { label: "7월", users: 2480 },
  ],
  subscriptionRatio: {
    followCount: 1640,
    membershipCount: 840,
    followPercentage: 66.1,
    membershipPercentage: 33.9,
  },
  superCreators: [
    { id: 2, nickname: "김백엔드", subscriberCount: 450, followerIncrease: 125, introduction: "MSA와 스프링 부트 아키텍처를 작성합니다." },
    { id: 3, nickname: "이프론트", subscriberCount: 380, followerIncrease: 98, introduction: "React, Next.js 모던 웹 프론트엔드 노하우 공유" },
    { id: 4, nickname: "박데브옵스", subscriberCount: 310, followerIncrease: 82, introduction: "쿠버네티스와 Cloud Native 아키텍트" },
    { id: 5, nickname: "최AI랩", subscriberCount: 275, followerIncrease: 74, introduction: "LLM, RAG 및 엔터프라이즈 AI 서비스 개발" },
    { id: 6, nickname: "정보안", subscriberCount: 220, followerIncrease: 61, introduction: "웹 보안, 취약점 점검 및 DevSecOps" },
  ],
  topPosts: [
    { id: 201, title: "2026년 백엔드 개발자 필수 로드맵 & 기술 스택", accessLevel: "FREE", viewCount: 9420, likeCount: 680, bookmarkCount: 340 },
    { id: 202, title: "대용량 트래픽 처리 시스템 설계 핵심 노하우 10가지", accessLevel: "PAID", viewCount: 8150, likeCount: 590, bookmarkCount: 290 },
    { id: 203, title: "주니어 개발자가 자주 실수하는 5가지 디자인 패턴", accessLevel: "FREE", viewCount: 6730, likeCount: 480, bookmarkCount: 210 },
    { id: 204, title: "Spring Cloud 기반 MSA 트랜잭션 완벽 제어 패턴", accessLevel: "PAID", viewCount: 5920, likeCount: 420, bookmarkCount: 195 },
    { id: 205, title: "Redis 캐싱 전략과 메모리 누수 방지 실무 팁", accessLevel: "FREE", viewCount: 4890, likeCount: 360, bookmarkCount: 170 },
  ],
};

// --- API Service Functions with Graceful Silent Mock Fallback ---
export async function getCreatorDashboard(period: string = "30d"): Promise<CreatorDashboard> {
  try {
    const data = await apiFetch<CreatorDashboard>(`/api/dashboard/user?period=${period}`);
    return data;
  } catch {
    // API 미연동 시 콘솔 에러 대신 조용히 Mock 데이터 사용
    if (period === "7d") return MOCK_CREATOR_DASHBOARD_7D;
    if (period === "all") return MOCK_CREATOR_DASHBOARD_ALL;
    return MOCK_CREATOR_DASHBOARD_30D;
  }
}

export async function getAdminDashboard(period: string = "30d"): Promise<AdminDashboard> {
  try {
    const data = await apiFetch<AdminDashboard>(`/api/dashboard/admin?period=${period}`);
    return data;
  } catch {
    if (period === "7d") return MOCK_ADMIN_DASHBOARD_7D;
    if (period === "30d") return MOCK_ADMIN_DASHBOARD_30D;
    return MOCK_ADMIN_DASHBOARD;
  }
}

export interface Mainpage {
  trendingCreators: SuperCreator[];      // 지금 뜨는 창작자 TOP 5
  popularPaidPosts: TopPost[];           // 실시간 인기 멤버십 TOP 5
  popularFreePosts: TopPost[];           // 무료 노하우 TOP 5
}

const MOCK_MAINPAGE_DATA: Mainpage = {
  trendingCreators: [
    { id: 2, nickname: "김백엔드", subscriberCount: 450, followerIncrease: 42, introduction: "MSA와 스프링 부트 아키텍처를 작성합니다." },
    { id: 3, nickname: "이프론트", subscriberCount: 380, followerIncrease: 35, introduction: "React, Next.js 모던 웹 프론트엔드 노하우 공유" },
    { id: 4, nickname: "박데브옵스", subscriberCount: 310, followerIncrease: 28, introduction: "쿠버네티스와 Cloud Native 아키텍트" },
    { id: 5, nickname: "최AI랩", subscriberCount: 275, followerIncrease: 24, introduction: "LLM, RAG 및 엔터프라이즈 AI 서비스 개발" },
    { id: 6, nickname: "정보안", subscriberCount: 220, followerIncrease: 19, introduction: "웹 보안, 취약점 점검 및 DevSecOps" },
  ],
  popularPaidPosts: [
    { id: 201, title: "대용량 트래픽 처리 시스템 설계 핵심 노하우 10가지", accessLevel: "PAID", viewCount: 4120, likeCount: 310, bookmarkCount: 155 },
    { id: 202, title: "Spring Cloud 기반 MSA 트랜잭션 완벽 제어 패턴", accessLevel: "PAID", viewCount: 2980, likeCount: 210, bookmarkCount: 98 },
    { id: 203, title: "고성능 데이터베이스 쿼리 최적화 실무 기법", accessLevel: "PAID", viewCount: 2450, likeCount: 185, bookmarkCount: 92 },
    { id: 204, title: "마이크로서비스 아키텍처 보안 완벽 가이드", accessLevel: "PAID", viewCount: 2100, likeCount: 155, bookmarkCount: 78 },
    { id: 205, title: "클라우드 네이티브 애플리케이션 패턴과 실전", accessLevel: "PAID", viewCount: 1850, likeCount: 135, bookmarkCount: 62 },
  ],
  popularFreePosts: [
    { id: 101, title: "2026년 백엔드 개발자 필수 로드맵 & 기술 스택", accessLevel: "FREE", viewCount: 9420, likeCount: 680, bookmarkCount: 340 },
    { id: 102, title: "주니어 개발자가 자주 실수하는 5가지 디자인 패턴", accessLevel: "FREE", viewCount: 6730, likeCount: 480, bookmarkCount: 210 },
    { id: 103, title: "개발자 커리어 로드맵과 연봉 협상 팁", accessLevel: "FREE", viewCount: 5920, likeCount: 420, bookmarkCount: 195 },
    { id: 104, title: "Redis 캐싱 전략과 메모리 누수 방지 실무 팁", accessLevel: "FREE", viewCount: 4890, likeCount: 360, bookmarkCount: 170 },
    { id: 105, title: "Git 워크플로우 완벽 이해하기", accessLevel: "FREE", viewCount: 3650, likeCount: 265, bookmarkCount: 125 },
  ],
};

export async function getMainpage(): Promise<Mainpage> {
  try {
    const data = await apiFetch<Mainpage>(`/api/dashboard/mainpage`);
    return data;
  } catch {
    // API 미연동 시 조용히 Mock 데이터 사용
    return MOCK_MAINPAGE_DATA;
  }
}
