"use client";

import React, { useCallback, useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { AnimatePresence, motion } from "framer-motion";
import { Check } from "lucide-react";
import { Avatar } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import { Tab, type TabItem } from "@/components/ui/tab";
import { PostGrid } from "@/components/common/post-grid";
import { PostPagination } from "@/components/common/post-pagination";
import { SeriesList } from "@/components/common/series-list";
import { SeriesPagination } from "@/components/common/series-pagination";
import { ConfirmModal } from "@/components/common/confirm-modal";
import { CancelMembershipModal } from "@/components/common/cancel-membership-modal";
import { useAuth } from "@/providers/auth-provider";
import { useHasMounted } from "@/hooks/use-has-mounted";
import { apiFetch, apiPost, apiDelete } from "@/lib/api";
import { cn } from "@/lib/utils";
import { PaymentWidgetModal } from "@/components/payment/payment-widget-modal";
import type { UserProfileResponse, PostListResponse, SeriesListResponse } from "./page";

// 팔로우 버튼 폭이 "팔로우"↔"팔로잉" 텍스트 길이 변화에 맞춰 부드럽게 늘어나도록,
// layout 애니메이션을 쓸 수 있는 motion 버전의 Button을 만듭니다.
const MotionButton = motion(Button);

// page.tsx의 UserProfileResponse를 그대로 재사용해서, API 응답 형태가 바뀌면
// 컴파일 타임에 바로 잡히도록 합니다. 이 파일에서 별도 Profile 타입을 정의하지 않습니다.
type Profile = UserProfileResponse;
type PostItem = PostListResponse;
type SeriesItem = SeriesListResponse;

// Swagger 확정 스펙: GET /api/subscriptions/status/{creatorId} → RsData<SubscriptionStatusResponse>
// 로그인한 사용자의 특정 창작자에 대한 구독 상태(NONE/FOLLOW/MEMBERSHIP) 단건 조회입니다.
interface SubscriptionStatusResponse {
  status: "NONE" | "FOLLOW" | "MEMBERSHIP";
}

interface UserProfileViewProps {
  profile: Profile;
  tab: "post" | "series";
  // 시리즈 탭 전용: 번호식 페이지네이션 기준 페이지
  page: number;
  // 시리즈 탭 전용: 번호식 페이지네이션 총 페이지 수
  totalPages: number;
  // 포스트 탭 전용: Slice 응답의 last 필드. true면 다음 페이지가 없음
  isLastPostsPage: boolean;
  posts: PostItem[];
  series: SeriesItem[];
  // 현재 탭(포스트/시리즈)의 목록 조회가 실패했는지 여부. 프로필 자체는 이미 불러온 상태이므로,
  // 이 경우에도 프로필 헤더는 그대로 보여주고 탭 영역에만 에러를 표시합니다.
  tabDataError: boolean;
}

const TABS: TabItem[] = [
  { id: "post", label: "포스트" },
  { id: "series", label: "시리즈" },
];

export function UserProfileView({ profile, tab, page, totalPages, isLastPostsPage, posts, series, tabDataError }: UserProfileViewProps) {
  const router = useRouter();
  const { isLoggedIn, user } = useAuth();
  const hasMounted = useHasMounted();
  const [showLoginModal, setShowLoginModal] = useState(false);
  // mypage(FollowButton)의 멤버십 해지 확인 팝업과 동일한 UX를 여기서도 제공합니다.
  const [showCancelMembershipModal, setShowCancelMembershipModal] = useState(false);
  const [isPaymentModalOpen, setIsPaymentModalOpen] = useState(false);
  // 로그인한 사용자의 초기 isFollowing/isMember 상태는 프로필 응답(UserProfileResponse)에 없어,
  // 아래 useEffect에서 GET /api/subscriptions/status/{creatorId} 단건 조회로 판단합니다.
  const [isFollowing, setIsFollowing] = useState(false);
  const [isMember, setIsMember] = useState(false);
  const [isFollowSubmitting, setIsFollowSubmitting] = useState(false);
  const [isMembershipSubmitting, setIsMembershipSubmitting] = useState(false);
  // 팔로우/멤버십 가입·해지 시 새로고침 없이 구독자 수를 즉시 반영하기 위한 낙관적(optimistic) 카운트.
  // 서버 진짜 값과는 다음 방문(재조회) 시 다시 맞춰집니다.
  const [subscriberCount, setSubscriberCount] = useState(profile.subscriberCount);

  // useAuth().user.id(number)와 프로필 id를 비교해 본인 프로필 여부를 판단합니다.
  // hasMounted 가드: 서버는 로그인 여부를 몰라 항상 비로그인으로 렌더링하므로,
  // 하이드레이션 이전엔 isLoggedIn을 그대로 쓰면 서버 HTML과 어긋납니다.
  const isOwnProfile = hasMounted && isLoggedIn && user?.id === profile.id;

  const isFirstRender = useRef(true);
  useEffect(() => {
    if (isFirstRender.current) {
      isFirstRender.current = false;
      return;
    }
    window.scrollTo({ top: 0, behavior: "smooth" });
  }, [page]);

  // Check if URL has Toss redirect params, open modal automatically
  useEffect(() => {
    if (typeof window !== "undefined") {
      const params = new URLSearchParams(window.location.search);
      if (params.get("paymentKey") || params.get("payment_fail")) {
        setIsPaymentModalOpen(true);
      }
    }
  }, []);

  // 로그인한 사용자이면서 본인 프로필이 아닐 때만, 이미 팔로우/멤버십 중인지 확인합니다.
  // 로그아웃했거나 본인 프로필일 때는 이전 세션(다른 계정)의 상태가 남지 않도록 초기화합니다.
  useEffect(() => {
    let cancelled = false;
    (async () => {
      if (!isLoggedIn || isOwnProfile) {
        setIsFollowing(false);
        setIsMember(false);
        return;
      }
      try {
        const data = await apiFetch<SubscriptionStatusResponse>(`/api/subscriptions/status/${profile.id}`);
        if (cancelled) return;
        // 재로그인/계정 전환 시 이전 계정의 구독 상태가 남지 않도록 매 상태를 명시적으로 반영합니다.
        setIsFollowing(data.status === "FOLLOW" || data.status === "MEMBERSHIP");
        setIsMember(data.status === "MEMBERSHIP");
      } catch {
        // 조회 실패 시 기존 기본값(false)을 유지합니다.
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [isLoggedIn, isOwnProfile, profile.id]);

  // 응답은 RsData<Void>. 인증은 이 프로젝트의 기존 클라이언트 패턴(apiPost/apiDelete → credentials:'include'
  // 쿠키 기반)을 그대로 따릅니다. 실패 시 최소한의 alert로 알리고 버튼 상태는 성공했을 때만 갱신합니다.
  const handleFollow = async () => {
    if (isFollowSubmitting) return;
    setIsFollowSubmitting(true);
    try {
      if (isFollowing) {
        await apiDelete(`/api/subscriptions/follow/${profile.id}`);
        setSubscriberCount((prev) => prev - 1);
      } else {
        await apiPost(`/api/subscriptions/follow/${profile.id}`);
        setSubscriberCount((prev) => prev + 1);
      }
      setIsFollowing((prev) => !prev);
    } catch {
      alert(isFollowing ? "언팔로우에 실패했습니다." : "팔로우에 실패했습니다.");
    } finally {
      setIsFollowSubmitting(false);
    }
  };

  // 가입: POST /api/subscriptions/membership/{creatorId} (가입 시 팔로우 자동 처리됨)
  // 해지: DELETE /api/subscriptions/membership/{creatorId} (해지 시 팔로우 상태로 돌아감)
  const handleMembership = async () => {
    if (isMembershipSubmitting) return;
    setIsMembershipSubmitting(true);
    try {
      if (isMember) {
        await apiDelete(`/api/subscriptions/membership/${profile.id}`);
      } else {
        await apiPost(`/api/subscriptions/membership/${profile.id}`);
        // 백엔드가 멤버십 가입 시 자동으로 팔로우 처리하므로, 프론트 상태도 함께 맞춰줍니다.
        // 이걸 빼먹으면 팔로우 없이 바로 멤버십에 가입한 사용자가 나중에 멤버십을 해지했을 때
        // isFollowing이 여전히 false로 남아 팔로우 버튼이 "팔로우"로 보이고, 클릭 시 백엔드가
        // 이미 존재하는 구독으로 거부(ALREADY_SUBSCRIBED)해 실패 alert가 뜨는 버그가 있었습니다.
        // 이미 팔로우 중이었다면 백엔드는 기존 구독 row를 MEMBERSHIP으로 승급만 하고 새로 만들지
        // 않으므로, 구독자 수는 팔로우 중이 아니었을 때(자동 팔로우로 row가 새로 생길 때)만 올립니다.
        if (!isFollowing) {
          setSubscriberCount((prev) => prev + 1);
        }
        setIsFollowing(true);
      }
      setIsMember((prev) => !prev);
    } catch {
      alert(isMember ? "멤버십 해지에 실패했습니다." : "멤버십 가입에 실패했습니다.");
    } finally {
      setIsMembershipSubmitting(false);
    }
  };

  const handleFollowClick = () => {
    if (!isLoggedIn) {
      setShowLoginModal(true);
      return;
    }
    handleFollow();
  };

  const handleMembershipClick = () => {
    if (!isLoggedIn) {
      setShowLoginModal(true);
      return;
    }
    // 해지는 되돌리기 번거로운 동작이라, mypage(FollowButton)와 동일하게 확인 팝업을 먼저 띄웁니다.
    if (isMember) {
      setShowCancelMembershipModal(true);
      return;
    }
    // 가입은 결제 모달을 띄웁니다.
    setIsPaymentModalOpen(true);
  };

  // ConfirmModal의 useEffect가 onCancel을 deps로 사용하므로, 매 렌더마다 새 함수를
  // 넘기지 않도록 참조를 고정합니다.
  const closeLoginModal = useCallback(() => setShowLoginModal(false), []);

  const handleTabChange = (newTab: string) => {
    router.push(`/users/${profile.id}?tab=${newTab}&page=1`);
  };

  const handlePageChange = (newPage: number) => {
    router.push(`/users/${profile.id}?tab=${tab}&page=${newPage}`);
  };

  const stats: { label: string; value: string }[] = [
    { label: "포스트", value: profile.postCount.toLocaleString("ko-KR") },
    { label: "구독자", value: subscriberCount.toLocaleString("ko-KR") },
    { label: "구독 중", value: profile.subscribingCount.toLocaleString("ko-KR") },
  ];

  return (
    <>
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        {/* 프로필 사진 + 닉네임: 배너 경계가 아바타(xl, 96px) 세로 길이의 45% 지점을 가로지르도록 배치.
            겹침 비율은 이 margin-top 값(아바타 높이의 45%)으로만 결정됩니다 — 배너 높이(page.tsx의 h-40 sm:h-48)는
            겹침 비율에 영향을 주지 않습니다. */}
        <div className="flex flex-wrap items-end justify-between gap-4 mt-[-43.2px] relative z-10">
          <div className="flex items-end gap-4">
            <Avatar
              src={profile.profileImageUrl}
              name={profile.nickname}
              size="xl"
              className="border-4 border-white shadow-lg bg-white shrink-0"
            />
            <div className="pb-2">
              <h1 className="text-2xl sm:text-3xl font-extrabold text-neutral-dark">{profile.nickname}</h1>
            </div>
          </div>

          {!isOwnProfile && (
            <div className="flex items-end gap-2 pb-2">
              <MotionButton
                layout
                transition={{ layout: { duration: 0.2, ease: "easeOut" } }}
                size="sm"
                variant="outlined"
                className={cn(
                  "rounded-full px-6 overflow-hidden transition-colors",
                  isFollowing && "border-primary bg-primary/5 text-primary"
                )}
                onClick={handleFollowClick}
                disabled={isFollowSubmitting || isMember}
                title={isMember ? "멤버십 해지 후 언팔로우할 수 있습니다." : undefined}
              >
                <AnimatePresence mode="popLayout" initial={false}>
                  <motion.span
                    key={isFollowing ? "following" : "follow"}
                    initial={{ opacity: 0, y: 6, scale: 0.9 }}
                    animate={{ opacity: 1, y: 0, scale: 1 }}
                    exit={{ opacity: 0, y: -6, scale: 0.9 }}
                    transition={{ duration: 0.18, ease: "easeOut" }}
                    className="flex items-center gap-1.5"
                  >
                    {isFollowing && <Check className="h-3.5 w-3.5" />}
                    {isFollowing ? "팔로잉" : "팔로우"}
                  </motion.span>
                </AnimatePresence>
              </MotionButton>
              {profile.offersMembership && (
                <Button
                  size="sm"
                  variant="filled"
                  className="rounded-full px-6 bg-membership text-white hover:bg-membership/90 active:bg-membership/90"
                  onClick={handleMembershipClick}
                  disabled={isMembershipSubmitting}
                >
                  {isMember ? "멤버십 해지" : "멤버십"}
                </Button>
              )}
            </div>
          )}
        </div>

        {/* 자기소개 */}
        <p className="mt-6 max-w-2xl text-[15px] leading-relaxed text-neutral-600">
          {profile.introduction || "작성한 소개글이 없습니다."}
        </p>

        {/* 통계 */}
        <div className="mt-6 flex flex-wrap gap-3">
          {stats.map((stat) => (
            <div
              key={stat.label}
              className="flex items-baseline gap-1.5 rounded-xl border border-neutral-border bg-white px-4 py-2.5 shadow-sm"
            >
              <span className="text-base font-extrabold text-neutral-dark">{stat.value}</span>
              <span className="text-sm font-medium text-neutral-meta">{stat.label}</span>
            </div>
          ))}
        </div>

        {/* 구분선 */}
        <div className="mt-8 border-t border-neutral-border" />

        {/* 탭 */}
        <div className="mt-6">
          <Tab tabs={TABS} activeTabId={tab} onChange={handleTabChange} />
        </div>

        {/* 포스트 / 시리즈 목록 */}
        <div className="mt-8 min-h-75">
          {tabDataError ? (
            <div className="flex flex-col items-center justify-center rounded-2xl border border-dashed border-neutral-300 bg-white py-24 text-center">
              <p className="text-neutral-500">
                {tab === "post" ? "포스트를 불러오지 못했습니다." : "시리즈를 불러오지 못했습니다."} 잠시 후 다시 시도해주세요.
              </p>
            </div>
          ) : (
            <>
              {tab === "post" ? (
                // authorName은 이 페이지의 포스트가 모두 profile(조회 중인 유저)의 글이므로 profile.nickname을 그대로 사용합니다.
                <PostGrid posts={posts} authorName={profile.nickname} />
              ) : (
                <SeriesList series={series} isOwner={isOwnProfile} />
              )}

              {tab === "post" ? (
                <PostPagination page={page} isLastPage={isLastPostsPage} onPageChange={handlePageChange} />
              ) : (
                <SeriesPagination page={page} totalPages={totalPages} onPageChange={handlePageChange} />
              )}
            </>
          )}
        </div>
      </div>

      {showLoginModal && (
        <ConfirmModal
          title="로그인이 필요합니다"
          description="팔로우 및 멤버십 기능은 로그인 후 이용할 수 있어요."
          onConfirm={() => {
            closeLoginModal();
            // TODO: /login 페이지 완성되면 아래 리다이렉트를 활성화하세요.
            // router.push("/login");
          }}
          onCancel={closeLoginModal}
        />
      )}

      {/* mypage(FollowButton)와 완전히 동일한 문구/디자인의 멤버십 해지 확인 팝업입니다. */}
      <CancelMembershipModal
        open={showCancelMembershipModal}
        onConfirm={() => {
          setShowCancelMembershipModal(false);
          handleMembership();
        }}
        onCancel={() => setShowCancelMembershipModal(false)}
      />

      <PaymentWidgetModal
        isOpen={isPaymentModalOpen}
        onClose={() => setIsPaymentModalOpen(false)}
        creatorName={profile.nickname}
        creatorId={profile.id}
        onSuccess={() => {
          setIsMember(true);
          if (!isFollowing) {
            setIsFollowing(true);
            setSubscriberCount((prev) => prev + 1);
          }
        }}
      />
    </>
  );
}
