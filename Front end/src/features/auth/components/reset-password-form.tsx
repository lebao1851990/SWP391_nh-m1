'use client';

import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation } from '@tanstack/react-query';
import { useSearchParams } from 'next/navigation';
import { resetPasswordSchema, ResetPasswordFormData } from '../schemas/auth.schema';
import { authApi } from '../api/auth.api';
import { useTranslation } from '@/i18n';
import { Eye, EyeOff, Lock, ArrowRight, AlertCircle, CheckCircle, RefreshCw, ArrowLeft } from 'lucide-react';
import Link from 'next/link';
import { isAxiosError } from 'axios';

export function ResetPasswordForm() {
  const searchParams = useSearchParams();
  const token = searchParams?.get('token') || '';
  const { t } = useTranslation();

  const [showPassword, setShowPassword] = useState(false);
  const [showConfirm, setShowConfirm] = useState(false);
  const [success, setSuccess] = useState(false);

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<ResetPasswordFormData>({
    resolver: zodResolver(resetPasswordSchema),
    defaultValues: {
      password: '',
      confirmPassword: '',
    },
  });

  const mutation = useMutation({
    mutationFn: (data: ResetPasswordFormData) => authApi.resetPassword(token, data),
    onSuccess: () => {
      setSuccess(true);
    },
  });

  const onSubmit = (data: ResetPasswordFormData) => {
    mutation.mutate(data);
  };

  if (!token) {
    return (
      <main className="w-full max-w-md bg-card border border-border/40 rounded-xl p-8 shadow-[0_4px_12px_rgba(0,0,0,0.05)]">
        <div className="flex flex-col w-full text-center items-center">
          <div className="w-16 h-16 rounded-xl bg-destructive/10 flex items-center justify-center p-2 mb-6">
            <AlertCircle className="w-8 h-8 text-destructive" />
          </div>
          <h1 className="text-2xl font-semibold text-foreground tracking-tight">{t('auth.invalidResetLink')}</h1>
          <p className="text-sm text-muted-foreground mt-2 mb-6">
            {t('auth.invalidResetLinkDesc')}
          </p>
          <Link
            href="/forgot-password"
            className="text-sm font-medium text-primary hover:text-primary/80 transition-colors inline-flex items-center gap-1"
          >
            <ArrowLeft className="w-4 h-4" />
            {t('auth.requestNewCode')}
          </Link>
        </div>
      </main>
    );
  }

  if (success) {
    return (
      <main className="w-full max-w-md bg-card border border-border/40 rounded-xl p-8 shadow-[0_4px_12px_rgba(0,0,0,0.05)]">
        <div className="flex flex-col w-full text-center items-center">
          <div className="w-16 h-16 rounded-xl bg-green-100 dark:bg-green-950/40 flex items-center justify-center p-2 mb-6">
            <CheckCircle className="w-8 h-8 text-green-700 dark:text-green-400" />
          </div>
          <h1 className="text-2xl font-semibold text-foreground tracking-tight">{t('auth.passwordUpdatedSuccess')}</h1>
          <p className="text-sm text-muted-foreground mt-2 mb-6">
            {t('auth.passwordUpdatedDesc')}
          </p>
          <Link
            href="/login"
            className="w-full flex items-center justify-center py-2.5 rounded-lg bg-primary text-primary-foreground font-medium hover:bg-primary/90 transition-colors shadow-sm"
          >
            {t('auth.backToLogin')}
          </Link>
        </div>
      </main>
    );
  }

  return (
    <main className="w-full max-w-md bg-card border border-border/40 rounded-xl p-8 shadow-[0_4px_12px_rgba(0,0,0,0.05)]">
      <div className="flex flex-col w-full">
        {/* Header */}
        <div className="flex flex-col items-center text-center mb-8">
          <div className="w-16 h-16 rounded-xl bg-muted flex items-center justify-center p-2 mb-2 shadow-sm">
            <span className="font-bold text-2xl text-primary">CA</span>
          </div>
          <h1 className="text-2xl font-semibold text-foreground tracking-tight">{t('auth.createNewPasswordTitle')}</h1>
          <p className="text-sm text-muted-foreground mt-1 max-w-xs">
            {t('auth.createNewPasswordSubtitle')}
          </p>
        </div>

        {mutation.isError && (
          <div
            role="alert"
            className="mb-4 p-3 rounded-lg flex items-start gap-3 bg-destructive/10 text-destructive transition-all"
          >
            <AlertCircle className="w-5 h-5 mt-0.5 shrink-0" />
            <div className="flex-1">
              <p className="text-[13px] font-semibold">{t('auth.resetFailed')}</p>
              <p className="text-xs mt-0.5">
                {(isAxiosError<{ message?: string }>(mutation.error) && mutation.error.response?.data?.message) ||
                  t('auth.invalidResetLinkDesc')}
              </p>
            </div>
          </div>
        )}

        <form className="flex flex-col gap-4" onSubmit={handleSubmit(onSubmit)} noValidate>
          {/* Password Field */}
          <div className="flex flex-col gap-1.5">
            <label className="text-[13px] font-medium text-foreground" htmlFor="password">
              {t('auth.newPasswordLabel')}
            </label>
            <div className="relative flex items-center">
              <span className="absolute left-3 text-muted-foreground pointer-events-none">
                <Lock className="w-4 h-4" />
              </span>
              <input
                id="password"
                type={showPassword ? 'text' : 'password'}
                autoComplete="new-password"
                placeholder={t('auth.newPasswordPlaceholder')}
                {...register('password')}
                className="w-full pl-10 pr-10 py-2 bg-background text-foreground text-sm rounded-lg border border-border shadow-sm focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary transition-all"
              />
              <button
                type="button"
                onClick={() => setShowPassword(!showPassword)}
                className="absolute right-3 text-muted-foreground hover:text-foreground transition-colors cursor-pointer"
                aria-label={showPassword ? t('auth.hidePassword') : t('auth.showPassword')}
              >
                {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
              </button>
            </div>
            {errors.password && (
              <p role="alert" className="text-xs text-destructive mt-0.5">
                {errors.password.message}
              </p>
            )}
          </div>

          {/* Confirm Password Field */}
          <div className="flex flex-col gap-1.5">
            <label className="text-[13px] font-medium text-foreground" htmlFor="confirmPassword">
              {t('auth.confirmPasswordLabel')}
            </label>
            <div className="relative flex items-center">
              <span className="absolute left-3 text-muted-foreground pointer-events-none">
                <Lock className="w-4 h-4" />
              </span>
              <input
                id="confirmPassword"
                type={showConfirm ? 'text' : 'password'}
                autoComplete="new-password"
                placeholder={t('auth.confirmPasswordPlaceholder')}
                {...register('confirmPassword')}
                className="w-full pl-10 pr-10 py-2 bg-background text-foreground text-sm rounded-lg border border-border shadow-sm focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary transition-all"
              />
              <button
                type="button"
                onClick={() => setShowConfirm(!showConfirm)}
                className="absolute right-3 text-muted-foreground hover:text-foreground transition-colors cursor-pointer"
                aria-label={showConfirm ? t('auth.hidePassword') : t('auth.showPassword')}
              >
                {showConfirm ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
              </button>
            </div>
            {errors.confirmPassword && (
              <p role="alert" className="text-xs text-destructive mt-0.5">
                {errors.confirmPassword.message}
              </p>
            )}
          </div>

          <button
            type="submit"
            disabled={mutation.isPending}
            className="w-full mt-2 py-2.5 px-4 rounded-lg bg-primary text-primary-foreground font-semibold text-base shadow-sm flex items-center justify-center gap-2 hover:bg-primary/90 transition-colors disabled:opacity-50 disabled:cursor-not-allowed cursor-pointer"
          >
            {mutation.isPending ? (
              <>
                <RefreshCw className="w-4 h-4 animate-spin" />
                <span>{t('auth.updatingPassword')}</span>
              </>
            ) : (
              <>
                <span>{t('auth.updatePasswordButton')}</span>
                <ArrowRight className="w-5 h-5" />
              </>
            )}
          </button>
        </form>

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
