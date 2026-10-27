'use client';

import React, { useState } from 'react';
import { AppLink as Link } from '@/components/ui/app-link';
import { useSearchParams } from 'next/navigation';
import { useAppRouter as useRouter } from '@/hooks/use-app-router';
import { AxiosError } from 'axios';
import { useCreateAppeal, useStudentAppeals } from '../hooks';
import { useStudentResult } from '@/features/scoring/hooks';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { createAppealSchema, AppealFormData } from '../schemas';
import { 
  ArrowLeft, 
  Send, 
  CheckCircle2, 
  AlertCircle, 
  X, 
  Scale, 
  FileText, 
  Check, 
  Loader2,
  HelpCircle,
  ShieldCheck,
  ShieldAlert,
  Award
} from 'lucide-react';
import { toast } from 'sonner';

export function StudentAppealCreateView() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const submissionId = searchParams?.get('submissionId') || '';
  const examTitleParam = searchParams?.get('examTitle') || '';
  const scoreParam = searchParams?.get('score') || '';

  const { data: appeals, isLoading: isLoadingAppeals } = useStudentAppeals();
  const { data: studentResult } = useStudentResult(submissionId);
  const createAppeal = useCreateAppeal();

  const displayExamName = studentResult?.examName || examTitleParam || `Bài thi liên quan`;
  const displayScore = studentResult?.effectiveScore ?? (studentResult?.overrideTotalScore != null ? studentResult.overrideTotalScore : (studentResult?.totalScore != null ? studentResult.totalScore : (scoreParam ? Number(scoreParam) : '—')));

  const existingPendingAppeal = appeals?.find(a => a.submissionId === submissionId && a.status === 'PENDING');

  const [showConfirm, setShowConfirm] = useState(false);
  const [formData, setFormData] = useState<AppealFormData | null>(null);

  const { register, handleSubmit, formState: { errors }, watch } = useForm<AppealFormData>({
    resolver: zodResolver(createAppealSchema),
    defaultValues: {
      submissionId: submissionId,
      basis: 'ast_clarification',
      explanation: '',
    }
  });

  const explanationLength = watch('explanation')?.length || 0;

  const onValid = (data: AppealFormData) => {
    setFormData(data);
    setShowConfirm(true);
  };

  const confirmSubmit = () => {
    if (!formData) return;
    createAppeal.mutate(formData, {
      onSuccess: () => {
        toast.success('Đã gửi đơn khiếu nại thành công! Đã chuyển tiếp đến Hội đồng Khảo thí.');
        router.push('/student/appeals');
      },
      onError: (err: unknown) => {
        setShowConfirm(false);
        const msg = (err as { response?: { data?: { message?: string } }; message?: string })?.response?.data?.message || (err as { message?: string })?.message || 'Không thể gửi đơn khiếu nại';
        toast.error(msg);
      }
    });
  };

  if (!submissionId) {
    return (
      <div className="p-space-lg max-w-xl mx-auto my-16 text-center bg-surface-container-lowest rounded-2xl border border-outline-variant/40 shadow-lg flex flex-col items-center gap-3">
        <div className="p-3 rounded-full bg-amber-50 text-amber-600">
          <AlertCircle className="w-8 h-8" />
        </div>
        <h2 className="font-headline-md text-headline-md text-on-surface">Thiếu thông tin bài nộp</h2>
        <p className="text-xs text-on-surface-variant">Không thể tạo đơn khiếu nại nếu không có thông tin bài nộp liên quan.</p>
        <Link href="/student/dashboard" className="mt-2 px-4 py-2 bg-primary text-on-primary rounded-lg text-xs font-medium">
          Quay lại Bảng điều khiển
        </Link>
      </div>
    );
  }

  return (
    <div className="max-w-7xl mx-auto px-space-lg py-space-lg">
      <div className="flex flex-col w-full space-y-space-lg">
        {/* Top Header */}
        <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-space-md pb-space-md border-b border-outline-variant/30">
          <div className="space-y-1">
            <nav className="flex items-center gap-2 text-on-surface-variant text-xs mb-1">
              <Link className="hover:text-primary transition-colors flex items-center gap-1 font-medium" href="/student/appeals">
                <ArrowLeft className="w-3.5 h-3.5" />
                <span>Đơn khiếu nại của tôi</span>
              </Link>
              <span className="text-outline">/</span>
              <span className="text-primary font-semibold">Tạo đơn khiếu nại mới</span>
            </nav>
            <div className="flex items-center gap-space-sm flex-wrap">
              <h1 className="font-headline-lg text-headline-lg text-on-surface tracking-tight">
                Gửi đơn khiếu nại Điểm số &amp; Liêm chính
              </h1>
              <span className="text-[10px] px-2.5 py-0.5 rounded-full bg-purple-50 text-purple-700 border border-purple-200 font-bold uppercase">
                Quy trình chính thức
              </span>
            </div>
            <p className="text-xs sm:text-sm text-on-surface-variant">
              Cung cấp giải trình học thuật chính thức, lý do kiến trúc mã hoặc trích dẫn tài liệu bài giảng cho bài nộp của bạn.
            </p>
          </div>
          <div className="flex items-center gap-2 self-start lg:self-auto">
            <span className="text-xs text-outline font-medium">Bài nộp liên quan:</span>
            <span className="font-mono text-xs px-2.5 py-1 rounded-lg bg-surface-container text-on-surface font-semibold border border-outline-variant/30">
              #{submissionId.substring(0, 8)}
            </span>
          </div>
        </div>

        {/* Existing Pending Warning */}
        {existingPendingAppeal && (
          <div className="bg-amber-50 p-space-md rounded-2xl border border-amber-200 text-amber-800 flex items-start gap-3">
            <AlertCircle className="w-5 h-5 text-amber-600 shrink-0 mt-0.5" />
            <div className="text-xs space-y-1">
              <p className="font-semibold text-amber-900">Đã có đơn khiếu nại đang chờ xử lý</p>
              <p>Bạn đã có một đơn khiếu nại đang chờ duyệt cho bài nộp này (#{existingPendingAppeal.id.substring(0, 8)}). Theo quy định, mỗi bài nộp chỉ được xử lý một đơn khiếu nại tại một thời điểm.</p>
              <Link href="/student/appeals" className="inline-block mt-1 font-semibold underline text-amber-900">
                Xem đơn khiếu nại hiện tại &rarr;
              </Link>
            </div>
          </div>
        )}

        {/* Exam & Score Summary Card */}
        <div className="bg-surface-container-lowest p-4 rounded-2xl border border-outline-variant/40 shadow-xs flex flex-col sm:flex-row sm:items-center justify-between gap-4">
          <div className="space-y-1">
            <div className="flex items-center gap-1.5 text-xs text-outline font-semibold uppercase tracking-wider">
              <Award className="w-4 h-4 text-primary" />
              <span>Bài thi cần khiếu nại</span>
            </div>
            <h3 className="font-semibold text-on-surface text-base">{displayExamName}</h3>
            <span className="text-outline text-xs font-mono">Mã bài nộp: #{submissionId}</span>
          </div>
          <div className="flex items-center gap-3 bg-surface-container-low px-4 py-2.5 rounded-xl border border-outline-variant/30 self-start sm:self-auto">
            <span className="text-on-surface-variant text-xs font-medium">Điểm hiện tại:</span>
            <span className="text-xl font-bold text-primary font-mono">
              {displayScore} <span className="text-xs text-outline font-normal">/ 10</span>
            </span>
          </div>
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-12 gap-space-lg items-start">
          {/* Sidebar Guidelines */}
          <aside className="lg:col-span-4 space-y-space-md">
            <div className="bg-surface-container-lowest rounded-2xl p-space-md shadow-xs border border-outline-variant/40 space-y-3 text-xs">
              <div className="flex items-center gap-1.5 pb-2 border-b border-outline-variant/20">
                <Scale className="w-4 h-4 text-primary" />
                <h3 className="font-semibold text-on-surface">Thông báo Quy định của Nhà trường</h3>
              </div>
              <p className="text-on-surface-variant leading-relaxed">
                Đơn khiếu nại được xem xét bởi Hội đồng Khảo thí Bộ môn. Việc làm giả trích dẫn hoặc gian lận nguồn gốc mã nguồn sẽ bị xử lý theo Quy tắc Liêm chính Học thuật.
              </p>
              <div className="p-2.5 rounded-lg bg-surface-container-low border border-outline-variant/20 space-y-1.5">
                <div className="flex items-center gap-1.5 text-primary font-semibold">
                  <ShieldCheck className="w-3.5 h-3.5" />
                  <span>Cam kết thời gian xử lý (SLA)</span>
                </div>
                <p className="text-[11px] text-on-surface-variant">
                  Quyết định chính thức sẽ được đưa ra trong vòng 48 giờ làm việc kể từ khi nhận đơn.
                </p>
              </div>

              <div className="p-2.5 rounded-lg bg-surface-container-low border border-outline-variant/20 space-y-1.5">
                <div className="flex items-center gap-1.5 text-on-surface font-semibold">
                  <FileText className="w-3.5 h-3.5 text-outline" />
                  <span>Minh chứng đính kèm</span>
                </div>
                <p className="text-[11px] text-on-surface-variant">
                  Hệ thống tiếp nhận minh chứng qua nội dung giải trình chi tiết (mô tả thuật toán, số dòng code, tham chiếu slide môn học).
                </p>
              </div>
            </div>
          </aside>

          {/* Form */}
          <main className="lg:col-span-8">
            <form 
              className="bg-surface-container-lowest rounded-2xl border border-outline-variant/40 shadow-xs p-space-lg space-y-space-lg" 
              onSubmit={handleSubmit(onValid)}
            >
              <div className="space-y-space-sm">
                <label className="text-xs font-semibold text-on-surface block uppercase tracking-wider">
                  1. Chọn phân loại khiếu nại *
                </label>
                <div className="grid grid-cols-1 md:grid-cols-3 gap-2">
                  <label className="flex flex-col p-3 rounded-xl bg-surface-container-low border border-outline-variant/40 hover:border-primary cursor-pointer transition-all">
                    <input {...register('basis')} className="sr-only peer" type="radio" value="ast_clarification"/>
                    <div className="flex items-center justify-between mb-1">
                      <Scale className="w-4 h-4 text-primary" />
                      <span className="w-3.5 h-3.5 rounded-full border border-outline peer-checked:border-primary peer-checked:bg-primary transition-all"></span>
                    </div>
                    <span className="text-xs font-semibold text-on-surface">Giải trình tương đồng AST</span>
                    <span className="text-[10px] text-on-surface-variant mt-0.5">Trùng lặp cấu trúc giả định</span>
                  </label>

                  <label className="flex flex-col p-3 rounded-xl bg-surface-container-low border border-outline-variant/40 hover:border-primary cursor-pointer transition-all">
                    <input {...register('basis')} className="sr-only peer" type="radio" value="grading_dispute"/>
                    <div className="flex items-center justify-between mb-1">
                      <FileText className="w-4 h-4 text-secondary" />
                      <span className="w-3.5 h-3.5 rounded-full border border-outline peer-checked:border-primary peer-checked:bg-primary transition-all"></span>
                    </div>
                    <span className="text-xs font-semibold text-on-surface">Chấm điểm tự động</span>
                    <span className="text-[10px] text-on-surface-variant mt-0.5">Lỗi thời gian chạy test runner</span>
                  </label>

                  <label className="flex flex-col p-3 rounded-xl bg-surface-container-low border border-outline-variant/40 hover:border-primary cursor-pointer transition-all">
                    <input {...register('basis')} className="sr-only peer" type="radio" value="rubric_clarification"/>
                    <div className="flex items-center justify-between mb-1">
                      <CheckCircle2 className="w-4 h-4 text-emerald-600" />
                      <span className="w-3.5 h-3.5 rounded-full border border-outline peer-checked:border-primary peer-checked:bg-primary transition-all"></span>
                    </div>
                    <span className="text-xs font-semibold text-on-surface">Làm rõ tiêu chí chấm</span>
                    <span className="text-[10px] text-on-surface-variant mt-0.5">Tính sai điểm tiêu chí</span>
                  </label>
                </div>
                {errors.basis && <span className="text-rose-600 text-xs font-medium">{errors.basis.message}</span>}
              </div>

              <div className="space-y-2 pt-space-md border-t border-outline-variant/30">
                <div className="flex items-center justify-between">
                  <label className="text-xs font-semibold text-on-surface block uppercase tracking-wider">
                    2. Lời giải trình &amp; Bằng chứng chứng minh *
                  </label>
                  <span className={`text-[11px] font-mono ${explanationLength < 20 ? 'text-rose-600' : 'text-outline'}`}>
                    {explanationLength} / 1000 ký tự (tối thiểu 20)
                  </span>
                </div>
                <p className="text-xs text-on-surface-variant">
                  Mô tả chi tiết giải thuật logic, quyết định thiết kế và nêu rõ slide bài giảng hoặc mã khung do môn học cung cấp.
                </p>
                <textarea 
                  {...register('explanation')}
                  className="w-full bg-surface-container-low rounded-xl p-3 text-xs text-on-surface border border-outline-variant/40 focus:border-primary focus:outline-none transition-all resize-y leading-relaxed" 
                  placeholder="Ví dụ: Trong hàm calculateHash() và traverseTree(), thuật toán BFS chuẩn từ sách giáo trình được áp dụng từ slide Bài giảng 6..." 
                  rows={6}
                />
                {errors.explanation && <span className="text-rose-600 text-xs font-medium">{errors.explanation.message}</span>}
              </div>

              <div className="pt-space-md border-t border-outline-variant/30 flex items-center justify-end gap-3">
                <Link 
                  href="/student/appeals"
                  className="px-4 py-2 rounded-lg text-xs font-medium text-on-surface-variant hover:bg-surface-container transition-colors"
                >
                  Hủy
                </Link>
                <button 
                  disabled={createAppeal.isPending || !!existingPendingAppeal} 
                  className="px-6 py-2 rounded-lg bg-primary text-on-primary text-xs font-semibold hover:bg-primary/90 transition-all flex items-center gap-1.5 shadow-sm disabled:opacity-50 cursor-pointer" 
                  type="submit"
                >
                  <Send className="w-3.5 h-3.5" />
                  <span>Gửi đơn khiếu nại chính thức</span>
                </button>
              </div>
            </form>
          </main>
        </div>
      </div>

      {/* Confirmation Modal */}
      {showConfirm && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50 backdrop-blur-xs animate-in fade-in duration-150">
          <div className="bg-surface-container-lowest rounded-2xl max-w-md w-full shadow-2xl border border-outline-variant/40 p-6 flex flex-col gap-4 animate-in zoom-in-95 duration-150">
            <div className="flex items-center gap-3">
              <div className="p-2.5 rounded-full bg-blue-50 text-blue-600">
                <Scale className="w-6 h-6" />
              </div>
              <div>
                <h3 className="font-headline-sm text-headline-sm font-semibold text-on-surface">Xác nhận gửi đơn khiếu nại</h3>
                <span className="text-xs text-outline">Quy trình quản trị học thuật</span>
              </div>
            </div>

            <p className="text-xs text-on-surface-variant leading-relaxed">
              Sau khi gửi, hồ sơ của bạn sẽ được chuyển đến hội đồng giảng viên. Bạn sẽ không thể gửi thêm đơn khiếu nại cho bài nộp này cho đến khi có kết quả giải quyết.
            </p>

            <div className="flex justify-end gap-2 pt-2 border-t border-outline-variant/30">
              <button 
                type="button" 
                onClick={() => setShowConfirm(false)}
                className="px-4 py-2 rounded-lg text-xs font-medium border border-outline-variant/40 hover:bg-surface-container transition-colors cursor-pointer"
              >
                Xem lại lý do
              </button>
              <button 
                type="button" 
                onClick={confirmSubmit}
                disabled={createAppeal.isPending}
                className="px-5 py-2 rounded-lg bg-primary text-on-primary text-xs font-semibold hover:bg-primary/90 transition-colors flex items-center gap-2 cursor-pointer shadow-sm"
              >
                {createAppeal.isPending ? (
                  <>
                    <Loader2 className="w-3.5 h-3.5 animate-spin" />
                    <span>Đang gửi...</span>
                  </>
                ) : (
                  <>
                    <Check className="w-3.5 h-3.5" />
                    <span>Xác nhận &amp; Gửi đơn</span>
                  </>
                )}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
