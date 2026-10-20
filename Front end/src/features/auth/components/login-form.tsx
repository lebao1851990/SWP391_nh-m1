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
import { Eye, EyeOff, Lock, ArrowRight, Info, AlertCircle, CheckCircle, RefreshCcw } from 'lucide-react';
import { parseApiError } from '@/lib/api-error';

export function LoginForm() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const [showPassword, setShowPassword] = useState(false);
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
      // Set Zustand session (only minimal metadata, no token)
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

  const parsedLoginError = loginMutation.isError ? parseApiError(loginMutation.error) : null;

  const getLoginErrorMessage = () => {
    if (!parsedLoginError) return null;
    if (parsedLoginError.isNetworkError) {
      return {
        title: 'Không thể kết nối đến máy chủ',
        desc: 'Không thể kết nối đến máy chủ. Vui lòng kiểm tra kết nối mạng và thử lại sau.',
      };
    }
    if (parsedLoginError.isUnauthorized) {
      return {
        title: 'Đăng nhập thất bại',
        desc: 'Thông tin đăng nhập không chính xác. Vui lòng kiểm tra lại email hoặc mật khẩu.',
      };
    }
    if (parsedLoginError.isForbidden) {
      return {
        title: 'Tài khoản bị hạn chế',
        desc: 'Tài khoản của bạn đã bị khóa hoặc không có quyền truy cập.',
      };
    }
    if (parsedLoginError.isBadRequest) {
      return {
        title: 'Yêu cầu không hợp lệ',
        desc: 'Định dạng thông tin đăng nhập không hợp lệ. Vui lòng kiểm tra lại email.',
      };
    }
    if (parsedLoginError.isServerError) {
      return {
        title: 'Lỗi máy chủ',
        desc: 'Máy chủ gặp sự cố trong quá trình xử lý xác thực. Vui lòng thử lại sau ít phút.',
      };
    }
    return {
      title: 'Đăng nhập thất bại',
      desc: 'Đã xảy ra lỗi trong quá trình xác thực. Vui lòng thử lại sau.',
    };
  };

  const loginErrorDetails = getLoginErrorMessage();

  const onSubmit = (data: z.input<typeof loginSchema>) => {
    loginMutation.mutate({
      identity: data.identity,
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
              TRUY CẬP HỆ THỐNG
            </div>
            <h1 className="text-2xl font-semibold text-foreground tracking-tight">Đăng nhập CodeAssess</h1>
            <p className="text-sm text-muted-foreground mt-1 max-w-xs">
              Cổng thông tin thi lập trình đại học, chấm điểm tự động &amp; liêm chính học thuật.
            </p>
          </div>

          {/* Feedback Banner */}
          {loginMutation.isError && loginErrorDetails && (
            <div className="mb-4 p-3 rounded-lg flex items-start gap-3 bg-destructive/10 text-destructive transition-all">
              <AlertCircle className="w-5 h-5 mt-0.5 flex-shrink-0" />
              <div className="flex-1">
                <p className="text-[13px] font-semibold">{loginErrorDetails.title}</p>
                <p className="text-xs mt-0.5">{loginErrorDetails.desc}</p>
              </div>
            </div>
          )}

          {loginMutation.isSuccess && (
            <div className="mb-4 p-3 rounded-lg flex items-start gap-3 bg-green-100 text-green-800 transition-all">
              <CheckCircle className="w-5 h-5 mt-0.5" />
              <div className="flex-1">
                <p className="text-[13px] font-semibold">Đang xác thực thông tin</p>
                <p className="text-xs mt-0.5">Xác thực thành công. Đang chuyển hướng đến Bảng điều khiển...</p>
              </div>
            </div>
          )}

          {/* Unified Form */}
          <form className="flex flex-col gap-4" onSubmit={handleSubmit(onSubmit)} noValidate>
            
            {/* Identifier Field */}
            <div className="flex flex-col gap-1.5">
              <div className="flex items-center justify-between">
                <label className="text-[13px] font-medium text-foreground" htmlFor="identity">
                  Email trường hoặc Mã số Sinh viên / Cán bộ
                </label>
                {isTeacher && (
                   <span className="px-2 py-0.5 rounded bg-green-100 text-green-800 text-[11px] font-semibold tracking-wide">
                     TÀI KHOẢN GIẢNG VIÊN
                   </span>
                )}
                {isStudent && (
                   <span className="px-2 py-0.5 rounded bg-blue-100 text-primary text-[11px] font-semibold tracking-wide">
                     TÀI KHOẢN SINH VIÊN
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
                  placeholder="Ví dụ: student@fpt.edu.vn hoặc GV1084"
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
                  Mật khẩu
                </label>
                <span className="text-[11px] font-medium text-muted-foreground">Tối thiểu 4 ký tự</span>
              </div>
              <div className="relative flex items-center">
                <span className="absolute left-3 text-muted-foreground pointer-events-none">
                  <Lock className="w-4 h-4" />
                </span>
                <input
                  id="password"
                  type={showPassword ? 'text' : 'password'}
                  autoComplete="current-password"
                  placeholder="Nhập mật khẩu an toàn"
                  className={`w-full pl-10 pr-10 py-2 bg-card text-foreground text-sm rounded-lg border ${errors.password ? 'border-destructive focus:ring-destructive' : 'border-border focus:ring-primary'} shadow-sm placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:border-transparent transition-all`}
                  {...register('password')}
                />
                <button
                  type="button"
                  title="Hiện/ẩn mật khẩu"
                  className="absolute right-2 p-1 text-muted-foreground hover:text-foreground rounded transition-colors flex items-center justify-center"
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
                  Ghi nhớ máy tính này
                  <span title="Duy trì phiên đăng nhập bảo mật trong 30 ngày">
                    <Info className="w-3 h-3 text-border group-hover:text-primary transition-colors" />
                  </span>
                </span>
              </label>
              <a
                href="/forgot-password"
                className="text-[13px] font-medium text-primary hover:text-primary/80 transition-colors"
              >
                Quên mật khẩu?
              </a>
            </div>

            {/* Action Button */}
            <button
              type="submit"
              disabled={loginMutation.isPending || loginMutation.isSuccess}
              className="w-full mt-2 py-2.5 px-4 rounded-lg bg-primary hover:bg-primary/90 text-primary-foreground font-semibold text-base shadow-sm transition-all flex items-center justify-center gap-2 disabled:opacity-70 disabled:cursor-not-allowed"
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
            <p className="text-[13px] text-muted-foreground">
              Chưa có tài khoản?{' '}
              <a href="/register" className="font-medium text-primary hover:text-primary/80 transition-colors ml-1">
                Tạo tài khoản mới
              </a>
            </p>
          </div>

          {/* SSO Alternative / Student Directory Info */}
          <div className="mt-8 pt-4 bg-muted/50 rounded-lg p-3 flex items-center justify-between">
            <div className="flex items-center gap-2">
              <div className="flex flex-col">
                <span className="text-[13px] font-medium text-foreground">Truy cập Cổng trực tiếp</span>
                <span className="text-xs text-muted-foreground">Tự động nhận diện quyền Giảng viên &amp; Sinh viên</span>
              </div>
            </div>
            <span className="text-[11px] px-2 py-1 bg-muted rounded font-mono text-secondary">
              v4.12-PROCTOR
            </span>
          </div>
        </div>

        {/* Institutional Compliance Badge */}
        <div className="mt-4 flex flex-col items-center text-center gap-1">
          <div className="flex items-center gap-1.5 text-muted-foreground">
            <span className="text-xs">
              Được bảo vệ bởi xác thực an toàn &amp; Giám sát liêm chính học thuật AST
            </span>
          </div>
          <span className="text-[11px] text-border">
            Năm học 2025–2026 • Ban Khảo thí &amp; Đánh giá Năng lực Tin học
          </span>
        </div>
      </div>
    </main>
  );
}
