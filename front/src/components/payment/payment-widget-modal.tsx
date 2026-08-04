'use client';

import { useEffect, useRef, useState } from 'react';
import { loadPaymentWidget, PaymentWidgetInstance } from '@tosspayments/payment-widget-sdk';
import { useRouter, useSearchParams } from 'next/navigation';
import { X, CheckCircle2, AlertCircle } from 'lucide-react';
import { apiPost } from '@/lib/api';

const clientKey = 'test_gck_docs_Ovk5rk1EwkEbP0W43n07xlzm';
const customerKey = 'test_customer_123'; // 임시로 고정된 키 사용

interface PaymentWidgetModalProps {
  isOpen: boolean;
  onClose: () => void;
  creatorName: string;
  creatorId: number;
  amount: number;
}

export function PaymentWidgetModal({ isOpen, onClose, creatorName, creatorId, amount }: PaymentWidgetModalProps) {
  const [paymentWidget, setPaymentWidget] = useState<PaymentWidgetInstance | null>(null);
  const paymentMethodsWidgetRef = useRef<ReturnType<PaymentWidgetInstance['renderPaymentMethods']> | null>(null);
  
  const searchParams = useSearchParams();
  const router = useRouter();
  
  // 상태 관리: IDLE (결제위젯 표시), SUCCESS (성공 화면), FAIL (실패 화면)
  const [status, setStatus] = useState<'IDLE' | 'SUCCESS' | 'FAIL'>('IDLE');
  const [errorMessage, setErrorMessage] = useState('');
  const [isConfirming, setIsConfirming] = useState(false);

  // 결제 완료/실패 꼬리표(파라미터) 감지 로직
  useEffect(() => {
    if (!isOpen) return;
    
    const paymentKey = searchParams.get('paymentKey');
    const orderId = searchParams.get('orderId');
    const returnedAmount = searchParams.get('amount');
    const isFail = searchParams.get('payment_fail');
    const msg = searchParams.get('message');
    
    if (paymentKey && orderId && returnedAmount && !isConfirming) {
      // 1. 프론트엔드 URL로 결제 성공 파라미터가 들어온 경우
      setIsConfirming(true);
      
      const confirmPayment = async () => {
        try {
          // 2. 백엔드에 최종 승인 요청 (토스 서버와 통신 및 멤버십 승급)
          const res = await apiPost('/api/payments/toss/confirm', {
            paymentKey,
            orderId,
            amount: Number(returnedAmount)
          });
          
          if (res.resultCode.startsWith('200')) {
            setStatus('SUCCESS');
          } else {
            setStatus('FAIL');
            setErrorMessage(res.msg || '결제 승인 중 오류가 발생했습니다.');
          }
        } catch (err: any) {
          setStatus('FAIL');
          setErrorMessage('서버와 통신 중 오류가 발생했습니다.');
        } finally {
          setIsConfirming(false);
        }
      };
      
      confirmPayment();
    } else if (isFail) {
      // 결제 실패!
      setStatus('FAIL');
      setErrorMessage(msg || '결제 중 오류가 발생했습니다.');
    } else if (!paymentKey && !isConfirming) {
      setStatus('IDLE');
    }
  }, [searchParams, isOpen]);

  // 토스 결제 위젯 SDK 로드
  useEffect(() => {
    if (!isOpen || status !== 'IDLE') return;

    const fetchPaymentWidget = async () => {
      try {
        const widget = await loadPaymentWidget(clientKey, customerKey);
        setPaymentWidget(widget);
      } catch (error) {
        console.error('결제 위젯 로드 실패:', error);
      }
    };

    fetchPaymentWidget();
  }, [isOpen, status]);

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
      const res = await apiPost('/api/payments/toss/ready', {
        amount,
        orderName: `${creatorName} 멤버십 구독`,
        targetCreatorId: creatorId
      });
      
      if (!res.resultCode.startsWith('200') || !res.data?.orderId) {
        throw new Error('결제 준비에 실패했습니다.');
      }
      
      const orderId = res.data.orderId;
      
      const currentUrl = new URL(window.location.href);
      currentUrl.searchParams.delete('paymentKey');
      currentUrl.searchParams.delete('payment_fail');
      currentUrl.searchParams.delete('message');
      
      // 2. 토스 페이먼츠 결제 팝업 띄우기
      await paymentWidget.requestPayment({
        orderId: orderId,
        orderName: `${creatorName} 멤버십 구독`,
        successUrl: `${currentUrl.origin}${currentUrl.pathname}`,
        failUrl: `${currentUrl.origin}${currentUrl.pathname}?payment_fail=true`,
        customerEmail: 'customer123@gmail.com', // TODO: 실제 유저 정보로 변경
        customerName: 'SCommit 유저', // TODO: 실제 유저 정보로 변경
      });
    } catch (error: any) {
      console.error(error);
      if (error.code === 'USER_CANCEL') {
        setStatus('FAIL');
        setErrorMessage('결제를 취소하셨습니다.');
      } else {
        setStatus('FAIL');
        setErrorMessage(error.message || '결제 요청 중 오류가 발생했습니다.');
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
    }
    
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
