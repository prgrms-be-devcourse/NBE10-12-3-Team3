"use client";

import React from "react";
import { SubscriptionRatio } from "@/lib/dashboard-api";
import { PieChart, CreditCard, Users } from "lucide-react";
import { formatDecimal } from "@/lib/format";

interface DonutChartProps {
  data: SubscriptionRatio;
}

export function DonutChart({ data }: DonutChartProps) {
  const size = 200;
  const center = size / 2;
  const radius = 70;
  const strokeWidth = 24;
  const circumference = 2 * Math.PI * radius;

  const followOffset = 0;
  const followDash = (circumference * data.followPercentage) / 100;

  const membershipDash = (circumference * data.membershipPercentage) / 100;
  const membershipOffset = -followDash;

  const totalUsers = data.followCount + data.membershipCount;

  return (
    <div className="rounded-2xl border border-neutral-200/80 bg-white p-6 shadow-sm flex flex-col justify-between h-full">
      {/* Top Header */}
      <div className="flex items-center justify-between mb-2">
        <div>
          <h3 className="text-base font-bold text-neutral-900 flex items-center gap-2">
            <PieChart className="h-5 w-5 text-emerald-600" /> 구독자 유료 멤버십 전환율
          </h3>
          <p className="text-xs font-medium text-neutral-500 mt-0.5">
            창작자를 구독 중인 유저 중 유료 멤버십 결제 경험 비율
          </p>
          <p className="text-[11px] font-medium text-neutral-400 mt-1">
            ※ 선택한 기간과 무관하게 항상 현재 시점 스냅샷 기준으로 표시됩니다
          </p>
        </div>
        <span className="rounded-md bg-emerald-50 px-2.5 py-1 text-xs font-bold text-emerald-700 border border-emerald-200/60">
          전환율 {formatDecimal(data.membershipPercentage)}%
        </span>
      </div>

      {/* Donut Chart Visual */}
      <div className="relative flex items-center justify-center my-4">
        <svg width={size} height={size} className="-rotate-90 overflow-visible">
          {/* Base Track */}
          <circle
            cx={center}
            cy={center}
            r={radius}
            className="fill-none stroke-neutral-100"
            strokeWidth={strokeWidth}
          />

          {/* Non-Paying Free Users Segment (Dark Slate) */}
          <circle
            cx={center}
            cy={center}
            r={radius}
            className="fill-none stroke-neutral-800 transition-all duration-500"
            strokeWidth={strokeWidth}
            strokeDasharray={`${followDash} ${circumference}`}
            strokeDashoffset={followOffset}
            strokeLinecap="round"
          />

          {/* Paying Customers Segment (Emerald) */}
          <circle
            cx={center}
            cy={center}
            r={radius}
            className="fill-none stroke-emerald-600 transition-all duration-500"
            strokeWidth={strokeWidth}
            strokeDasharray={`${membershipDash} ${circumference}`}
            strokeDashoffset={membershipOffset}
            strokeLinecap="round"
          />
        </svg>

        {/* Center Text */}
        <div className="absolute inset-0 flex flex-col items-center justify-center text-center">
          <span className="text-2xl font-black text-neutral-900">{formatDecimal(data.membershipPercentage)}%</span>
          <span className="text-[11px] font-bold text-emerald-700">유료 전환율</span>
        </div>
      </div>

      {/* Legend & Stats */}
      <div className="grid grid-cols-2 gap-3 pt-3 border-t border-neutral-100">
        <div className="rounded-xl bg-emerald-50/60 p-2.5 flex items-center gap-2.5 border border-emerald-100">
          <div className="p-2 rounded-lg bg-emerald-600 text-white shrink-0">
            <CreditCard className="h-3.5 w-3.5" />
          </div>
          <div className="min-w-0">
            <div className="text-[11px] font-medium text-neutral-500 truncate">유료 멤버십 구독자</div>
            <div className="text-xs font-bold text-emerald-800 truncate">
              {data.membershipCount.toLocaleString()}명 ({formatDecimal(data.membershipPercentage)}%)
            </div>
          </div>
        </div>

        <div className="rounded-xl bg-neutral-50 p-2.5 flex items-center gap-2.5 border border-neutral-100">
          <div className="p-2 rounded-lg bg-neutral-800 text-white shrink-0">
            <Users className="h-3.5 w-3.5" />
          </div>
          <div className="min-w-0">
            <div className="text-[11px] font-medium text-neutral-500 truncate">무료 팔로우 구독자</div>
            <div className="text-xs font-bold text-neutral-900 truncate">
              {data.followCount.toLocaleString()}명 ({formatDecimal(data.followPercentage)}%)
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
