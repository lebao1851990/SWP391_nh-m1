'use client';

import React from 'react';
import { AppLink as Link } from '@/components/ui/app-link';
import { useTeacherAppeal } from '../hooks';
import { GradeAuditHistory } from '@/features/scoring/components';
import { 
  ArrowLeft, 
  CheckCircle2, 
  XCircle, 
  AlertCircle, 
  Scale, 
  FileText, 
  Calendar, 
  Lock, 
  ExternalLink,
  Award,
  ShieldAlert,
  UserCheck
} from 'lucide-react';

export function TeacherAppealDetailView({ appealId }: { appealId: string }) {
  const { data: appeal, isLoading, isError } = useTeacherAppeal(appealId);

  if (isLoading) {
    return (
      <div className="flex flex-col h-72 items-center justify-center gap-3">
        <div className="w-8 h-8 rounded-full border-2 border-primary border-t-transparent animate-spin" />
        <p className="text-on-surface-variant font-label-md text-xs">Đang tải hồ sơ khiếu nại...</p>
      </div>
    );
  }

  if (isError || !appeal) {
    return (
      <div className="max-w-xl mx-auto my-12 p-8 rounded-2xl bg-surface-container-lowest border border-outline-variant/40 shadow-lg text-center flex flex-col items-center gap-3">
        <div className="p-3 rounded-full bg-rose-50 text-rose-600">
          <AlertCircle className="w-8 h-8" />
        </div>
        <h2 className="font-headline-md text-headline-md text-on-surface">Không tìm thấy đơn khiếu nại</h2>
        <p className="font-body-md text-on-surface-variant text-xs">
          Không tìm thấy đơn khiếu nại này hoặc bạn không có quyền xem khiếu nại của lớp học này.
        </p>
        <Link href="/teacher/appeals" className="mt-2 px-4 py-2 bg-primary text-on-primary rounded-lg text-xs font-medium">
          Quay lại Danh sách khiếu nại
        </Link>
      </div>
    );
  }

  const isPending = appeal.status === 'PENDING';
  const isApproved = appeal.status === 'APPROVED';
  const isRejected = appeal.status === 'REJECTED';

  return (
    <div className="max-w-7xl mx-auto px-space-lg py-space-lg flex flex-col w-full space-y-space-lg">
      {/* Top Bar */}
      <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-space-md pb-space-md border-b border-outline-variant/30">
        <div className="space-y-1">
          <nav className="flex items-center gap-2 text-on-surface-variant text-xs mb-1">
            <Link className="hover:text-primary transition-colors flex items-center gap-1 font-medium" href="/teacher/appeals">
              <ArrowLeft className="w-3.5 h-3.5" />
              <span>Trung tâm Khiếu nại</span>
            </Link>
            <span className="text-outline">/</span>
            <span className="text-primary font-semibold font-mono">#{appeal.id.substring(0, 8)}</span>
          </nav>
          <div className="flex items-center gap-space-sm flex-wrap">
            <h1 className="font-headline-lg text-headline-lg text-on-surface tracking-tight">
              Chi tiết Khiếu nại — #{appeal.id.substring(0, 8)}
            </h1>
            {isPending && (
              <span className="text-xs px-2.5 py-0.5 rounded-full bg-amber-50 text-amber-700 border border-amber-200 font-semibold flex items-center gap-1">
                <span className="w-1.5 h-1.5 rounded-full bg-amber-500 animate-pulse"></span>
                <span>Chờ Quản trị viên xử lý</span>
              </span>
            )}
            {isApproved && (
              <span className="text-xs px-2.5 py-0.5 rounded-full bg-emerald-50 text-emerald-700 border border-emerald-200 font-semibold flex items-center gap-1">
                <CheckCircle2 className="w-3.5 h-3.5 text-emerald-600" />
                <span>Đã chấp thuận</span>
              </span>
            )}
            {isRejected && (
              <span className="text-xs px-2.5 py-0.5 rounded-full bg-rose-50 text-rose-700 border border-rose-200 font-semibold flex items-center gap-1">
                <XCircle className="w-3.5 h-3.5 text-rose-600" />
                <span>Đã từ chối</span>
              </span>
            )}
          </div>
          <p className="text-xs text-on-surface-variant">
            Nộp bởi <strong className="text-on-surface">{appeal.studentName || 'Sinh viên'}</strong> ({appeal.studentEmail || 'N/A'}) vào ngày {new Date(appeal.createdAt).toLocaleDateString()}
          </p>
        </div>

        <div className="flex items-center gap-2 self-start lg:self-auto">
          <Link
            href="/teacher/appeals"
            className="px-4 py-2 rounded-lg bg-surface-container hover:bg-surface-container-high text-on-surface text-xs font-medium border border-outline-variant/40 transition-colors"
          >
            Quay lại Danh sách
          </Link>
        </div>
      </div>

      {/* Role Authority Notice Banner */}
      <div className="p-3.5 rounded-xl bg-blue-50 text-blue-800 text-xs flex items-center gap-2.5 border border-blue-200">
        <ShieldAlert className="w-4 h-4 text-blue-600 shrink-0" />
        <span>
          <strong>Lưu ý nghiệp vụ:</strong> Quyền xem xét, phê duyệt hoặc từ chối khiếu nại thuộc thẩm quyền của <strong>Quản trị viên (Admin)</strong>. Giảng viên chỉ có quyền xem thông tin phản hồi nhằm phục vụ công tác giảng dạy.
        </span>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-12 gap-space-lg items-start">
        {/* Left Column: Context Card */}
        <aside className="lg:col-span-4 space-y-space-md">
          <div className="bg-surface-container-lowest rounded-2xl p-space-md border border-outline-variant/40 shadow-xs space-y-3 text-xs">
            <div className="font-label-xs uppercase tracking-wider text-outline font-semibold pb-2 border-b border-outline-variant/20">
              Thông tin Bài kiểm tra
            </div>
            <div>
              <span className="text-outline uppercase text-[10px] block">Bài thi</span>
              <span className="font-semibold text-on-surface text-sm">{appeal.examName}</span>
            </div>
            <div>
              <span className="text-outline uppercase text-[10px] block">Mã bài nộp tham chiếu</span>
              <span className="font-mono text-on-surface text-xs">#{appeal.submissionId.substring(0, 8)}</span>
            </div>
            <div>
              <span className="text-outline uppercase text-[10px] block">Lý do khiếu nại</span>
              <span className="font-semibold text-on-surface capitalize">{appeal.basis.replace(/_/g, ' ')}</span>
            </div>
            <div>
              <span className="text-outline uppercase text-[10px] block">Thông tin Sinh viên</span>
              <span className="font-semibold text-on-surface block">{appeal.studentName || 'Sinh viên'}</span>
              <span className="text-on-surface-variant text-[11px]">{appeal.studentEmail}</span>
            </div>

            <div className="pt-2 border-t border-outline-variant/20">
              <span className="text-outline uppercase text-[10px] block mb-1">Điểm số bài nộp</span>
              <div className="flex items-center gap-2">
                <div className="flex flex-col">
                  <span className="text-[10px] text-outline">Điểm ban đầu</span>
                  <span className="font-mono font-bold text-on-surface text-sm">
                    {appeal.originalScore != null ? appeal.originalScore : '—'}
                  </span>
                </div>
                {appeal.updatedScore != null && (
                  <>
                    <span className="text-outline">&rarr;</span>
                    <div className="flex flex-col">
                      <span className="text-[10px] text-emerald-700 font-semibold">Điểm sau phúc khảo</span>
                      <span className="font-mono font-bold text-emerald-700 text-sm">
                        {appeal.updatedScore}
                      </span>
                    </div>
                  </>
                )}
              </div>
            </div>

            <div className="pt-2 border-t border-outline-variant/20">
              <Link
                href={`/teacher/submissions/${appeal.submissionId}`}
                className="w-full inline-flex items-center justify-center gap-1.5 px-3 py-1.5 bg-surface-container hover:bg-surface-container-high text-primary rounded-lg font-medium text-xs transition-colors"
              >
                <span>Xem chi tiết bài nộp &amp; Code</span>
                <ExternalLink className="w-3 h-3" />
              </Link>
            </div>
          </div>
        </aside>

        {/* Right Column: Explanation, Audit History and Decision Status */}
        <main className="lg:col-span-8 space-y-space-lg">
          {/* Student Explanation */}
          <div className="bg-surface-container-lowest rounded-2xl border border-outline-variant/40 shadow-xs p-space-lg space-y-2">
            <div className="flex items-center gap-2 pb-2 border-b border-outline-variant/20">
              <FileText className="w-4 h-4 text-primary" />
              <h2 className="font-headline-sm text-headline-sm text-on-surface font-semibold">Lời giải trình của Sinh viên</h2>
            </div>
            <div className="bg-surface-container-low p-space-md rounded-xl whitespace-pre-wrap text-xs text-on-surface leading-relaxed font-mono border border-outline-variant/20">
              {appeal.explanation}
            </div>
          </div>

          {/* Audit History & Criteria */}
          <div className="bg-surface-container-lowest rounded-2xl border border-outline-variant/40 shadow-xs p-space-lg">
            <div className="flex items-center gap-2 pb-3 mb-3 border-b border-outline-variant/20">
              <Award className="w-4 h-4 text-primary" />
              <h2 className="font-headline-sm text-headline-sm text-on-surface font-semibold">Lịch sử Chấm điểm &amp; Tiêu chí Rubric</h2>
            </div>
            <GradeAuditHistory submissionId={appeal.submissionId} />
          </div>

          {/* Decision Status Box (Read-Only) */}
          <div className="bg-surface-container-lowest rounded-2xl border border-outline-variant/40 shadow-xs p-space-lg space-y-space-md">
            <div className="flex items-center gap-2 pb-2 border-b border-outline-variant/20">
              <Scale className="w-4 h-4 text-primary" />
              <h2 className="font-headline-sm text-headline-sm text-on-surface font-semibold">Trạng thái Phán quyết Khiếu nại</h2>
            </div>

            {!isPending ? (
              <div className="space-y-space-md text-xs">
                <div className={`p-4 rounded-xl flex items-center gap-2.5 ${
                  isApproved 
                    ? 'bg-emerald-50 text-emerald-800 border border-emerald-200' 
                    : 'bg-rose-50 text-rose-800 border border-rose-200'
                }`}>
                  {isApproved ? <CheckCircle2 className="w-5 h-5 text-emerald-600 shrink-0" /> : <XCircle className="w-5 h-5 text-rose-600 shrink-0" />}
                  <div>
                    <span className="font-semibold block">
                      Quyết định của Admin: {appeal.status === 'APPROVED' ? 'Chấp thuận khiếu nại' : 'Từ chối khiếu nại'} (Hoàn tất vào lúc {new Date(appeal.resolvedAt || '').toLocaleString()})
                    </span>
                    {appeal.reviewerName && (
                      <span className="text-[11px] block mt-0.5 text-on-surface-variant flex items-center gap-1">
                        <UserCheck className="w-3.5 h-3.5" />
                        <span>Người phán quyết: <strong>{appeal.reviewerName}</strong></span>
                      </span>
                    )}
                    {appeal.updatedScore != null && (
                      <span className="text-emerald-700 font-medium block mt-0.5">
                        Điểm số sau điều chỉnh: <strong>{appeal.updatedScore}</strong> / 10
                      </span>
                    )}
                  </div>
                </div>

                <div className="space-y-1">
                  <span className="font-label-xs uppercase tracking-wider text-outline">Ghi chú &amp; Giải trình của Admin:</span>
                  <div className="bg-surface-container-low p-space-md rounded-xl text-xs text-on-surface border border-outline-variant/20 whitespace-pre-wrap">
                    {appeal.reviewerNotes || 'Không có ghi chú thêm từ người duyệt.'}
                  </div>
                </div>

                <div className="flex items-center gap-1.5 text-outline text-[11px] italic">
                  <Lock className="w-3.5 h-3.5" />
                  <span>Hồ sơ này đã được giải quyết bởi Quản trị viên và lưu vào nhật ký kiểm toán.</span>
                </div>
              </div>
            ) : (
              <div className="p-4 rounded-xl bg-amber-50 text-amber-800 border border-amber-200 text-xs space-y-1.5">
                <div className="flex items-center gap-2 font-semibold">
                  <span className="w-2 h-2 rounded-full bg-amber-500 animate-pulse" />
                  <span>Đơn khiếu nại đang chờ Quản trị viên (Admin) xem xét và ra quyết định.</span>
                </div>
                <p className="text-amber-700 text-[11px] leading-relaxed">
                  Giảng viên có thể xem lý do khiếu nại và bài nộp của sinh viên ở trên. Khi Quản trị viên đưa ra phán quyết, kết quả và điểm cập nhật (nếu có) sẽ tự động hiển thị tại đây.
                </p>
              </div>
            )}
          </div>
        </main>
      </div>
    </div>
  );
}
