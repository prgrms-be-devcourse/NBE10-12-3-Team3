"use client";

import React, { useState, useEffect } from "react";
import { Inbox, Loader2, CreditCard } from "lucide-react";
import { useAuth } from "@/providers/auth-provider";
import { apiFetch, ApiError } from "@/lib/api";

interface PaymentResponse {
  id: number;
  orderId: string;
  orderName: string;
  amount: number;
  status: string;
  createDate: string;
}

export function PaymentHistoryList() {
  const [items, setItems] = useState<PaymentResponse[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const { isLoggedIn } = useAuth();

  useEffect(() => {
    let ignore = false;

    async function fetchPayments() {
      setIsLoading(true);
      try {
        const response = await apiFetch<PaymentResponse[]>("/api/payments/history");
        if (!ignore) {
          setItems(response);
        }
      } catch (e) {
        if (ignore) return;
        if (!(e instanceof ApiError && e.status === 401)) {
          console.error(e);
        }
      } finally {
        if (!ignore) setIsLoading(false);
      }
    }

    if (isLoggedIn) {
      fetchPayments();
    } else {
      setIsLoading(false);
    }

    return () => {
      ignore = true;
    };
  }, [isLoggedIn]);

  if (isLoading) {
    return (
      <div className="flex justify-center items-center py-20">
        <Loader2 className="h-8 w-8 animate-spin text-primary" />
      </div>
    );
  }

  if (items.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center py-20 text-center">
        <div className="w-16 h-16 bg-neutral-100 rounded-full flex items-center justify-center mb-4">
          <Inbox className="h-8 w-8 text-neutral-400" />
        </div>
        <h3 className="text-lg font-bold text-neutral-dark mb-2">결제 내역이 없습니다</h3>
        <p className="text-sm text-neutral-500">아직 멤버십 결제 내역이 존재하지 않습니다.</p>
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-4">
      {items.map((payment) => {
        const date = new Date(payment.createDate).toLocaleDateString("ko-KR", {
          year: "numeric",
          month: "long",
          day: "numeric",
          hour: "2-digit",
          minute: "2-digit",
        });

        const isSuccess = payment.status === "DONE";

        return (
          <div
            key={payment.id}
            className="flex items-center justify-between p-6 bg-white rounded-2xl border border-neutral-200 shadow-sm transition-shadow hover:shadow-md"
          >
            <div className="flex items-center gap-4">
              <div className="w-12 h-12 bg-blue-50 rounded-full flex items-center justify-center">
                <CreditCard className="h-6 w-6 text-blue-500" />
              </div>
              <div>
                <h4 className="font-bold text-neutral-800 text-lg">{payment.orderName}</h4>
                <p className="text-sm text-neutral-500 mt-1">{date}</p>
                <p className="text-xs text-neutral-400 mt-0.5">주문번호: {payment.orderId}</p>
              </div>
            </div>

            <div className="text-right">
              <p className="font-bold text-xl text-neutral-800 mb-1">
                {payment.amount.toLocaleString()}원
              </p>
              <span
                className={`inline-block px-3 py-1 rounded-full text-xs font-semibold ${
                  isSuccess
                    ? "bg-green-100 text-green-700"
                    : "bg-red-100 text-red-700"
                }`}
              >
                {isSuccess ? "결제 완료" : payment.status}
              </span>
            </div>
          </div>
        );
      })}
    </div>
  );
}
