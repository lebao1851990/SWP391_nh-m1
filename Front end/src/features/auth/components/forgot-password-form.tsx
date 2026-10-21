'use client';

import { useState, useEffect } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation } from '@tanstack/react-query';
import { useRouter } from 'next/navigation';
import {
  forgotPasswordSchema,
  ForgotPasswordFormData,
  verifyOtpSchema,
  VerifyOtpFormData,
} from '../schemas/auth.schema';
import { authApi } from '../api/auth.api';
import { useTranslation } from '@/i18n';
import { ArrowLeft, ArrowRight, AlertCircle, CheckCircle, Mail, KeyRound, RefreshCw } from 'lucide-react';
import Link from 'next/link';
import { isAxiosError } from 'axios';

export function ForgotPasswordForm() {
  const router = useRouter();
  const { t } = useTranslation();
  const [step, setStep] = useState<'request' | 'verify'>('request');
  const [submittedEmail, setSubmittedEmail] = useState('');
  const [requestNotice, setRequestNotice] = useState<string | null>(null);
  const [countdown, setCountdown] = useState<number>(60);
  const [resendNotice, setResendNotice] = useState<string | null>(null);
  const [resendError, setResendError] = useState<string | null>(null);

  // Countdown timer for resend OTP
  useEffect(() => {
    if (step !== 'verify' || countdown <= 0) return;
    const timer = setInterval(() => {
      setCountdown((prev) => (prev > 0 ? prev - 1 : 0));
    }, 1000);
    return () => clearInterval(timer);
  }, [step, countdown]);

  // Form for requesting OTP
  const {
    register: registerRequest,
    handleSubmit: handleSubmitRequest,
    formState: { errors: requestErrors },
  } = useForm<ForgotPasswordFormData>({
    resolver: zodResolver(forgotPasswordSchema),
    defaultValues: {
      email: '',
    },
  });

  // Form for verifying OTP
  const {
    register: registerOtp,
    handleSubmit: handleSubmitOtp,
    setValue: setOtpValue,
    formState: { errors: otpErrors },
  } = useForm<VerifyOtpFormData>({
    resolver: zodResolver(verifyOtpSchema),
    defaultValues: {
      email: '',
      otp: '',
    },
  });

  // Mutation to request OTP
  const requestMutation = useMutation({
    mutationFn: authApi.forgotPassword,
    onSuccess: (data, variables) => {
      setSubmittedEmail(variables.email);
      setOtpValue('email', variables.email);
      setRequestNotice(data.message || 'If an account exists with this email, a verification code has been sent.');
      setResendNotice(null);
      setResendError(null);
      setCountdown(60);
      setStep('verify');
    },
  });

  // Mutation to resend OTP
  const resendMutation = useMutation({
    mutationFn: authApi.resendOtp,
    onSuccess: (data) => {
      setResendError(null);
      setResendNotice(data.message || 'Yêu cầu gửi lại mã đã được xử lý. Vui lòng kiểm tra email.');
      setCountdown(data.cooldownSeconds || 60);
    },
    onError: (error: unknown) => {
      setResendNotice(null);
      const msg = (isAxiosError<{ message?: string }>(error) && error.response?.data?.message) ||
        'Không thể gửi lại mã lúc này. Vui lòng thử lại sau.';
      setResendError(msg);
    },
  });

  // Mutation to verify OTP
  const verifyMutation = useMutation({
    mutationFn: authApi.verifyOtp,
    onSuccess: (data) => {
      // Navigate to /reset-password with the issued single-use reset token
      router.push(`/reset-password?token=${encodeURIComponent(data.resetToken)}`);
    },
  });

  const onRequestSubmit = (data: ForgotPasswordFormData) => {
    requestMutation.mutate(data);
  };

  const onOtpSubmit = (data: VerifyOtpFormData) => {
    verifyMutation.mutate({
      email: submittedEmail || data.email,
      otp: data.otp,
    });
  };

  const handleResendCode = () => {
    if (!submittedEmail || countdown > 0 || resendMutation.isPending) return;
    setResendError(null);
    setResendNotice(null);
    resendMutation.mutate({ email: submittedEmail });
  };

  return (
    <main className="w-full max-w-md bg-card border border-border/40 rounded-xl p-8 shadow-[0_4px_12px_rgba(0,0,0,0.05)]">
      <div className="flex flex-col w-full">
        {/* Header */}
        <div className="flex flex-col items-center text-center mb-8">
          <div className="w-16 h-16 rounded-xl bg-muted flex items-center justify-center p-2 mb-2 shadow-sm">
            <span className="font-bold text-2xl text-primary">CA</span>
          </div>
          <h1 className="text-2xl font-semibold text-foreground tracking-tight">
            {step === 'request' ? t('auth.resetPasswordTitle') : t('auth.enterVerificationCodeTitle')}
          </h1>
          <p className="text-sm text-muted-foreground mt-1 max-w-xs">
            {step === 'request'
              ? t('auth.resetPasswordSubtitle')
              : t('auth.enterVerificationCodeSubtitle')}
          </p>
        </div>

        {/* Step 1: Request OTP Form */}
        {step === 'request' && (
          <>
            {requestMutation.isError && (
              <div
                role="alert"
                className="mb-4 p-3 rounded-lg flex items-start gap-3 bg-destructive/10 text-destructive transition-all"
              >
                <AlertCircle className="w-5 h-5 mt-0.5 shrink-0" />
                <div className="flex-1">
                  <p className="text-[13px] font-semibold">{t('auth.requestFailed')}</p>
                  <p className="text-xs mt-0.5">
                    {(isAxiosError<{ message?: string }>(requestMutation.error) &&
                      requestMutation.error.response?.data?.message) ||
                      t('auth.requestFailed')}
                  </p>
                </div>
              </div>
            )}

            <form
              className="flex flex-col gap-4"
              onSubmit={handleSubmitRequest(onRequestSubmit)}
              noValidate
            >
              {/* Email Input Field */}
              <div className="flex flex-col gap-1.5">
                <label className="text-[13px] font-medium text-foreground" htmlFor="email">
                  {t('auth.registeredEmailLabel')}
                </label>
                <div className="relative flex items-center">
                  <span className="absolute left-3 text-muted-foreground pointer-events-none">
                    <Mail className="w-4 h-4" />
                  </span>
                  <input
                    id="email"
                    type="email"
                    autoComplete="email"
                    placeholder="e.g., student@fpt.edu.vn"
                    {...registerRequest('email')}
                    className="w-full pl-10 pr-3 py-2 bg-background text-foreground text-sm rounded-lg border border-border shadow-sm focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary transition-all"
                  />
                </div>
                {requestErrors.email && (
                  <p role="alert" className="text-xs text-destructive mt-0.5">
                    {requestErrors.email.message}
                  </p>
                )}
              </div>

              <button
                type="submit"
                disabled={requestMutation.isPending}
                className="w-full mt-2 py-2.5 px-4 rounded-lg bg-primary text-primary-foreground font-semibold text-base shadow-sm flex items-center justify-center gap-2 hover:bg-primary/90 transition-colors disabled:opacity-50 disabled:cursor-not-allowed cursor-pointer"
              >
                {requestMutation.isPending ? (
                  <>
                    <RefreshCw className="w-4 h-4 animate-spin" />
                    <span>Sending Code...</span>
                  </>
                ) : (
                  <>
                    <span>Send Verification Code</span>
                    <ArrowRight className="w-5 h-5" />
                  </>
                )}
              </button>
            </form>
          </>
        )}

        {/* Step 2: Verify OTP Form */}
        {step === 'verify' && (
          <>
            {requestNotice && (
              <div className="mb-4 p-3 rounded-lg flex items-start gap-2.5 bg-green-500/10 text-green-700 dark:text-green-400 border border-green-500/20 text-xs">
                <CheckCircle className="w-4 h-4 mt-0.5 shrink-0" />
                <span>{requestNotice}</span>
              </div>
            )}

            {verifyMutation.isError && (
              <div
                role="alert"
                className="mb-4 p-3 rounded-lg flex items-start gap-3 bg-destructive/10 text-destructive transition-all"
              >
                <AlertCircle className="w-5 h-5 mt-0.5 shrink-0" />
                <div className="flex-1">
                  <p className="text-[13px] font-semibold">{t('auth.verificationFailed')}</p>
                  <p className="text-xs mt-0.5">
                    {(isAxiosError<{ message?: string }>(verifyMutation.error) &&
                      verifyMutation.error.response?.data?.message) ||
                      t('auth.invalidResetLinkDesc')}
                  </p>
                </div>
              </div>
            )}

            <form
              className="flex flex-col gap-4"
              onSubmit={handleSubmitOtp(onOtpSubmit)}
              noValidate
            >
              {/* Hidden Email */}
              <input type="hidden" {...registerOtp('email')} value={submittedEmail} />

              {/* OTP Field */}
              <div className="flex flex-col gap-1.5">
                <div className="flex items-center justify-between">
                  <label className="text-[13px] font-medium text-foreground" htmlFor="otp">
                    {t('auth.enterOtpLabel')}
                  </label>
                  <button
                    type="button"
                    onClick={() => setStep('request')}
                    className="text-xs text-primary hover:underline font-medium cursor-pointer"
                  >
                    {t('auth.changeEmail')}
                  </button>
                </div>
                <div className="relative flex items-center">
                  <span className="absolute left-3 text-muted-foreground pointer-events-none">
                    <KeyRound className="w-4 h-4" />
                  </span>
                  <input
                    id="otp"
                    type="text"
                    maxLength={6}
                    autoComplete="one-time-code"
                    placeholder="123456"
                    {...registerOtp('otp')}
                    className="w-full pl-10 pr-3 py-2 bg-background text-foreground font-mono text-center tracking-[0.3em] text-lg rounded-lg border border-border shadow-sm focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary transition-all"
                  />
                </div>
                {otpErrors.otp && (
                  <p role="alert" className="text-xs text-destructive mt-0.5">
                    {otpErrors.otp.message}
                  </p>
                )}
              </div>

              <button
                type="submit"
                disabled={verifyMutation.isPending}
                className="w-full mt-2 py-2.5 px-4 rounded-lg bg-primary text-primary-foreground font-semibold text-base shadow-sm flex items-center justify-center gap-2 hover:bg-primary/90 transition-colors disabled:opacity-50 disabled:cursor-not-allowed cursor-pointer"
              >
                {verifyMutation.isPending ? (
                  <>
                    <RefreshCw className="w-4 h-4 animate-spin" />
                    <span>Verifying...</span>
                  </>
                ) : (
                  <>
                    <span>Verify Code</span>
                    <ArrowRight className="w-5 h-5" />
                  </>
                )}
              </button>

              {/* Resend OTP Section */}
              <div className="flex flex-col items-center gap-2 mt-3 pt-2 border-t border-border/40">
                {resendNotice && (
                  <div
                    role="status"
                    aria-live="polite"
                    className="w-full p-2.5 rounded-lg flex items-start gap-2 bg-green-500/10 text-green-700 dark:text-green-400 border border-green-500/20 text-xs transition-all"
                  >
                    <CheckCircle className="w-4 h-4 mt-0.5 shrink-0" />
                    <span className="flex-1 font-medium">{resendNotice}</span>
                  </div>
                )}

                {resendError && (
                  <div
                    role="alert"
                    aria-live="assertive"
                    className="w-full p-2.5 rounded-lg flex items-start gap-2 bg-destructive/10 text-destructive border border-destructive/20 text-xs transition-all"
                  >
                    <AlertCircle className="w-4 h-4 mt-0.5 shrink-0" />
                    <span className="flex-1 font-medium">{resendError}</span>
                  </div>
                )}

                <div className="flex items-center justify-center flex-wrap gap-1.5 text-xs text-muted-foreground">
                  <span>Chưa nhận được mã?</span>
                  <button
                    type="button"
                    id="resend-otp-button"
                    onClick={handleResendCode}
                    disabled={countdown > 0 || resendMutation.isPending}
                    className="text-primary hover:underline font-semibold disabled:opacity-50 disabled:cursor-not-allowed inline-flex items-center gap-1 transition-colors cursor-pointer"
                    aria-label={countdown > 0 ? `Gửi lại mã sau ${countdown} giây` : 'Gửi lại mã'}
                  >
                    {resendMutation.isPending ? (
                      <>
                        <RefreshCw className="w-3.5 h-3.5 animate-spin" />
                        <span>Đang gửi lại...</span>
                      </>
                    ) : countdown > 0 ? (
                      <span>Gửi lại mã ({countdown}s)</span>
                    ) : (
                      <span>Gửi lại mã</span>
                    )}
                  </button>
                </div>
              </div>
            </form>
          </>
        )}

        <div className="mt-6 text-center">
          <Link
            href="/login"
            className="text-sm font-medium text-primary hover:text-primary/80 transition-colors inline-flex items-center justify-center gap-1"
          >
            <ArrowLeft className="w-4 h-4" />
            {t('auth.backToLogin')}
          </Link>
        </div>
      </div>
    </main>
  );
}
