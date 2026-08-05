"use client";

import React from "react";
import { CreatorRadarChart } from "@/lib/dashboard-api";
import { Compass } from "lucide-react";
import { formatDecimal } from "@/lib/format";

interface ActivityRadarChartProps {
  data: CreatorRadarChart;
}

export function ActivityRadarChart({ data }: ActivityRadarChartProps) {
  const axes = [
    { label: "포스트 작성", value: data.postWriteRate },
    { label: "시리즈 구축", value: data.seriesBuildRate },
    { label: "댓글 소통", value: data.commentRate },
    { label: "반응 (좋아요)", value: data.reactionRate },
    { label: "구독/팔로우", value: data.subscriptionRate },
  ];

  // SVG viewBox 400x330 & 반지름 115px
  // 차트 스케일 상한선(MAX_SCALE)을 40%로 설정하여 백엔드 COUNT API(합계 100% 비중)와 100% 연동되면서도
  // 오각형 폴리곤이 차트 외곽선까지 큼직하게 꽉 채워지도록 조율
  const width = 400;
  const height = 330;
  const center = { x: width / 2, y: height / 2 + 5 };
  const radius = 115;
  const MAX_SCALE = 40; // 40%가 오방진 최외곽선(100% Radius)으로 스케일링
  const totalAxes = axes.length;

  const getCoordinates = (index: number, valPercent: number) => {
    const angle = (Math.PI * 2 / totalAxes) * index - Math.PI / 2;
    // MAX_SCALE(40%) 기준으로 반지름 비율 계산
    const clampedVal = Math.min(valPercent, MAX_SCALE);
    const r = (radius * clampedVal) / MAX_SCALE;
    const x = center.x + r * Math.cos(angle);
    const y = center.y + r * Math.sin(angle);
    return { x, y };
  };

  const topAxis = axes.reduce((max, axis) => (axis.value > max.value ? axis : max), axes[0]);

  const levels = [0.25, 0.5, 0.75, 1.0];
  const gridPolygons = levels.map((level) => {
    return axes
      .map((_, i) => {
        const { x, y } = getCoordinates(i, level * MAX_SCALE);
        return `${x},${y}`;
      })
      .join(" ");
  });

  const polygonPoints = axes
    .map((axis, i) => {
      const { x, y } = getCoordinates(i, axis.value);
      return `${x},${y}`;
    })
    .join(" ");

  return (
    <div className="rounded-2xl border border-neutral-200/80 bg-white p-6 shadow-sm flex flex-col justify-between h-full">
      {/* Top Header */}
      <div className="flex items-center justify-between mb-1">
        <div>
          <h3 className="text-base font-bold text-neutral-900 flex items-center gap-2">
            <Compass className="h-4 w-4 text-emerald-600" /> 개발자 활동 성향 분석
          </h3>
          <p className="text-xs text-neutral-500 mt-0.5">
            활동 영역별 참여 비중 (총합 100% 분석)
          </p>
        </div>
        <span className="rounded-md bg-neutral-100 px-2 py-0.5 text-[11px] font-semibold text-neutral-600">
          활동 비중
        </span>
      </div>

      {/* SVG Radar Chart: MAX_SCALE 40% 기준으로 오각형이 큼직하게 꽉 차게 표시 */}
      <div className="relative flex items-center justify-center my-auto py-2">
        <svg viewBox={`0 0 ${width} ${height}`} className="w-full max-w-[400px] h-auto overflow-visible">
          <defs>
            <radialGradient id="radarFill" cx="50%" cy="50%" r="50%">
              <stop offset="0%" stopColor="#10B981" stopOpacity="0.35" />
              <stop offset="100%" stopColor="#059669" stopOpacity="0.1" />
            </radialGradient>
          </defs>

          {/* Background Pentagons */}
          {gridPolygons.map((points, idx) => (
            <polygon
              key={idx}
              points={points}
              className="fill-none stroke-neutral-200 stroke-1"
              strokeDasharray={idx === 3 ? "none" : "3,3"}
            />
          ))}

          {/* Axis Lines */}
          {axes.map((_, i) => {
            const outer = getCoordinates(i, MAX_SCALE);
            return (
              <line
                key={i}
                x1={center.x}
                y1={center.y}
                x2={outer.x}
                y2={outer.y}
                className="stroke-neutral-200 stroke-1"
              />
            );
          })}

          {/* Data Polygon */}
          <polygon
            points={polygonPoints}
            fill="url(#radarFill)"
            className="stroke-emerald-600 stroke-[2.5]"
          />

          {/* Data Points */}
          {axes.map((axis, i) => {
            const pt = getCoordinates(i, axis.value);
            return (
              <circle
                key={i}
                cx={pt.x}
                cy={pt.y}
                r="4.5"
                className="fill-emerald-600 stroke-white stroke-2 shadow-sm"
              />
            );
          })}

          {/* Vertex Labels & Percentages */}
          {axes.map((axis, i) => {
            const labelCoord = getCoordinates(i, MAX_SCALE * 1.18);
            let textAnchor: "end" | "middle" | "start" = "middle";
            if (labelCoord.x < center.x - 25) textAnchor = "end";
            if (labelCoord.x > center.x + 25) textAnchor = "start";

            return (
              <g key={`label-${i}`} transform={`translate(${labelCoord.x}, ${labelCoord.y})`}>
                <text
                  textAnchor={textAnchor}
                  dominantBaseline="central"
                  className="text-[12px] font-bold fill-neutral-800"
                >
                  {axis.label}
                </text>
                <text
                  textAnchor={textAnchor}
                  dominantBaseline="central"
                  dy="14"
                  className="text-[11px] font-bold fill-emerald-600"
                >
                  {formatDecimal(axis.value)}%
                </text>
              </g>
            );
          })}
        </svg>
      </div>

      {/* Clean Footer Note */}
      <div className="pt-3 border-t border-neutral-100 flex items-center justify-between text-xs text-neutral-500">
        <span className="font-medium text-neutral-400">주요 특징:</span>
        <span className="font-semibold text-emerald-700">{topAxis.label} ({formatDecimal(topAxis.value)}%) 최우수 활동</span>
      </div>
    </div>
  );
}
