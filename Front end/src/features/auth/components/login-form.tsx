'use client';

import { useState } from 'react';
import Link from 'next/link';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation } from '@tanstack/react-query';
import { useRouter, useSearchParams } from 'next/navigation';
import { z } from 'zod';
import { loginSchema } from '../schemas/auth.schema';
import { authApi } from '../api/auth.api';
import { useSessionStore } from '@/stores/session.store';
import { Eye, EyeOff, Lock, ArrowRight, Info, AlertCircle, CheckCircle, RefreshCcw } from 'lucide-react';
import { HudCorners } from '@/components/ui/hud-card';

export function LoginForm() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const [showPassword, setShowPassword] = useState(false);
  const [spotlightPos, setSpotlightPos] = useState({ x: 220, y: 140, active: false });
  const setSession = useSessionStore((state) => state.setSession);

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
      // Update local tab session cache with authenticated user
      setSession(data.user);

      // (The backend will set the HttpOnly cookie for real authentication)

      // Redirect
      const redirect = searchParams?.get('redirect');
      const isSafeRedirect = redirect && redirect.startsWith('/') && !redirect.startsWith('//');

      if (isSafeRedirect) {
        router.push(redirect);
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
    <main
      onMouseMove={(e) => {
        const rect = e.currentTarget.getBoundingClientRect();
        setSpotlightPos({
          x: e.clientX - rect.left,
          y: e.clientY - rect.top,
          active: true,
        });
      }}
      onMouseLeave={() => setSpotlightPos((prev) => ({ ...prev, active: false }))}
      className="relative w-full max-w-md rounded-2xl p-[1px] overflow-hidden shadow-[0_0_0_1px_rgba(34,211,238,0.22),0_16px_48px_-8px_rgba(37,99,235,0.45)]"
    >
      {/* Animated Conic Gradient Border */}
      <div
        className="nc-anim-orbit-cw pointer-events-none absolute -inset-[120%] opacity-65"
        style={{
          background:
            'conic-gradient(from 0deg, transparent 0%, #2563EB 20%, #22D3EE 35%, #6366F1 50%, transparent 70%)',
          animation: 'nc-orbit-cw 14s linear infinite',
        }}
        aria-hidden="true"
      />

      {/* Inner Frosted Glass Surface */}
      <div
        className="relative z-10 w-full rounded-[15px] bg-[#0B1220]/80 backdrop-blur-2xl border border-slate-400/15 p-6 sm:p-8 shadow-[inset_0_1px_0_rgba(255,255,255,0.08)]"
      >
        {/* 4 HUD Corner Brackets */}
        <HudCorners colorClass="border-cyan-400/60" sizeClass="w-3 h-3" />

        {/* Subtle Cursor Spotlight */}
        <div
          className="pointer-events-none absolute inset-0 rounded-[15px] transition-opacity duration-300"
          style={{
            opacity: spotlightPos.active ? 1 : 0,
            background: `radial-gradient(320px circle at ${spotlightPos.x}px ${spotlightPos.y}px, rgba(34,211,238,0.09), transparent 75%)`,
          }}
          aria-hidden="true"
        />

        <div className="relative z-10 flex flex-col w-full">
          <div className="rounded-xl flex flex-col">
            {/* Platform Branding Header */}
            <div className="flex flex-col items-center text-center mb-7">
              <div className="w-16 h-16 rounded-2xl bg-gradient-to-br from-blue-600/25 via-cyan-500/20 to-indigo-600/25 border border-cyan-400/35 flex items-center justify-center p-2 mb-3 shadow-[0_0_24px_rgba(34,211,238,0.25)] relative overflow-hidden">
                <span className="font-mono font-bold text-2xl bg-gradient-to-r from-white via-cyan-200 to-blue-300 bg-clip-text text-transparent tracking-tight">
                  CA
                </span>
              </div>
              <div className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full bg-cyan-500/10 border border-cyan-400/30 text-cyan-300 font-mono text-[11px] font-semibold mb-2 tracking-wider">
                <span className="w-1.5 h-1.5 rounded-full bg-cyan-400 animate-pulse"></span>
                TRUY CẬP HỆ THỐNG
              </div>
              <h1 className="text-2xl font-semibold text-white tracking-tight">Đăng nhập CodeAssess</h1>
              <p className="text-sm text-slate-300 mt-1 max-w-xs leading-relaxed">
                Cổng thông tin thi lập trình đại học, chấm điểm tự động &amp; liêm chính học thuật.
              </p>
            </div>

            {/* Feedback Banner */}
            {loginMutation.isError && (
              <div className="mb-4 p-3 rounded-lg flex items-start gap-3 bg-rose-500/15 border border-rose-500/40 text-rose-200 transition-all">
                <AlertCircle className="w-5 h-5 mt-0.5 text-rose-400 shrink-0" />
                <div className="flex-1">
                  <p className="text-[13px] font-semibold text-rose-100">Đăng nhập thất bại</p>
                  <p className="text-xs mt-0.5 text-rose-200/90">Thông tin đăng nhập không hợp lệ hoặc tài khoản bị khóa tạm thời.</p>
                </div>
              </div>
            )}

            {loginMutation.isSuccess && (
              <div className="mb-4 p-3 rounded-lg flex items-start gap-3 bg-emerald-500/15 border border-emerald-500/40 text-emerald-200 transition-all">
                <CheckCircle className="w-5 h-5 mt-0.5 text-emerald-400 shrink-0" />
                <div className="flex-1">
                  <p className="text-[13px] font-semibold text-emerald-100">Đang xác thực thông tin</p>
                  <p className="text-xs mt-0.5 text-emerald-200/90">Xác thực thành công. Đang chuyển hướng đến Bảng điều khiển...</p>
                </div>
              </div>
            )}

            {/* Unified Form */}
            <form className="flex flex-col gap-4" onSubmit={handleSubmit(onSubmit)} noValidate>

              {/* Identifier Field */}
              <div className="flex flex-col gap-1.5">
                <div className="flex items-center justify-between gap-2">
                  <label className="text-[13px] font-medium text-slate-200" htmlFor="identity">
                    Email trường hoặc Mã số Sinh viên / Cán bộ
                  </label>
                  {isTeacher && (
                     <span className="px-2 py-0.5 rounded bg-emerald-500/20 border border-emerald-400/40 text-emerald-300 font-mono text-[10px] font-semibold tracking-wide shrink-0">
                       TÀI KHOẢN GIẢNG VIÊN
                     </span>
                  )}
                  {isStudent && (
                     <span className="px-2 py-0.5 rounded bg-cyan-500/20 border border-cyan-400/40 text-cyan-300 font-mono text-[10px] font-semibold tracking-wide shrink-0">
                       TÀI KHOẢN SINH VIÊN
                     </span>
                  )}
                </div>
                <div className="relative flex items-center">
                  <span className="absolute left-3 text-slate-400 pointer-events-none">
                    <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M3 7V5a2 2 0 0 1 2-2h2"/><path d="M17 3h2a2 2 0 0 1 2 2v2"/><path d="M21 17v2a2 2 0 0 1-2 2h-2"/><path d="M7 21H5a2 2 0 0 1-2-2v-2"/><rect width="7" height="5" x="7" y="7" rx="1"/><rect width="7" height="5" x="10" y="12" rx="1"/></svg>
                  </span>
                  <input
                    id="identity"
                    type="text"
                    autoComplete="username"
                    placeholder="Ví dụ: student@fpt.edu.vn hoặc GV1084"
                    className={`w-full pl-10 pr-3 py-2.5 bg-slate-950/75 text-white text-sm rounded-lg border ${errors.identity ? 'border-rose-500 focus:ring-rose-500/50' : 'border-slate-700/80 focus:border-cyan-400 focus:ring-cyan-400/35'} shadow-sm placeholder:text-slate-400 focus:outline-none focus:ring-2 transition-all`}
                    {...register('identity')}
                  />
                </div>
                {errors.identity && (
                  <p className="text-xs text-rose-400 flex items-center gap-1 mt-0.5">
                    <AlertCircle className="w-3 h-3" />
                    <span>{errors.identity.message}</span>
                  </p>
                )}
              </div>

              {/* Password Field */}
              <div className="flex flex-col gap-1.5">
                <div className="flex items-center justify-between">
                  <label className="text-[13px] font-medium text-slate-200" htmlFor="password">
                    Mật khẩu
                  </label>
                  <span className="text-[11px] font-mono text-slate-400">Tối thiểu 4 ký tự</span>
                </div>
                <div className="relative flex items-center">
                  <span className="absolute left-3 text-slate-400 pointer-events-none">
                    <Lock className="w-4 h-4" />
                  </span>
                  <input
                    id="password"
                    type={showPassword ? 'text' : 'password'}
                    autoComplete="current-password"
                    placeholder="Nhập mật khẩu an toàn"
                    className={`w-full pl-10 pr-10 py-2.5 bg-slate-950/75 text-white text-sm rounded-lg border ${errors.password ? 'border-rose-500 focus:ring-rose-500/50' : 'border-slate-700/80 focus:border-cyan-400 focus:ring-cyan-400/35'} shadow-sm placeholder:text-slate-400 focus:outline-none focus:ring-2 transition-all`}
                    {...register('password')}
                  />
                  <button
                    type="button"
                    title="Hiện/ẩn mật khẩu"
                    className="absolute right-2 p-1 text-slate-400 hover:text-cyan-300 rounded transition-colors flex items-center justify-center cursor-pointer"
                    onClick={() => setShowPassword(!showPassword)}
                  >
                    {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                  </button>
                </div>
                {errors.password && (
                  <p className="text-xs text-rose-400 flex items-center gap-1 mt-0.5">
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
                    className="w-4 h-4 rounded bg-slate-900 text-cyan-400 border-slate-600 focus:ring-cyan-400 shadow-sm transition"
                    {...register('rememberMe')}
                  />
                  <span className="text-sm text-slate-300 group-hover:text-white transition-colors flex items-center gap-1">
                    Ghi nhớ máy tính này
                    <span title="Duy trì phiên đăng nhập bảo mật trong 30 ngày">
                      <Info className="w-3 h-3 text-slate-500 group-hover:text-cyan-400 transition-colors" />
                    </span>
                  </span>
                </label>
                <Link
                  href="/forgot-password"
                  className="text-[13px] font-medium text-cyan-400 hover:text-cyan-300 transition-colors"
                >
                  Quên mật khẩu?
                </Link>
              </div>

              {/* Action Button */}
              <button
                type="submit"
                disabled={loginMutation.isPending || loginMutation.isSuccess}
                className="nc-btn-shimmer relative overflow-hidden w-full mt-2 py-2.5 px-4 rounded-lg bg-gradient-to-r from-[#2563EB] via-[#0284C7] to-[#6366F1] hover:brightness-110 text-white font-semibold text-base shadow-[0_0_24px_rgba(37,99,235,0.45)] transition-all flex items-center justify-center gap-2 disabled:opacity-70 disabled:cursor-not-allowed cursor-pointer"
              >
                {loginMutation.isPending || loginMutation.isSuccess ? (
                  <>
                    <span>{loginMutation.isSuccess ? 'Đang chuyển hướng...' : 'Đang xác thực...'}</span>
                    <RefreshCcw className="w-5 h-5 animate-spin" />
                  </>
                ) : (
                  <>
                    <span>Đăng nhập hệ thống</span>
                    <ArrowRight className="w-5 h-5" />
                  </>
                )}
              </button>
            </form>

            {/* Secondary Navigation */}
            <div className="mt-6 text-center">
              <p className="text-[13px] text-slate-300">
                Chưa có tài khoản?{' '}
                <Link href="/register" className="font-medium text-cyan-400 hover:text-cyan-300 transition-colors ml-1">
                  Tạo tài khoản mới
                </Link>
              </p>
            </div>

            {/* SSO Alternative / Student Directory Info */}
            <div className="mt-7 pt-3 bg-slate-950/60 border border-slate-800/80 rounded-lg p-3 flex items-center justify-between">
              <div className="flex items-center gap-2">
                <div className="flex flex-col">
                  <span className="text-[13px] font-medium text-slate-200">Truy cập Cổng trực tiếp</span>
                  <span className="text-xs text-slate-400">Tự động nhận diện quyền Giảng viên &amp; Sinh viên</span>
                </div>
              </div>
              <span className="text-[11px] px-2 py-1 bg-cyan-500/10 border border-cyan-400/25 rounded font-mono text-cyan-300">
                v4.12-PROCTOR
              </span>
            </div>
          </div>

          {/* Institutional Compliance Badge */}
          <div className="mt-4 flex flex-col items-center text-center gap-1">
            <div className="flex items-center gap-1.5 text-slate-300">
              <span className="text-xs">
                Được bảo vệ bởi xác thực an toàn &amp; Giám sát liêm chính học thuật AST
              </span>
            </div>
            <span className="text-[11px] font-mono text-slate-400">
              Năm học 2025–2026 • Ban Khảo thí &amp; Đánh giá Năng lực Tin học
            </span>
          </div>
        </div>
      </div>
    </main>
  );
}
