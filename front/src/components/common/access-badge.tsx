import React from "react";
import { Crown, Lock } from "lucide-react";

interface AccessBadgeProps {
  accessLevel: "FREE" | "PAID";
  variant?: "default" | "compact";
}

export function AccessBadge({ accessLevel, variant = "default" }: AccessBadgeProps) {
  if (variant === "compact") {
    if (accessLevel === "PAID") {
      return (
        <span className="shrink-0 inline-flex items-center gap-1 rounded bg-neutral-200/70 px-1.5 py-0.5 text-[11px] font-semibold text-neutral-700">
          <Lock className="h-3 w-3 text-amber-600" />
          멤버십
        </span>
      );
    }
    return (
      <span className="shrink-0 text-[11px] font-medium text-neutral-400">
        공개
      </span>
    );
  }

  if (accessLevel === "PAID") {
    return (
      <div className="flex items-center gap-1.5 rounded-full bg-black/85 px-3 py-1.5 backdrop-blur-md border border-white/40 shadow-lg shadow-black/40">
        <Crown className="h-3.5 w-3.5 text-amber-400" />
        <span className="text-xs font-bold text-white">멤버십 전용</span>
      </div>
    );
  }
  return (
    <div className="flex items-center gap-1.5 rounded-full bg-white/95 px-3 py-1.5 backdrop-blur-md border border-black/20 shadow-lg shadow-black/20">
      <span className="text-xs font-bold text-primary">전체 공개</span>
    </div>
  );
}
