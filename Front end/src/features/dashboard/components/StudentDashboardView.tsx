'use client';

import React from 'react';
import { useStudentDashboard } from '../hooks/use-dashboard';
import { AppLink as Link } from '@/components/ui/app-link';
import { useTranslation } from '@/i18n';
import { 
  FileCode2, 
  CheckCircle2, 
  Award, 
  Scale, 
  Calendar, 
  ArrowRight, 
  ShieldCheck, 
  Terminal, 
  Clock, 
  ExternalLink,
  AlertCircle,
  Cpu
} from 'lucide-react';

export function StudentDashboardView() {
  const { data, isLoading, isError, error } = useStudentDashboard();
  const { t, formatDate } = useTranslation();

  if (isLoading) {
    return (
      <div className="flex flex-col h-72 items-center justify-center gap-3">
        <div className="w-8 h-8 rounded-full border-2 border-primary border-t-transparent animate-spin" />
        <p className="text-on-surface-variant font-label-md text-xs">{t('dashboard.loadingWorkspace')}</p>
      </div>
    );
  }

  if (isError) {
    return (
      <div className="max-w-xl mx-auto my-12 p-6 rounded-2xl bg-surface-container-lowest border border-rose-200/60 dark:border-rose-900/60 shadow-lg flex flex-col items-center text-center gap-3">
        <div className="p-3 rounded-full bg-rose-50 dark:bg-rose-950/60 text-rose-600 dark:text-rose-400">
          <AlertCircle className="w-8 h-8" />
        </div>
        <h2 className="font-headline-md text-headline-md text-on-surface">{t('dashboard.failedToLoadDashboard')}</h2>
        <p className="font-body-md text-on-surface-variant text-xs">{(error as Error)?.message || t('errors.somethingWentWrong')}</p>
        <button 
          onClick={() => window.location.reload()}
          className="mt-2 px-4 py-2 bg-primary text-on-primary rounded-lg text-xs font-medium hover:bg-primary/90 transition-all cursor-pointer"
        >
          {t('dashboard.retryConnection')}
        </button>
      </div>
    );
  }

  if (!data) return null;

  return (
    <div className="w-full max-w-7xl mx-auto px-4 sm:px-space-lg py-space-lg min-w-0">
      <div className="flex flex-col w-full gap-space-lg min-w-0">
        {/* Welcome Banner */}
        <section className="relative rounded-2xl p-4 sm:p-space-lg bg-surface-container-lowest border border-outline-variant/40 shadow-xs overflow-hidden w-full min-w-0">
          <div className="absolute -right-16 -top-16 w-80 h-80 rounded-full bg-primary-fixed/30 blur-3xl pointer-events-none"></div>
          <div className="relative z-10 flex flex-col lg:flex-row lg:items-center lg:justify-between gap-space-md w-full min-w-0">
            <div className="flex flex-col gap-space-xs min-w-0">
              <div className="flex flex-wrap items-center gap-space-sm min-w-0">
                <h1 className="font-headline-md sm:font-display-lg text-headline-md sm:text-display-lg text-on-surface tracking-tight break-words">
                  {t('dashboard.studentWelcome')}, {data.fullName}
                </h1>
                <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full bg-surface-container border border-outline-variant/40 text-on-surface-variant font-label-xs text-xs font-medium max-w-full truncate">
                  <span className="w-2 h-2 rounded-full bg-emerald-500 shrink-0"></span>
                  <span className="truncate">{data.email}</span>
                </span>
              </div>
              <p className="font-body-md text-body-md text-on-surface-variant max-w-2xl text-xs sm:text-sm">
                {t('dashboard.studentDashboardDesc')}
              </p>
            </div>

            <div className="flex items-center gap-2 self-start lg:self-center">
              <Link 
                href="/student/exams" 
                className="px-4 py-2 bg-primary text-on-primary rounded-lg text-xs font-medium hover:bg-primary/90 transition-all shadow-sm flex items-center gap-1.5"
              >
                <Terminal className="w-3.5 h-3.5" />
                <span>{t('dashboard.enterExamTerminal')}</span>
              </Link>
            </div>
          </div>
        </section>

        {/* 4 Metric Cards */}
        <section className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-space-md">
          {/* Card 1: Available Exams */}
          <div className="rounded-xl p-space-md bg-surface-container-lowest border border-outline-variant/40 shadow-xs flex flex-col justify-between hover:border-primary/40 transition-all">
            <div className="flex items-center justify-between">
              <span className="font-label-xs uppercase tracking-wider text-outline font-semibold">{t('dashboard.availableExams')}</span>
              <div className="w-8 h-8 rounded-lg bg-blue-50 dark:bg-blue-950/60 text-blue-600 dark:text-blue-400 flex items-center justify-center">
                <Terminal className="w-4 h-4" />
              </div>
            </div>
            <div className="mt-space-md flex flex-col">
              <span className="font-display-lg text-display-lg font-bold text-on-surface leading-none">
                {data.activeExamsCount}
              </span>
              <span className="font-body-sm text-body-sm text-on-surface-variant mt-1">
                {t('dashboard.activeExams')}
              </span>
            </div>
            <Link 
              href="/student/exams" 
              className="mt-space-sm pt-space-xs flex items-center gap-1 font-label-xs text-xs text-primary font-semibold hover:underline"
            >
              <span>{t('dashboard.viewAll')}</span>
              <ArrowRight className="w-3.5 h-3.5" />
            </Link>
          </div>

          {/* Card 2: Submitted */}
          <div className="rounded-xl p-space-md bg-surface-container-lowest border border-outline-variant/40 shadow-xs flex flex-col justify-between hover:border-sky-400/40 transition-all">
            <div className="flex items-center justify-between">
              <span className="font-label-xs uppercase tracking-wider text-outline font-semibold">{t('dashboard.totalSubmissionsCount')}</span>
              <div className="w-8 h-8 rounded-lg bg-sky-50 dark:bg-sky-950/60 text-sky-600 dark:text-sky-400 flex items-center justify-center">
                <CheckCircle2 className="w-4 h-4" />
              </div>
            </div>
            <div className="mt-space-md flex flex-col">
              <span className="font-display-lg text-display-lg font-bold text-on-surface leading-none">
                {data.submittedCount}
              </span>
              <span className="font-body-sm text-body-sm text-on-surface-variant mt-1">
                {t('common.completed')}
              </span>
            </div>
            <Link 
              href="/student/submissions" 
              className="mt-space-sm pt-space-xs flex items-center gap-1 font-label-xs text-xs text-sky-700 dark:text-sky-300 font-semibold hover:underline"
            >
              <span>{t('dashboard.viewAll')}</span>
              <ArrowRight className="w-3.5 h-3.5" />
            </Link>
          </div>

          {/* Card 3: Graded */}
          <div className="rounded-xl p-space-md bg-surface-container-lowest border border-outline-variant/40 shadow-xs flex flex-col justify-between hover:border-emerald-400/40 transition-all">
            <div className="flex items-center justify-between">
              <span className="font-label-xs uppercase tracking-wider text-outline font-semibold">{t('submissions.statusGraded')}</span>
              <div className="w-8 h-8 rounded-lg bg-emerald-50 dark:bg-emerald-950/60 text-emerald-600 dark:text-emerald-400 flex items-center justify-center">
                <Award className="w-4 h-4" />
              </div>
            </div>
            <div className="mt-space-md flex flex-col">
              <span className="font-display-lg text-display-lg font-bold text-on-surface leading-none">
                {data.gradedCount}
              </span>
              <span className="font-body-sm text-body-sm text-on-surface-variant mt-1">
                {t('scoring.officialResult')}
              </span>
            </div>
            <div className="mt-space-sm pt-space-xs flex items-center gap-1 font-label-xs text-xs text-emerald-700 dark:text-emerald-400 font-medium">
              <CheckCircle2 className="w-3.5 h-3.5" />
              <span>{t('scoring.evaluationSaved')}</span>
            </div>
          </div>

          {/* Card 4: Pending Appeals */}
          <div className="rounded-xl p-space-md bg-surface-container-lowest border border-outline-variant/40 shadow-xs flex flex-col justify-between hover:border-purple-400/40 transition-all">
            <div className="flex items-center justify-between">
              <span className="font-label-xs uppercase tracking-wider text-outline font-semibold">{t('dashboard.activeAppealsCount')}</span>
              <div className="w-8 h-8 rounded-lg bg-purple-50 dark:bg-purple-950/60 text-purple-600 dark:text-purple-400 flex items-center justify-center">
                <Scale className="w-4 h-4" />
              </div>
            </div>
            <div className="mt-space-md flex flex-col">
              <div className="flex items-center gap-2">
                <span className="font-display-lg text-display-lg font-bold text-on-surface leading-none">
                  {data.pendingAppealsCount}
                </span>
                <span className="text-[10px] px-1.5 py-0.5 rounded font-semibold bg-purple-50 dark:bg-purple-950/60 text-purple-700 dark:text-purple-300">
                  {t('appeals.appealStatusPending')}
                </span>
              </div>
              <span className="font-body-sm text-body-sm text-on-surface-variant mt-1">
                {t('common.pending')}
              </span>
            </div>
            <Link 
              href="/student/appeals" 
              className="mt-space-sm pt-space-xs flex items-center gap-1 font-label-xs text-xs text-purple-700 dark:text-purple-300 font-semibold hover:underline"
            >
              <span>{t('dashboard.viewAll')}</span>
              <ArrowRight className="w-3.5 h-3.5" />
            </Link>
          </div>
        </section>

        {/* Upcoming Deadlines */}
        <section className="flex flex-col gap-space-sm">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-space-xs">
              <Calendar className="w-4 h-4 text-primary" />
              <h2 className="font-headline-sm text-headline-sm text-on-surface tracking-tight">{t('dashboard.upcomingExams')}</h2>
            </div>
            <span className="font-label-xs text-xs text-outline">{t('header.termBadge')}</span>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-space-md">
            {data.upcomingDeadlines && data.upcomingDeadlines.length > 0 ? (
              data.upcomingDeadlines.map((exam, idx) => (
                <div key={idx} className="rounded-xl p-space-md bg-surface-container-lowest border border-outline-variant/40 shadow-xs flex flex-col justify-between hover:border-primary/40 transition-all">
                  <div className="flex flex-col gap-1.5">
                    <div className="flex justify-between items-start gap-2">
                      <span className="font-label-md font-semibold text-on-surface line-clamp-1">{exam.title}</span>
                      <span className="px-2 py-0.5 rounded bg-surface-container font-mono text-[10px] font-semibold text-on-surface-variant shrink-0">
                        {exam.courseCode || 'SE-PRACTICAL'}
                      </span>
                    </div>
                    <div className="flex items-center gap-1.5 text-xs text-primary font-medium">
                      <Clock className="w-3.5 h-3.5" />
                      <span>{exam.examDate ? formatDate(exam.examDate) : t('common.active')}</span>
                    </div>
                  </div>
                  <div className="mt-space-md pt-2 border-t border-outline-variant/30 flex justify-end">
                    <Link 
                      href="/student/exams"
                      className="inline-flex items-center gap-1 px-3 py-1 rounded-lg bg-primary text-on-primary text-xs font-medium hover:bg-primary/90 transition-colors"
                    >
                      <span>{t('exams.startExamButton')}</span>
                      <ArrowRight className="w-3 h-3" />
                    </Link>
                  </div>
                </div>
              ))
            ) : (
              <div className="col-span-full rounded-xl p-space-lg bg-surface-container-lowest border border-outline-variant/30 text-outline text-center text-xs">
                {t('dashboard.emptyUpcomingExams')}
              </div>
            )}
          </div>
        </section>

        {/* Recent Submissions & Results Table */}
        <section className="flex flex-col gap-space-sm w-full min-w-0">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-space-xs">
              <FileCode2 className="w-4 h-4 text-primary" />
              <h2 className="font-headline-sm text-headline-sm text-on-surface tracking-tight">{t('dashboard.recentSubmissions')}</h2>
            </div>
            <Link href="/student/submissions" className="text-xs text-primary hover:underline font-medium">
              {t('dashboard.viewAll')}
            </Link>
          </div>

          <div className="rounded-xl bg-surface-container-lowest border border-outline-variant/40 shadow-xs overflow-hidden w-full min-w-0">
            <div className="overflow-x-auto w-full">
              <table className="w-full text-left border-collapse text-xs">
                <thead>
                  <tr className="bg-surface-container-low text-on-surface-variant font-label-xs uppercase tracking-wider border-b border-outline-variant/30">
                    <th className="py-2.5 px-4 font-semibold">{t('submissions.studentId')}</th>
                    <th className="py-2.5 px-4 font-semibold">{t('submissions.submittedAt')}</th>
                    <th className="py-2.5 px-4 font-semibold">{t('submissions.score')}</th>
                    <th className="py-2.5 px-4 font-semibold">{t('common.status')}</th>
                    <th className="py-2.5 px-4 font-semibold">{t('dashboard.astColumn')}</th>
                    <th className="py-2.5 px-4 font-semibold text-right">{t('common.actions')}</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-outline-variant/20 text-on-surface">
                  {data.recentSubmissions && data.recentSubmissions.length > 0 ? (
                    data.recentSubmissions.map((sub, idx) => (
                      <tr key={idx} className="hover:bg-surface-container-low/60 transition-colors">
                        <td className="py-3 px-4 font-mono font-medium text-on-surface">
                          #{sub.id ? sub.id.slice(0, 8) : 'SUBMITTED'}
                        </td>
                        <td className="py-3 px-4 text-on-surface-variant">
                          {sub.submittedAt ? formatDate(sub.submittedAt) : 'N/A'}
                        </td>
                        <td className="py-3 px-4">
                          <span className="font-mono bg-surface-container px-2 py-0.5 rounded font-bold text-on-surface">
                            {sub.totalScore ?? '--'}
                          </span>
                          <span className="text-outline text-[11px] ml-0.5">/100</span>
                        </td>
                        <td className="py-3 px-4">
                          <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-[10px] font-semibold ${
                            sub.status === 'GRADED' 
                              ? 'bg-emerald-50 dark:bg-emerald-950/60 text-emerald-700 dark:text-emerald-300 border border-emerald-200 dark:border-emerald-800' 
                              : 'bg-amber-50 dark:bg-amber-950/60 text-amber-700 dark:text-amber-300 border border-amber-200 dark:border-amber-800'
                          }`}>
                            {sub.status === 'GRADED' ? t('submissions.statusGraded') : t('common.pending')}
                          </span>
                        </td>
                        <td className="py-3 px-4">
                          <div className="flex items-center gap-1.5 text-emerald-700 dark:text-emerald-400">
                            <ShieldCheck className="w-3.5 h-3.5" />
                            <span className="font-label-xs font-semibold">{t('header.astEngineReady')}</span>
                          </div>
                        </td>
                        <td className="py-3 px-4 text-right">
                          <Link 
                            className="inline-flex items-center gap-1 px-2.5 py-1 rounded bg-surface-container hover:bg-surface-container-high text-primary font-medium transition-colors" 
                            title={t('common.details')} 
                            href={`/student/submissions/${sub.id}`}
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
                        {t('dashboard.emptySubmissions')}
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          </div>
        </section>

        {/* Technical Specification & Honor Code Section */}
        <section className="grid grid-cols-1 lg:grid-cols-3 gap-space-md">
          {/* Docker Sandbox Specs */}
          <div className="rounded-xl p-space-md bg-surface-container-lowest border border-outline-variant/40 shadow-xs flex flex-col justify-between">
            <div>
              <div className="flex items-center justify-between pb-2 border-b border-outline-variant/20">
                <span className="font-label-xs uppercase tracking-wider text-outline font-semibold">IDE Sandbox Specs</span>
                <span className="text-[10px] px-1.5 py-0.5 rounded font-semibold bg-emerald-50 dark:bg-emerald-950/60 text-emerald-700 dark:text-emerald-300 border border-emerald-200 dark:border-emerald-800">
                  {t('common.systemOnline')}
                </span>
              </div>
              <h4 className="font-headline-sm text-headline-sm text-on-surface mt-2 font-bold flex items-center gap-1.5">
                <Cpu className="w-4 h-4 text-primary" />
                {t('header.dockerPolicyTitle')}
              </h4>
              <div className="mt-space-sm space-y-1.5 text-xs">
                <div className="flex justify-between py-1 px-2.5 rounded bg-surface-container-low">
                  <span className="text-outline">Runtime Engine</span>
                  <span className="text-on-surface font-semibold font-mono">OpenJDK 17 + Clang</span>
                </div>
                <div className="flex justify-between py-1 px-2.5 rounded bg-surface-container-low">
                  <span className="text-outline">Memory Cap</span>
                  <span className="text-on-surface font-semibold font-mono">512MB cgroup v2</span>
                </div>
                <div className="flex justify-between py-1 px-2.5 rounded bg-surface-container-low">
                  <span className="text-outline">Timeout Threshold</span>
                  <span className="text-on-surface font-semibold font-mono">3.00s per Test Case</span>
                </div>
              </div>
            </div>
          </div>

          {/* Honor Code & Security Standard */}
          <div className="lg:col-span-2 rounded-xl p-space-md bg-surface-container-lowest border border-outline-variant/40 shadow-xs flex flex-col justify-between">
            <div className="flex flex-col gap-2">
              <div className="flex items-center justify-between pb-2 border-b border-outline-variant/20">
                <span className="font-label-xs uppercase tracking-wider text-outline font-semibold">
                  {t('header.honorCodeTitle')}
                </span>
                <span className="text-[10px] px-1.5 py-0.5 rounded font-semibold bg-blue-50 dark:bg-blue-950/60 text-blue-700 dark:text-blue-300 border border-blue-200 dark:border-blue-800">
                  {t('common.academicPortal')}
                </span>
              </div>
              <h4 className="font-headline-sm text-headline-sm text-on-surface font-bold flex items-center gap-1.5">
                <ShieldCheck className="w-4 h-4 text-emerald-600 dark:text-emerald-400" />
                {t('header.astPolicyTitle')}
              </h4>
              <p className="text-xs text-on-surface-variant leading-relaxed">
                {t('header.astPolicyDesc')}
              </p>
            </div>

            <div className="pt-space-md mt-2 border-t border-outline-variant/20 flex items-center justify-between text-xs text-outline">
              <span>{t('auth.academicYearFooter')}</span>
              <span className="font-mono text-[11px] text-on-surface-variant">CodeAssess Platform v2.4</span>
            </div>
          </div>
        </section>
      </div>
    </div>
  );
}
