'use client';

import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation } from '@tanstack/react-query';
import { forgotPasswordSchema, ForgotPasswordFormData } from '../schemas/auth.schema';
import { authApi } from '../api/auth.api';
import { ArrowLeft, ArrowRight, AlertCircle, CheckCircle, RefreshCcw } from 'lucide-react';
import Link from 'next/link';

export function ForgotPasswordForm() {
  const [success, setSuccess] = useState(false);

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<ForgotPasswordFormData>({
    resolver: zodResolver(forgotPasswordSchema),
    defaultValues: {
      identity: '',
    },
  });

  const mutation = useMutation({
    mutationFn: authApi.forgotPassword,
    onSuccess: () => {
      setSuccess(true);
    },
  });

  const onSubmit = (data: ForgotPasswordFormData) => {
    mutation.mutate(data);
  };

  if (success) {
    return (
      <main className="w-full max-w-md bg-card border border-border/40 rounded-xl p-8 shadow-[0_4px_12px_rgba(0,0,0,0.05)]">
        <div className="flex flex-col w-full text-center items-center">
          <div className="w-16 h-16 rounded-xl bg-green-100 flex items-center justify-center p-2 mb-6">
            <CheckCircle className="w-8 h-8 text-green-700" />
          </div>
          <h1 className="text-2xl font-semibold text-foreground tracking-tight">Kiểm tra hộp thư của bạn</h1>
          <p className="text-sm text-muted-foreground mt-2 mb-6">
            Nếu tài khoản tồn tại trong hệ thống, chúng tôi đã gửi hướng dẫn đặt lại mật khẩu đến email của bạn.
          </p>
          <Link
            href="/login"
            className="text-sm font-medium text-primary hover:text-primary/80 transition-colors flex items-center gap-1"
          >
            <ArrowLeft className="w-4 h-4" />
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
          <h1 className="text-2xl font-semibold text-foreground tracking-tight">Đặt lại mật khẩu</h1>
          <p className="text-sm text-muted-foreground mt-1 max-w-xs">
            Nhập email trường hoặc mã định danh của bạn để nhận hướng dẫn khôi phục.
          </p>
        </div>

        {mutation.isError && (
          <div className="mb-4 p-3 rounded-lg flex items-start gap-3 bg-destructive/10 text-destructive transition-all">
            <AlertCircle className="w-5 h-5 mt-0.5" />
            <div className="flex-1">
              <p className="text-[13px] font-semibold">Yêu cầu thất bại</p>
              <p className="text-xs mt-0.5">Không thể xử lý yêu cầu lúc này. Vui lòng thử lại sau.</p>
            </div>
          </div>
        )}

        <form className="flex flex-col gap-4" onSubmit={handleSubmit(onSubmit)} noValidate>
          {/* Identifier Field */}
          <div className="flex flex-col gap-1.5">
            <label className="text-[13px] font-medium text-foreground" htmlFor="identity">
              Email trường hoặc Mã số Sinh viên / Cán bộ
            </label>
            <div className="relative flex items-center">
              <input
                id="identity"
                type="text"
                {...register('identity')}
                disabled={mutation.isPending}
                placeholder="Ví dụ: student@fpt.edu.vn"
                className="w-full px-3 py-2 bg-background text-foreground text-sm rounded-lg border border-border shadow-sm focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary"
              />
            </div>
            {errors.identity && (
              <span className="text-xs text-destructive">{errors.identity.message}</span>
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
                <span>Đang gửi yêu cầu...</span>
              </>
            ) : (
              <>
                <span>Gửi hướng dẫn</span>
                <ArrowRight className="w-5 h-5" />
              </>
            )}
          </button>

          <div className="mt-4 text-center">
            <Link
              href="/login"
              className="text-sm font-medium text-primary hover:text-primary/80 transition-colors flex items-center justify-center gap-1"
            >
              <ArrowLeft className="w-4 h-4" />
              Quay lại Đăng nhập
            </Link>
          </div>
        </form>
      </div>
    </main>
  );
}
