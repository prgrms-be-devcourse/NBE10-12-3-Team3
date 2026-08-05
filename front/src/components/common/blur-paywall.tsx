import React, { useState } from "react";
import { Button } from "@/components/ui/button";
import Link from "next/link";
import { cn } from "@/lib/utils";
import { PaymentWidgetModal } from "@/components/payment/payment-widget-modal";

interface BlurPaywallProps {
  isLoggedIn?: boolean;
  className?: string;
  creatorName?: string;
  creatorId?: number;
}

export function BlurPaywall({ isLoggedIn = false, className, creatorName = "창작자", creatorId = 0 }: BlurPaywallProps) {
  const [isModalOpen, setIsModalOpen] = useState(false);

  // Check if URL has Toss redirect params, open modal automatically
  React.useEffect(() => {
    if (typeof window !== "undefined") {
      const params = new URLSearchParams(window.location.search);
      if (params.get("paymentKey") || params.get("payment_fail")) {
        setIsModalOpen(true);
      }
    }
  }, []);

  return (
    <>
      <div
        className={cn(
          "relative mt-8 flex flex-col items-center justify-center overflow-hidden rounded-card bg-primary-tint/40 px-6 py-16 text-center backdrop-blur-sm",
          className
        )}
      >
        {/* Icon */}
        <div className="mb-4 flex h-12 w-12 items-center justify-center rounded-full bg-primary text-white shadow-sm">
          <span className="text-xl font-bold">C</span>
        </div>

        <h3 className="mb-2 text-xl font-bold tracking-tight text-neutral-dark">
          {isLoggedIn ? "유료 멤버십 전용 포스트입니다" : "로그인이 필요한 포스트입니다"}
        </h3>
        
        <p className="mb-8 max-w-sm text-sm leading-relaxed text-neutral-meta">
          {isLoggedIn
            ? "멤버십을 구독하시면 이 글을 포함한 모든 유료 포스트를 볼 수 있어요."
            : "Commit에 가입하고 개발자들의 진짜 경험과 노하우를 확인해 보세요."}
        </p>

        {isLoggedIn ? (
          <Button
            variant="filled"
            onClick={() => setIsModalOpen(true)}
            className="rounded-full px-8 py-6 font-bold text-base shadow-sm hover:shadow-md transition-shadow"
          >
            월 9,900원 멤버십 구독하기
          </Button>
        ) : (
          <Link href="/users/login">
            <Button
              variant="filled"
              className="rounded-full px-8 py-6 font-bold text-base shadow-sm hover:shadow-md transition-shadow"
            >
              Commit 시작하기
            </Button>
          </Link>
        )}
        
        {/* Top blur gradient to blend with text above */}
        <div className="absolute top-0 left-0 right-0 h-24 bg-gradient-to-b from-white to-transparent opacity-80" />
      </div>

      <PaymentWidgetModal
        isOpen={isModalOpen}
        onClose={() => setIsModalOpen(false)}
        creatorName={creatorName}
        creatorId={creatorId}
      />
    </>
  );
}
