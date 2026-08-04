"use client";

import React from "react";
import { motion } from "framer-motion";
import { LucideIcon, TrendingUp } from "lucide-react";
import { cn } from "@/lib/utils";

interface MetricCardProps {
  title: string;
  value: string | number;
  subValue?: string;
  badgeText?: string;
  badgeType?: "default" | "success" | "highlight" | "warning";
  icon: LucideIcon;
  delay?: number;
  trendText?: string;
  colorVariant?: "emerald" | "indigo" | "blue" | "rose" | "amber" | "teal";
}

export function MetricCard({
  title,
  value,
  subValue,
  badgeText,
  badgeType = "default",
  icon: Icon,
  delay = 0,
  trendText,
  colorVariant = "emerald",
}: MetricCardProps) {
  // Variant Icon & Border Styling (100% 동일한 백색 카드 + 미세 Accent Icon Box)
  const variantStyles = {
    teal: {
      iconBg: "bg-teal-50 text-teal-700 group-hover:bg-teal-100",
      borderHover: "hover:border-teal-300/80",
    },
    emerald: {
      iconBg: "bg-emerald-50 text-emerald-700 group-hover:bg-emerald-100",
      borderHover: "hover:border-emerald-300/80",
    },
    indigo: {
      iconBg: "bg-indigo-50 text-indigo-700 group-hover:bg-indigo-100",
      borderHover: "hover:border-indigo-300/80",
    },
    blue: {
      iconBg: "bg-sky-50 text-sky-700 group-hover:bg-sky-100",
      borderHover: "hover:border-sky-300/80",
    },
    rose: {
      iconBg: "bg-rose-50 text-rose-700 group-hover:bg-rose-100",
      borderHover: "hover:border-rose-300/80",
    },
    amber: {
      iconBg: "bg-amber-50 text-amber-700 group-hover:bg-amber-100",
      borderHover: "hover:border-amber-300/80",
    },
  }[colorVariant] || {
    iconBg: "bg-neutral-100/80 text-neutral-700 group-hover:bg-emerald-50 group-hover:text-emerald-700",
    borderHover: "hover:border-emerald-300/80",
  };

  return (
    <motion.div
      initial={{ opacity: 0, y: 12 }}
      animate={{ opacity: 1, y: 0 }}
      whileHover={{ y: -2, scale: 1.01 }}
      whileTap={{ scale: 0.99 }}
      transition={{ duration: 0.25, delay, type: "spring", stiffness: 300, damping: 25 }}
      className={cn(
        "relative rounded-2xl border border-neutral-200/80 bg-white p-5 shadow-sm hover:shadow-md transition-all duration-200 flex flex-col justify-between cursor-pointer group h-full",
        variantStyles.borderHover
      )}
    >
      {/* Top Header: Title & Icon */}
      <div className="flex items-center justify-between mb-3">
        <span className="text-xs font-bold text-neutral-500 uppercase tracking-wider group-hover:text-neutral-900 transition-colors">
          {title}
        </span>
        <div className={cn("p-2 rounded-xl transition-colors", variantStyles.iconBg)}>
          <Icon className="h-4 w-4" />
        </div>
      </div>

      {/* Main Metric Value */}
      <div className="space-y-1.5">
        <div className="flex items-baseline justify-between gap-2">
          <div className="text-2xl sm:text-3xl font-bold text-neutral-900 tracking-tight">
            {typeof value === "number" ? value.toLocaleString() : value}
          </div>
          {trendText && (
            <span className="inline-flex items-center gap-0.5 text-xs font-semibold text-emerald-700 bg-emerald-50 px-2 py-0.5 rounded-full border border-emerald-200/60">
              <TrendingUp className="h-3 w-3 text-emerald-600" />
              {trendText}
            </span>
          )}
        </div>

        {/* Subvalue & Badge */}
        <div className="flex items-center gap-2 text-xs font-medium text-neutral-500 flex-nowrap whitespace-nowrap overflow-hidden pt-1">
          {badgeText && (
            <span
              className={cn(
                "inline-flex items-center shrink-0 rounded-md px-2 py-0.5 text-[11px] font-semibold transition-colors",
                badgeType === "highlight" && "bg-emerald-50 text-emerald-700 border border-emerald-200/60",
                badgeType === "success" && "bg-emerald-50 text-emerald-700 border border-emerald-200/60",
                badgeType === "warning" && "bg-amber-50 text-amber-700 border border-amber-200/60",
                badgeType === "default" && "bg-neutral-100 text-neutral-600 border border-neutral-200/60"
              )}
            >
              {badgeText}
            </span>
          )}
          {subValue && <span className="text-neutral-500 truncate">{subValue}</span>}
        </div>
      </div>
    </motion.div>
  );
}
