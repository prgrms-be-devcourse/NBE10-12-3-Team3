"use client";

import React, { useState, useEffect } from "react";
import { motion } from "framer-motion";
import { useRouter } from "next/navigation";
import { ProfileHeader } from "@/components/mypage/profile-header";
import { ProfileHeaderSkeleton } from "@/components/mypage/profile-header-skeleton";
import { SubscriptionList } from "@/components/mypage/subscription-list";
import { MySeriesList } from "@/components/mypage/my-series-list";
import { MyPostList } from "@/components/mypage/my-post-list";
import { PaymentHistoryList } from "@/components/mypage/payment-history-list";
import { cn } from "@/lib/utils";
import { useAuth } from "@/providers/auth-provider";
import { apiFetch, ApiError } from "@/lib/api";

const TABS = [
  { id: "subscriptions", label: "내 구독 목록" },
  { id: "posts", label: "내 포스트" },
  { id: "series", label: "내 시리즈" },
  { id: "payments", label: "결제 내역" },
];

export default function MyPage() {
  const [activeTab, setActiveTab] = useState(TABS[0].id);
  const { user, isLoggedIn, isAuthLoading } = useAuth();
  const [followerCount, setFollowerCount] = useState(0);
  const router = useRouter();

  useEffect(() => {
    if (isAuthLoading) return;
    if (!isLoggedIn) {
      router.push("/users/login");
      return;
    }
    apiFetch<number>("/api/subscriptions/followers/count")
      .then(setFollowerCount)
      .catch((e) => {
        if (e instanceof ApiError && e.status === 401) {
          router.push("/users/login");
          return;
        }
        console.error(e);
      });
  }, [isAuthLoading, isLoggedIn, router]);

  return (
    <div className="min-h-screen bg-[#F8F9FA] pb-24">
      {/* Background Decor */}
      <div className="absolute top-0 left-0 w-full h-[400px] bg-gradient-to-b from-white to-transparent pointer-events-none" />

      <main className="max-w-[1024px] mx-auto px-4 sm:px-6 pt-12 md:pt-20 relative z-10">
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.5, ease: "easeOut" }}
          className="flex flex-col gap-10"
        >
          {/* Top: Profile Info */}
          <section>
            {isAuthLoading ? (
              <ProfileHeaderSkeleton />
            ) : (
              <ProfileHeader
                nickname={user?.nickname || "테스트 유저"}
                email={user?.email || "로그인해주세요"}
                followerCount={followerCount}
                avatarUrl={user?.avatarUrl}
              />
            )}
          </section>

          {/* Middle: Tabs */}
          <section className="flex items-center gap-6 border-b border-neutral-200">
            {TABS.map((tab) => {
              const isActive = activeTab === tab.id;
              return (
                <button
                  key={tab.id}
                  onClick={() => setActiveTab(tab.id)}
                  className={cn(
                    "relative pb-4 px-2 text-[15px] font-bold transition-colors duration-200",
                    isActive ? "text-primary" : "text-neutral-400 hover:text-neutral-700"
                  )}
                >
                  {tab.label}
                  {isActive && (
                    <motion.div
                      layoutId="mypage-tab-indicator"
                      className="absolute bottom-0 left-0 right-0 h-[3px] bg-primary rounded-t-full"
                      transition={{ type: "spring", stiffness: 400, damping: 30 }}
                    />
                  )}
                </button>
              );
            })}
          </section>

          {/* Bottom: Tab Content */}
          <section className="min-h-[400px]">
            <motion.div
              key={activeTab}
              initial={{ opacity: 0, y: 10 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.3 }}
            >
              {activeTab === "subscriptions" && <SubscriptionList />}
              
              {activeTab === "posts" && <MyPostList />}

              {activeTab === "series" && <MySeriesList />}

              {activeTab === "payments" && <PaymentHistoryList />}
            </motion.div>
          </section>
        </motion.div>
      </main>
    </div>
  );
}
