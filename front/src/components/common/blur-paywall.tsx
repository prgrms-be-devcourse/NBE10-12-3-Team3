import React from "react";
import { Button } from "@/components/ui/button";
import Link from "next/link";
import { cn } from "@/lib/utils";

interface BlurPaywallProps {
  isLoggedIn?: boolean;
  className?: string;
  /**
   * 멤버십 가입 버튼을 눌렀을 때 호출됩니다.
   *
   * 결제 모달은 이 컴포넌트가 아니라 페이지가 소유합니다.
   * 토스 결제 후 복귀하면 잠금이 풀리기 전까지 페이월이 사라졌다 나타날 수 있는데,
   * 모달이 여기 있으면 그 순간 함께 언마운트되어 승인 요청이 유실되기 때문입니다.
   */
  onJoinClick: () => void;
}

export function BlurPaywall({ isLoggedIn = false, className, onJoinClick }: BlurPaywallProps) {
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
            onClick={onJoinClick}
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
    </>
  );
}
