'use client';

import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation } from '@tanstack/react-query';
import { useRouter, useSearchParams } from 'next/navigation';
import { z } from 'zod';
import { loginSchema } from '../schemas/auth.schema';
import { authApi } from '../api/auth.api';
import { useSessionStore } from '@/stores/session.store';
import { useTranslation } from '@/i18n';
import { Eye, EyeOff, Lock, ArrowRight, AlertCircle, CheckCircle, RefreshCcw } from 'lucide-react';

export function LoginForm() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const [showPassword, setShowPassword] = useState(false);
  const setSession = useSessionStore((state) => state.setSession);
  const { t } = useTranslation();

  const {
    register,
    handleSubmit,
    watch,
    formState: { errors },
  } = useForm<z.input<typeof loginSchema>>({
    resolver: zodResolver(loginSchema),
    defaultValues: {
      identity: '',
      password: '',
      rememberMe: true,
    },
  });

  const identityValue = watch('identity');
  const isTeacher = /^(GV|TC|LEC|PROF)[0-9]*/i.test(identityValue || '') || (identityValue || '').includes('@lecturer.') || (identityValue || '').includes('@faculty.');
  const isStudent = /^(SE|CS|IT|DS|AI|ST)[0-9]+/i.test(identityValue || '') || (identityValue || '').includes('@student.') || (identityValue || '').includes('@fpt.edu.vn');

  const loginMutation = useMutation({
    mutationFn: authApi.login,
    onSuccess: (data) => {
      // Set Zustand session (only minimal metadata, no token)
      setSession(data.user);

      // Ensure client-side role cookie matches authenticated backend role immediately
      if (typeof document !== 'undefined') {
        document.cookie = `codeassess_role=${data.user.role}; path=/; max-age=86400; SameSite=Lax`;
      }

      // Sanitize redirect parameter to eliminate self-redirect loops and open redirects
      const rawRedirect = searchParams?.get('redirect');
      const safeRedirect = (() => {
        if (!rawRedirect) return null;
        if (!rawRedirect.startsWith('/') || rawRedirect.startsWith('//') || rawRedirect.startsWith('/\\')) {
          return null;
        }
        try {
          const dummyOrigin = 'http://localhost';
          const parsed = new URL(rawRedirect, dummyOrigin);
          if (parsed.origin !== dummyOrigin) return null;
          const cleanPath = parsed.pathname.toLowerCase();
          const authRoutes = ['/login', '/register', '/forgot-password', '/reset-password'];
          if (authRoutes.includes(cleanPath)) {
            return null;
          }
          return parsed.pathname + parsed.search + parsed.hash;
        } catch {
          return null;
        }
      })();
      
      if (safeRedirect) {
        router.push(safeRedirect);
      } else {
        if (data.user.role === 'TEACHER') router.push('/teacher/dashboard');
        else if (data.user.role === 'ADMIN') router.push('/admin/dashboard');
        else router.push('/student/dashboard');
      }
    },
  });

  const onSubmit = (data: z.input<typeof loginSchema>) => {
    // In Phase 2, this will make a real API call. 
    // Since backend isn't up, it will throw an error handled by onError.
    loginMutation.mutate({
      identity: data.identity, // Will be mapped to email in API client
      password: data.password,
      rememberMe: data.rememberMe ?? false,
    });
  };

  return (
    <main className="w-full max-w-md bg-card border border-border/40 rounded-xl p-8 shadow-[0_4px_12px_rgba(0,0,0,0.05)]">
      <div className="flex flex-col w-full">
        <div className="rounded-xl bg-card flex flex-col">
          {/* Platform Branding Header */}
          <div className="flex flex-col items-center text-center mb-8">
            <div className="w-16 h-16 rounded-xl bg-muted flex items-center justify-center p-2 mb-2 shadow-sm relative overflow-hidden">
              <span className="font-bold text-2xl text-primary">CA</span>
            </div>
            <div className="inline-flex items-center gap-1.5 px-2 py-0.5 rounded-full bg-sidebar-accent text-primary text-xs font-medium mb-1 tracking-tight">
              <span className="w-1.5 h-1.5 rounded-full bg-primary animate-pulse"></span>
              {t('auth.directoryAccess')}
            </div>
            <h1 className="text-2xl font-semibold text-foreground tracking-tight">{t('auth.signInTitle')}</h1>
            <p className="text-sm text-muted-foreground mt-1 max-w-xs">
              {t('auth.signInSubtitle')}
            </p>
          </div>

          {/* Feedback Banner */}
          {loginMutation.isError && (
            <div className="mb-4 p-3 rounded-lg flex items-start gap-3 bg-destructive/10 text-destructive transition-all">
              <AlertCircle className="w-5 h-5 mt-0.5" />
              <div className="flex-1">
                <p className="text-[13px] font-semibold">{t('auth.authFailed')}</p>
                <p className="text-xs mt-0.5">{t('auth.authFailedDesc')}</p>
              </div>
            </div>
          )}

          {loginMutation.isSuccess && (
            <div className="mb-4 p-3 rounded-lg flex items-start gap-3 bg-green-100 text-green-800 transition-all">
              <CheckCircle className="w-5 h-5 mt-0.5" />
              <div className="flex-1">
                <p className="text-[13px] font-semibold">{t('auth.signingIn')}</p>
              </div>
            </div>
          )}

          {/* Unified Form */}
          <form className="flex flex-col gap-4" onSubmit={handleSubmit(onSubmit)} noValidate>
            
            {/* Identifier Field */}
            <div className="flex flex-col gap-1.5">
              <div className="flex items-center justify-between">
                <label className="text-[13px] font-medium text-foreground" htmlFor="identity">
                  {t('auth.identityLabel')}
                </label>
                {isTeacher && (
                   <span className="px-2 py-0.5 rounded bg-green-100 text-green-800 text-[11px] font-semibold tracking-wide">
                     {t('auth.facultyBadge')}
                   </span>
                )}
                {isStudent && (
                   <span className="px-2 py-0.5 rounded bg-blue-100 text-primary text-[11px] font-semibold tracking-wide">
                     {t('auth.studentBadge')}
                   </span>
                )}
              </div>
              <div className="relative flex items-center">
                <span className="absolute left-3 text-muted-foreground pointer-events-none">
                  <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M3 7V5a2 2 0 0 1 2-2h2"/><path d="M17 3h2a2 2 0 0 1 2 2v2"/><path d="M21 17v2a2 2 0 0 1-2 2h-2"/><path d="M7 21H5a2 2 0 0 1-2-2v-2"/><rect width="7" height="5" x="7" y="7" rx="1"/><rect width="7" height="5" x="10" y="12" rx="1"/></svg>
                </span>
                <input
                  id="identity"
                  type="text"
                  autoComplete="username"
                  placeholder={t('auth.identityPlaceholder')}
                  className={`w-full pl-10 pr-3 py-2 bg-card text-foreground text-sm rounded-lg border ${errors.identity ? 'border-destructive focus:ring-destructive' : 'border-border focus:ring-primary'} shadow-sm placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:border-transparent transition-all`}
                  {...register('identity')}
                />
              </div>
              {errors.identity && (
                <p className="text-xs text-destructive flex items-center gap-1 mt-0.5">
                  <AlertCircle className="w-3 h-3" />
                  <span>{errors.identity.message}</span>
                </p>
              )}
            </div>

            {/* Password Field */}
            <div className="flex flex-col gap-1.5">
              <div className="flex items-center justify-between">
                <label className="text-[13px] font-medium text-foreground" htmlFor="password">
                  {t('auth.passwordLabel')}
                </label>
              </div>
              <div className="relative flex items-center">
                <span className="absolute left-3 text-muted-foreground pointer-events-none">
                  <Lock className="w-4 h-4" />
                </span>
                <input
                  id="password"
                  type={showPassword ? 'text' : 'password'}
                  autoComplete="current-password"
                  placeholder={t('auth.passwordPlaceholder')}
                  className={`w-full pl-10 pr-10 py-2 bg-card text-foreground text-sm rounded-lg border ${errors.password ? 'border-destructive focus:ring-destructive' : 'border-border focus:ring-primary'} shadow-sm placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:border-transparent transition-all`}
                  {...register('password')}
                />
                <button
                  type="button"
                  title={showPassword ? t('auth.hidePassword') : t('auth.showPassword')}
                  className="absolute right-2 p-1 text-muted-foreground hover:text-foreground rounded transition-colors flex items-center justify-center cursor-pointer"
                  onClick={() => setShowPassword(!showPassword)}
                >
                  {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                </button>
              </div>
              {errors.password && (
                <p className="text-xs text-destructive flex items-center gap-1 mt-0.5">
                  <AlertCircle className="w-3 h-3" />
                  <span>{errors.password.message}</span>
                </p>
              )}
            </div>

            {/* Options Row */}
            <div className="flex items-center justify-between pt-1">
              <label className="flex items-center gap-2 cursor-pointer group select-none">
                <input
                  type="checkbox"
                  className="w-4 h-4 rounded bg-card text-primary border-border focus:ring-primary shadow-sm transition"
                  {...register('rememberMe')}
                />
                <span className="text-sm text-muted-foreground group-hover:text-foreground transition-colors flex items-center gap-1">
                  {t('auth.rememberMe')}
                </span>
              </label>
              <a
                href="/forgot-password"
                className="text-[13px] font-medium text-primary hover:text-primary/80 transition-colors"
              >
                {t('auth.forgotPasswordLink')}
              </a>
            </div>

            {/* Action Button */}
            <button
              type="submit"
              disabled={loginMutation.isPending || loginMutation.isSuccess}
              className="w-full mt-2 py-2.5 px-4 rounded-lg bg-primary hover:bg-primary/90 text-primary-foreground font-semibold text-base shadow-sm transition-all flex items-center justify-center gap-2 disabled:opacity-70 disabled:cursor-not-allowed cursor-pointer"
            >
              {loginMutation.isPending || loginMutation.isSuccess ? (
                <>
                  <span>{t('auth.signingIn')}</span>
                  <RefreshCcw className="w-5 h-5 animate-spin" />
                </>
              ) : (
                <>
                  <span>{t('auth.signInButton')}</span>
                  <ArrowRight className="w-5 h-5" />
                </>
              )}
            </button>
          </form>

          {/* Secondary Navigation */}
          <div className="mt-6 text-center">
            <p className="text-[13px] text-muted-foreground">
              {t('auth.noAccountPrompt')}{' '}
              <a href="/register" className="font-medium text-primary hover:text-primary/80 transition-colors ml-1">
                {t('auth.createOne')}
              </a>
            </p>
          </div>

          {/* SSO Alternative / Student Directory Info */}
          <div className="mt-8 pt-4 bg-muted/50 rounded-lg p-3 flex items-center justify-between">
            <div className="flex items-center gap-2">
              <div className="flex flex-col">
                <span className="text-[13px] font-medium text-foreground">{t('auth.directPortalAccess')}</span>
                <span className="text-xs text-muted-foreground">{t('auth.autoDetectRole')}</span>
              </div>
            </div>
            <span className="text-[11px] px-2 py-1 bg-muted rounded font-mono text-secondary">
              {t('auth.proctorVersion')}
            </span>
          </div>
        </div>

        {/* Institutional Compliance Badge */}
        <div className="mt-4 flex flex-col items-center text-center gap-1">
          <div className="flex items-center gap-1.5 text-muted-foreground">
            <span className="text-xs">
              {t('auth.complianceNotice')}
            </span>
          </div>
          <span className="text-[11px] text-border">
            {t('auth.academicYearFooter')}
          </span>
        </div>
      </div>
    </main>
  );
}
