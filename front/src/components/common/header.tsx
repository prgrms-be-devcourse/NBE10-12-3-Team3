"use client";

import React, {KeyboardEvent, useEffect, useRef, useState} from "react";
import Link from "next/link";
import {usePathname, useRouter, useSearchParams} from "next/navigation";
import {
    Bell,
    Bookmark,
    ChevronDown,
    Clock,
    History,
    LogOut,
    Menu,
    Pencil,
    Search,
    Settings,
    User,
    X
} from "lucide-react";
import {useNotifications} from "@/providers/notification-provider";
import {Button} from "@/components/ui/button";
import {Avatar} from "@/components/ui/avatar";
import {useAuth} from "@/providers/auth-provider";
import {AnimatePresence, motion} from "framer-motion";
import {useRecentSearches} from "@/hooks/use-recent-searches";
import {cn} from "@/lib/utils";

// --- Custom Hook for Click Outside ---
function useClickOutside(ref: React.RefObject<HTMLElement | null>, handler: () => void) {
  useEffect(() => {
    const listener = (event: MouseEvent | TouchEvent) => {
      if (!ref.current || ref.current.contains(event.target as Node)) {
        return;
      }
      handler();
    };
    document.addEventListener("mousedown", listener);
    document.addEventListener("touchstart", listener);
    return () => {
      document.removeEventListener("mousedown", listener);
      document.removeEventListener("touchstart", listener);
    };
  }, [ref, handler]);
}

export function Header() {
  const pathname = usePathname();
  const router = useRouter();
  const searchParams = useSearchParams();
  const { isLoggedIn, user, logout } = useAuth();
    const {toasts} = useNotifications();
  
  const [isDropdownOpen, setIsDropdownOpen] = useState(false);
  const profileDropdownRef = useRef<HTMLDivElement>(null);
  useClickOutside(profileDropdownRef, () => setIsDropdownOpen(false));

  // --- Header Search Logic ---
  const [searchQuery, setSearchQuery] = useState("");
  const [isSearchFocused, setIsSearchFocused] = useState(false);
  const [showSearchDropdown, setShowSearchDropdown] = useState(false);
  const [focusedSearchIndex, setFocusedSearchIndex] = useState<number>(-1);

  // URL 쿼리와 검색어 동기화 (검색 후 다른 페이지 이동 시 잔류 현상 해결)
  useEffect(() => {
    const timer = setTimeout(() => {
      if (pathname === "/search") {
        setSearchQuery(searchParams?.get("q") || "");
      } else {
        setSearchQuery("");
      }
    }, 0);
    return () => clearTimeout(timer);
  }, [pathname, searchParams]);
  const [isMounted, setIsMounted] = useState(false);
  // isMounted 가드: 서버는 로그인 여부를 알 수 없어 항상 비로그인 상태로 렌더링하므로,
  // 하이드레이션 이전(첫 클라이언트 렌더)에도 isLoggedIn을 그대로 쓰면 서버 HTML과
  // 달라져 hydration mismatch가 납니다. 마운트 후에만 실제 로그인 상태를 반영합니다.
  const isAuthReady = isMounted && isLoggedIn;

  const searchContainerRef = useRef<HTMLFormElement>(null);
  const searchInputRef = useRef<HTMLInputElement>(null);
  const { recentSearches, addSearchTerm, removeSearchTerm } = useRecentSearches();

  useEffect(() => {
    const timer = setTimeout(() => {
      setIsMounted(true);
    }, 0);
    return () => clearTimeout(timer);
  }, []);

  useClickOutside(searchContainerRef, () => {
    setShowSearchDropdown(false);
    setIsSearchFocused(false);
    setFocusedSearchIndex(-1);
  });

  const handleSearchSubmit = (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    if (searchQuery.trim()) {
      addSearchTerm(searchQuery.trim());
      setShowSearchDropdown(false);
      searchInputRef.current?.blur();
      router.push(`/search?q=${encodeURIComponent(searchQuery.trim())}`);
    }
  };

  const onRecentSearchClick = (keyword: string) => {
    setSearchQuery(keyword);
    addSearchTerm(keyword);
    setShowSearchDropdown(false);
    router.push(`/search?q=${encodeURIComponent(keyword)}`);
  };

  const handleSearchKeyDown = (e: KeyboardEvent<HTMLInputElement>) => {
    if (!showSearchDropdown || !isMounted) return;

    if (e.key === "ArrowDown") {
      e.preventDefault();
      setFocusedSearchIndex((prev) => (prev < recentSearches.length - 1 ? prev + 1 : prev));
    } else if (e.key === "ArrowUp") {
      e.preventDefault();
      setFocusedSearchIndex((prev) => (prev > 0 ? prev - 1 : -1));
    } else if (e.key === "Escape") {
      e.preventDefault();
      setShowSearchDropdown(false);
      setIsSearchFocused(false);
      setFocusedSearchIndex(-1);
      searchInputRef.current?.blur();
    } else if (e.key === "Enter" && focusedSearchIndex >= 0) {
      e.preventDefault();
      onRecentSearchClick(recentSearches[focusedSearchIndex]);
    }
  };

  const handleDummyClick = (e: React.MouseEvent, menuName: string) => {
    e.preventDefault();
    alert(`🚀 [${menuName}] 기능은 백엔드 연동과 함께 곧 오픈됩니다!`);
    setIsDropdownOpen(false);
  };

  return (
    <header className="sticky top-0 z-50 w-full border-b border-neutral-200/60 bg-white/80 backdrop-blur-xl">
      {/* 1440px 확장 */}
      <div className="mx-auto flex h-[72px] max-w-[1440px] items-center justify-between px-4 sm:px-6 lg:px-8 xl:px-12">
        
        {/* Left: Logo & Main Navigation */}
        <div className="flex items-center gap-10">
          <Link href="/" className="flex items-center gap-2.5 group">
            <div className="h-8 w-8 rounded-[8px] overflow-hidden border border-neutral-800/10 shadow-sm transition-transform duration-200 group-hover:scale-105 group-active:scale-95">
              <img 
                src="/images/app-icon.jpg" 
                alt="SCommit Icon" 
                className="h-full w-full object-cover"
              />
            </div>
            <span className="text-xl font-black tracking-normal text-neutral-dark select-none">COMMIT</span>
          </Link>

          {/* ERD 기반 GNB */}
          <nav className="hidden md:flex items-center gap-8">
            <Link href="/posts" className="text-[15px] font-bold text-neutral-dark hover:text-primary transition-colors">✍️ 게시글</Link>
            <Link href="/series" className="text-[15px] font-bold text-neutral-dark hover:text-primary transition-colors">📚 시리즈</Link>
            {isAuthReady && user?.role === "ADMIN" && (
              <Link href="/admin/dashboard" className="text-[15px] font-bold text-neutral-dark hover:text-primary transition-colors">
                👑 관리자 대시보드
              </Link>
            )}
          </nav>
        </div>

        {/* Right: Actions & Auth */}
        <div className="flex items-center gap-5">
          <div className="hidden sm:flex items-center gap-4 text-neutral-dark">
            <form 
              ref={searchContainerRef}
              onSubmit={handleSearchSubmit}
              className="relative flex items-center w-[280px] rounded-full border border-neutral-300 bg-neutral-100 transition-all focus-within:ring-2 focus-within:ring-primary/20 focus-within:border-primary focus-within:bg-white"
            >
              <Search className={cn("absolute left-3 h-4 w-4 transition-colors", isSearchFocused ? "text-primary" : "text-neutral-500")} />
              <input
                ref={searchInputRef}
                type="text"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                onFocus={() => {
                  setIsSearchFocused(true);
                  setShowSearchDropdown(true);
                }}
                onKeyDown={handleSearchKeyDown}
                placeholder="검색어를 입력하세요"
                className="w-full bg-transparent py-2 pl-9 pr-8 text-sm text-neutral-dark placeholder-neutral-500 outline-none"
              />
              {searchQuery.length > 0 && (
                <button
                  type="button"
                  onClick={() => {
                    setSearchQuery("");
                    searchInputRef.current?.focus();
                  }}
                  className="absolute right-2 top-1/2 -translate-y-1/2 p-1 text-neutral-400 hover:text-neutral-700 transition-colors"
                >
                  <X className="h-4 w-4" />
                </button>
              )}

              {/* Recent Searches Dropdown */}
              {isMounted && showSearchDropdown && recentSearches.length > 0 && (
                <div className="absolute top-[110%] left-0 w-full rounded-2xl border border-neutral-200 bg-white p-3 shadow-xl animate-in slide-in-from-top-2 fade-in duration-200 z-50">
                  <div className="mb-2 px-3 flex items-center justify-between">
                    <h4 className="text-xs font-bold text-neutral-500 flex items-center gap-1.5">
                      <History className="h-3.5 w-3.5" /> 최근 검색어
                    </h4>
                  </div>
                  <ul className="flex flex-col">
                    {recentSearches.map((term, idx) => (
                      <li key={`${term}-${idx}`} className="flex items-center group/item">
                        <button
                          type="button"
                          onClick={() => onRecentSearchClick(term)}
                          className={cn(
                            "flex flex-1 items-center gap-3 rounded-xl px-3 py-2 text-left text-sm font-medium text-neutral-700 transition-colors",
                            focusedSearchIndex === idx ? "bg-neutral-100 text-primary" : "hover:bg-neutral-50 hover:text-primary"
                          )}
                        >
                          <Clock className="h-4 w-4 text-neutral-400" />
                          {term}
                        </button>
                        <button
                          type="button"
                          onClick={(e) => {
                            e.stopPropagation();
                            removeSearchTerm(term);
                          }}
                          className="p-2 text-neutral-400 opacity-0 transition-opacity hover:text-red-500 group-hover/item:opacity-100"
                          aria-label="삭제"
                        >
                          <X className="h-4 w-4" />
                        </button>
                      </li>
                    ))}
                  </ul>
                </div>
              )}
            </form>
            
            {isAuthReady && (
              <>
                <Link href="/posts/new">
                  <Button variant="filled" className="hidden sm:flex items-center gap-1.5 text-sm font-bold px-4 py-2">
                    <Pencil className="h-4 w-4" />
                    글쓰기
                  </Button>
                </Link>
                  <div className="relative">
                      <Bell className="h-5 w-5 text-neutral-600"/>
                      {toasts.length > 0 && (
                          <span className="absolute -top-0.5 -right-0.5 h-2 w-2 rounded-full bg-red-500"/>
                      )}
                  </div>
              </>
            )}
          </div>

          <div className="h-4 w-px bg-neutral-200 hidden sm:block"></div>

          {isAuthReady ? (
            <div className="flex items-center gap-4" ref={profileDropdownRef}>
              {/* Avatar & Dropdown Container */}
              <div className="relative">
                <button 
                  onClick={() => setIsDropdownOpen(!isDropdownOpen)}
                  className="flex items-center justify-center relative active:scale-[0.98] transition-transform outline-none ring-primary focus-visible:ring-2 rounded-full"
                >
                  <Avatar src={user?.avatarUrl} name={user?.nickname || "User"} className="h-9 w-9 border border-neutral-200" />
                </button>

                {/* Framer Motion Premium Dropdown */}
                <AnimatePresence>
                  {isDropdownOpen && (
                    <motion.div
                      initial={{ opacity: 0, scale: 0.95, y: 10 }}
                      animate={{ opacity: 1, scale: 1, y: 0 }}
                      exit={{ opacity: 0, scale: 0.95, y: 10 }}
                      transition={{ type: "spring", stiffness: 400, damping: 30 }}
                      className="absolute right-0 top-full mt-3 w-64 origin-top-right overflow-hidden rounded-2xl border border-neutral-200/60 bg-white p-2 shadow-2xl"
                    >
                      {/* Mini Profile Header */}
                      <div className="mb-2 rounded-xl bg-neutral-50 px-4 py-3">
                        <p className="text-sm font-bold text-neutral-dark">{user?.nickname}</p>
                        <p className="text-xs font-medium text-neutral-500">{user?.email}</p>
                      </div>

                      {/* Menu List */}
                      <div className="flex flex-col gap-0.5">
                        <Link href="/mypage" onClick={() => setIsDropdownOpen(false)} className="flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-[14px] font-semibold text-neutral-600 transition-colors hover:bg-neutral-100 hover:text-neutral-900">
                          <User className="h-4 w-4" /> 마이페이지
                        </Link>
                        <Link
                          href="/bookmarks"
                          onClick={() => setIsDropdownOpen(false)}
                          className="flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-[14px] font-semibold text-neutral-600 transition-colors hover:bg-neutral-100 hover:text-neutral-900"
                        >
                          <Bookmark className="h-4 w-4" /> 내 북마크
                        </Link>

                        <div className="my-1 h-px w-full bg-neutral-100" />

                        <button className="flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-[14px] font-semibold text-neutral-600 transition-colors hover:bg-neutral-100 hover:text-neutral-900">
                          <Settings className="h-4 w-4" /> 설정
                        </button>
                        <button
                          onClick={() => {
                            logout();
                            setIsDropdownOpen(false);
                          }}
                          className="flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-[14px] font-semibold text-red-600 transition-colors hover:bg-red-50 hover:text-red-700"
                        >
                          <LogOut className="h-4 w-4" /> 로그아웃
                        </button>
                      </div>
                    </motion.div>
                  )}
                </AnimatePresence>
              </div>
            </div>
          ) : (
            <div className="flex items-center gap-3">
              <Link href="/users/login" className="hidden sm:block text-sm font-bold text-neutral-dark hover:text-primary transition-colors">
                로그인
              </Link>
              <Link href="/users/signup">
                <Button size="sm" className="rounded-full font-bold px-5">
                  회원가입
                </Button>
              </Link>
            </div>
          )}

          {/* Mobile Menu */}
          <button className="md:hidden p-1.5 text-neutral-dark hover:bg-neutral-100 rounded-full transition-colors">
            <Menu className="h-6 w-6" />
          </button>
        </div>
        
      </div>
    </header>
  );
}
