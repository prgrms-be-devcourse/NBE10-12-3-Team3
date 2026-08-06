"use client";

import React, { useState } from "react";
import { Settings, Users } from "lucide-react";
import { Avatar } from "@/components/ui/avatar";
import { EditProfileModal } from "./edit-profile-modal";

interface ProfileHeaderProps {
  nickname: string;
  email: string;
  followerCount: number;
  avatarUrl?: string;
  introduction?: string;
}

export function ProfileHeader({ nickname, email, followerCount, avatarUrl, introduction }: ProfileHeaderProps) {
  const [isEditModalOpen, setIsEditModalOpen] = useState(false);

  const formattedFollowers = new Intl.NumberFormat("ko-KR").format(followerCount);

  return (
    <>
      <div className="w-full bg-white border border-neutral-200/60 rounded-3xl p-8 shadow-sm flex flex-col md:flex-row items-center md:items-start justify-between gap-6 relative overflow-hidden">
        {/* Background Decoration */}
        <div className="absolute top-0 right-0 w-64 h-64 bg-primary/5 rounded-full blur-3xl -translate-y-1/2 translate-x-1/3 pointer-events-none" />
        
        {/* Left: User Info */}
        <div className="flex flex-col md:flex-row items-center md:items-start gap-6 relative z-10">
          <Avatar src={avatarUrl} name={nickname} className="h-24 w-24 border-4 border-white shadow-md" />
          
          <div className="flex flex-col items-center md:items-start text-center md:text-left pt-2">
            <div className="flex items-center gap-3 mb-1">
              <h1 className="text-2xl font-black text-neutral-dark">{nickname}</h1>
              <button 
                onClick={() => setIsEditModalOpen(true)}
                className="p-1.5 text-neutral-400 hover:text-primary hover:bg-primary/10 rounded-full transition-all active:scale-95"
                title="내 정보 수정"
              >
                <Settings className="h-5 w-5" />
              </button>
            </div>
            <p className="text-sm font-medium text-neutral-500 mb-2">{email}</p>
            <p className="text-sm text-neutral-600 font-normal line-clamp-2 max-w-md">
              {introduction || "작성한 소개글이 없습니다."}
            </p>
          </div>
        </div>

        {/* Right: Stats (API 6) */}
        <div className="relative z-10 flex items-center h-full pt-4 md:pt-0">
          <div className="flex flex-col items-center justify-center px-8 py-4 bg-primary/5 border border-primary/20 rounded-2xl">
            <div className="flex items-center gap-2 text-primary font-bold mb-1">
              <Users className="h-4 w-4" />
              <span className="text-sm">Followers</span>
            </div>
            <span className="text-3xl font-black text-neutral-dark tracking-tight">{formattedFollowers}</span>
          </div>
        </div>
      </div>

      {/* Edit Profile Modal */}
      {/* key={email}: nickname은 내부 state로 관리되는데, useAuth().user가 비동기로 늦게
          채워지면 최초 마운트 시 placeholder(예: "테스트 유저")로 초기화된 뒤 실제 닉네임이
          로드돼도 반영되지 않는 문제가 있었습니다. email이 placeholder → 실제 값으로 바뀌는
          시점에 모달을 새로 마운트시켜 nickname state를 최신값으로 다시 초기화합니다. */}
      <EditProfileModal
        key={email}
        isOpen={isEditModalOpen}
        onClose={() => setIsEditModalOpen(false)}
        currentNickname={nickname}
        currentEmail={email}
        currentAvatarUrl={avatarUrl}
        currentIntroduction={introduction}
      />
    </>
  );
}
