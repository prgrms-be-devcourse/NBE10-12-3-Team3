"use client";

import React, { useEffect, useRef, useState } from "react";
import { motion, AnimatePresence } from "framer-motion";
import { X, Save, Loader2 } from "lucide-react";
import { Avatar } from "@/components/ui/avatar";
import { useAuth } from "@/providers/auth-provider";
import { apiFetch, ApiError } from "@/lib/api";

interface EditProfileModalProps {
  isOpen: boolean;
  onClose: () => void;
  currentNickname?: string;
  currentEmail?: string;
  currentAvatarUrl?: string;
  currentIntroduction?: string;
}

export function EditProfileModal({
  isOpen,
  onClose,
  currentNickname = "User",
  currentEmail = "user@test.com",
  currentAvatarUrl,
  currentIntroduction = "",
}: EditProfileModalProps) {
  const [nickname, setNickname] = useState(currentNickname);
  const [introduction, setIntroduction] = useState(currentIntroduction);
  const [isLoading, setIsLoading] = useState(false);
  const [profileImageFile, setProfileImageFile] = useState<File | null>(null);
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const { refreshUser } = useAuth();

  // object URL은 명시적으로 해제하지 않으면 메모리에 계속 남으므로, 미리보기가 바뀌거나
  // 컴포넌트가 사라질 때 이전 URL을 정리합니다.
  useEffect(() => {
    return () => {
      if (previewUrl) URL.revokeObjectURL(previewUrl);
    };
  }, [previewUrl]);

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    if (previewUrl) URL.revokeObjectURL(previewUrl);
    setProfileImageFile(file);
    setPreviewUrl(URL.createObjectURL(file));
  };

  // 다음에 다시 열었을 때 이전에 골랐던(저장 안 된) 사진 미리보기가 남아있지 않도록,
  // 닫는 시점에 선택 상태를 정리합니다.
  const handleClose = () => {
    if (previewUrl) URL.revokeObjectURL(previewUrl);
    setProfileImageFile(null);
    setPreviewUrl(null);
    onClose();
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!nickname.trim() || isLoading) return;

    setIsLoading(true);
    try {
      // multipart/form-data 처리를 위해 FormData 사용 (백엔드 요구사항: request 파트)
      const formData = new FormData();
      const requestBlob = new Blob([JSON.stringify({ nickname, introduction })], { type: "application/json" });
      formData.append("request", requestBlob);
      if (profileImageFile) {
        formData.append("profileImage", profileImageFile);
      }

      await apiFetch("/api/users/me", {
        method: "PATCH",
        body: formData,
      });

      await refreshUser(); // 전역 상태 업데이트
      alert("정보가 성공적으로 수정되었습니다!");
      handleClose();
    } catch (e) {
      if (e instanceof ApiError) {
        console.error("Profile update failed", e.status, e.message);
        alert(`정보 수정에 실패했습니다. (상태: ${e.status}, 사유: ${e.message})`);
        return;
      }
      console.error(e);
      alert("오류가 발생했습니다.");
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <AnimatePresence>
      {isOpen && (
        <>
          {/* Backdrop */}
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            onClick={handleClose}
            className="fixed inset-0 z-[100] bg-neutral-900/40 backdrop-blur-sm"
          />
          
          {/* Modal Content */}
          <div className="fixed inset-0 z-[101] flex items-center justify-center p-4 pointer-events-none">
            <motion.div
              initial={{ opacity: 0, scale: 0.95, y: 20 }}
              animate={{ opacity: 1, scale: 1, y: 0 }}
              exit={{ opacity: 0, scale: 0.95, y: 20 }}
              transition={{ type: "spring", stiffness: 300, damping: 30 }}
              className="w-full max-w-md bg-white rounded-3xl shadow-2xl overflow-hidden pointer-events-auto border border-neutral-200"
            >
              {/* Header */}
              <div className="flex items-center justify-between px-6 py-5 border-b border-neutral-100">
                <h2 className="text-lg font-bold text-neutral-dark">내 정보 수정</h2>
                <button
                  onClick={handleClose}
                  className="p-2 -mr-2 text-neutral-400 hover:text-neutral-700 hover:bg-neutral-100 rounded-full transition-colors"
                >
                  <X className="h-5 w-5" />
                </button>
              </div>

              {/* Body */}
              <form onSubmit={handleSubmit} className="p-6">
                <div className="flex flex-col items-center gap-4 mb-6">
                  <input
                    ref={fileInputRef}
                    type="file"
                    accept="image/*"
                    onChange={handleFileChange}
                    className="hidden"
                  />
                  <div
                    className="relative group cursor-pointer"
                    onClick={() => fileInputRef.current?.click()}
                  >
                    <Avatar src={previewUrl ?? currentAvatarUrl} name={nickname} className="h-24 w-24 border-4 border-white shadow-md transition-transform group-hover:scale-105" />
                    <div className="absolute inset-0 bg-black/40 rounded-full opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center">
                      <span className="text-white text-xs font-bold">사진 변경</span>
                    </div>
                  </div>
                  <p className="text-sm font-medium text-neutral-500">{currentEmail}</p>
                </div>

                <div className="space-y-4">
                  <div>
                    <label className="block text-sm font-bold text-neutral-700 mb-2">닉네임</label>
                    <input
                      type="text"
                      value={nickname}
                      onChange={(e) => setNickname(e.target.value)}
                      className="w-full px-4 py-3 rounded-xl border border-neutral-200 bg-neutral-50 text-neutral-dark focus:bg-white focus:border-primary focus:ring-2 focus:ring-primary/20 outline-none transition-all font-medium"
                      placeholder="새로운 닉네임을 입력하세요"
                      required
                    />
                  </div>

                  <div>
                    <div className="flex items-center justify-between mb-2">
                      <label className="block text-sm font-bold text-neutral-700">자기소개</label>
                      <span className="text-xs text-neutral-400 font-medium">{introduction.length}/100자</span>
                    </div>
                    <textarea
                      value={introduction}
                      onChange={(e) => setIntroduction(e.target.value)}
                      maxLength={100}
                      rows={3}
                      className="w-full px-4 py-3 rounded-xl border border-neutral-200 bg-neutral-50 text-neutral-dark focus:bg-white focus:border-primary focus:ring-2 focus:ring-primary/20 outline-none transition-all font-medium resize-none text-sm"
                      placeholder="자신을 나타내는 소개글을 작성해보세요 (100자 이내)"
                    />
                  </div>
                </div>

                {/* Footer */}
                <div className="mt-8 flex gap-3">
                  <button
                    type="button"
                    onClick={handleClose}
                    className="flex-1 py-3 rounded-xl font-bold text-neutral-600 bg-neutral-100 hover:bg-neutral-200 transition-colors"
                  >
                    취소
                  </button>
                  <button
                    type="submit"
                    disabled={isLoading}
                    className="flex-1 py-3 rounded-xl font-bold text-white bg-primary hover:bg-primary-hover shadow-md transition-colors flex items-center justify-center gap-2 disabled:opacity-50"
                  >
                    {isLoading ? <Loader2 className="h-4 w-4 animate-spin" /> : <Save className="h-4 w-4" />}
                    저장하기
                  </button>
                </div>
              </form>
            </motion.div>
          </div>
        </>
      )}
    </AnimatePresence>
  );
}
