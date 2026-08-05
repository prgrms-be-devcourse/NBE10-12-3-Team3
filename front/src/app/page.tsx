"use client";
import React, {useCallback, useEffect, useRef, useState} from "react";
import {ContentCard} from "@/components/common/content-card";
import {ContentCardSkeleton} from "@/components/common/content-card-skeleton";
import {CreatorCard} from "@/components/common/creator-card";
import {Button} from "@/components/ui/button";
import {AnimatePresence, motion} from "framer-motion";
import {useRouter} from "next/navigation";
import Link from "next/link";
import {ChevronLeft, ChevronRight, Sparkles, TrendingUp, Zap} from "lucide-react";
import {cn} from "@/lib/utils";
import {useCarouselObserver} from "@/hooks/use-carousel-observer";
import {useAuth} from "@/providers/auth-provider";
import {useHasMounted} from "@/hooks/use-has-mounted";

import {apiDelete, apiFetch, apiPost} from "@/lib/api";
import {getMainpage, TopPost as DashboardTopPost} from "@/lib/dashboard-api";

interface PostItem {
  id: number;
  userId: number;
  nickname: string;
  title: string;
  accessLevel: "FREE" | "PAID";
  viewCount: number;
    likeCount: number;
    bookmarkCount: number;
    isLiked: boolean;
    isBookmarked: boolean;
  createdAt: string;
}

interface SliceResponse {
  content: PostItem[];
  last: boolean;
}

interface CreatorItem {
    id: number;
    nickname: string;
    subscriberCount: number;
    introduction?: string;
    profileImageUrl?: string;
}

function toPostItem(post: DashboardTopPost): PostItem {
  return {
    id: post.id,
    userId: post.authorId ?? 0,
    nickname: post.authorNickname ?? "",
    title: post.title,
    accessLevel: post.accessLevel,
    viewCount: post.viewCount,
    likeCount: post.likeCount,
    bookmarkCount: post.bookmarkCount,
    isLiked: false,
    isBookmarked: false,
    createdAt: post.createdAt ?? "",
  };
}

function toCardProps(post: PostItem) {
  return {
    id: post.id,
    title: post.title,
    description: "",
    accessLevel: post.accessLevel,
    authorId: post.userId,
    authorName: post.nickname,
    createdAt: post.createdAt.split("T")[0],
    viewCount: post.viewCount,
      likeCount: post.likeCount,
      bookmarkCount: post.bookmarkCount,
      isLiked: post.isLiked,
      isBookmarked: post.isBookmarked,
  };
}

const HERO_ITEMS = [
  { text: "개발자들의 진짜 경험을,", color: "from-primary to-emerald-400", glow: "bg-primary/20", tags: ["#이직후기", "#신입생존기", "#연봉협상"] },
  { text: "상위 1% 실무 노하우를,", color: "from-blue-400 to-cyan-400", glow: "bg-blue-500/20", tags: ["#대규모트래픽", "#MSA전환", "#성능최적화"] },
  { text: "생생한 트러블슈팅을,", color: "from-purple-400 to-fuchsia-400", glow: "bg-purple-500/20", tags: ["#메모리누수", "#OOM해결", "#DB데드락"] },
];

export default function Home() {
  const router = useRouter();
  const { isLoggedIn } = useAuth();
  const hasMounted = useHasMounted();
  const [heroIdx, setHeroIdx] = useState(0);
  const [isLoading, setIsLoading] = useState(true);
  const [recentError, setRecentError] = useState(false);

  // 캐러셀 옵저버를 위한 Ref
  const premiumRef = useRef<HTMLDivElement>(null);
  const freeRef = useRef<HTMLDivElement>(null);

    const [freePosts, setFreePosts] = useState<PostItem[]>([]);
    const [trendingPaidPosts, setTrendingPaidPosts] = useState<PostItem[]>([]);
    const [topCreators, setTopCreators] = useState<CreatorItem[]>([]);

  useEffect(() => {
    const timer = setInterval(() => {
      setHeroIdx((prev) => (prev + 1) % HERO_ITEMS.length);
    }, 3000);

      // 메인페이지 실데이터 (지금 뜨는 창작자 TOP 5 + 인기 멤버십/무료 포스트 TOP 5)
      getMainpage()
      .then((data) => {
        setTopCreators(data.trendingCreators.map((c) => ({
          id: c.id,
          nickname: c.nickname,
          subscriberCount: c.subscriberCount,
          introduction: c.introduction,
          profileImageUrl: c.profileImageUrl,
        })));
        setTrendingPaidPosts(data.popularPaidPosts.map(toPostItem));
        setFreePosts(data.popularFreePosts.map(toPostItem));
      })
      .catch(console.error)
      .finally(() => setIsLoading(false));

    return () => clearInterval(timer);
  }, []);

    const toggleLike = async (
        setter: React.Dispatch<React.SetStateAction<PostItem[]>>,
        postId: number,
        currentlyLiked: boolean
    ) => {
        if (!isLoggedIn) return;
        try {
            if (currentlyLiked) {
                await apiDelete(`/api/posts/${postId}/likes`);
            } else {
                await apiPost(`/api/posts/${postId}/likes`);
            }
            setter((prev) =>
                prev.map((p) =>
                    p.id === postId
                        ? {
                            ...p,
                            isLiked: !currentlyLiked,
                            likeCount: currentlyLiked ? p.likeCount - 1 : p.likeCount + 1
                        }
                        : p
                )
            );
        } catch (err) {
            console.error(err);
        }
    };

    const toggleBookmark = async (
        setter: React.Dispatch<React.SetStateAction<PostItem[]>>,
        postId: number,
        currentlyBookmarked: boolean
    ) => {
        if (!isLoggedIn) return;
        try {
            if (currentlyBookmarked) {
                await apiDelete(`/api/posts/${postId}/bookmarks`);
            } else {
                await apiPost(`/api/posts/${postId}/bookmarks`);
            }
            setter((prev) =>
                prev.map((p) =>
                    p.id === postId
                        ? {
                            ...p,
                            isBookmarked: !currentlyBookmarked,
                            bookmarkCount: currentlyBookmarked ? p.bookmarkCount - 1 : p.bookmarkCount + 1
                        }
                        : p
                )
            );
        } catch (err) {
            console.error(err);
        }
    };

  // 옵저버 훅 연결
  const { showLeft: premiumLeft, showRight: premiumRight } = useCarouselObserver(premiumRef, [isLoading, trendingPaidPosts]);
  const { showLeft: freeLeft, showRight: freeRight } = useCarouselObserver(freeRef, [isLoading, freePosts]);

  // 무한 스크롤 상태 관리
  const [recentPosts, setRecentPosts] = useState<PostItem[]>([]);
  const [isLoadingMore, setIsLoadingMore] = useState(false);
  const isLoadingMoreRef = useRef(false);
  const hasMoreRef = useRef(true);
  const recentPageRef = useRef(0);
  const infiniteObserverRef = useRef<IntersectionObserver | null>(null);

  const fetchNextRecentPage = useCallback(() => {
    if (isLoadingMoreRef.current || !hasMoreRef.current) return;
    isLoadingMoreRef.current = true;
    setIsLoadingMore(true);
    apiFetch<SliceResponse>(`/api/posts?page=${recentPageRef.current}&size=10&sort=id,desc`)
      .then((data) => {
        setRecentPosts(prev => {
          const existingIds = new Set(prev.map(p => p.id));
          return [...prev, ...data.content.filter((p: PostItem) => !existingIds.has(p.id))];
        });
        hasMoreRef.current = !data.last;
        recentPageRef.current += 1;
      })
      .catch((err) => {
        console.error(err);
        setRecentError(true);
      })
      .finally(() => {
        isLoadingMoreRef.current = false;
        setIsLoadingMore(false);
      });
  }, []);

  useEffect(() => {
    fetchNextRecentPage();
  }, [fetchNextRecentPage]);

  const observerTargetRef = useCallback((node: HTMLDivElement | null) => {
    if (infiniteObserverRef.current) infiniteObserverRef.current.disconnect();
    if (!node) return;
    infiniteObserverRef.current = new IntersectionObserver(
      (entries) => {
        if (entries[0].isIntersecting) fetchNextRecentPage();
      },
      { threshold: 0.1 }
    );
    infiniteObserverRef.current.observe(node);
  }, [fetchNextRecentPage]);

  // 화면 가로 길이(한 페이지) 기준으로 스크롤 이동
  const scrollByPage = (ref: React.RefObject<HTMLDivElement | null>, direction: "left" | "right") => {
    if (ref.current) {
      const scrollAmount = ref.current.clientWidth - 100; // 살짝 겹치게 100px 마진
      const amount = direction === "left" ? -scrollAmount : scrollAmount;
      ref.current.scrollBy({ left: amount, behavior: "smooth" });
    }
  };

  // 화살표 유무에 따라 좌우 안개 마스킹(Edge Masking)을 동적으로 렌더링하는 헬퍼 함수
  const getMaskClass = (showLeft: boolean, showRight: boolean) => {
    if (showLeft && showRight) return "[mask-image:linear-gradient(to_right,transparent_0%,black_1.5%,black_98.5%,transparent_100%)]";
    if (showLeft && !showRight) return "[mask-image:linear-gradient(to_right,transparent_0%,black_1.5%,black_100%)]";
    if (!showLeft && showRight) return "[mask-image:linear-gradient(to_right,black_0%,black_98.5%,transparent_100%)]";
    return ""; // 둘 다 없으면 마스킹 없음
  };

  return (
    <>
      {/* --- 1. Cinematic Hero & Giant Search --- */}
        <section className="relative w-full bg-neutral-900 pb-20 pt-28 text-white overflow-hidden">
          {/* Subtle Background Glow */}
          <div className={cn("absolute left-1/2 top-0 -translate-x-1/2 w-full max-w-5xl h-full blur-[120px] pointer-events-none rounded-full transition-colors duration-1000", HERO_ITEMS[heroIdx].glow)} />
          
          <div className="relative mx-auto flex max-w-[1440px] flex-col items-center px-4 sm:px-6 lg:px-8 xl:px-12 text-center">
            <span className="mb-6 flex items-center gap-2 rounded-full border border-white/20 bg-white/10 px-4 py-1.5 text-xs font-bold text-white backdrop-blur-md">
              <Sparkles className="h-3.5 w-3.5 text-primary" />
              현업 개발자들의 지식 구독 플랫폼
            </span>
            <h1 className="mb-6 text-4xl font-extrabold tracking-tight sm:text-5xl md:text-6xl leading-tight h-[96px] md:h-[144px]">
              <span className="inline-grid">
                <AnimatePresence mode="wait">
                  <motion.span
                    key={heroIdx}
                    initial={{ y: 20, opacity: 0 }}
                    animate={{ y: 0, opacity: 1 }}
                    exit={{ y: -20, opacity: 0 }}
                    transition={{ type: "spring", bounce: 0, duration: 0.5 }}
                    className="col-start-1 row-start-1"
                  >
                    {HERO_ITEMS[heroIdx].text}
                  </motion.span>
                </AnimatePresence>
              </span><br />
              <span className={cn("text-transparent bg-clip-text bg-gradient-to-r transition-all duration-1000", HERO_ITEMS[heroIdx].color)}>COMMIT</span>에서 만나보세요
            </h1>
            <p className="mb-12 max-w-2xl text-lg text-neutral-400 font-medium tracking-tight break-keep">
              현업 개발자들이 쌓아온 노하우를 글로 만나고, 마음에 드는 창작자를 팔로우하거나 멤버십으로 구독하세요.
            </p>
            
            <div className="mt-8 flex flex-wrap items-center justify-center gap-3 text-sm font-semibold text-neutral-400 h-[36px]">
              <span>추천 검색어:</span>
              <AnimatePresence mode="popLayout">
                {HERO_ITEMS[heroIdx].tags.map((tag) => (
                  <motion.button 
                    key={heroIdx + tag}
                    initial={{ opacity: 0, scale: 0.9 }}
                    animate={{ opacity: 1, scale: 1 }}
                    exit={{ opacity: 0, scale: 0.9 }}
                    transition={{ duration: 0.3 }}
                    onClick={() => router.push(`/search?q=${encodeURIComponent(tag.replace('#', ''))}`)}
                    className="rounded-full bg-neutral-800 px-4 py-1.5 hover:bg-neutral-700 hover:text-white transition-colors"
                  >
                    {tag}
                  </motion.button>
                ))}
              </AnimatePresence>
            </div>
          </div>
        </section>

        {/* --- 2. Top Creators (Premium Cards - ERD Compliant) --- */}
        <section className="mx-auto mt-20 max-w-[1440px] px-4 sm:px-6 lg:px-8 xl:px-12 relative z-10">
          <div className="mb-6 flex items-center justify-between">
            <h2 className="text-2xl font-bold tracking-tight text-neutral-dark flex items-center gap-2">
              <TrendingUp className="h-6 w-6 text-primary" /> 
              지금 뜨는 창작자
            </h2>
          </div>
          
          <div className="grid grid-cols-2 md:grid-cols-5 gap-4 pb-6 pt-2">
              {topCreators.map((creator) => (
              <CreatorCard
                key={creator.id}
                id={creator.id}
                nickname={creator.nickname}
                subscriberCount={creator.subscriberCount}
                introduction={creator.introduction}
                profileImageUrl={creator.profileImageUrl}
                className="w-full"
              />
            ))}
          </div>
        </section>

        {/* --- 3. Trending Paid (Premium Carousel - Full Bleed) --- */}
        <section className="mx-auto mt-20 mb-8 max-w-[1440px] px-4 sm:px-6 lg:px-8 xl:px-12 relative">
          <div className="mb-6 flex items-end justify-between">
            <div>
              <h2 className="text-2xl font-bold tracking-tight text-neutral-dark flex items-center gap-2">
                <Zap className="h-6 w-6 text-amber-500 fill-amber-500/20" />
                실시간 인기 멤버십 
              </h2>
              <p className="mt-1 text-sm text-neutral-meta">구독자들이 가장 많이 읽고 있는 압도적 퀄리티의 포스트</p>
            </div>
            {/* 데스크탑 전용 좌우 네비게이션 버튼 (제목 우측 이동) */}
            <div className="hidden md:flex items-center gap-2">
              <button 
                onClick={() => scrollByPage(premiumRef, "left")} 
                disabled={!premiumLeft}
                className={cn(
                  "flex h-10 w-10 items-center justify-center rounded-full border border-neutral-200 bg-white text-neutral-600 transition-all",
                  premiumLeft ? "hover:bg-neutral-100 hover:text-neutral-900 shadow-sm hover:scale-105" : "opacity-30 cursor-not-allowed"
                )}
              >
                <ChevronLeft className="h-5 w-5" />
              </button>
              <button 
                onClick={() => scrollByPage(premiumRef, "right")} 
                disabled={!premiumRight}
                className={cn(
                  "flex h-10 w-10 items-center justify-center rounded-full border border-neutral-200 bg-white text-neutral-600 transition-all",
                  premiumRight ? "hover:bg-neutral-100 hover:text-neutral-900 shadow-sm hover:scale-105" : "opacity-30 cursor-not-allowed"
                )}
              >
                <ChevronRight className="h-5 w-5" />
              </button>
            </div>
          </div>
          
          {/* Horizontal Scroll for premium content (Edge Masking + overflow-hidden on desktop) */}
          <div ref={premiumRef} className={cn("flex gap-6 overflow-x-auto md:overflow-hidden scrollbar-hide snap-x relative", getMaskClass(premiumLeft, premiumRight))}>
            <div data-carousel-dummy="left" className="w-[1px] shrink-0 opacity-0 pointer-events-none -mr-6" />
            {isLoading ? (
              Array.from({ length: 10 }).map((_, i) => (
                <div key={i} className="w-[260px] sm:w-[280px] flex-none snap-start">
                  <ContentCardSkeleton />
                </div>
              ))
            ) : (
              trendingPaidPosts.map((post) => (
                <div key={`trending-${post.id}`} className="w-[260px] sm:w-[280px] flex-none snap-start">
                    <ContentCard
                        {...toCardProps(post)}
                        onLike={() => toggleLike(setTrendingPaidPosts, post.id, post.isLiked)}
                        onBookmark={() => toggleBookmark(setTrendingPaidPosts, post.id, post.isBookmarked)}
                    />
                </div>
              ))
            )}
            <div data-carousel-dummy="right" className="w-[1px] shrink-0 opacity-0 pointer-events-none -ml-6" />
          </div>
        </section>

        {/* --- 4. Free Content Carousel (Full Bleed) --- */}
        <section className="mx-auto mt-12 mb-8 max-w-[1440px] px-4 sm:px-6 lg:px-8 xl:px-12 relative">
          <div className="mb-6 flex items-end justify-between">
            <div>
              <h2 className="text-2xl font-bold tracking-tight text-neutral-dark flex items-center gap-2">
                <Sparkles className="h-6 w-6 text-emerald-500 fill-emerald-500/20" />
                0원으로 시작하는 노하우
              </h2>
              <p className="mt-1 text-sm text-neutral-meta">누구나 조건 없이 바로 읽을 수 있는 무료 공개 포스트</p>
            </div>
            {/* 데스크탑 전용 좌우 네비게이션 버튼 (제목 우측 이동) */}
            <div className="hidden md:flex items-center gap-2">
              <button 
                onClick={() => scrollByPage(freeRef, "left")} 
                disabled={!freeLeft}
                className={cn(
                  "flex h-10 w-10 items-center justify-center rounded-full border border-neutral-200 bg-white text-neutral-600 transition-all",
                  freeLeft ? "hover:bg-neutral-100 hover:text-neutral-900 shadow-sm hover:scale-105" : "opacity-30 cursor-not-allowed"
                )}
              >
                <ChevronLeft className="h-5 w-5" />
              </button>
              <button 
                onClick={() => scrollByPage(freeRef, "right")} 
                disabled={!freeRight}
                className={cn(
                  "flex h-10 w-10 items-center justify-center rounded-full border border-neutral-200 bg-white text-neutral-600 transition-all",
                  freeRight ? "hover:bg-neutral-100 hover:text-neutral-900 shadow-sm hover:scale-105" : "opacity-30 cursor-not-allowed"
                )}
              >
                <ChevronRight className="h-5 w-5" />
              </button>
            </div>
          </div>

          <div ref={freeRef} className={cn("flex gap-6 overflow-x-auto md:overflow-hidden scrollbar-hide snap-x relative", getMaskClass(freeLeft, freeRight))}>
            <div data-carousel-dummy="left" className="w-[1px] shrink-0 opacity-0 pointer-events-none -mr-6" />
            {isLoading ? (
              Array.from({ length: 10 }).map((_, i) => (
                <div key={i} className="w-[260px] sm:w-[280px] flex-none snap-start">
                  <ContentCardSkeleton />
                </div>
              ))
            ) : (
              freePosts.map((post) => (
                <div key={`free-${post.id}`} className="w-[260px] sm:w-[280px] flex-none snap-start">
                    <ContentCard
                        {...toCardProps(post)}
                        onLike={() => toggleLike(setFreePosts, post.id, post.isLiked)}
                        onBookmark={() => toggleBookmark(setFreePosts, post.id, post.isBookmarked)}
                    />
                </div>
              ))
            )}
            <div data-carousel-dummy="right" className="w-[1px] shrink-0 opacity-0 pointer-events-none -ml-6" />
          </div>
        </section>

        {/* --- 5. All Content Grid --- */}
        <section className="mx-auto mt-16 max-w-[1440px] px-4 sm:px-6 lg:px-8 xl:px-12 border-t border-neutral-200/60 pt-16">
          <div className="mb-8 flex items-end justify-between">
            <div>
              <h2 className="text-2xl font-bold tracking-tight text-neutral-dark">
                최근 업데이트
              </h2>
            </div>
            <Link href="/posts" className="text-sm font-bold text-neutral-meta hover:text-primary">전체보기</Link>
          </div>
          
          {recentError ? (
            <div className="flex flex-col items-center justify-center py-16 text-neutral-meta">
              <p className="text-lg font-medium">게시글을 불러오지 못했습니다.</p>
              <p className="mt-1 text-sm">잠시 후 다시 시도해 주세요.</p>
            </div>
          ) : (
            <>
            <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5 gap-6">
              {isLoading ? (
                Array.from({ length: 10 }).map((_, i) => (
                  <ContentCardSkeleton key={i} />
                ))
              ) : recentPosts.length === 0 ? (
                <div className="col-span-full flex flex-col items-center justify-center py-16 text-neutral-meta">
                  <p className="text-lg font-medium">아직 게시글이 없습니다.</p>
                  <p className="mt-1 text-sm">첫 번째 글을 작성해 보세요!</p>
                </div>
              ) : (
                recentPosts.map((post, index) => {
                  if (recentPosts.length === index + 1) {
                    return (
                        <div key={post.id} className="w-full">
                          <ContentCard
                              {...toCardProps(post)}
                              onLike={() => toggleLike(setRecentPosts, post.id, post.isLiked)}
                              onBookmark={() => toggleBookmark(setRecentPosts, post.id, post.isBookmarked)}
                          />
                      </div>
                    );
                  } else {
                      return (
                          <ContentCard
                              key={post.id}
                              {...toCardProps(post)}
                              onLike={() => toggleLike(setRecentPosts, post.id, post.isLiked)}
                              onBookmark={() => toggleBookmark(setRecentPosts, post.id, post.isBookmarked)}
                          />
                      );
                  }
                })
              )}
              {isLoadingMore && (
                Array.from({ length: 5 }).map((_, i) => (
                  <ContentCardSkeleton key={`loading-${i}`} />
                ))
              )}
            </div>
            <div ref={observerTargetRef} className="h-10 mt-4" />
            </>
          )}
        </section>

        {/* --- 6. Bottom CTA (Full-width Soft Background) --- */}
        {/* hasMounted 가드: 서버는 로그인 여부를 몰라 항상 비로그인으로 렌더링하므로,
            하이드레이션 이전엔 isLoggedIn을 그대로 쓰면 서버 HTML과 어긋납니다. */}
        {hasMounted && !isLoggedIn && (
          <section className="w-full mt-32 bg-[#ebebeb] pt-28 pb-32">
            <div className="mx-auto max-w-[1000px] px-4 sm:px-6 lg:px-8 xl:px-12">
              <div className="flex flex-col items-center justify-center text-center">
                <h2 className="mb-4 text-3xl md:text-[40px] font-extrabold tracking-tight text-neutral-900 leading-tight">
                  가장 깊이 있는 개발 인사이트, <br className="hidden md:block" />
                  지금 바로 내 것으로 만드세요.
                </h2>
                <p className="mb-10 text-lg text-neutral-500 font-medium">
                  상위 1% 창작자들의 실무 노하우를 구독하고 커리어를 성장시키세요.
                </p>
                <Link href="/users/signup">
                  <Button size="lg" className="rounded-full px-12 py-7 text-[17px] font-bold bg-neutral-900 text-white hover:bg-black hover:scale-105 hover:shadow-2xl hover:shadow-neutral-900/20 transition-all duration-300">
                    이메일로 3초 만에 시작하기
                  </Button>
                </Link>
              </div>
            </div>
          </section>
        )}

    </>
  );
}
