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
          <h1 className="text-2xl font-semibold text-foreground tracking-tight">Invalid Link</h1>
          <p className="text-sm text-muted-foreground mt-2 mb-6">
            The password reset link is missing or invalid. Please request a new one.
          </p>
          <Link
            href="/forgot-password"
            className="text-sm font-medium text-primary hover:text-primary/80 transition-colors"
          >
            Request new link
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
          <h1 className="text-2xl font-semibold text-foreground tracking-tight">Password Reset</h1>
          <p className="text-sm text-muted-foreground mt-2 mb-6">
            Your password has been successfully reset.
          </p>
          <Link
            href="/login"
            className="w-full flex items-center justify-center py-2.5 rounded-lg bg-primary text-primary-foreground font-medium hover:bg-primary/90 transition-colors"
          >
            Return to Login
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
          <h1 className="text-2xl font-semibold text-foreground tracking-tight">Create New Password</h1>
          <p className="text-sm text-muted-foreground mt-1 max-w-xs">
            Enter your new secure password below to regain access.
          </p>
        </div>

        {mutation.isError && (
          <div className="mb-4 p-3 rounded-lg flex items-start gap-3 bg-destructive/10 text-destructive transition-all">
            <AlertCircle className="w-5 h-5 mt-0.5" />
            <div className="flex-1">
              <p className="text-[13px] font-semibold">Reset Failed</p>
              <p className="text-xs mt-0.5">The link may have expired or is invalid.</p>
            </div>
          </div>
        )}

        <form className="flex flex-col gap-4" onSubmit={(e) => e.preventDefault()} noValidate>
          {/* Password Field */}
          <div className="flex flex-col gap-1.5 opacity-60">
            <label className="text-[13px] font-medium text-foreground" htmlFor="password">
              New Password
            </label>
            <div className="relative flex items-center">
              <span className="absolute left-3 text-muted-foreground pointer-events-none">
                <Lock className="w-4 h-4" />
              </span>
              <input
                id="password"
                type="password"
                disabled
                placeholder="Enter new password"
                className="w-full pl-10 pr-10 py-2 bg-muted text-muted-foreground text-sm rounded-lg border border-border shadow-sm cursor-not-allowed"
              />
            </div>
          </div>

          {/* Confirm Password Field */}
          <div className="flex flex-col gap-1.5 opacity-60">
            <label className="text-[13px] font-medium text-foreground" htmlFor="confirmPassword">
              Confirm Password
            </label>
            <div className="relative flex items-center">
              <span className="absolute left-3 text-muted-foreground pointer-events-none">
                <Lock className="w-4 h-4" />
              </span>
              <input
                id="confirmPassword"
                type="password"
                disabled
                placeholder="Confirm new password"
                className="w-full pl-10 pr-10 py-2 bg-muted text-muted-foreground text-sm rounded-lg border border-border shadow-sm cursor-not-allowed"
              />
            </div>
          </div>

          <div className="mt-2 p-4 rounded-lg bg-surface-container-low border border-outline-variant/30 text-center flex flex-col items-center gap-2">
            <AlertCircle className="w-6 h-6 text-outline" />
            <span className="font-semibold text-on-surface text-sm">Password Recovery Not Configured</span>
            <span className="text-xs text-on-surface-variant">
              This feature is currently unavailable in this environment. Please contact your system administrator if you need to reset your password.
            </span>
          </div>

          <button
            type="button"
            disabled
            className="w-full mt-2 py-2.5 px-4 rounded-lg bg-surface-container-highest text-on-surface-variant font-semibold text-base shadow-sm flex items-center justify-center gap-2 cursor-not-allowed opacity-70"
          >
            <span>Save Password</span>
            <ArrowRight className="w-5 h-5" />
          </button>
        </form>
      </div>
    </main>
  );
}
