'use client';

import { useEffect, useRef, useState } from 'react';
import { loadPaymentWidget, PaymentWidgetInstance } from '@tosspayments/payment-widget-sdk';
import { useRouter, useSearchParams } from 'next/navigation';
import { X, CheckCircle2, AlertCircle } from 'lucide-react';
import { apiPost } from '@/lib/api';
import { useAuth } from '@/providers/auth-provider';

// 운영 키는 빌드 시 환경변수로 주입합니다. (미설정 시 토스 공개 문서용 테스트 키로 동작)
const clientKey =
  process.env.NEXT_PUBLIC_TOSS_CLIENT_KEY ?? 'test_gck_docs_Ovk5rk1EwkEbP0W43n07xlzm';

/**
 * 위젯에 처음 표시할 금액입니다. 실제 청구 금액은 서버(PaymentService.MEMBERSHIP_PRICE)가
 * 결정하며, 결제 준비 응답과 다르면 requestPayment 직전에 서버 금액으로 교체됩니다.
 */
export const MEMBERSHIP_PRICE = 9900;

interface PaymentWidgetModalProps {
  isOpen: boolean;
  onClose: () => void;
  creatorName: string;
  creatorId: number;
  amount?: number;
  onSuccess?: () => void;
}

export function PaymentWidgetModal({ isOpen, onClose, creatorName, creatorId, amount = MEMBERSHIP_PRICE, onSuccess }: PaymentWidgetModalProps) {
  const [paymentWidget, setPaymentWidget] = useState<PaymentWidgetInstance | null>(null);
  const paymentMethodsWidgetRef = useRef<ReturnType<PaymentWidgetInstance['renderPaymentMethods']> | null>(null);
  
  const searchParams = useSearchParams();
  const router = useRouter();
  const { user } = useAuth();

  // 토스는 customerKey로 고객을 식별합니다. 전 유저가 같은 값을 쓰면
  // 결제수단 정보가 서로 섞이므로 반드시 유저별로 구분되는 값을 사용합니다.
  const customerKey = user ? `scommit_user_${user.id}` : null;

  // 상태 관리: IDLE (결제위젯 표시), SUCCESS (성공 화면), FAIL (실패 화면)
  const [status, setStatus] = useState<'IDLE' | 'SUCCESS' | 'FAIL'>('IDLE');
  const [errorMessage, setErrorMessage] = useState('');
  // 승인 요청 중복 방지는 state로 하면 반영 전에 effect가 다시 돌아 중복 호출될 수 있으므로
  // 즉시 반영되는 ref로 처리하고, 이미 승인을 시도한 주문번호를 기록해 둡니다.
  const confirmedOrderIdRef = useRef<string | null>(null);

  // 결제 완료/실패 꼬리표(파라미터) 감지 로직
  useEffect(() => {
    if (!isOpen) return;

    const paymentKey = searchParams.get('paymentKey');
    const orderId = searchParams.get('orderId');
    const isFail = searchParams.get('payment_fail');
    const msg = searchParams.get('message');

    if (paymentKey && orderId && confirmedOrderIdRef.current !== orderId) {
      // 1. 프론트엔드 URL로 결제 성공 파라미터가 들어온 경우
      confirmedOrderIdRef.current = orderId;

      const confirmPayment = async () => {
        try {
          // 2. 백엔드에 최종 승인 요청 (토스 서버와 통신 및 멤버십 승급)
          // 금액은 서버가 보관 중인 값을 사용하므로 전달하지 않습니다.
          // apiPost는 성공 시 반환 데이터를, 실패 시 ApiError를 던집니다.
          await apiPost('/api/payments/toss/confirm', {
            paymentKey,
            orderId
          });

          setStatus('SUCCESS');
        } catch (err: any) {
          setStatus('FAIL');
          setErrorMessage(err?.msg || err?.message || '서버와 통신 중 오류가 발생했습니다.');
        }
      };

      confirmPayment();
    } else if (isFail) {
      // 결제 실패!
      setStatus('FAIL');
      setErrorMessage(msg || '결제 중 오류가 발생했습니다.');
    } else if (!paymentKey) {
      setStatus('IDLE');
    }
  }, [searchParams, isOpen]);

  // 토스 결제 위젯 SDK 로드
  useEffect(() => {
    if (!isOpen || status !== 'IDLE' || !customerKey) return;

    const fetchPaymentWidget = async () => {
      try {
        const widget = await loadPaymentWidget(clientKey, customerKey);
        setPaymentWidget(widget);
      } catch (error) {
        console.error('결제 위젯 로드 실패:', error);
      }
    };

    fetchPaymentWidget();
  }, [isOpen, status, customerKey]);

  // 토스 결제 위젯 화면 렌더링
  useEffect(() => {
    if (paymentWidget == null || status !== 'IDLE') {
      return;
    }

    // 결제 수단 영역 렌더링
    const paymentMethodsWidget = paymentWidget.renderPaymentMethods(
      '#payment-widget',
      { value: amount },
      { variantKey: 'DEFAULT' }
    );

    // 이용 약관 영역 렌더링
    paymentWidget.renderAgreement('#agreement', { variantKey: 'AGREEMENT' });
    paymentMethodsWidgetRef.current = paymentMethodsWidget;

  }, [paymentWidget, amount, status]);

  // [결제하기] 버튼 클릭 핸들러
  const handlePayment = async () => {
    if (!paymentWidget) return;
    
    try {
      // 1. 백엔드에 결제 준비 요청 (orderId 생성 및 DB 저장)
      //    결제 금액과 주문명은 서버가 확정해 내려줍니다.
      const res = await apiPost<{ orderId: string; orderName: string; amount: number }>(
        '/api/payments/toss/ready',
        { targetCreatorId: creatorId }
      );

      if (!res?.orderId) {
        throw new Error('결제 준비에 실패했습니다.');
      }

      const { orderId, orderName, amount: confirmedAmount } = res;

      // 2. 위젯에 표시된 금액을 서버가 확정한 금액으로 맞춥니다.
      if (confirmedAmount !== amount) {
        await paymentMethodsWidgetRef.current?.updateAmount(confirmedAmount);
      }

      const currentUrl = new URL(window.location.href);
      currentUrl.searchParams.delete('paymentKey');
      currentUrl.searchParams.delete('payment_fail');
      currentUrl.searchParams.delete('message');

      // 3. 토스 페이먼츠 결제 팝업 띄우기
      await paymentWidget.requestPayment({
        orderId: orderId,
        orderName: orderName,
        successUrl: `${currentUrl.origin}${currentUrl.pathname}`,
        failUrl: `${currentUrl.origin}${currentUrl.pathname}?payment_fail=true`,
        customerEmail: user?.email,
        customerName: user?.nickname,
      });
    } catch (error: any) {
      console.error(error);
      if (error.code === 'USER_CANCEL') {
        setStatus('FAIL');
        setErrorMessage('결제를 취소하셨습니다.');
      } else {
        setStatus('FAIL');
        setErrorMessage(error.msg || error.message || '결제 요청 중 오류가 발생했습니다.');
      }
    }
  };

  // 모달 닫기 핸들러
  const handleClose = () => {
    // URL에 묻은 꼬리표 청소
    if (status !== 'IDLE') {
      const currentUrl = new URL(window.location.href);
      currentUrl.searchParams.delete('paymentKey');
      currentUrl.searchParams.delete('orderId');
      currentUrl.searchParams.delete('amount');
      currentUrl.searchParams.delete('paymentType');
      currentUrl.searchParams.delete('payment_fail');
      currentUrl.searchParams.delete('message');
      currentUrl.searchParams.delete('code');
      router.replace(currentUrl.pathname, { scroll: false });
      // 결제 성공 후 닫는 거라면, 새로운 등급(MEMBERSHIP) 정보를 반영하기 위해 페이지 데이터를 리프레시합니다.
      if (status === 'SUCCESS') {
        router.refresh();
        onSuccess?.();
      }
    }

    confirmedOrderIdRef.current = null;

    setStatus('IDLE');
    onClose();
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 sm:p-0">
      {/* 백그라운드 반투명 오버레이 */}
      <div 
        className="absolute inset-0 bg-black/40 backdrop-blur-sm"
        onClick={handleClose}
      />
      
      {/* 모달 창 본체 (Glassmorphism) */}
      <div className="relative bg-white/95 dark:bg-zinc-900/95 backdrop-blur-md w-full max-w-md rounded-3xl shadow-2xl overflow-hidden animate-in fade-in zoom-in-95 duration-200">
        
        {/* 닫기 버튼 */}
        <button 
          onClick={handleClose}
          className="absolute right-4 top-4 p-2 rounded-full hover:bg-black/5 dark:hover:bg-white/10 transition-colors z-10"
        >
          <X className="w-5 h-5 text-zinc-500" />
        </button>

        {/* 1. IDLE 상태: 토스 결제 위젯 표시 */}
        {status === 'IDLE' && (
          <div className="flex flex-col h-full max-h-[85vh]">
            {/* 상단 헤더 영역 */}
            <div className="p-6 pb-2 shrink-0">
              <h2 className="text-2xl font-bold text-zinc-900 dark:text-white">멤버십 가입</h2>
              <p className="text-zinc-500 dark:text-zinc-400 mt-1">
                {creatorName}님의 모든 유료 포스트를 잠금 해제하세요.
              </p>
            </div>
            
            {/* 토스 결제 위젯이 그려질 공간 */}
            <div className="flex-1 overflow-y-auto px-4 custom-scrollbar">
              <div id="payment-widget" className="w-full" />
              <div id="agreement" className="w-full mt-2" />
            </div>

            {/* 하단 결제 버튼 영역 */}
            <div className="p-6 shrink-0 bg-white/80 dark:bg-zinc-900/80 backdrop-blur-sm border-t border-zinc-100 dark:border-zinc-800">
              <button
                onClick={handlePayment}
                className="w-full bg-blue-600 hover:bg-blue-700 text-white font-medium py-3.5 px-4 rounded-xl transition-all active:scale-[0.98] shadow-lg shadow-blue-500/25"
              >
                {amount.toLocaleString()}원 결제하기
              </button>
            </div>
          </div>
        )}

        {/* 2. SUCCESS 상태: 결제 성공 화면 */}
        {status === 'SUCCESS' && (
          <div className="p-10 flex flex-col items-center justify-center text-center">
            <div className="w-16 h-16 bg-green-100 dark:bg-green-500/20 rounded-full flex items-center justify-center mb-6">
              <CheckCircle2 className="w-8 h-8 text-green-600 dark:text-green-400" />
            </div>
            <h2 className="text-2xl font-bold text-zinc-900 dark:text-white mb-2">결제 완료!</h2>
            <p className="text-zinc-500 dark:text-zinc-400 mb-8">
              {creatorName}님의 멤버십 가입이 완료되었습니다.<br />
              이제 모든 프리미엄 콘텐츠를 즐겨보세요!
            </p>
            <button
              onClick={handleClose}
              className="w-full bg-zinc-900 dark:bg-white text-white dark:text-zinc-900 font-medium py-3.5 px-4 rounded-xl transition-all active:scale-[0.98]"
            >
              확인
            </button>
          </div>
        )}

        {/* 3. FAIL 상태: 결제 실패 화면 */}
        {status === 'FAIL' && (
          <div className="p-10 flex flex-col items-center justify-center text-center">
            <div className="w-16 h-16 bg-red-100 dark:bg-red-500/20 rounded-full flex items-center justify-center mb-6">
              <AlertCircle className="w-8 h-8 text-red-600 dark:text-red-400" />
            </div>
            <h2 className="text-2xl font-bold text-zinc-900 dark:text-white mb-2">결제 실패</h2>
            <p className="text-zinc-500 dark:text-zinc-400 mb-8">
              {errorMessage}
            </p>
            <div className="flex gap-3 w-full">
              <button
                onClick={handleClose}
                className="flex-1 bg-zinc-100 hover:bg-zinc-200 dark:bg-zinc-800 dark:hover:bg-zinc-700 text-zinc-900 dark:text-white font-medium py-3.5 px-4 rounded-xl transition-all"
              >
                닫기
              </button>
              <button
                onClick={() => setStatus('IDLE')}
                className="flex-1 bg-blue-600 hover:bg-blue-700 text-white font-medium py-3.5 px-4 rounded-xl transition-all active:scale-[0.98]"
              >
                다시 시도
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
