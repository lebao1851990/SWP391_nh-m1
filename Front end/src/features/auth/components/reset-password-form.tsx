'use client';

import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation } from '@tanstack/react-query';
import { useSearchParams } from 'next/navigation';
import { resetPasswordSchema, ResetPasswordFormData } from '../schemas/auth.schema';
import { authApi } from '../api/auth.api';
import { Eye, EyeOff, Lock, ArrowRight, AlertCircle, CheckCircle, RefreshCcw } from 'lucide-react';
import Link from 'next/link';

export function ResetPasswordForm() {
  const searchParams = useSearchParams();
  const token = searchParams?.get('token') || '';
  
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
          <h1 className="text-2xl font-semibold text-foreground tracking-tight">Liên kết không hợp lệ</h1>
          <p className="text-sm text-muted-foreground mt-2 mb-6">
            Liên kết đặt lại mật khẩu bị thiếu hoặc đã hết hạn. Vui lòng yêu cầu một liên kết mới.
          </p>
          <Link
            href="/forgot-password"
            className="text-sm font-medium text-primary hover:text-primary/80 transition-colors"
          >
            Yêu cầu liên kết mới
          </Link>
        </div>
      </main>
    );
  }

  if (success) {
    return (
      <main className="w-full max-w-md bg-card border border-border/40 rounded-xl p-8 shadow-[0_4px_12px_rgba(0,0,0,0.05)]">
        <div className="flex flex-col w-full text-center items-center">
          <div className="w-16 h-16 rounded-xl bg-green-100 flex items-center justify-center p-2 mb-6">
            <CheckCircle className="w-8 h-8 text-green-700" />
          </div>
          <h1 className="text-2xl font-semibold text-foreground tracking-tight">Đặt lại mật khẩu thành công</h1>
          <p className="text-sm text-muted-foreground mt-2 mb-6">
            Mật khẩu của bạn đã được cập nhật thành công.
          </p>
          <Link
            href="/login"
            className="w-full flex items-center justify-center py-2.5 rounded-lg bg-primary text-primary-foreground font-medium hover:bg-primary/90 transition-colors"
          >
            Quay lại Đăng nhập
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
          <h1 className="text-2xl font-semibold text-foreground tracking-tight">Tạo mật khẩu mới</h1>
          <p className="text-sm text-muted-foreground mt-1 max-w-xs">
            Nhập mật khẩu an toàn mới của bạn để truy cập hệ thống.
          </p>
        </div>

        {mutation.isError && (
          <div className="mb-4 p-3 rounded-lg flex items-start gap-3 bg-destructive/10 text-destructive transition-all">
            <AlertCircle className="w-5 h-5 mt-0.5" />
            <div className="flex-1">
              <p className="text-[13px] font-semibold">Đặt lại thất bại</p>
              <p className="text-xs mt-0.5">Liên kết có thể đã hết hạn hoặc không hợp lệ.</p>
            </div>
          </div>
        )}

        <form className="flex flex-col gap-4" onSubmit={handleSubmit(onSubmit)} noValidate>
          {/* Password Field */}
          <div className="flex flex-col gap-1.5">
            <label className="text-[13px] font-medium text-foreground" htmlFor="password">
              Mật khẩu mới
            </label>
            <div className="relative flex items-center">
              <span className="absolute left-3 text-muted-foreground pointer-events-none">
                <Lock className="w-4 h-4" />
              </span>
              <input
                id="password"
                type={showPassword ? 'text' : 'password'}
                {...register('password')}
                disabled={mutation.isPending}
                placeholder="Nhập mật khẩu mới (tối thiểu 8 ký tự)"
                className="w-full pl-10 pr-10 py-2 bg-background text-foreground text-sm rounded-lg border border-border shadow-sm focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary"
              />
              <button
                type="button"
                onClick={() => setShowPassword(!showPassword)}
                className="absolute right-3 text-muted-foreground hover:text-foreground transition-colors"
                tabIndex={-1}
              >
                {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
              </button>
            </div>
            {errors.password && (
              <span className="text-xs text-destructive">{errors.password.message}</span>
            )}
          </div>

          {/* Confirm Password Field */}
          <div className="flex flex-col gap-1.5">
            <label className="text-[13px] font-medium text-foreground" htmlFor="confirmPassword">
              Xác nhận mật khẩu mới
            </label>
            <div className="relative flex items-center">
              <span className="absolute left-3 text-muted-foreground pointer-events-none">
                <Lock className="w-4 h-4" />
              </span>
              <input
                id="confirmPassword"
                type={showConfirm ? 'text' : 'password'}
                {...register('confirmPassword')}
                disabled={mutation.isPending}
                placeholder="Nhập lại mật khẩu mới"
                className="w-full pl-10 pr-10 py-2 bg-background text-foreground text-sm rounded-lg border border-border shadow-sm focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary"
              />
              <button
                type="button"
                onClick={() => setShowConfirm(!showConfirm)}
                className="absolute right-3 text-muted-foreground hover:text-foreground transition-colors"
                tabIndex={-1}
              >
                {showConfirm ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
              </button>
            </div>
            {errors.confirmPassword && (
              <span className="text-xs text-destructive">{errors.confirmPassword.message}</span>
            )}
          </div>

          <button
            type="submit"
            disabled={mutation.isPending}
            className="w-full mt-2 py-2.5 px-4 rounded-lg bg-primary text-primary-foreground font-semibold text-base shadow-sm flex items-center justify-center gap-2 hover:bg-primary/90 transition-colors disabled:opacity-50 cursor-pointer"
          >
            {mutation.isPending ? (
              <>
                <RefreshCcw className="w-5 h-5 animate-spin" />
                <span>Đang cập nhật mật khẩu...</span>
              </>
            ) : (
              <>
                <span>Lưu mật khẩu</span>
                <ArrowRight className="w-5 h-5" />
              </>
            )}
          </button>
        </form>
      </div>
    </main>
  );
}
