import type {Metadata} from "next";
import "./globals.css";
import {AuthProvider} from "@/providers/auth-provider";
import {NotificationProvider} from "@/providers/notification-provider";
import {NotificationToastContainer} from "@/components/common/notification-toast";
import {Header} from "@/components/common/header";
import {Footer} from "@/components/common/footer";

import {Suspense} from "react";

export const metadata: Metadata = {
  title: "Commit - 개발자들의 진짜 경험을 만나보세요",
  description: "현업 개발자들이 숨겨둔 노하우를 글로 만나고, 가볍게 쓰는 아이디어를 팔로우하거나 멤버십으로 구독하세요.",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="ko" className="h-full antialiased">
      <body className="min-h-full flex flex-col bg-neutral-50 pb-12">
        <AuthProvider>
          <NotificationProvider>
            <Suspense fallback={<div className="h-[65px] bg-white border-b border-neutral-100"/>}>
              <Header/>
            </Suspense>
            <main className="flex-1">{children}</main>
            <Footer/>
            <NotificationToastContainer/>
          </NotificationProvider>
        </AuthProvider>
      </body>
    </html>
  );
}
