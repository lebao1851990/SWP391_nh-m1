'use client';

import React, { useState } from 'react';
import { AppLink as Link } from '@/components/ui/app-link';
import { useAdminAppeal, useResolveAdminAppeal } from '../hooks';
import { GradeAuditHistory } from '@/features/scoring/components';
import { 
  ArrowLeft, 
  CheckCircle2, 
  XCircle, 
  AlertCircle, 
  Scale, 
  FileText, 
  Check, 
  X, 
  Loader2, 
  Calendar, 
  Lock, 
  ShieldCheck,
  ExternalLink,
  Award,
  Code2,
  FileCode2,
  UserCheck
} from 'lucide-react';
import { toast } from 'sonner';

export function AdminAppealDetailView({ appealId }: { appealId: string }) {
  const { data: appeal, isLoading, isError, refetch } = useAdminAppeal(appealId);
  const resolveMutation = useResolveAdminAppeal(appealId);
  const [decision, setDecision] = useState<'APPROVED' | 'REJECTED'>('APPROVED');
  const [reviewerNotes, setReviewerNotes] = useState('');
  const [adjustedScore, setAdjustedScore] = useState<string>('');
  const [errorMessage, setErrorMessage] = useState('');
  const [successMessage, setSuccessMessage] = useState('');
  const [showConfirmModal, setShowConfirmModal] = useState(false);

  if (isLoading) {
    return (
      <div className="flex flex-col h-72 items-center justify-center gap-3">
        <div className="w-8 h-8 rounded-full border-2 border-primary border-t-transparent animate-spin" />
        <p className="text-on-surface-variant font-label-md text-xs">Đang tải hồ sơ khiếu nại Admin...</p>
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
          Đơn khiếu nại không tồn tại hoặc đã bị xóa khỏi hệ thống.
        </p>
        <Link href="/admin/appeals" className="mt-2 px-4 py-2 bg-primary text-on-primary rounded-lg text-xs font-medium">
          Quay lại Danh sách khiếu nại
        </Link>
      </div>
    );
  }

  const isPending = appeal.status === 'PENDING';
  const isApproved = appeal.status === 'APPROVED';
  const isRejected = appeal.status === 'REJECTED';

  const validateAndPrompt = () => {
    setErrorMessage('');
    setSuccessMessage('');

    if (!reviewerNotes.trim()) {
      setErrorMessage('Vui lòng nhập lý do / giải trình quyết định của Admin (bắt buộc).');
      return;
    }

    if (decision === 'APPROVED' && adjustedScore.trim() !== '') {
      const num = Number(adjustedScore);
      if (isNaN(num) || num < 0 || num > 100) {
        setErrorMessage('Điểm điều chỉnh phải là số hợp lệ từ 0 đến 100.');
        return;
      }
    }

    setShowConfirmModal(true);
  };

  const handleConfirmResolve = () => {
    let parsedScore: number | undefined = undefined;
    if (decision === 'APPROVED' && adjustedScore.trim() !== '') {
      parsedScore = Number(adjustedScore);
    }

    resolveMutation.mutate(
      { 
        decision, 
        reviewerNotes: reviewerNotes.trim(), 
        adjustedScore: parsedScore 
      },
      {
        onSuccess: (data) => {
          setShowConfirmModal(false);
          const statusText = data.status === 'APPROVED' ? 'chấp thuận' : 'từ chối';
          toast.success(`Đơn khiếu nại #${appeal.id.substring(0, 8)} đã được ${statusText}!`);
          setSuccessMessage(`Đã ghi nhận quyết định Admin: ${data.status === 'APPROVED' ? 'Chấp thuận' : 'Từ chối'}`);
          refetch();
        },
        onError: (err: unknown) => {
          setShowConfirmModal(false);
          const msg = (err as { response?: { data?: { message?: string } }; message?: string })?.response?.data?.message || (err as { message?: string })?.message || 'Không thể ghi nhận quyết định.';
          setErrorMessage(msg);
          toast.error(msg);
        },
      }
    );
  };

  return (
    <div className="max-w-7xl mx-auto px-space-lg py-space-lg flex flex-col w-full space-y-space-lg">
      {/* Top Bar */}
      <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-space-md pb-space-md border-b border-outline-variant/30">
        <div className="space-y-1">
          <nav className="flex items-center gap-2 text-on-surface-variant text-xs mb-1">
            <Link className="hover:text-primary transition-colors flex items-center gap-1 font-medium" href="/admin/appeals">
              <ArrowLeft className="w-3.5 h-3.5" />
              <span>Quản lý Khiếu nại</span>
            </Link>
            <span className="text-outline">/</span>
            <span className="text-primary font-semibold font-mono">#{appeal.id.substring(0, 8)}</span>
          </nav>
          <div className="flex items-center gap-space-sm flex-wrap">
            <h1 className="font-headline-lg text-headline-lg text-on-surface tracking-tight">
              Xem xét &amp; Phán quyết Khiếu nại — #{appeal.id.substring(0, 8)}
            </h1>
            {isPending && (
              <span className="text-xs px-2.5 py-0.5 rounded-full bg-amber-50 text-amber-700 border border-amber-200 font-semibold flex items-center gap-1">
                <span className="w-1.5 h-1.5 rounded-full bg-amber-500 animate-pulse"></span>
                <span>Chờ Admin xem xét</span>
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
            href="/admin/appeals"
            className="px-4 py-2 rounded-lg bg-surface-container hover:bg-surface-container-high text-on-surface text-xs font-medium border border-outline-variant/40 transition-colors"
          >
            Quay lại Danh sách
          </Link>
        </div>
      </div>

      {successMessage && (
        <div className="p-3.5 rounded-xl bg-emerald-50 text-emerald-800 text-xs flex items-center gap-2 border border-emerald-200">
          <CheckCircle2 className="w-4 h-4 text-emerald-600 shrink-0" />
          <span>{successMessage}</span>
        </div>
      )}

      {errorMessage && (
        <div className="p-3.5 rounded-xl bg-rose-50 text-rose-700 text-xs flex items-center gap-2 border border-rose-200">
          <AlertCircle className="w-4 h-4 text-rose-600 shrink-0" />
          <span>{errorMessage}</span>
        </div>
      )}

      <div className="grid grid-cols-1 lg:grid-cols-12 gap-space-lg items-start">
        {/* Left Column: Context Card */}
        <aside className="lg:col-span-4 space-y-space-md">
          <div className="bg-surface-container-lowest rounded-2xl p-space-md border border-outline-variant/40 shadow-xs space-y-3 text-xs">
            <div className="font-label-xs uppercase tracking-wider text-outline font-semibold pb-2 border-b border-outline-variant/20 flex items-center gap-1.5">
              <ShieldCheck className="w-4 h-4 text-primary" />
              <span>Hồ sơ Thẩm định Admin</span>
            </div>
            <div>
              <span className="text-outline uppercase text-[10px] block">Bài thi</span>
              <span className="font-semibold text-on-surface text-sm">{appeal.examName}</span>
            </div>
            <div>
              <span className="text-outline uppercase text-[10px] block">Mã bài nộp tham chiếu</span>
              <span className="font-mono text-on-surface text-xs">#{appeal.submissionId?.substring(0, 8)}</span>
            </div>
            <div>
              <span className="text-outline uppercase text-[10px] block">Lý do khiếu nại</span>
              <span className="font-semibold text-on-surface capitalize">{appeal.basis?.replace(/_/g, ' ')}</span>
            </div>
            <div>
              <span className="text-outline uppercase text-[10px] block">Thông tin Sinh viên</span>
              <span className="font-semibold text-on-surface block">{appeal.studentName || 'Sinh viên'}</span>
              <span className="text-on-surface-variant text-[11px]">{appeal.studentEmail}</span>
            </div>

            {/* Score Breakdown */}
            <div className="pt-2 border-t border-outline-variant/20 space-y-1.5">
              <span className="text-outline uppercase text-[10px] block font-semibold">Bảng điểm đối chiếu</span>
              <div className="grid grid-cols-2 gap-2 bg-surface-container-low p-2.5 rounded-xl border border-outline-variant/20">
                <div>
                  <span className="text-[10px] text-outline block">Điểm tự động</span>
                  <span className="font-mono font-bold text-on-surface text-xs">
                    {appeal.autoScore != null ? `${appeal.autoScore} đ` : '—'}
                  </span>
                </div>
                <div>
                  <span className="text-[10px] text-outline block">Điểm ban đầu</span>
                  <span className="font-mono font-bold text-on-surface text-xs">
                    {appeal.originalScore != null ? `${appeal.originalScore} đ` : '—'}
                  </span>
                </div>
                <div>
                  <span className="text-[10px] text-outline block">Điểm ghi đè</span>
                  <span className="font-mono font-bold text-emerald-700 text-xs">
                    {appeal.updatedScore != null ? `${appeal.updatedScore} đ` : 'Chưa có'}
                  </span>
                </div>
                <div>
                  <span className="text-[10px] text-outline block">Điểm hiệu lực</span>
                  <span className="font-mono font-bold text-primary text-xs">
                    {appeal.currentScore != null ? `${appeal.currentScore} đ` : '—'}
                  </span>
                </div>
              </div>
            </div>

            {/* Test cases passed summary if present */}
            {appeal.testCasesTotal != null && (
              <div className="pt-2 border-t border-outline-variant/20">
                <span className="text-outline uppercase text-[10px] block mb-1">Kết quả Test Cases</span>
                <span className="text-xs font-semibold px-2 py-0.5 rounded bg-emerald-50 text-emerald-800 border border-emerald-200">
                  Đạt {appeal.testCasesPassed ?? 0} / {appeal.testCasesTotal} test cases
                </span>
              </div>
            )}
          </div>
        </aside>

        {/* Right Column: Explanation, Source Code, Audit History and Decision Panel */}
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

          {/* Source Code Preview if available */}
          {appeal.sourceCode && (
            <div className="bg-surface-container-lowest rounded-2xl border border-outline-variant/40 shadow-xs p-space-lg space-y-2">
              <div className="flex items-center justify-between pb-2 border-b border-outline-variant/20">
                <div className="flex items-center gap-2">
                  <Code2 className="w-4 h-4 text-primary" />
                  <h2 className="font-headline-sm text-headline-sm text-on-surface font-semibold">Mã nguồn bài làm của Thí sinh</h2>
                </div>
                <span className="text-[11px] font-mono text-outline">Java Source Code</span>
              </div>
              <pre className="bg-slate-950 text-slate-100 p-space-md rounded-xl overflow-x-auto text-xs font-mono max-h-96 leading-relaxed border border-slate-800">
                <code>{appeal.sourceCode}</code>
              </pre>
            </div>
          )}

          {/* Audit History & Rubric */}
          <div className="bg-surface-container-lowest rounded-2xl border border-outline-variant/40 shadow-xs p-space-lg">
            <div className="flex items-center gap-2 pb-3 mb-3 border-b border-outline-variant/20">
              <Award className="w-4 h-4 text-primary" />
              <h2 className="font-headline-sm text-headline-sm text-on-surface font-semibold">Lịch sử Chấm điểm &amp; Kiểm toán (Audit Trail)</h2>
            </div>
            <GradeAuditHistory submissionId={appeal.submissionId} />
          </div>

          {/* Admin Decision Panel */}
          <div className="bg-surface-container-lowest rounded-2xl border border-outline-variant/40 shadow-xs p-space-lg space-y-space-md">
            <div className="flex items-center gap-2 pb-2 border-b border-outline-variant/20">
              <Scale className="w-4 h-4 text-primary" />
              <h2 className="font-headline-sm text-headline-sm text-on-surface font-semibold">Quyết định Phán quyết của Quản trị viên (Admin)</h2>
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
                      Quyết định phán quyết: {appeal.status === 'APPROVED' ? 'Chấp thuận khiếu nại' : 'Từ chối khiếu nại'} (Hoàn tất vào lúc {new Date(appeal.resolvedAt || '').toLocaleString()})
                    </span>
                    {appeal.reviewerName && (
                      <span className="text-[11px] block mt-0.5 text-on-surface-variant flex items-center gap-1">
                        <UserCheck className="w-3.5 h-3.5" />
                        <span>Người phán quyết: <strong>{appeal.reviewerName}</strong> ({appeal.reviewerEmail || 'Admin'})</span>
                      </span>
                    )}
                    {appeal.updatedScore != null && (
                      <span className="text-emerald-700 font-medium block mt-0.5">
                        Điểm số chính thức đã ghi đè: <strong>{appeal.updatedScore}</strong> / 100
                      </span>
                    )}
                  </div>
                </div>

                <div className="space-y-1">
                  <span className="font-label-xs uppercase tracking-wider text-outline">Ghi chú &amp; Căn cứ phán quyết của Admin:</span>
                  <div className="bg-surface-container-low p-space-md rounded-xl text-xs text-on-surface border border-outline-variant/20 whitespace-pre-wrap">
                    {appeal.reviewerNotes || 'Không có ghi chú thêm từ Quản trị viên.'}
                  </div>
                </div>

                <div className="flex items-center gap-1.5 text-outline text-[11px] italic">
                  <Lock className="w-3.5 h-3.5" />
                  <span>Hồ sơ này đã được giải quyết và khóa hoàn toàn. Không thể chỉnh sửa thêm.</span>
                </div>
              </div>
            ) : (
              <div className="space-y-space-md">
                {/* Decision Selector */}
                <div className="space-y-1.5">
                  <label className="text-xs font-semibold text-on-surface block">
                    1. Lựa chọn phán quyết của Admin:
                  </label>
                  <div className="grid grid-cols-2 gap-3">
                    <button
                      type="button"
                      onClick={() => setDecision('APPROVED')}
                      className={`p-3 rounded-xl border text-xs font-semibold transition-all flex items-center justify-center gap-2 cursor-pointer ${
                        decision === 'APPROVED'
                          ? 'bg-emerald-50 text-emerald-800 border-emerald-300 ring-2 ring-emerald-200'
                          : 'bg-surface-container-low text-on-surface-variant border-outline-variant/40 hover:bg-surface-container'
                      }`}
                    >
                      <CheckCircle2 className="w-4 h-4 text-emerald-600" />
                      <span>Chấp thuận khiếu nại (APPROVED)</span>
                    </button>

                    <button
                      type="button"
                      onClick={() => setDecision('REJECTED')}
                      className={`p-3 rounded-xl border text-xs font-semibold transition-all flex items-center justify-center gap-2 cursor-pointer ${
                        decision === 'REJECTED'
                          ? 'bg-rose-50 text-rose-800 border-rose-300 ring-2 ring-rose-200'
                          : 'bg-surface-container-low text-on-surface-variant border-outline-variant/40 hover:bg-surface-container'
                      }`}
                    >
                      <XCircle className="w-4 h-4 text-rose-600" />
                      <span>Từ chối khiếu nại (REJECTED)</span>
                    </button>
                  </div>
                </div>

                {/* Score adjustment (if approved) */}
                {decision === 'APPROVED' && (
                  <div className="space-y-1.5 p-3 rounded-xl bg-emerald-50/50 border border-emerald-200/60">
                    <div className="flex items-center justify-between">
                      <label className="text-xs font-semibold text-emerald-950 block">
                        2. Cập nhật điểm chính thức mới (Tùy chọn):
                      </label>
                      <span className="text-[11px] text-emerald-800">
                        Điểm hiện tại: <strong className="font-mono">{appeal.currentScore ?? appeal.originalScore ?? '—'}</strong> / 100
                      </span>
                    </div>
                    <p className="text-[11px] text-emerald-800">
                      Nếu đồng ý nâng/hạ điểm sau phúc khảo, nhập điểm mới bên dưới (0 - 100). Nếu chấp thuận giải trình nhưng giữ nguyên điểm số, để trống ô này.
                    </p>
                    <div className="flex items-center gap-2 max-w-xs mt-1">
                      <input
                        type="number"
                        step="0.1"
                        min="0"
                        max="100"
                        placeholder="VD: 90.0"
                        value={adjustedScore}
                        onChange={(e) => setAdjustedScore(e.target.value)}
                        className="w-full px-3 py-1.5 rounded-lg border border-emerald-300 bg-surface-container-lowest text-on-surface text-xs font-mono focus:outline-none focus:ring-1 focus:ring-emerald-500"
                        disabled={resolveMutation.isPending}
                      />
                      <span className="text-xs text-emerald-900 font-semibold font-mono">/ 100</span>
                    </div>
                  </div>
                )}

                {/* Reviewer Notes (Mandatory) */}
                <div className="space-y-1">
                  <div className="flex items-center justify-between">
                    <label className="text-xs font-semibold text-on-surface block">
                      {decision === 'APPROVED' ? '3.' : '2.'} Căn cứ phán quyết &amp; Giải trình của Admin * (Bắt buộc):
                    </label>
                    <span className="text-[11px] text-outline font-mono">Bắt buộc</span>
                  </div>
                  <textarea
                    rows={4}
                    placeholder="Nhập căn cứ pháp lý, lý do điều chỉnh điểm hoặc nguyên nhân bác bỏ đơn khiếu nại để sinh viên xem..."
                    value={reviewerNotes}
                    onChange={(e) => setReviewerNotes(e.target.value)}
                    className="w-full p-3 rounded-xl border border-outline-variant/40 bg-surface-container-low text-on-surface text-xs focus:outline-none focus:border-primary transition-colors leading-relaxed"
                    disabled={resolveMutation.isPending}
                  />
                </div>

                <div className="flex items-center justify-end gap-3 pt-space-sm border-t border-outline-variant/20">
                  <button
                    type="button"
                    onClick={validateAndPrompt}
                    disabled={resolveMutation.isPending}
                    className={`px-6 py-2.5 rounded-lg text-white text-xs font-semibold transition-all inline-flex items-center gap-1.5 shadow-xs disabled:opacity-50 cursor-pointer ${
                      decision === 'APPROVED' ? 'bg-emerald-600 hover:bg-emerald-700' : 'bg-rose-600 hover:bg-rose-700'
                    }`}
                  >
                    <Check className="w-3.5 h-3.5" />
                    <span>Lưu Phán quyết Khiếu nại</span>
                  </button>
                </div>
              </div>
            )}
          </div>
        </main>
      </div>

      {/* Confirmation Modal */}
      {showConfirmModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
          <div className="bg-surface-container-lowest rounded-2xl border border-outline-variant/40 shadow-xl max-w-md w-full p-6 space-y-4">
            <div className="flex items-center gap-3">
              <div className={`p-2.5 rounded-xl ${decision === 'APPROVED' ? 'bg-emerald-50 text-emerald-600' : 'bg-rose-50 text-rose-600'}`}>
                {decision === 'APPROVED' ? <CheckCircle2 className="w-6 h-6" /> : <XCircle className="w-6 h-6" />}
              </div>
              <div>
                <h3 className="font-semibold text-on-surface text-sm">
                  Xác nhận phán quyết khiếu nại?
                </h3>
                <p className="text-xs text-on-surface-variant">
                  {decision === 'APPROVED' ? 'Bạn đang chấp thuận khiếu nại này.' : 'Bạn đang từ chối khiếu nại này.'}
                </p>
              </div>
            </div>

            <div className="bg-surface-container-low p-3 rounded-xl text-xs space-y-2 border border-outline-variant/20">
              <div className="flex justify-between">
                <span className="text-outline">Quyết định:</span>
                <strong className={decision === 'APPROVED' ? 'text-emerald-700' : 'text-rose-700'}>
                  {decision === 'APPROVED' ? 'Chấp thuận (APPROVED)' : 'Từ chối (REJECTED)'}
                </strong>
              </div>
              {decision === 'APPROVED' && adjustedScore.trim() !== '' && (
                <div className="flex justify-between">
                  <span className="text-outline">Điểm số mới:</span>
                  <strong className="font-mono text-emerald-700">{adjustedScore} / 100</strong>
                </div>
              )}
              <div className="pt-2 border-t border-outline-variant/20">
                <span className="text-outline block mb-1">Giải trình:</span>
                <p className="text-on-surface italic line-clamp-3">&ldquo;{reviewerNotes}&rdquo;</p>
              </div>
            </div>

            <p className="text-[11px] text-amber-700 bg-amber-50 p-2.5 rounded-lg border border-amber-200">
              <strong>Lưu ý:</strong> Sau khi xác nhận, quyết định sẽ được lưu vĩnh viễn vào nhật ký kiểm toán (Audit Log) và không thể hoàn tác.
            </p>

            <div className="flex items-center justify-end gap-2 pt-2">
              <button
                type="button"
                onClick={() => setShowConfirmModal(false)}
                disabled={resolveMutation.isPending}
                className="px-4 py-2 rounded-lg text-xs font-medium text-on-surface bg-surface-container hover:bg-surface-container-high transition-colors cursor-pointer"
              >
                Hủy bỏ
              </button>
              <button
                type="button"
                onClick={handleConfirmResolve}
                disabled={resolveMutation.isPending}
                className={`px-5 py-2 rounded-lg text-xs font-semibold text-white transition-colors flex items-center gap-1.5 cursor-pointer ${
                  decision === 'APPROVED' ? 'bg-emerald-600 hover:bg-emerald-700' : 'bg-rose-600 hover:bg-rose-700'
                }`}
              >
                {resolveMutation.isPending ? (
                  <>
                    <Loader2 className="w-3.5 h-3.5 animate-spin" />
                    <span>Đang lưu...</span>
                  </>
                ) : (
                  <span>Xác nhận thực hiện</span>
                )}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
