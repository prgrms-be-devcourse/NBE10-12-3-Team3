"use client";

import React, { useState } from "react";
import { HeatmapPoint } from "@/lib/dashboard-api";
import { Calendar } from "lucide-react";

interface ActivityHeatmapProps {
  data: HeatmapPoint[];
}

export function ActivityHeatmap({ data }: ActivityHeatmapProps) {
  const [hoveredData, setHoveredData] = useState<{ date: string; count: number } | null>(null);

  const totalCount = data.reduce((acc, curr) => acc + curr.count, 0);

  const getCellColor = (count: number) => {
    if (count === 0) return "bg-neutral-100 border-neutral-200/50";
    if (count === 1) return "bg-emerald-200 border-emerald-300";
    if (count === 2) return "bg-emerald-400 border-emerald-500";
    if (count <= 4) return "bg-emerald-600 border-emerald-700";
    return "bg-emerald-800 border-emerald-900";
  };

  // 정확히 52주(52 columns x 7 rows = 364일 1년 완전체) 데이터 분할
  const weeks: HeatmapPoint[][] = [];
  const chunkSize = 7;
  for (let i = 0; i < data.length; i += chunkSize) {
    weeks.push(data.slice(i, i + chunkSize));
  }

  const DAY_LABELS = ["일", "월", "화", "수", "목", "금", "토"];

  return (
    <div className="rounded-2xl border border-neutral-200/80 bg-white p-6 shadow-sm flex flex-col justify-between h-full">
      {/* Top Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 mb-4 border-b border-neutral-100 pb-3">
        <div>
          <h3 className="text-base font-bold text-neutral-900 flex items-center gap-2">
            <Calendar className="h-4 w-4 text-emerald-600" /> 작성 이력 잔디밭
          </h3>
          <p className="text-xs font-medium text-neutral-500 mt-0.5">
            최근 1년(52주)간 총 <strong className="text-emerald-700 font-bold">{totalCount}회</strong>의 작성 활동 (초안·수정 포함)
          </p>
        </div>

        {/* Hover Tooltip Info Box */}
        <div className="h-7 px-3 py-1 rounded-md bg-neutral-900 text-white text-xs font-medium flex items-center gap-1.5 transition-all self-start sm:self-auto shrink-0">
          {hoveredData ? (
            <span>
              {hoveredData.date}: <strong className="text-emerald-400">{hoveredData.count}개 작성</strong>
            </span>
          ) : (
            <span className="text-neutral-400">타일에 마우스를 올려보세요</span>
          )}
        </div>
      </div>

      {/* Heatmap Grid Container: 52개 컬럼이 절댓값 잘림 없이 100% 모두 노출되도록 Grid 적용 */}
      <div className="my-auto py-2 w-full">
        <div className="grid grid-cols-[auto_repeat(52,minmax(0,1fr))] gap-[2px] sm:gap-[3px] w-full select-none items-center">
          {/* Day of Week Labels */}
          <div className="grid grid-rows-7 gap-[2px] sm:gap-[3px] text-[9px] font-bold text-neutral-400 pr-1 shrink-0">
            {DAY_LABELS.map((d, i) => (
              <span key={i} className="h-2.5 sm:h-3 flex items-center justify-end leading-none">
                {i % 2 === 1 ? d : ""}
              </span>
            ))}
          </div>

          {/* 52 Week Columns (정확히 52개 컬럼 100% 모두 노출) */}
          {weeks.map((week, wIdx) => (
            <div key={wIdx} className="grid grid-rows-7 gap-[2px] sm:gap-[3px]">
              {week.map((item) => (
                <div
                  key={item.date}
                  onMouseEnter={() => setHoveredData(item)}
                  onMouseLeave={() => setHoveredData(null)}
                  className={`w-full aspect-square rounded-[2px] border transition-transform duration-150 hover:scale-125 cursor-pointer ${getCellColor(
                    item.count
                  )}`}
                />
              ))}
            </div>
          ))}
        </div>
      </div>

      {/* Legend Footer */}
      <div className="mt-4 flex items-center justify-between text-xs text-neutral-500 pt-3 border-t border-neutral-100">
        <span className="font-medium text-neutral-400">꾸준한 커밋이 성장의 열쇠입니다</span>
        <div className="flex items-center gap-1.5 text-[11px]">
          <span>적음</span>
          <div className="h-3 w-3 rounded-[2px] bg-neutral-100 border border-neutral-200" />
          <div className="h-3 w-3 rounded-[2px] bg-emerald-200 border border-emerald-300" />
          <div className="h-3 w-3 rounded-[2px] bg-emerald-400 border border-emerald-500" />
          <div className="h-3 w-3 rounded-[2px] bg-emerald-600 border border-emerald-700" />
          <div className="h-3 w-3 rounded-[2px] bg-emerald-800 border border-emerald-900" />
          <span>많음</span>
        </div>
      </div>
    </div>
  );
}
