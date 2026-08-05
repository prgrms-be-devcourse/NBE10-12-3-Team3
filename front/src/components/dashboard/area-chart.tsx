"use client";

import React, { useState } from "react";
import { SignupTrendPoint, DashboardPeriod } from "@/lib/dashboard-api";
import { TrendingUp } from "lucide-react";

interface AreaChartProps {
  data: SignupTrendPoint[];
  period?: DashboardPeriod;
}

export function AreaChart({ data, period = "30d" }: AreaChartProps) {
  const [activePoint, setActivePoint] = useState<SignupTrendPoint | null>(null);

  if (data.length === 0) {
    return (
      <div className="rounded-2xl border border-neutral-200/80 bg-white p-6 shadow-sm flex items-center justify-center h-full text-sm text-neutral-400">
        표시할 가입자 추이 데이터가 없습니다
      </div>
    );
  }

  const width = 500;
  const height = 220;
  const padding = 40;

  const maxVal = Math.max(...data.map((d) => d.users), 100);

  const getCoords = (idx: number, val: number) => {
    const x = padding + (idx / Math.max(data.length - 1, 1)) * (width - padding * 2);
    const y = height - padding - (val / maxVal) * (height - padding * 2);
    return { x, y };
  };

  const points = data.map((d, i) => getCoords(i, d.users));

  const pathD = points.reduce((acc, pt, i) => {
    if (i === 0) return `M ${pt.x} ${pt.y}`;
    const prev = points[i - 1];
    const cx = (prev.x + pt.x) / 2;
    return `${acc} C ${cx} ${prev.y}, ${cx} ${pt.y}, ${pt.x} ${pt.y}`;
  }, "");

  const areaD = `${pathD} L ${points[points.length - 1].x} ${height - padding} L ${points[0].x} ${height - padding} Z`;

  return (
    <div className="rounded-2xl border border-neutral-200/80 bg-white p-6 shadow-sm flex flex-col justify-between h-full">
      <div className="flex items-center justify-between mb-2">
        <div>
          <h3 className="text-lg font-bold text-neutral-900 flex items-center gap-2">
            <TrendingUp className="h-5 w-5 text-emerald-600" />
            {period === "all" ? "누적 가입자 추이" : "신규 가입자 추이"}
          </h3>
          <p className="text-xs font-medium text-neutral-500 mt-0.5">
            {period === "all"
              ? "서비스 오픈 이후 누적 유저 성장 곡선 (Area Chart)"
              : "선택 기간 내 구간별 신규 가입 곡선 (Area Chart)"}
          </p>
        </div>
        <span className="rounded-md bg-neutral-100 px-2.5 py-1 text-xs font-semibold text-neutral-700">
          가입 성장세
        </span>
      </div>

      <div className="relative flex items-center justify-center my-2">
        <svg width="100%" viewBox={`0 0 ${width} ${height}`} className="overflow-visible">
          <defs>
            <linearGradient id="areaGradient" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor="#10B981" stopOpacity="0.25" />
              <stop offset="100%" stopColor="#10B981" stopOpacity="0.0" />
            </linearGradient>
          </defs>

          {/* Grid Lines */}
          {[0, 0.33, 0.66, 1].map((ratio, i) => {
            const y = height - padding - ratio * (height - padding * 2);
            return (
              <line
                key={i}
                x1={padding}
                y1={y}
                x2={width - padding}
                y2={y}
                className="stroke-neutral-100 stroke-1"
              />
            );
          })}

          {/* Area Fill */}
          <path d={areaD} fill="url(#areaGradient)" />

          {/* Smooth Line */}
          <path d={pathD} className="fill-none stroke-emerald-600 stroke-[2.5]" />

          {/* Data Points */}
          {points.map((pt, i) => (
            <g key={i}>
              <circle
                cx={pt.x}
                cy={pt.y}
                r={activePoint === data[i] ? 6 : 4.5}
                onMouseEnter={() => setActivePoint(data[i])}
                onMouseLeave={() => setActivePoint(null)}
                className="fill-emerald-600 stroke-white stroke-2 transition-all cursor-pointer shadow-sm"
              />
              <text
                x={pt.x}
                y={height - 12}
                textAnchor="middle"
                className="text-[11px] font-semibold fill-neutral-400"
              >
                {data[i].label}
              </text>
            </g>
          ))}
        </svg>

        {/* Hover Tooltip */}
        {activePoint && (
          <div className="absolute top-2 right-4 rounded-md bg-neutral-900 px-3 py-1.5 text-xs text-white shadow-md animate-in fade-in">
            <span className="font-medium">{activePoint.label} 신규 가입: </span>
            <span className="text-emerald-400 font-bold">{activePoint.users}명</span>
          </div>
        )}
      </div>

      <div className="pt-3 border-t border-neutral-100 flex items-center justify-between text-xs text-neutral-500 font-medium">
        <span>{period === "all" ? "전체 기간 누적 가입 곡선" : "선택 구간별 신규 가입 합계"}</span>
        <span className="text-emerald-700 font-bold">
          {period === "all"
            ? `누적 ${(data[data.length - 1]?.users ?? 0).toLocaleString()}명`
            : `합계 ${data.reduce((acc, d) => acc + d.users, 0).toLocaleString()}명`}
        </span>
      </div>
    </div>
  );
}
