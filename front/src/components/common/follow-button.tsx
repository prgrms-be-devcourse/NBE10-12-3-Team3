"use client";

import React, { useState, useRef, useEffect } from "react";
import { motion, AnimatePresence } from "framer-motion";
import { Star, Check, Plus, Loader2, Ban } from "lucide-react";
import { cn } from "@/lib/utils";
import { useAuth } from "@/providers/auth-provider";
import { apiFetch } from "@/lib/api";
import { ModalOverlay } from "@/components/common/modal-overlay";
import { CancelMembershipModal } from "@/components/common/cancel-membership-modal";
import { PaymentWidgetModal } from "@/components/payment/payment-widget-modal";

export type FollowTier = "NONE" | "FOLLOW" | "MEMBERSHIP";

interface FollowButtonProps {
  creatorId?: string | number;
  creatorName?: string;
  initialTier?: FollowTier;
  className?: string;
}

export function FollowButton({
  creatorId,
  creatorName,
  initialTier = "NONE",
  className,
}: FollowButtonProps) {
  const [tier, setTier] = useState<FollowTier>(initialTier);
  const [isLoading, setIsLoading] = useState(false);
  const [isMenuOpen, setIsMenuOpen] = useState(false);

  // Modal states
  const [isJoinModalOpen, setIsJoinModalOpen] = useState(false);
  const [isCancelModalOpen, setIsCancelModalOpen] = useState(false);

  const { isLoggedIn } = useAuth();
  const menuRef = useRef<HTMLDivElement>(null);

  // 최초 구독 상태 조회
  useEffect(() => {
    if (isLoggedIn && creatorId) {
      apiFetch<{ status: FollowTier }>(`/api/subscriptions/status/${creatorId}`)
        .then((data) => setTier(data.status))
        .catch(console.error);
    }
  }, [isLoggedIn, creatorId]);

  // 드롭다운 외부 클릭 시 닫기
  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (menuRef.current && !menuRef.current.contains(e.target as Node)) {
        setIsMenuOpen(false);
      }
    };
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  const handleMainClick = async (e: React.MouseEvent) => {
    e.stopPropagation();
    e.preventDefault();

    if (isLoading) return;

    if (tier === "NONE") {
      // 미구독 상태: 즉시 팔로우
      await executeAction("FOLLOW");
    } else if (tier === "FOLLOW") {
      // 팔로우 상태: 드롭다운 토글
      setIsMenuOpen((prev) => !prev);
    } else if (tier === "MEMBERSHIP") {
      // 멤버십 상태: 멤버십 해지 모달 오픈
      setIsMenuOpen(false);
      setIsCancelModalOpen(true);
    }
  };

  const executeAction = async (action: "FOLLOW" | "UNFOLLOW" | "JOIN_MEMBERSHIP" | "CANCEL_MEMBERSHIP") => {
    if (!isLoggedIn) {
      alert("로그인이 필요합니다.");
      return;
    }
    setIsLoading(true);
    setIsMenuOpen(false);
    setIsJoinModalOpen(false);
    setIsCancelModalOpen(false);
    try {
      switch (action) {
        case "FOLLOW":
          await apiFetch(`/api/subscriptions/follow/${creatorId}`, { method: "POST" });
          break;
        case "UNFOLLOW":
          await apiFetch(`/api/subscriptions/follow/${creatorId}`, { method: "DELETE" });
          break;
        case "JOIN_MEMBERSHIP":
          await apiFetch(`/api/subscriptions/membership/${creatorId}`, { method: "POST" });
          break;
        case "CANCEL_MEMBERSHIP":
          await apiFetch(`/api/subscriptions/membership/${creatorId}`, { method: "DELETE" });
          break;
      }

      switch (action) {
        case "FOLLOW": setTier("FOLLOW"); break;
        case "UNFOLLOW": setTier("NONE"); break;
        case "JOIN_MEMBERSHIP": setTier("MEMBERSHIP"); break;
        case "CANCEL_MEMBERSHIP": setTier("FOLLOW"); break;
      }
    } catch (error) {
      console.error("Action failed", error);
      alert(error instanceof Error ? error.message : "요청에 실패했습니다.");
    } finally {
      setIsLoading(false);
    }
  };

  const getStyle = () => {
    switch (tier) {
      case "MEMBERSHIP":
        return "bg-membership hover:bg-membership/90 text-white shadow-md border-transparent";
      case "FOLLOW":
        return "bg-neutral-50 hover:bg-neutral-100 border-neutral-200 text-neutral-600";
      default:
        return "bg-white hover:bg-primary/5 border-primary/30 text-primary hover:border-primary/50 shadow-sm";
    }
  };

  return (
    <div className="relative" ref={menuRef} onClick={(e) => e.stopPropagation()}>
      <button
        onClick={handleMainClick}
        disabled={isLoading}
        className={cn(
          "relative flex items-center justify-center gap-1.5 px-6 py-2 rounded-full border text-[13px] font-bold transition-all duration-200 active:scale-[0.98] overflow-hidden min-w-[100px]",
          getStyle(),
          isLoading && "opacity-70 cursor-not-allowed",
          className
        )}
      >
        <AnimatePresence mode="wait" initial={false}>
          {isLoading && (
            <motion.div
              key="loading"
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              className="flex items-center justify-center"
            >
              <Loader2 className="h-4 w-4 animate-spin" />
            </motion.div>
          )}
          {!isLoading && tier === "MEMBERSHIP" && (
            <motion.div
              key="membership"
              initial={{ opacity: 0, y: 10 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -10 }}
              transition={{ duration: 0.15 }}
              className="flex items-center gap-1.5"
            >
              <Star className="h-3.5 w-3.5 fill-white" />
              <span>멤버십</span>
            </motion.div>
          )}
          {!isLoading && tier === "FOLLOW" && (
            <motion.div
              key="follow"
              initial={{ opacity: 0, y: 10 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -10 }}
              transition={{ duration: 0.15 }}
              className="flex items-center gap-1.5"
            >
              <Check className="h-3.5 w-3.5" />
              <span>팔로잉</span>
            </motion.div>
          )}
          {!isLoading && tier === "NONE" && (
            <motion.div
              key="none"
              initial={{ opacity: 0, y: 10 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -10 }}
              transition={{ duration: 0.15 }}
              className="flex items-center gap-1.5"
            >
              <Plus className="h-3.5 w-3.5" />
              <span>팔로우</span>
            </motion.div>
          )}
        </AnimatePresence>
      </button>

      {/* Dropdown Menu for FOLLOW tier */}
      <AnimatePresence>
        {isMenuOpen && tier === "FOLLOW" && (
          <motion.div
            initial={{ opacity: 0, y: 5, scale: 0.95 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: 5, scale: 0.95 }}
            transition={{ duration: 0.15 }}
            className="absolute top-full mt-2 right-0 w-48 bg-white rounded-xl shadow-xl border border-neutral-100 z-50 overflow-hidden"
          >
            <div className="p-1 flex flex-col">
              <button
                onClick={() => { setIsMenuOpen(false); setIsJoinModalOpen(true); }}
                className="flex items-center gap-3 px-3 py-2.5 w-full text-left rounded-lg hover:bg-neutral-50 transition-colors text-sm font-semibold text-neutral-800"
              >
                <div className="bg-yellow-100/50 p-1.5 rounded-md">
                  <Star className="h-4 w-4 text-yellow-600 fill-yellow-600" />
                </div>
                멤버십 가입
              </button>
              <div className="h-px bg-neutral-100 mx-2 my-1" />
              <button
                onClick={() => executeAction("UNFOLLOW")}
                className="flex items-center gap-3 px-3 py-2.5 w-full text-left rounded-lg hover:bg-red-50 transition-colors text-sm font-semibold text-red-600"
              >
                <div className="bg-red-100/50 p-1.5 rounded-md">
                  <Ban className="h-4 w-4 text-red-600" />
                </div>
                팔로우 취소
              </button>
            </div>
          </motion.div>
        )}
      </AnimatePresence>

      {/* Join Membership Modal - PaymentWidgetModal */}
      <PaymentWidgetModal
        isOpen={isJoinModalOpen}
        onClose={() => setIsJoinModalOpen(false)}
        creatorName={creatorName || "창작자"}
        creatorId={Number(creatorId) || 0}
        amount={10000}
      />

      {/* Cancel Membership Modal */}
      <CancelMembershipModal
        open={isCancelModalOpen}
        onCancel={() => setIsCancelModalOpen(false)}
        onConfirm={() => executeAction("CANCEL_MEMBERSHIP")}
      />
    </div>
  );
}
