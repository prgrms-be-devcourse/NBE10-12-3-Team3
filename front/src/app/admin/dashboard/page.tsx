"use client";

import React from "react";
import { useRouter } from "next/navigation";
import { AdminDashboardView } from "@/components/dashboard/admin-dashboard-view";
import { useAuth } from "@/providers/auth-provider";

export default function AdminDashboardPage() {
  const { user, isAuthLoading } = useAuth();
  const router = useRouter();

  React.useEffect(() => {
    if (isAuthLoading) return;
    if (user?.role !== "ADMIN") {
      router.replace("/");
    }
  }, [isAuthLoading, user, router]);

  // 인증 확인 중이거나 관리자가 아니면 화면 자체를 그리지 않는다 —
  // AdminDashboardView가 잠깐이라도 렌더링되면 API 호출이 나가고 mock 폴백으로
  // 그럴듯한 가짜 통계가 비관리자에게 노출될 수 있으므로 아예 마운트하지 않는다.
  if (isAuthLoading || user?.role !== "ADMIN") {
    return null;
  }

  return (
    <div className="min-h-screen bg-[#F8F9FA] pb-24">
      <main className="max-w-[1440px] mx-auto px-4 sm:px-6 lg:px-8 xl:px-12 pt-8 md:pt-12">
        <AdminDashboardView />
      </main>
    </div>
  );
}
