'use client';

import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation } from '@tanstack/react-query';

import Link from 'next/link';
import { registerSchema, RegisterFormData } from '../schemas/auth.schema';
import { authApi } from '../api/auth.api';
import { Eye, EyeOff, Lock, User, Mail, Shield, CheckCircle, AlertCircle, Loader2 } from 'lucide-react';
import { parseApiError } from '@/lib/api-error';

export function RegisterForm() {
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);

  const {
    register,
    handleSubmit,
    watch,
    setValue,
    formState: { errors },
  } = useForm<RegisterFormData>({
    resolver: zodResolver(registerSchema),
    defaultValues: {
      fullName: '',
      email: '',
      accountRole: 'student',
      password: '',
      confirmPassword: '',
      terms: true,
    },
  });

  const accountRole = watch('accountRole');
  const password = watch('password') || '';

  const registerMutation = useMutation({
    mutationFn: authApi.register,
  });

  const onSubmit = (data: RegisterFormData) => {
    registerMutation.mutate(data);
  };

  const parsedError = registerMutation.isError ? parseApiError(registerMutation.error) : null;
  const isEmailExists = parsedError?.isConflict;
  const isNetworkError = parsedError?.isNetworkError;
  const isValidationError = parsedError?.isBadRequest;
  const isServerError = parsedError?.isServerError;
  const isGenericError = registerMutation.isError && !isEmailExists && !isNetworkError && !isValidationError && !isServerError;

  const getPasswordReqClasses = (met: boolean) => 
    met ? 'text-green-700 font-medium' : 'text-slate-600';

  const getPasswordReqIcon = (met: boolean) => 
    met ? <span className="w-2 h-2 rounded-full bg-green-500"></span> : <span className="w-2 h-2 rounded-full bg-slate-300"></span>;

  const hasLength = password.length >= 10;
  const hasUpperLower = /[A-Z]/.test(password) && /[a-z]/.test(password);
  const hasNumber = /[0-9]/.test(password);
  const hasSymbol = /[!@#$%^&*(),.?":{}|<>]/.test(password);

  return (
    <div className="w-full max-w-xl mx-auto flex flex-col items-center antialiased">
      <div className="w-full bg-card rounded-2xl border border-border/40 shadow-[0_4px_12px_rgba(0,0,0,0.05)] p-6 sm:p-10 relative overflow-hidden">
        
        {/* Brand & Badging Header */}
        <div className="flex flex-col items-center text-center mb-7">
          {/* CodeAssess Logo */}
          <div className="mb-3 flex items-center justify-center">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-xl bg-primary flex items-center justify-center shadow-sm relative text-primary-foreground">
                <span className="font-bold text-xl">CA</span>
                <div className="w-2.5 h-2.5 rounded-full bg-emerald-400 border-2 border-primary absolute -top-1 -right-1"></div>
              </div>
              <div className="text-left">
                <div className="text-xl font-bold tracking-tight text-foreground leading-tight">
                  Code<span className="text-primary">Assess</span>
                </div>
                <div className="text-[9px] font-semibold tracking-wider uppercase text-muted-foreground font-mono">
                  Liêm chính Học thuật &amp; Bài thi
                </div>
              </div>
            </div>
          </div>

          {/* Institutional Badge */}
          <div className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full bg-blue-50 border border-blue-100/80 text-[11px] font-medium text-blue-700 mb-2 font-mono">
            <span className="w-1.5 h-1.5 rounded-full bg-blue-600 animate-pulse"></span>
            ĐĂNG KÝ HỆ THỐNG • CỔNG TRƯỜNG
          </div>

          <h1 className="text-2xl sm:text-3xl font-bold text-foreground tracking-tight">
            Tạo tài khoản CodeAssess
          </h1>
          <p className="text-sm text-muted-foreground mt-1.5 max-w-md">
            Đăng ký để truy cập các bài thi lập trình, nộp bài, chấm điểm tự động và công cụ liêm chính học thuật.
          </p>
        </div>

        {/* Banner Feedback Alerts */}
        <div className="mb-6">
          {isEmailExists && (
            <div className="p-4 rounded-xl bg-destructive/10 border border-destructive/20 text-destructive flex items-start gap-3 transition-all">
              <AlertCircle className="w-5 h-5 flex-shrink-0 mt-0.5" />
              <div className="text-xs sm:text-sm">
                <p className="font-semibold">Email này đã được đăng ký</p>
                <p className="mt-0.5 opacity-90">Email này đã được đăng ký. Vui lòng <Link href="/login" className="underline font-medium hover:opacity-80">đăng nhập</Link> hoặc sử dụng email khác.</p>
              </div>
            </div>
          )}

          {isNetworkError && (
            <div className="p-4 rounded-xl bg-destructive/10 border border-destructive/20 text-destructive flex items-start gap-3 transition-all">
              <AlertCircle className="w-5 h-5 flex-shrink-0 mt-0.5" />
              <div className="text-xs sm:text-sm">
                <p className="font-semibold">Không thể kết nối máy chủ</p>
                <p className="mt-0.5 opacity-90">Không thể kết nối đến máy chủ. Vui lòng kiểm tra kết nối mạng và thử lại sau.</p>
              </div>
            </div>
          )}

          {isValidationError && (
            <div className="p-4 rounded-xl bg-destructive/10 border border-destructive/20 text-destructive flex items-start gap-3 transition-all">
              <AlertCircle className="w-5 h-5 flex-shrink-0 mt-0.5" />
              <div className="text-xs sm:text-sm">
                <p className="font-semibold">Dữ liệu không hợp lệ</p>
                <p className="mt-0.5 opacity-90">Thông tin đăng ký không hợp lệ. Vui lòng kiểm tra lại các trường thông tin.</p>
              </div>
            </div>
          )}

          {isServerError && (
            <div className="p-4 rounded-xl bg-destructive/10 border border-destructive/20 text-destructive flex items-start gap-3 transition-all">
              <AlertCircle className="w-5 h-5 flex-shrink-0 mt-0.5" />
              <div className="text-xs sm:text-sm">
                <p className="font-semibold">Lỗi hệ thống</p>
                <p className="mt-0.5 opacity-90">Máy chủ đang gặp sự cố trong quá trình xử lý. Vui lòng thử lại sau ít phút.</p>
              </div>
            </div>
          )}

          {isGenericError && (
             <div className="p-4 rounded-xl bg-destructive/10 border border-destructive/20 text-destructive flex items-start gap-3 transition-all">
             <AlertCircle className="w-5 h-5 flex-shrink-0 mt-0.5" />
             <div className="text-xs sm:text-sm">
               <p className="font-semibold">Đăng ký thất bại</p>
               <p className="mt-0.5 opacity-90">Không thể tạo tài khoản vào lúc này. Vui lòng thử lại sau.</p>
             </div>
           </div>
          )}

          {registerMutation.isSuccess && (
            <div className="p-5 rounded-xl bg-green-50 border border-green-200 text-green-900 flex items-start gap-3.5 transition-all">
              <div className="w-8 h-8 rounded-full bg-green-100 flex items-center justify-center text-green-700 flex-shrink-0">
                <CheckCircle className="w-5 h-5" />
              </div>
              <div className="text-xs sm:text-sm">
                <p className="font-bold text-green-950 text-base">Đăng ký thành công</p>
                <p className="text-green-800 mt-1 leading-relaxed">
                  Liên kết xác minh đã được gửi đến hộp thư trường của bạn. Vui lòng xác minh địa chỉ để kích hoạt tài khoản.
                </p>
                <div className="mt-3 flex gap-2">
                  <Link href="/login" className="inline-flex items-center gap-1.5 px-3.5 py-1.5 bg-green-700 hover:bg-green-800 text-white rounded-lg text-xs font-semibold shadow-sm transition">
                    Đến trang Đăng nhập
                  </Link>
                </div>
              </div>
            </div>
          )}
        </div>

        {/* Registration Form */}
        {!registerMutation.isSuccess && (
          <form onSubmit={handleSubmit(onSubmit)} className="space-y-5" noValidate>
            
            {/* Full Name Field */}
            <div>
              <label htmlFor="fullName" className="block text-xs font-semibold text-foreground uppercase tracking-wider mb-1.5">
                Họ và Tên <span className="text-destructive">*</span>
              </label>
              <div className="relative">
                <div className="absolute inset-y-0 left-0 pl-3.5 flex items-center pointer-events-none text-muted-foreground">
                  <User className="w-4 h-4" />
                </div>
                <input 
                  type="text" 
                  id="fullName" 
                  placeholder="Nhập họ và tên đầy đủ" 
                  className={`w-full pl-10 pr-3.5 py-2.5 rounded-xl border bg-background text-foreground text-sm focus:outline-none focus:ring-2 focus:ring-primary focus:border-transparent transition placeholder:text-muted-foreground ${errors.fullName ? 'border-destructive bg-destructive/5' : 'border-border'}`}
                  {...register('fullName')}
                />
              </div>
              {errors.fullName && (
                <p className="mt-1.5 text-xs text-destructive font-medium flex items-center gap-1">
                  <AlertCircle className="w-3.5 h-3.5 flex-shrink-0" />
                  {errors.fullName.message}
                </p>
              )}
            </div>

            {/* Email Field */}
            <div>
              <div className="flex items-center justify-between mb-1.5">
                <label htmlFor="email" className="block text-xs font-semibold text-foreground uppercase tracking-wider">
                  Email trường <span className="text-destructive">*</span>
                </label>
                <span className="text-[11px] font-mono text-muted-foreground">Hỗ trợ SSO</span>
              </div>
              <div className="relative">
                <div className="absolute inset-y-0 left-0 pl-3.5 flex items-center pointer-events-none text-muted-foreground">
                  <Mail className="w-4 h-4" />
                </div>
                <input 
                  type="email" 
                  id="email" 
                  placeholder="Nhập email trường (Ví dụ: s.nguyen@university.edu)" 
                  className={`w-full pl-10 pr-3.5 py-2.5 rounded-xl border bg-background text-foreground text-sm focus:outline-none focus:ring-2 focus:ring-primary focus:border-transparent transition placeholder:text-muted-foreground ${errors.email ? 'border-destructive bg-destructive/5' : 'border-border'}`}
                  {...register('email')}
                />
              </div>
              {errors.email && (
                <p className="mt-1.5 text-xs text-destructive font-medium flex items-center gap-1">
                  <AlertCircle className="w-3.5 h-3.5 flex-shrink-0" />
                  {errors.email.message}
                </p>
              )}
            </div>

            {/* Account Role Selection */}
            <div>
              <div className="flex items-center justify-between mb-2">
                <label className="block text-xs font-semibold text-foreground uppercase tracking-wider">
                  Vai trò tài khoản <span className="text-destructive">*</span>
                </label>
                <span className="text-[11px] font-mono text-muted-foreground">Phân quyền theo bộ môn</span>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                {/* Student Option */}
                <div 
                  onClick={() => setValue('accountRole', 'student')} 
                  className={`relative flex flex-col p-3.5 rounded-xl border cursor-pointer transition-all hover:border-primary ${accountRole === 'student' ? 'border-primary bg-primary/5 ring-1 ring-primary' : 'border-border bg-card'}`}
                >
                  <div className="flex items-center justify-between mb-1.5">
                    <div className="flex items-center gap-2">
                      <div className="w-6 h-6 rounded-lg bg-blue-100 text-blue-700 flex items-center justify-center">
                        <User className="w-3.5 h-3.5" />
                      </div>
                      <span className="text-sm font-bold text-foreground">Sinh viên</span>
                    </div>
                    <input 
                      type="radio" 
                      value="student" 
                      className="w-4 h-4 text-primary focus:ring-primary border-border" 
                      {...register('accountRole')}
                    />
                  </div>
                  <p className="text-xs text-muted-foreground leading-snug">
                    Làm bài thi tính giờ, xem kết quả test case tự động và xem phân tích tương đồng AST.
                  </p>
                  <div className="mt-2 text-[10px] font-medium text-emerald-700 bg-emerald-50 px-2 py-0.5 rounded inline-block w-fit">
                    Kích hoạt tự động ngay lập tức
                  </div>
                </div>

                {/* Teacher Option */}
                <div 
                  onClick={() => setValue('accountRole', 'teacher')} 
                  className={`relative flex flex-col p-3.5 rounded-xl border cursor-pointer transition-all hover:border-primary ${accountRole === 'teacher' ? 'border-primary bg-primary/5 ring-1 ring-primary' : 'border-border bg-card'}`}
                >
                  <div className="flex items-center justify-between mb-1.5">
                    <div className="flex items-center gap-2">
                      <div className="w-6 h-6 rounded-lg bg-slate-100 text-slate-700 flex items-center justify-center">
                        <Shield className="w-3.5 h-3.5" />
                      </div>
                      <span className="text-sm font-bold text-foreground">Giảng viên</span>
                    </div>
                    <input 
                      type="radio" 
                      value="teacher" 
                      className="w-4 h-4 text-primary focus:ring-primary border-border" 
                      {...register('accountRole')}
                    />
                  </div>
                  <p className="text-xs text-muted-foreground leading-snug">
                    Tạo đề bài thi, chấm bài hàng loạt và kiểm tra báo cáo độ tương đồng mã nguồn.
                  </p>
                  <div className="mt-2 text-[10px] font-medium text-amber-800 bg-amber-50 px-2 py-0.5 rounded inline-block w-fit">
                    Yêu cầu xác minh danh sách giảng viên
                  </div>
                </div>
              </div>

              {/* Disclaimer */}
              <div className="mt-2.5 p-2.5 rounded-lg bg-secondary/20 border border-border/70 text-[11px] text-muted-foreground flex items-start gap-2">
                <AlertCircle className="w-4 h-4 flex-shrink-0 mt-0.5 text-muted-foreground" />
                <span className="leading-tight">
                  <strong>Lưu ý về quyền đặc quyền:</strong> Chọn Giảng viên sẽ yêu cầu quyền chấm bài và quản lý kỳ thi. Toàn bộ quyền sẽ được mở sau khi đối chiếu với danh sách cán bộ giảng dạy.
                </span>
              </div>
            </div>

            {/* Password Grid */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div>
                <label htmlFor="password" className="block text-xs font-semibold text-foreground uppercase tracking-wider mb-1.5">
                  Mật khẩu <span className="text-destructive">*</span>
                </label>
                <div className="relative">
                  <div className="absolute inset-y-0 left-0 pl-3.5 flex items-center pointer-events-none text-muted-foreground">
                    <Lock className="w-4 h-4" />
                  </div>
                  <input 
                    type={showPassword ? 'text' : 'password'}
                    id="password" 
                    placeholder="Tạo mật khẩu" 
                    className={`w-full pl-10 pr-10 py-2.5 rounded-xl border bg-background text-foreground text-sm focus:outline-none focus:ring-2 focus:ring-primary focus:border-transparent transition placeholder:text-muted-foreground ${errors.password ? 'border-destructive bg-destructive/5' : 'border-border'}`}
                    {...register('password')}
                  />
                  <button 
                    type="button" 
                    onClick={() => setShowPassword(!showPassword)}
                    className="absolute inset-y-0 right-0 pr-3 flex items-center text-muted-foreground hover:text-foreground focus:outline-none"
                  >
                    {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                  </button>
                </div>
                {errors.password && (
                  <p className="mt-1 text-xs text-destructive font-medium">{errors.password.message}</p>
                )}
              </div>

              <div>
                <label htmlFor="confirmPassword" className="block text-xs font-semibold text-foreground uppercase tracking-wider mb-1.5">
                  Xác nhận mật khẩu <span className="text-destructive">*</span>
                </label>
                <div className="relative">
                  <div className="absolute inset-y-0 left-0 pl-3.5 flex items-center pointer-events-none text-muted-foreground">
                    <Lock className="w-4 h-4" />
                  </div>
                  <input 
                    type={showConfirmPassword ? 'text' : 'password'}
                    id="confirmPassword" 
                    placeholder="Nhập lại mật khẩu" 
                    className={`w-full pl-10 pr-10 py-2.5 rounded-xl border bg-background text-foreground text-sm focus:outline-none focus:ring-2 focus:ring-primary focus:border-transparent transition placeholder:text-muted-foreground ${errors.confirmPassword ? 'border-destructive bg-destructive/5' : 'border-border'}`}
                    {...register('confirmPassword')}
                  />
                  <button 
                    type="button" 
                    onClick={() => setShowConfirmPassword(!showConfirmPassword)}
                    className="absolute inset-y-0 right-0 pr-3 flex items-center text-muted-foreground hover:text-foreground focus:outline-none"
                  >
                    {showConfirmPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                  </button>
                </div>
                {errors.confirmPassword && (
                  <p className="mt-1 text-xs text-destructive font-medium">{errors.confirmPassword.message}</p>
                )}
              </div>
            </div>

            {/* Password Requirements Checklist */}
            <div className="p-3 bg-secondary/10 border border-border/60 rounded-xl">
              <div className="text-[11px] font-semibold text-muted-foreground uppercase tracking-wider mb-2 font-mono flex items-center justify-between">
                <span>Yêu cầu bảo mật</span>
                <span className="text-primary lowercase font-normal">NIST SP 800-63B</span>
              </div>
              <div className="grid grid-cols-2 gap-2 text-xs">
                <div className={`flex items-center gap-1.5 ${getPasswordReqClasses(hasLength)}`}>
                  {getPasswordReqIcon(hasLength)}
                  <span>Tối thiểu 10 ký tự</span>
                </div>
                <div className={`flex items-center gap-1.5 ${getPasswordReqClasses(hasUpperLower)}`}>
                  {getPasswordReqIcon(hasUpperLower)}
                  <span>Chữ hoa &amp; chữ thường</span>
                </div>
                <div className={`flex items-center gap-1.5 ${getPasswordReqClasses(hasNumber)}`}>
                  {getPasswordReqIcon(hasNumber)}
                  <span>Ít nhất một chữ số</span>
                </div>
                <div className={`flex items-center gap-1.5 ${getPasswordReqClasses(hasSymbol)}`}>
                  {getPasswordReqIcon(hasSymbol)}
                  <span>Ký tự đặc biệt (!@#$)</span>
                </div>
              </div>
            </div>

            {/* Terms Checkbox */}
            <div className="flex items-start gap-2.5 pt-1">
              <input 
                type="checkbox" 
                id="terms" 
                className="mt-1 w-4 h-4 rounded text-primary border-border focus:ring-primary"
                {...register('terms')}
              />
              <div className="flex flex-col">
                <label htmlFor="terms" className="text-xs text-muted-foreground leading-relaxed">
                  Tôi đồng ý tuân thủ <span className="text-primary font-medium hover:underline cursor-pointer">Quy tắc Liêm chính Học thuật</span> của nhà trường và hướng dẫn kiểm tra tương đồng mã tự động AST.
                </label>
                {errors.terms && <span className="text-destructive text-xs mt-1">{errors.terms.message}</span>}
              </div>
            </div>

            {/* Submit Button */}
            <button 
              type="submit" 
              disabled={registerMutation.isPending}
              className="w-full py-3 px-4 rounded-xl bg-primary hover:bg-primary/90 text-primary-foreground font-semibold text-sm shadow-sm transition flex items-center justify-center gap-2 disabled:opacity-70 disabled:cursor-not-allowed"
            >
              {registerMutation.isPending ? (
                <>
                  <span>Đang đăng ký tài khoản...</span>
                  <Loader2 className="w-4 h-4 animate-spin" />
                </>
              ) : (
                <span>Tạo tài khoản</span>
              )}
            </button>

            {/* Secondary Navigation */}
            <div className="text-center pt-2">
              <p className="text-xs text-muted-foreground">
                Đã có tài khoản? 
                <Link href="/login" className="font-semibold text-primary hover:text-primary/80 hover:underline transition ml-1">
                  Đăng nhập
                </Link>
              </p>
            </div>
          </form>
        )}

      </div>

      {/* Footer */}
      <div className="mt-8 text-center text-xs text-muted-foreground space-y-2">
        <div className="flex items-center justify-center gap-2">
          <Shield className="w-4 h-4 text-emerald-600" />
          <span className="font-medium">Được bảo vệ bởi SSO Trường học &amp; Giám sát Liêm chính AST</span>
        </div>
        <p className="font-mono text-[11px] opacity-70">
          Năm học 2025–2026 • Ban Khảo thí &amp; Đánh giá Năng lực Tin học • Giao thức CA-AUTH-v4
        </p>
      </div>
    </div>
  );
}
