'use client';

import React, { useMemo } from 'react';
import { AppLink as Link } from '@/components/ui/app-link';
import { useTeacherExams, useTeacherSubmissions } from '@/features/exams/hooks/use-teacher-exams';
import { usePlagiarismList } from '@/features/plagiarism/hooks';
import { useTeacherAppeals } from '@/features/appeals/hooks';
import { useTranslation } from '@/i18n';
import { 
  FileCode2, 
  Code2, 
  Clock, 
  ShieldAlert, 
  Scale, 
  PlusCircle, 
  Download, 
  ArrowRight, 
  CheckCircle2, 
  ExternalLink,
  Calendar,
  Sparkles,
  BookOpen
} from 'lucide-react';
import { toast } from 'sonner';

export function TeacherDashboardView() {
  const { t, formatDate } = useTranslation();

  const { data: exams, isLoading: examsLoading } = useTeacherExams();
  const { data: submissions, isLoading: submissionsLoading } = useTeacherSubmissions();
  const { data: plagiarismList, isLoading: plagiarismLoading } = usePlagiarismList();
  const { data: appeals, isLoading: appealsLoading } = useTeacherAppeals();

  const totalExams = exams?.length || 0;
  const totalSubmissions = submissions?.length || 0;
  const pendingGradingCount = useMemo(() => {
    if (!submissions) return 0;
    return submissions.filter(s => s.status !== 'GRADED').length;
  }, [submissions]);

  const flaggedCount = useMemo(() => {
    if (!plagiarismList) return 0;
    return plagiarismList.filter(p => Boolean(p.pair.isFlagged)).length;
  }, [plagiarismList]);

  const pendingAppealsCount = useMemo(() => {
    if (!appeals) return 0;
    return appeals.filter(a => a.status === 'PENDING').length;
  }, [appeals]);

  // Export summary report as CSV
  const handleExportReports = () => {
    if (!exams || exams.length === 0) {
      toast.error(t('dashboard.exportReportsEmpty'));
      return;
    }

    const headers = ['Exam ID', 'Title', 'Course Code', 'Exam Date', 'Duration (Min)', 'Status'];
    const rows = exams.map(e => [
      `"${e.id}"`,
      `"${e.title.replace(/"/g, '""')}"`,
      `"${e.courseCode || ''}"`,
      `"${e.examDate ? new Date(e.examDate).toISOString() : ''}"`,
      e.duration || 90,
      `"${e.status || 'ACTIVE'}"`
    ]);

    const csvContent = 'data:text/csv;charset=utf-8,' + [headers.join(','), ...rows.map(r => r.join(','))].join('\n');
    const encodedUri = encodeURI(csvContent);
    const link = document.createElement('a');
    link.setAttribute('href', encodedUri);
    link.setAttribute('download', `CodeAssess_Faculty_Report_${new Date().toISOString().slice(0, 10)}.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);

    toast.success(t('dashboard.exportReportsSuccess'));
  };

  return (
    <div className="w-full max-w-7xl mx-auto px-4 sm:px-space-lg py-space-lg min-w-0">
      <div className="flex flex-col w-full gap-space-lg min-w-0">
        {/* Header Title & Actions */}
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-space-md">
          <div className="flex flex-col">
            <div className="flex items-center gap-space-xs text-xs">
              <span className="font-label-sm text-primary font-semibold uppercase tracking-wider">{t('dashboard.facultyWorkspace')}</span>
              <span className="text-outline-variant font-label-sm">/</span>
              <span className="font-label-sm text-outline">{t('dashboard.terminalView')}</span>
            </div>
            <h1 className="font-display-lg text-display-lg text-on-surface tracking-tight mt-1">{t('dashboard.facultyDashboard')}</h1>
            <p className="font-body-md text-body-md text-on-surface-variant">
              {t('dashboard.facultyDashboardDesc')}
            </p>
          </div>

          <div className="flex flex-wrap items-center gap-space-sm">
            <div className="flex items-center gap-space-xs px-3 py-1.5 bg-surface-container rounded-lg border border-outline-variant/30 text-on-surface text-xs font-medium">
              <Calendar className="w-3.5 h-3.5 text-primary" />
              <span>{t('header.termBadge')}</span>
            </div>

            <button 
              onClick={handleExportReports}
              className="flex items-center gap-1.5 px-3.5 py-1.5 bg-surface-container-high hover:bg-surface-container-highest text-on-surface rounded-lg font-label-md text-xs transition-colors border border-outline-variant/40 cursor-pointer shadow-xs"
            >
              <Download className="w-3.5 h-3.5 text-on-surface-variant" />
              <span>{t('dashboard.exportReports')}</span>
            </button>

            <Link 
              href="/teacher/exams?action=create"
              className="flex items-center gap-1.5 px-4 py-1.5 bg-primary hover:bg-primary/90 text-on-primary rounded-lg font-label-md text-xs transition-all shadow-sm cursor-pointer font-medium"
            >
              <PlusCircle className="w-3.5 h-3.5" />
              <span>+ {t('dashboard.createNewExam')}</span>
            </Link>
          </div>
        </div>

        {/* 5 KPI Metric Cards */}
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-space-md">
          {/* Card 1: Total Exams */}
          <div className="flex flex-col justify-between p-space-md bg-surface-container-lowest rounded-xl border border-outline-variant/40 shadow-xs hover:border-primary/40 transition-all">
            <div className="flex items-center justify-between text-on-surface-variant">
              <span className="font-label-xs uppercase tracking-wider text-outline font-semibold">{t('dashboard.totalExams')}</span>
              <div className="p-1.5 rounded-lg bg-blue-50 dark:bg-blue-950/60 text-blue-600 dark:text-blue-400">
                <FileCode2 className="w-4 h-4" />
              </div>
            </div>
            <div className="my-space-xs flex items-baseline gap-2">
              <span className="font-display-lg text-display-lg text-on-surface font-bold">
                {examsLoading ? '...' : totalExams}
              </span>
              <span className="font-label-xs text-xs text-emerald-600 dark:text-emerald-400 font-semibold flex items-center gap-0.5">
                <CheckCircle2 className="w-3 h-3" /> {t('common.active')}
              </span>
            </div>
            <div className="pt-space-xs text-on-surface-variant font-label-xs text-xs flex items-center gap-1.5 truncate">
              <span className="h-1.5 w-1.5 rounded-full bg-blue-500"></span>
              <span className="truncate">{t('exams.authorizedClasses')}</span>
            </div>
          </div>

          {/* Card 2: Total Submissions */}
          <div className="flex flex-col justify-between p-space-md bg-surface-container-lowest rounded-xl border border-outline-variant/40 shadow-xs hover:border-secondary/40 transition-all">
            <div className="flex items-center justify-between text-on-surface-variant">
              <span className="font-label-xs uppercase tracking-wider text-outline font-semibold">{t('dashboard.totalSubmissions')}</span>
              <div className="p-1.5 rounded-lg bg-sky-50 dark:bg-sky-950/60 text-sky-600 dark:text-sky-400">
                <Code2 className="w-4 h-4" />
              </div>
            </div>
            <div className="my-space-xs flex items-baseline gap-2">
              <span className="font-display-lg text-display-lg text-on-surface font-bold">
                {submissionsLoading ? '...' : totalSubmissions}
              </span>
              <span className="font-label-xs text-xs text-sky-700 dark:text-sky-300 font-medium">{t('common.completed')}</span>
            </div>
            <div className="pt-space-xs text-on-surface-variant font-label-xs text-xs flex items-center gap-1.5 truncate">
              <span className="h-1.5 w-1.5 rounded-full bg-sky-500"></span>
              <span className="truncate">{t('submissions.mySubmissionsTitle')}</span>
            </div>
          </div>

          {/* Card 3: Pending Grading */}
          <div className="flex flex-col justify-between p-space-md bg-surface-container-lowest rounded-xl border border-outline-variant/40 shadow-xs hover:border-amber-400/40 transition-all">
            <div className="flex items-center justify-between text-on-surface-variant">
              <span className="font-label-xs uppercase tracking-wider text-outline font-semibold">{t('dashboard.pendingGrading')}</span>
              <div className="p-1.5 rounded-lg bg-amber-50 dark:bg-amber-950/60 text-amber-600 dark:text-amber-400">
                <Clock className="w-4 h-4" />
              </div>
            </div>
            <div className="my-space-xs flex items-baseline gap-2">
              <span className="font-display-lg text-display-lg text-on-surface font-bold">
                {submissionsLoading ? '...' : pendingGradingCount}
              </span>
              <span className="font-label-xs text-xs text-amber-700 dark:text-amber-300 font-semibold px-1.5 py-0.5 rounded bg-amber-50 dark:bg-amber-950/60">
                {t('common.pending')}
              </span>
            </div>
            <div className="pt-space-xs text-on-surface-variant font-label-xs text-xs flex items-center gap-1.5 truncate">
              <span className="h-1.5 w-1.5 rounded-full bg-amber-500"></span>
              <span className="truncate">{t('scoring.scoringTitle')}</span>
            </div>
          </div>

          {/* Card 4: Flagged AST Plagiarism */}
          <div className="flex flex-col justify-between p-space-md bg-surface-container-lowest rounded-xl border border-outline-variant/40 shadow-xs hover:border-rose-400/40 transition-all">
            <div className="flex items-center justify-between">
              <span className="font-label-xs uppercase tracking-wider text-outline font-semibold">{t('dashboard.flaggedSubmissions')}</span>
              <div className="p-1.5 rounded-lg bg-rose-50 dark:bg-rose-950/60 text-rose-600 dark:text-rose-400">
                <ShieldAlert className="w-4 h-4" />
              </div>
            </div>
            <div className="my-space-xs flex items-baseline gap-2">
              <span className={`font-display-lg text-display-lg font-bold ${flaggedCount > 0 ? 'text-rose-600 dark:text-rose-400' : 'text-on-surface'}`}>
                {plagiarismLoading ? '...' : flaggedCount}
              </span>
              <span className="font-label-xs text-xs text-rose-700 dark:text-rose-300 font-semibold px-1.5 py-0.5 rounded bg-rose-50 dark:bg-rose-950/60">
                {t('plagiarism.flaggedPairs')}
              </span>
            </div>
            <div className="pt-space-xs text-on-surface-variant font-label-xs text-xs flex items-center gap-1.5 truncate">
              <span className="h-1.5 w-1.5 rounded-full bg-rose-500"></span>
              <span className="truncate">{t('header.astEngineReady')}</span>
            </div>
          </div>

          {/* Card 5: Pending Appeals */}
          <div className="flex flex-col justify-between p-space-md bg-surface-container-lowest rounded-xl border border-outline-variant/40 shadow-xs hover:border-purple-400/40 transition-all">
            <div className="flex items-center justify-between">
              <span className="font-label-xs uppercase tracking-wider text-outline font-semibold">{t('dashboard.activeAppealsCount')}</span>
              <div className="p-1.5 rounded-lg bg-purple-50 dark:bg-purple-950/60 text-purple-600 dark:text-purple-400">
                <Scale className="w-4 h-4" />
              </div>
            </div>
            <div className="my-space-xs flex items-baseline gap-2">
              <span className="font-display-lg text-display-lg text-on-surface font-bold">
                {appealsLoading ? '...' : pendingAppealsCount}
              </span>
              <span className="font-label-xs text-xs text-purple-700 dark:text-purple-300 font-semibold px-1.5 py-0.5 rounded bg-purple-50 dark:bg-purple-950/60">
                {t('appeals.appealStatusPending')}
              </span>
            </div>
            <div className="pt-space-xs text-on-surface-variant font-label-xs text-xs flex items-center gap-1.5 truncate">
              <span className="h-1.5 w-1.5 rounded-full bg-purple-500"></span>
              <span className="truncate">{t('appeals.appealsListTitle')}</span>
            </div>
          </div>
        </div>

        {/* Workstation Quick Shortcuts */}
        <div className="flex flex-wrap items-center justify-between p-space-sm bg-surface-container-low rounded-xl border border-outline-variant/30 gap-space-sm">
          <div className="flex items-center gap-space-xs">
            <Sparkles className="w-4 h-4 text-primary ml-2" />
            <span className="font-label-xs text-outline uppercase font-bold tracking-wider">{t('dashboard.quickActions')}:</span>
          </div>
          <div className="flex flex-wrap items-center gap-space-xs">
            <Link 
              className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-surface-container-lowest hover:bg-surface-container text-on-surface font-label-md text-xs transition-colors border border-outline-variant/40 shadow-xs" 
              href="/teacher/exams"
            >
              <BookOpen className="w-3.5 h-3.5 text-primary" />
              <span>{t('nav.exams')}</span>
            </Link>
            <Link 
              className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-surface-container-lowest hover:bg-surface-container text-on-surface font-label-md text-xs transition-colors border border-outline-variant/40 shadow-xs" 
              href="/teacher/submissions"
            >
              <Code2 className="w-3.5 h-3.5 text-secondary" />
              <span>{t('submissions.submissionsAndGrading')}</span>
            </Link>
            <Link 
              className="flex items-center gap-1.5 px-3.5 py-1.5 rounded-lg bg-rose-50 dark:bg-rose-950/60 hover:bg-rose-100 dark:hover:bg-rose-900/60 text-rose-800 dark:text-rose-200 font-label-md text-xs transition-colors border border-rose-200/60 dark:border-rose-800 shadow-xs font-medium" 
              href="/teacher/plagiarism"
            >
              <ShieldAlert className="w-3.5 h-3.5 text-rose-600 dark:text-rose-400" />
              <span>{t('nav.plagiarismCheck')}</span>
            </Link>
            <Link 
              className="flex items-center gap-1.5 px-3.5 py-1.5 rounded-lg bg-purple-50 dark:bg-purple-950/60 hover:bg-purple-100 dark:hover:bg-purple-900/60 text-purple-800 dark:text-purple-200 font-label-md text-xs transition-colors border border-purple-200/60 dark:border-purple-800 shadow-xs font-medium" 
              href="/teacher/appeals"
            >
              <Scale className="w-3.5 h-3.5 text-purple-600 dark:text-purple-400" />
              <span>{t('nav.appeals')}</span>
            </Link>
          </div>
        </div>

        {/* Live Recent Exams Table */}
        <div className="flex flex-col bg-surface-container-lowest rounded-xl border border-outline-variant/40 shadow-xs overflow-hidden w-full min-w-0">
          <div className="p-space-md flex flex-col sm:flex-row sm:items-center justify-between gap-space-md bg-surface-container-lowest border-b border-outline-variant/30">
            <div className="flex items-center gap-space-sm">
              <div className="p-1.5 rounded-lg bg-blue-50 dark:bg-blue-950/60 text-blue-600 dark:text-blue-400">
                <FileCode2 className="w-4 h-4" />
              </div>
              <div>
                <h2 className="font-headline-sm text-headline-sm text-on-surface">{t('dashboard.upcomingExams')}</h2>
                <span className="font-label-xs text-on-surface-variant">{t('dashboard.facultyDashboardDesc')}</span>
              </div>
            </div>
            <Link 
              href="/teacher/exams" 
              className="text-xs font-medium text-primary hover:underline flex items-center gap-1 self-start sm:self-center"
            >
              <span>{t('dashboard.viewAll')}</span>
              <ArrowRight className="w-3.5 h-3.5" />
            </Link>
          </div>

          <div className="w-full overflow-x-auto">
            <table className="w-full text-left font-body-md text-body-md border-collapse">
              <thead className="bg-surface-container-low text-on-surface-variant font-label-xs uppercase tracking-wider border-b border-outline-variant/30">
                <tr>
                  <th className="py-2.5 px-4 font-semibold">{t('exams.examTitle')}</th>
                  <th className="py-2.5 px-4 font-semibold">{t('exams.course')}</th>
                  <th className="py-2.5 px-4 font-semibold">{t('exams.startTime')}</th>
                  <th className="py-2.5 px-4 font-semibold">{t('exams.durationLabel')}</th>
                  <th className="py-2.5 px-4 font-semibold">{t('common.status')}</th>
                  <th className="py-2.5 px-4 text-right font-semibold">{t('common.actions')}</th>
                </tr>
              </thead>
              <tbody className="text-on-surface divide-y divide-outline-variant/20 text-xs">
                {examsLoading ? (
                  <tr>
                    <td colSpan={6} className="py-8 text-center text-outline">{t('common.loading')}</td>
                  </tr>
                ) : exams && exams.length > 0 ? (
                  exams.slice(0, 5).map((exam) => (
                    <tr key={exam.id} className="hover:bg-surface-container-low/60 transition-colors">
                      <td className="py-3 px-4 font-semibold text-on-surface">
                        <Link href={`/teacher/exams/${exam.id}`} className="hover:text-primary transition-colors">
                          {exam.title}
                        </Link>
                      </td>
                      <td className="py-3 px-4">
                        <span className="px-2 py-0.5 rounded bg-surface-container font-mono text-[11px] font-semibold text-on-surface-variant">
                          {exam.courseCode || 'SE-CORE'}
                        </span>
                      </td>
                      <td className="py-3 px-4 text-on-surface-variant">
                        {exam.examDate ? formatDate(exam.examDate) : t('common.active')}
                      </td>
                      <td className="py-3 px-4 text-on-surface-variant">
                        {exam.duration || 90} {t('common.minutes')}
                      </td>
                      <td className="py-3 px-4">
                        <span className="inline-flex items-center px-2 py-0.5 rounded-full text-[10px] font-semibold bg-emerald-50 dark:bg-emerald-950/60 text-emerald-700 dark:text-emerald-300 border border-emerald-200 dark:border-emerald-800">
                          {t('exams.statusActive')}
                        </span>
                      </td>
                      <td className="py-3 px-4 text-right">
                        <Link 
                          href={`/teacher/exams/${exam.id}`}
                          className="inline-flex items-center gap-1 px-2.5 py-1 rounded bg-surface-container hover:bg-surface-container-high text-primary font-medium transition-colors"
                        >
                          <span>{t('common.details')}</span>
                          <ExternalLink className="w-3 h-3" />
                        </Link>
                      </td>
                    </tr>
                  ))
                ) : (
                  <tr>
                    <td colSpan={6} className="py-8 text-center text-outline font-label-md">
                      {t('dashboard.emptyUpcomingExams')}
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </div>

        {/* 2 Bottom Cards: Assessment Activity & AST Integrity Forensics */}
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-space-lg">
          {/* Recent Submissions Feed */}
          <div className="lg:col-span-7 flex flex-col bg-surface-container-lowest rounded-xl border border-outline-variant/40 p-space-md shadow-xs">
            <div className="flex items-center justify-between mb-space-md pb-2 border-b border-outline-variant/30">
              <div className="flex items-center gap-space-xs">
                <Code2 className="w-4 h-4 text-primary" />
                <h3 className="font-headline-sm text-headline-sm text-on-surface">{t('dashboard.recentSubmissions')}</h3>
              </div>
              <Link href="/teacher/submissions" className="text-xs text-primary hover:underline font-medium">
                {t('dashboard.viewAll')}
              </Link>
            </div>

            <div className="flex flex-col gap-2">
              {submissionsLoading ? (
                <div className="py-8 text-center text-outline text-xs">{t('common.loading')}</div>
              ) : submissions && submissions.length > 0 ? (
                submissions.slice(0, 4).map((sub) => (
                  <div key={sub.id} className="flex items-center justify-between p-2.5 rounded-lg bg-surface-container-low border border-outline-variant/30 text-xs hover:border-primary/30 transition-all">
                    <div className="flex flex-col">
                      <span className="font-semibold text-on-surface truncate max-w-[220px]">
                        {t('submissions.submissionDetailTitle')} #{sub.id.slice(0, 8)}
                      </span>
                      <span className="text-[11px] text-on-surface-variant">
                        {sub.submittedAt ? formatDate(sub.submittedAt) : t('common.active')}
                      </span>
                    </div>

                    <div className="flex items-center gap-2">
                      <span className={`px-2 py-0.5 rounded font-mono text-[11px] font-semibold ${
                        sub.status === 'GRADED' 
                          ? 'bg-emerald-100 dark:bg-emerald-950/60 text-emerald-800 dark:text-emerald-300' 
                          : 'bg-amber-100 dark:bg-amber-950/60 text-amber-800 dark:text-amber-300'
                      }`}>
                        {sub.status === 'GRADED' ? t('submissions.statusGraded') : t('common.pending')}
                      </span>
                      <Link 
                        href={`/teacher/submissions/${sub.id}`}
                        className="px-2 py-1 rounded bg-surface-container hover:bg-surface-container-high text-primary font-medium transition-colors"
                      >
                        {t('scoring.scoringTitle')}
                      </Link>
                    </div>
                  </div>
                ))
              ) : (
                <div className="py-8 text-center text-outline text-xs">
                  {t('dashboard.emptySubmissions')}
                </div>
              )}
            </div>
          </div>

          {/* AST Integrity Forensics Box */}
          <div className="lg:col-span-5 flex flex-col bg-surface-container-lowest rounded-xl border border-outline-variant/40 p-space-md shadow-xs justify-between">
            <div className="flex flex-col gap-space-sm">
              <div className="flex items-center justify-between pb-2 border-b border-outline-variant/30">
                <div className="flex items-center gap-space-xs text-rose-600 dark:text-rose-400">
                  <ShieldAlert className="w-4 h-4" />
                  <span className="font-headline-sm text-headline-sm font-semibold">{t('plagiarism.astEngineBadge')}</span>
                </div>
                <span className="text-[10px] px-2 py-0.5 rounded-full bg-rose-50 dark:bg-rose-950/60 text-rose-700 dark:text-rose-300 font-semibold border border-rose-200 dark:border-rose-800">
                  {t('header.telemetryActive')}
                </span>
              </div>

              <div className="p-space-md rounded-xl bg-surface-container-low border border-outline-variant/30 flex flex-col gap-2 text-xs">
                <div className="flex justify-between items-center">
                  <span className="text-on-surface-variant font-medium">{t('plagiarism.flaggedPairs')}:</span>
                  <span className="font-bold text-rose-600 dark:text-rose-400">{flaggedCount}</span>
                </div>
                <div className="flex justify-between items-center">
                  <span className="text-on-surface-variant font-medium">{t('plagiarism.astStructureDiff')}:</span>
                  <span className="font-mono text-on-surface text-[11px]">Tree-Edit Distance + Tokenizer</span>
                </div>
                <div className="flex justify-between items-center">
                  <span className="text-on-surface-variant font-medium">{t('common.status')}:</span>
                  <span className="text-emerald-700 dark:text-emerald-400 font-semibold flex items-center gap-1">
                    <CheckCircle2 className="w-3.5 h-3.5" /> {t('header.astEngineReady')}
                  </span>
                </div>
              </div>
            </div>

            <div className="pt-space-md flex items-center justify-between gap-space-sm border-t border-outline-variant/30 mt-space-md">
              <span className="font-label-xs text-outline">{t('plagiarism.pairwiseComparisons')}</span>
              <Link 
                className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-rose-600 hover:bg-rose-700 text-white font-label-md text-xs transition-colors shadow-sm font-medium" 
                href="/teacher/plagiarism"
              >
                <span>{t('plagiarism.viewComparison')}</span>
                <ArrowRight className="w-3.5 h-3.5" />
              </Link>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
