"use client";

import React from "react";
import Link from "next/link";
import { TopPost, DashboardPeriod, getPeriodPrefix, getRankingPeriodPrefix } from "@/lib/dashboard-api";
import { AccessBadge } from "@/components/common/access-badge";
import { Eye, Heart } from "lucide-react";

interface PopularPostsChartProps {
  posts: TopPost[];
  period: DashboardPeriod;
}

export function PopularPostsChart({ posts, period }: PopularPostsChartProps) {
  const title = `${getRankingPeriodPrefix(period)} 인기 포스트 TOP 5`;
  const subtitle = `${getPeriodPrefix(period)} 조회수 기준 상위 포스트`;

  return (
    <div className="rounded-2xl border border-neutral-200/80 bg-white p-6 shadow-sm flex flex-col justify-between h-full">
      <div>
        {/* Top Header */}
        <div className="flex items-center justify-between mb-4">
          <div>
            <h3 className="text-base font-bold text-neutral-900 flex items-center gap-2">
              <Eye className="h-5 w-5 text-emerald-600" /> {title}
            </h3>
            <p className="text-xs font-medium text-neutral-500 mt-0.5">
              {subtitle}
            </p>
          </div>
          <span className="rounded-md bg-neutral-100 px-2.5 py-1 text-xs font-semibold text-neutral-700">
            포스트 랭킹
          </span>
        </div>

        {/* Flat Minimalist List Row Design (튀는 뱃지 없이 100% 플랫 정돈) */}
        <div className="space-y-2.5">
          {posts.slice(0, 5).map((post, idx) => (
            <div
              key={post.id}
              className="flex items-center justify-between p-3 rounded-xl bg-neutral-50 hover:bg-neutral-100/80 transition-colors"
            >
              <div className="flex items-center gap-3 min-w-0 flex-1">
                <span
                  className={`text-sm font-bold w-5 text-center shrink-0 ${
                    idx === 0
                      ? "text-emerald-700 font-extrabold"
                      : idx === 1
                      ? "text-neutral-800"
                      : "text-neutral-400"
                  }`}
                >
                  {idx + 1}
                </span>
                <div className="min-w-0 flex-1 flex items-center gap-2">
                  <Link
                    href={`/posts/${post.id}`}
                    className="font-bold text-sm text-neutral-900 hover:text-emerald-600 transition-colors truncate"
                  >
                    {post.title}
                  </Link>
                  <AccessBadge accessLevel={post.accessLevel} variant="compact" />
                </div>
              </div>

              <div className="text-right shrink-0 ml-3 flex items-center gap-4 text-xs font-medium text-neutral-600">
                <span className="flex items-center gap-1 text-neutral-800 font-bold">
                  <Eye className="h-3.5 w-3.5 text-neutral-400" />
                  {post.viewCount.toLocaleString()}회
                </span>
                <span className="flex items-center gap-1 text-emerald-700 font-semibold">
                  <Heart className="h-3.5 w-3.5 text-emerald-600" />
                  {post.likeCount}
                </span>
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
