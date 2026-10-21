'use client';

import React, { useState, useRef } from 'react';
import {
  X,
  FileSpreadsheet,
  UploadCloud,
  CheckCircle2,
  AlertTriangle,
  Loader2,
  AlertCircle,
  FileText,
  RotateCcw,
  Check,
} from 'lucide-react';
import { toast } from 'sonner';
import { useImportStudentsFromExcel } from '../hooks';
import { ExcelImportResult } from '../api/admin.api';
import { useTranslation } from '@/i18n';

interface ExcelImportModalProps {
  isOpen: boolean;
  onClose: () => void;
  classId: string;
  className: string;
  classCode: string;
  onImportSuccess?: (result: ExcelImportResult) => void;
}

const MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB

export function ExcelImportModal({
  isOpen,
  onClose,
  classId,
  className,
  classCode,
  onImportSuccess,
}: ExcelImportModalProps) {
  const { t } = useTranslation();
  const [file, setFile] = useState<File | null>(null);
  const [strict, setStrict] = useState<boolean>(false);
  const [isDragOver, setIsDragOver] = useState<boolean>(false);
  const [importResult, setImportResult] = useState<ExcelImportResult | null>(null);
  const [generalError, setGeneralError] = useState<string | null>(null);

  const fileInputRef = useRef<HTMLInputElement | null>(null);
  const importMutation = useImportStudentsFromExcel(classId);

  if (!isOpen) return null;

  const validateAndSetFile = (candidate: File | null) => {
    setGeneralError(null);
    setImportResult(null);

    if (!candidate) {
      setFile(null);
      return;
    }

    const name = candidate.name.toLowerCase();
    if (!name.endsWith('.xlsx') && !name.endsWith('.xls')) {
      const err = 'Invalid file format. Please select an Excel spreadsheet (.xlsx or .xls).';
      setGeneralError(err);
      toast.error(err);
      setFile(null);
      return;
    }

    if (candidate.size > MAX_FILE_SIZE) {
      const err = `File size exceeds the 10MB limit (${(candidate.size / (1024 * 1024)).toFixed(1)}MB).`;
      setGeneralError(err);
      toast.error(err);
      setFile(null);
      return;
    }

    setFile(candidate);
  };

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const selected = e.target.files?.[0] || null;
    validateAndSetFile(selected);
  };

  const handleDrop = (e: React.DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    setIsDragOver(false);
    const dropped = e.dataTransfer.files?.[0] || null;
    validateAndSetFile(dropped);
  };

  const handleDragOver = (e: React.DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    setIsDragOver(true);
  };

  const handleDragLeave = (e: React.DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    setIsDragOver(false);
  };

  const handleReset = () => {
    setFile(null);
    setImportResult(null);
    setGeneralError(null);
    if (fileInputRef.current) {
      fileInputRef.current.value = '';
    }
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!file || importMutation.isPending) return;

    setGeneralError(null);
    setImportResult(null);

    importMutation.mutate(
      { file, strict },
      {
        onSuccess: (data) => {
          setImportResult(data);
          if (data.importedCount > 0) {
            toast.success(`Successfully enrolled ${data.importedCount} student(s) into ${classCode}!`);
            onImportSuccess?.(data);
          } else {
            toast.warning(`No students enrolled. ${data.failedCount} row(s) had errors.`);
          }
        },
        onError: (err: unknown) => {
          const res = (err as { response?: { status?: number; data?: unknown } })?.response;
          const status = res?.status;
          const data = res?.data as ExcelImportResult | { message?: string } | undefined;

          // If strict mode aborted with structured ExcelImportResult in HTTP 400
          if (status === 400 && data && 'errors' in data && Array.isArray(data.errors)) {
            setImportResult(data as ExcelImportResult);
            toast.error(`Strict import aborted: ${(data as ExcelImportResult).failedCount} validation error(s) found.`);
            return;
          }

          let msg = 'Failed to import Excel roster.';
          if (status === 403) {
            msg = 'Permission denied. Admin role required to import student rosters.';
          } else if (status === 404) {
            msg = 'Class not found or no longer exists.';
          } else if (data && 'message' in data && typeof data.message === 'string') {
            msg = data.message;
          }

          setGeneralError(msg);
          toast.error(msg);
        },
      }
    );
  };

  const formatFileSize = (bytes: number) => {
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  };

  return (
    <div className="fixed inset-0 z-60 flex items-center justify-center bg-black/60 backdrop-blur-xs p-4 animate-in fade-in duration-150">
      <div
        className="bg-surface-container-lowest w-full max-w-2xl rounded-2xl shadow-2xl border border-outline-variant/30 flex flex-col overflow-hidden max-h-[92vh] animate-in zoom-in-95 duration-200"
        role="dialog"
        aria-modal="true"
        aria-label="Bulk Import Students from Excel"
        aria-labelledby="excel-import-modal-title"
      >
        {/* Modal Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-outline-variant/30 bg-surface-container-low shrink-0">
          <div className="flex items-center gap-3">
            <div className="p-2.5 rounded-xl bg-emerald-500/10 text-emerald-600 dark:text-emerald-400">
              <FileSpreadsheet className="w-5 h-5" />
            </div>
            <div>
              <h2 id="excel-import-modal-title" className="text-base font-semibold text-on-surface">
                {t.admin.excelImportTitle}
              </h2>
              <p className="text-xs text-on-surface-variant font-mono">
                {className} <span className="text-primary font-bold">({classCode})</span>
              </p>
            </div>
          </div>
          <button
            onClick={onClose}
            disabled={importMutation.isPending}
            className="p-1.5 rounded-lg text-on-surface-variant hover:text-on-surface hover:bg-surface-container transition-colors disabled:opacity-50 cursor-pointer"
            aria-label="Close import dialog"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Modal Content */}
        <div className="flex-1 overflow-y-auto p-6 space-y-5">
          {/* General Error Banner */}
          {generalError && (
            <div className="p-3.5 rounded-xl bg-error/10 border border-error/30 text-error flex items-start gap-2.5 text-xs animate-in fade-in duration-150">
              <AlertCircle className="w-4 h-4 shrink-0 mt-0.5" />
              <div>
                <p className="font-semibold">{t.common.error}</p>
                <p className="mt-0.5 leading-relaxed">{generalError}</p>
              </div>
            </div>
          )}

          {/* Form when not displaying full result */}
          {!importResult && (
            <form onSubmit={handleSubmit} className="space-y-5">
              {/* Dropzone */}
              <div
                onDrop={handleDrop}
                onDragOver={handleDragOver}
                onDragLeave={handleDragLeave}
                onClick={() => !importMutation.isPending && fileInputRef.current?.click()}
                className={`relative border-2 border-dashed rounded-xl p-6 flex flex-col items-center justify-center text-center cursor-pointer transition-all ${
                  isDragOver
                    ? 'border-primary bg-primary/5 scale-[1.01]'
                    : file
                    ? 'border-emerald-500/50 bg-emerald-500/5'
                    : 'border-outline-variant/60 hover:border-outline-variant bg-surface-container-low/30 hover:bg-surface-container-low/60'
                } ${importMutation.isPending ? 'opacity-60 cursor-not-allowed pointer-events-none' : ''}`}
              >
                <input
                  ref={fileInputRef}
                  type="file"
                  accept=".xlsx,.xls"
                  onChange={handleFileChange}
                  disabled={importMutation.isPending}
                  className="hidden"
                  id="excel-file-input"
                />

                {file ? (
                  <div className="flex flex-col items-center gap-2">
                    <div className="p-3 rounded-full bg-emerald-500/10 text-emerald-600">
                      <FileSpreadsheet className="w-8 h-8" />
                    </div>
                    <div>
                      <p className="text-sm font-semibold text-on-surface">{file.name}</p>
                      <p className="text-xs text-on-surface-variant font-mono">{formatFileSize(file.size)}</p>
                    </div>
                    <span className="text-xs text-primary font-medium mt-1">Click or drag another file to replace</span>
                  </div>
                ) : (
                  <div className="flex flex-col items-center gap-2">
                    <div className="p-3 rounded-full bg-surface-container text-on-surface-variant">
                      <UploadCloud className="w-8 h-8 text-primary" />
                    </div>
                    <div>
                      <p className="text-sm font-semibold text-on-surface">{t.admin.dropExcelHere}</p>
                      <p className="text-xs text-on-surface-variant mt-0.5">Supports Microsoft Excel (.xlsx, .xls) up to 10MB</p>
                    </div>
                  </div>
                )}
              </div>

              {/* Supported Columns Guide */}
              <div className="p-3 bg-surface-container-low/60 rounded-xl border border-outline-variant/30 text-xs text-on-surface-variant space-y-1">
                <div className="flex items-center gap-1.5 font-semibold text-on-surface">
                  <FileText className="w-3.5 h-3.5 text-primary" />
                  <span>Column Specification Guide</span>
                </div>
                <p className="leading-relaxed">
                  Headers are auto-detected: <code className="bg-surface-container px-1 py-0.5 rounded font-mono text-primary">Email</code>, <code className="bg-surface-container px-1 py-0.5 rounded font-mono text-primary">StudentCode</code> / <code className="bg-surface-container px-1 py-0.5 rounded font-mono text-primary">StudentId</code>, and <code className="bg-surface-container px-1 py-0.5 rounded font-mono">FullName</code>.
                  Each student must already possess a registered student account in CodeAssess.
                </p>
              </div>

              {/* Strict Mode Toggle */}
              <div className="p-4 rounded-xl bg-surface-container-low/40 border border-outline-variant/30 flex items-start justify-between gap-4">
                <div className="space-y-0.5">
                  <label htmlFor="strict-mode-toggle" className="text-xs font-semibold text-on-surface cursor-pointer select-none">
                    {t.admin.importMode}: Strict Validation
                  </label>
                  <p className="text-xs text-on-surface-variant leading-relaxed">
                    {strict
                      ? t.admin.fullImportDesc
                      : t.admin.partialImportDesc}
                  </p>
                </div>
                <input
                  id="strict-mode-toggle"
                  type="checkbox"
                  checked={strict}
                  disabled={importMutation.isPending}
                  onChange={(e) => setStrict(e.target.checked)}
                  className="mt-0.5 h-4 w-4 rounded border-outline-variant text-primary focus:ring-primary cursor-pointer disabled:opacity-50"
                />
              </div>

              {/* Submit Action */}
              <div className="flex items-center justify-end gap-3 pt-2">
                <button
                  type="button"
                  onClick={onClose}
                  disabled={importMutation.isPending}
                  className="px-4 py-2 rounded-lg text-xs font-semibold text-on-surface-variant hover:bg-surface-container transition-colors disabled:opacity-50 cursor-pointer"
                >
                  {t.common.cancel}
                </button>
                <button
                  type="submit"
                  disabled={!file || importMutation.isPending}
                  aria-label="Import Roster"
                  className="flex items-center gap-2 px-5 py-2.5 rounded-lg bg-primary text-on-primary text-xs font-semibold hover:bg-primary-container transition-colors shadow-sm disabled:opacity-50 disabled:cursor-not-allowed cursor-pointer"
                >
                  {importMutation.isPending ? (
                    <>
                      <Loader2 className="w-4 h-4 animate-spin" />
                      <span>{t.admin.importingRoster}</span>
                    </>
                  ) : (
                    <>
                      <FileSpreadsheet className="w-4 h-4" />
                      <span>Import Roster</span>
                    </>
                  )}
                </button>
              </div>
            </form>
          )}

          {/* Result View */}
          {importResult && (
            <div className="space-y-5 animate-in fade-in duration-200">
              {/* Summary Metrics */}
              <div className="grid grid-cols-2 sm:grid-cols-4 gap-2.5">
                <div className="p-3 rounded-xl bg-surface-container-low border border-outline-variant/30 text-center">
                  <span className="text-[10px] uppercase font-semibold text-on-surface-variant">Total Evaluated</span>
                  <p className="text-lg font-bold text-on-surface mt-0.5">{importResult.totalRows}</p>
                </div>
                <div className="p-3 rounded-xl bg-emerald-500/10 border border-emerald-500/30 text-center">
                  <span className="text-[10px] uppercase font-semibold text-emerald-700 dark:text-emerald-400">Imported</span>
                  <p className="text-lg font-bold text-emerald-600 dark:text-emerald-400 mt-0.5">
                    {importResult.importedCount}
                  </p>
                </div>
                <div className="p-3 rounded-xl bg-rose-500/10 border border-rose-500/30 text-center">
                  <span className="text-[10px] uppercase font-semibold text-rose-700 dark:text-rose-400">Failed / Skipped</span>
                  <p className="text-lg font-bold text-rose-600 dark:text-rose-400 mt-0.5">
                    {importResult.failedCount}
                  </p>
                </div>
                <div className="p-3 rounded-xl bg-surface-container-low border border-outline-variant/30 text-center">
                  <span className="text-[10px] uppercase font-semibold text-on-surface-variant">{t.admin.importMode}</span>
                  <p className="text-xs font-bold text-on-surface mt-1.5 uppercase font-mono">
                    {importResult.strict ? 'Strict' : 'Partial'}
                  </p>
                </div>
              </div>

              {/* Status Banner */}
              {importResult.failedCount === 0 ? (
                <div className="p-4 rounded-xl bg-emerald-500/10 border border-emerald-500/30 flex items-center gap-3 text-emerald-700 dark:text-emerald-400">
                  <CheckCircle2 className="w-5 h-5 shrink-0" />
                  <div>
                    <h4 className="text-xs font-bold">{t.admin.importSuccess}</h4>
                    <p className="text-xs text-on-surface-variant mt-0.5">
                      All {importResult.importedCount} student record(s) have been enrolled in {classCode}.
                    </p>
                  </div>
                </div>
              ) : (
                <div className="p-4 rounded-xl bg-amber-500/10 border border-amber-500/30 flex items-start gap-3 text-amber-800 dark:text-amber-400">
                  <AlertTriangle className="w-5 h-5 shrink-0 mt-0.5" />
                  <div>
                    <h4 className="text-xs font-bold">
                      {importResult.strict
                        ? 'Batch Rollback (Strict Mode Violated)'
                        : `Import Finished with ${importResult.failedCount} Row Error(s)`}
                    </h4>
                    <p className="text-xs text-on-surface-variant mt-0.5 leading-relaxed">
                      {importResult.strict
                        ? 'Due to strict mode constraints, no records were added. Review the row errors below and retry.'
                        : `${importResult.importedCount} student(s) were enrolled successfully. The remaining ${importResult.failedCount} row(s) failed validation.`}
                    </p>
                  </div>
                </div>
              )}

              {/* Detailed Row Error Table */}
              {importResult.errors && importResult.errors.length > 0 && (
                <div className="space-y-2">
                  <h4 className="text-xs font-semibold text-on-surface flex items-center justify-between">
                    <span>{t.admin.importErrors} ({importResult.errors.length})</span>
                    <span className="text-[10px] font-normal text-on-surface-variant">Sorted by Excel row number</span>
                  </h4>
                  <div className="max-h-56 overflow-y-auto rounded-xl border border-outline-variant/30 overflow-hidden">
                    <table className="w-full text-left text-xs">
                      <thead className="bg-surface-container-low text-on-surface-variant uppercase tracking-wider text-[10px] border-b border-outline-variant/30 sticky top-0">
                        <tr>
                          <th className="py-2 px-3 font-semibold w-16">Row</th>
                          <th className="py-2 px-3 font-semibold">Identifier</th>
                          <th className="py-2 px-3 font-semibold">Error Code</th>
                          <th className="py-2 px-3 font-semibold">Message</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-outline-variant/20">
                        {importResult.errors.map((err, idx) => (
                          <tr key={`${err.rowNumber}-${idx}`} className="hover:bg-surface-container-low/40">
                            <td className="py-2.5 px-3 font-mono font-bold text-on-surface">#{err.rowNumber}</td>
                            <td className="py-2.5 px-3 font-mono text-outline text-[11px] truncate max-w-[140px]" title={err.identifier}>
                              {err.identifier || '—'}
                            </td>
                            <td className="py-2.5 px-3">
                              <span className="px-2 py-0.5 rounded-md font-mono text-[10px] font-semibold bg-rose-500/10 text-rose-600 dark:text-rose-400">
                                {err.errorCode}
                              </span>
                            </td>
                            <td className="py-2.5 px-3 text-on-surface-variant leading-tight">{err.message}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </div>
              )}

              {/* Result Actions */}
              <div className="flex items-center justify-between pt-2 border-t border-outline-variant/20">
                <button
                  type="button"
                  onClick={handleReset}
                  className="flex items-center gap-1.5 px-3.5 py-1.5 rounded-lg text-xs font-semibold text-on-surface-variant hover:bg-surface-container transition-colors cursor-pointer"
                >
                  <RotateCcw className="w-3.5 h-3.5" />
                  <span>Import Another File</span>
                </button>
                <button
                  type="button"
                  onClick={onClose}
                  className="flex items-center gap-1.5 px-4 py-2 rounded-lg bg-surface-container-high text-on-surface text-xs font-semibold hover:bg-surface-container-highest transition-colors cursor-pointer"
                >
                  <Check className="w-3.5 h-3.5 text-primary" />
                  <span>{t.common.close}</span>
                </button>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
