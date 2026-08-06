import { cookies } from "next/headers";
import { notFound } from "next/navigation";
import { MOCK_CREATORS } from "@/lib/mock-data";
import { apiFetch, ApiError, resolveMediaUrl } from "@/lib/api";
import { UserProfileView } from "./user-profile-view";

export const dynamic = "force-dynamic";

const PAGE_SIZE = 10;

// Swagger 확정 스펙: GET /api/users/{id} → RsData<UserProfileApiResponse>
export interface UserProfileApiResponse {
  id: number;
  followerCount: number;
  profile: {
    nickname: string;
    profileImageUrl: string;
    introduction: string;
  };
}

// UserProfileApiResponse를 평탄화한 화면용 뷰 모델.
// id/nickname/profileImageUrl/introduction/subscriberCount(followerCount)는 UserProfileApiResponse를 그대로 매핑합니다.
// user-profile-view.tsx에서도 이 타입을 그대로 import해서 재사용하므로 별도 타입을 만들지 마세요.
export interface UserProfileResponse {
  id: number;
  nickname: string;
  introduction: string;
  profileImageUrl?: string;
  subscriberCount: number;
  postCount: number;
  // TODO: [백엔드 확인 필요] 구독 중 수 / 멤버십 제공 여부는 UserProfileApiResponse에 없고,
  // 통계 전용 엔드포인트도 Swagger에 아직 없음 — 백엔드 확인 후 추가 예정.
  subscribingCount: number;
  offersMembership: boolean;
}

// Swagger 확정 스펙: GET /api/posts?creatorId={id}&page={n}&size={s} → RsData<SlicePostListResponse>
// body/thumbnail/description 필드가 없어 목록에서는 제목 등 최소 정보만 내려옵니다.
export interface PostListResponse {
  id: number;
  userId: number;
  seriesId: number | null;
  title: string;
  publishStatus: "DRAFT" | "PRIVATE" | "PUBLIC";
  accessLevel: "FREE" | "PAID";
  viewCount: number;
  createdAt: string;
  thumbnailUrl?: string;
}

// Slice 응답이라 totalPages/totalElements가 없습니다. last로만 "다음 페이지 존재 여부"를 판단합니다.
export interface SlicePostListResponse {
  content: PostListResponse[];
  first: boolean;
  last: boolean;
  numberOfElements: number;
  pageable: unknown;
  sort: unknown;
  size: number;
  number: number;
  empty: boolean;
}

// Swagger 확정 스펙: GET /api/posts/users/{userId}?page={n}&size={s} → RsData<PagePostListResponse>
// /api/posts(creatorId)는 Slice라 totalElements가 없어, 포스트 수(postCount) 집계 전용으로만 사용합니다.
// (size=1로 요청해 content는 최소 1건만 받고 totalElements만 사용)
export interface PagePostListResponse {
  content: PostListResponse[];
  totalElements: number;
  totalPages: number;
}

// Swagger 확정 스펙: GET /api/series/users/{userId}?page={n}&size={s} → RsData<PageResponseSeriesListResponse>
export interface SeriesListResponse {
  id: number;
  userId: number;
  nickname: string;
  title: string;
  body: string;
  postCount: number;
  thumbnailUrl?: string;
  createdAt: string;
  updatedAt: string;
}

// 시리즈는 Page 응답이라 totalPages 기반 번호식 페이지네이션을 그대로 사용합니다.
// 필드명은 last가 아니라 isLast입니다.
export interface PageResponseSeriesListResponse {
  content: SeriesListResponse[];
  pageNumber: number;
  pageSize: number;
  totalElements: number;
  totalPages: number;
  isLast: boolean;
}

export default async function UserProfilePage({
  params,
  searchParams,
}: {
  params: Promise<{ id: string }>;
  searchParams: Promise<{ tab?: string; page?: string }>;
}) {
  const { id } = await params;
  const resolvedSearchParams = await searchParams;
  const tab = resolvedSearchParams.tab === "series" ? "series" : "post";

  const parsedPage = parseInt(resolvedSearchParams.page || "1", 10);
  const requestedPage = Number.isNaN(parsedPage) ? 1 : parsedPage;

  // TODO: 액세스 토큰 만료 시 리프레시 처리는 이번 범위에 포함하지 않음.
  // 로그인하지 않은 사용자(쿠키 없음)도 이 페이지에 접근 가능해야 하므로 헤더 없이 요청합니다.
  const cookieStore = await cookies();
  const accessToken = cookieStore.get("accessToken")?.value;
  const authHeaders: HeadersInit = accessToken ? { Authorization: `Bearer ${accessToken}` } : {};

  let apiProfile: UserProfileApiResponse;
  try {
    apiProfile = await apiFetch<UserProfileApiResponse>(`/api/users/${id}`, {
      cache: "no-store",
      headers: authHeaders,
    });
  } catch (err) {
    if (err instanceof ApiError && err.status === 404) {
      notFound();
    }
    // TODO: [백엔드 확인 필요] 401/403 등 상태별 세분화된 에러 처리는 아직 없음 — 최소한의 에러 메시지만 노출합니다.
    return (
      <div className="min-h-screen flex items-center justify-center bg-neutral-50">
        <p className="text-neutral-500">프로필을 불러오지 못했습니다. 잠시 후 다시 시도해주세요.</p>
      </div>
    );
  }

  // TODO: [백엔드 확인 필요] 구독 중 수 / 멤버십 제공 여부는 UserProfileApiResponse에 없습니다.
  // 통계 전용 엔드포인트가 Swagger에 아직 없음 — 백엔드 확인 후 실데이터로 교체 예정.
  // 실제 유저 id와 매칭되는 mock 크리에이터가 없으면 첫 번째 mock 크리에이터 값으로 대체합니다.
  const mockCreator = MOCK_CREATORS.find((c) => c.id === apiProfile.id) ?? MOCK_CREATORS[0];

  // 포스트 수는 총 개수만 필요하므로 size=1로 요청해 totalElements만 사용합니다.
  let postCount = 0;
  try {
    const postCountData = await apiFetch<PagePostListResponse>(
      `/api/posts/users/${id}?page=0&size=1`,
      { cache: "no-store", headers: authHeaders }
    );
    postCount = postCountData.totalElements;
  } catch {
    // TODO: [백엔드 확인 필요] 401/403 등 상태별 세분화된 에러 처리는 아직 없음 — 실패 시 0으로 표시합니다.
  }

  const profile: UserProfileResponse = {
    id: apiProfile.id,
    nickname: apiProfile.profile.nickname,
    introduction: apiProfile.profile.introduction,
    profileImageUrl: apiProfile.profile.profileImageUrl ? resolveMediaUrl(apiProfile.profile.profileImageUrl) : undefined,
    postCount,
    subscriberCount: apiProfile.followerCount,
    subscribingCount: mockCreator.subscribingCount,
    offersMembership: mockCreator.offersMembership,
  };

  let pagedPosts: PostListResponse[] = [];
  let isLastPostsPage = true;
  let pagedSeries: SeriesListResponse[] = [];
  let totalPages = 1;
  // 포스트 탭은 page(URL의 page 파라미터를 그대로 사용, 클램핑 없음 — 프론트가 page 번호를 직접 추적)
  const postPage = requestedPage;
  // 시리즈 탭: 번호식 페이지네이션 기준 페이지 (totalPages를 받은 뒤 클램핑)
  let page = requestedPage;
  // 창작자 프로필(apiProfile)은 이미 정상적으로 불러온 상태이므로, 포스트/시리즈 탭 조회가
  // 실패하더라도 페이지 전체를 에러 화면으로 대체하지 않고 탭 영역에만 실패를 표시합니다.
  let tabDataError = false;

  if (tab === "post") {
    try {
      // page/size는 개별 쿼리 파라미터로 전달합니다 (Pageable을 nested 객체로 보내지 않음)
      const data = await apiFetch<SlicePostListResponse>(
        `/api/posts?creatorId=${id}&page=${requestedPage - 1}&size=${PAGE_SIZE}`,
        { cache: "no-store", headers: authHeaders }
      );
      pagedPosts = data.content;
      isLastPostsPage = data.last;
    } catch {
      // TODO: [백엔드 확인 필요] 401/403 등 상태별 세분화된 에러 처리는 아직 없음 — 최소한의 에러 표시만 노출합니다.
      tabDataError = true;
    }
  } else {
    try {
      const data = await apiFetch<PageResponseSeriesListResponse>(
        `/api/series/users/${id}?page=${requestedPage - 1}&size=${PAGE_SIZE}`,
        { cache: "no-store", headers: authHeaders }
      );
      pagedSeries = data.content;
      totalPages = data.totalPages;
      page = Math.min(Math.max(requestedPage, 1), totalPages);
    } catch {
      // TODO: [백엔드 확인 필요] 401/403 등 상태별 세분화된 에러 처리는 아직 없음 — 최소한의 에러 표시만 노출합니다.
      tabDataError = true;
    }
  }

  return (
    <div className="min-h-screen bg-neutral-50 pb-20">
      {/* 최상단 정적 시각 배너 (series/page.tsx와 동일한 스타일) */}
      <div className="w-full h-40 sm:h-48 bg-neutral-dark relative overflow-hidden">
        <div className="absolute inset-0 bg-[radial-gradient(ellipse_at_top_right,_var(--tw-gradient-stops))] from-primary/20 via-neutral-dark to-neutral-dark opacity-50" />
      </div>

      <UserProfileView
        // 다른 유저의 프로필로 넘어갈 때 완전히 새로 마운트되도록 key를 줘서, 팔로우/멤버십 상태와
        // 낙관적으로 반영한 구독자 수 등 이전 프로필의 로컬 state가 남지 않게 합니다.
        key={profile.id}
        profile={profile}
        tab={tab}
        page={tab === "series" ? page : postPage}
        totalPages={totalPages}
        isLastPostsPage={isLastPostsPage}
        posts={pagedPosts}
        series={pagedSeries}
        tabDataError={tabDataError}
      />
    </div>
  );
}
