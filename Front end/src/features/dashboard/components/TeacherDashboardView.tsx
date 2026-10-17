'use client';

import React, { useMemo } from 'react';
import { useRouter } from 'next/navigation';
import { AppLink as Link } from '@/components/ui/app-link';
import { useTeacherExams, useTeacherSubmissions } from '@/features/exams/hooks/use-teacher-exams';
import { usePlagiarismList } from '@/features/plagiarism/hooks';
import { useTeacherAppeals } from '@/features/appeals/hooks';
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
  AlertCircle, 
  ExternalLink,
  Calendar,
  Sparkles,
  BookOpen
} from 'lucide-react';
import { toast } from 'sonner';

export function TeacherDashboardView() {
  const router = useRouter();

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
      toast.error('No exam data available to export.');
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

    toast.success('Faculty report exported successfully');
  };

  return (
    <div className="w-full max-w-7xl mx-auto px-4 sm:px-space-lg py-space-lg min-w-0">
      <div className="flex flex-col w-full gap-space-lg min-w-0">
        {/* Header Title & Actions */}
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-space-md">
          <div className="flex flex-col">
            <div className="flex items-center gap-space-xs text-xs">
              <span className="font-label-sm text-primary font-semibold uppercase tracking-wider">Faculty Workspace</span>
              <span className="text-outline-variant font-label-sm">/</span>
              <span className="font-label-sm text-outline">Terminal View</span>
            </div>
            <h1 className="font-display-lg text-display-lg text-on-surface tracking-tight mt-1">Faculty Dashboard</h1>
            <p className="font-body-md text-body-md text-on-surface-variant">
              Live telemetry on your courses, submissions, AST plagiarism detection, and student appeals.
            </p>
          </div>

          <div className="flex flex-wrap items-center gap-space-sm">
            <div className="flex items-center gap-space-xs px-3 py-1.5 bg-surface-container rounded-lg border border-outline-variant/30 text-on-surface text-xs font-medium">
              <Calendar className="w-3.5 h-3.5 text-primary" />
              <span>Fall 2026 Term</span>
            </div>

            <button 
              onClick={handleExportReports}
              className="flex items-center gap-1.5 px-3.5 py-1.5 bg-surface-container-high hover:bg-surface-container-highest text-on-surface rounded-lg font-label-md text-xs transition-colors border border-outline-variant/40 cursor-pointer shadow-xs"
            >
              <Download className="w-3.5 h-3.5 text-on-surface-variant" />
              <span>Export CSV</span>
            </button>

            <Link 
              href="/teacher/exams?action=create"
              className="flex items-center gap-1.5 px-4 py-1.5 bg-primary hover:bg-primary/90 text-on-primary rounded-lg font-label-md text-xs transition-all shadow-sm cursor-pointer font-medium"
            >
              <PlusCircle className="w-3.5 h-3.5" />
              <span>+ Create New Exam</span>
            </Link>
          </div>
        </div>

        {/* 5 KPI Metric Cards */}
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-space-md">
          {/* Card 1: Total Exams */}
          <div className="flex flex-col justify-between p-space-md bg-surface-container-lowest rounded-xl border border-outline-variant/40 shadow-xs hover:border-primary/40 transition-all">
            <div className="flex items-center justify-between text-on-surface-variant">
              <span className="font-label-xs uppercase tracking-wider text-outline font-semibold">Total Exams</span>
              <div className="p-1.5 rounded-lg bg-blue-50 text-blue-600">
                <FileCode2 className="w-4 h-4" />
              </div>
            </div>
            <div className="my-space-xs flex items-baseline gap-2">
              <span className="font-display-lg text-display-lg text-on-surface font-bold">
                {examsLoading ? '...' : totalExams}
              </span>
              <span className="font-label-xs text-xs text-emerald-600 font-semibold flex items-center gap-0.5">
                <CheckCircle2 className="w-3 h-3" /> Live
              </span>
            </div>
            <div className="pt-space-xs text-on-surface-variant font-label-xs text-xs flex items-center gap-1.5 truncate">
              <span className="h-1.5 w-1.5 rounded-full bg-blue-500"></span>
              <span className="truncate">Active course evaluations</span>
            </div>
          </div>

          {/* Card 2: Total Submissions */}
          <div className="flex flex-col justify-between p-space-md bg-surface-container-lowest rounded-xl border border-outline-variant/40 shadow-xs hover:border-secondary/40 transition-all">
            <div className="flex items-center justify-between text-on-surface-variant">
              <span className="font-label-xs uppercase tracking-wider text-outline font-semibold">Submissions</span>
              <div className="p-1.5 rounded-lg bg-sky-50 text-sky-600">
                <Code2 className="w-4 h-4" />
              </div>
            </div>
            <div className="my-space-xs flex items-baseline gap-2">
              <span className="font-display-lg text-display-lg text-on-surface font-bold">
                {submissionsLoading ? '...' : totalSubmissions}
              </span>
              <span className="font-label-xs text-xs text-sky-700 font-medium">Recorded</span>
            </div>
            <div className="pt-space-xs text-on-surface-variant font-label-xs text-xs flex items-center gap-1.5 truncate">
              <span className="h-1.5 w-1.5 rounded-full bg-sky-500"></span>
              <span className="truncate">Student code artifacts</span>
            </div>
          </div>

          {/* Card 3: Pending Grading */}
          <div className="flex flex-col justify-between p-space-md bg-surface-container-lowest rounded-xl border border-outline-variant/40 shadow-xs hover:border-amber-400/40 transition-all">
            <div className="flex items-center justify-between text-on-surface-variant">
              <span className="font-label-xs uppercase tracking-wider text-outline font-semibold">Pending Grading</span>
              <div className="p-1.5 rounded-lg bg-amber-50 text-amber-600">
                <Clock className="w-4 h-4" />
              </div>
            </div>
            <div className="my-space-xs flex items-baseline gap-2">
              <span className="font-display-lg text-display-lg text-on-surface font-bold">
                {submissionsLoading ? '...' : pendingGradingCount}
              </span>
              <span className="font-label-xs text-xs text-amber-700 font-semibold px-1.5 py-0.5 rounded bg-amber-50">
                Action Needed
              </span>
            </div>
            <div className="pt-space-xs text-on-surface-variant font-label-xs text-xs flex items-center gap-1.5 truncate">
              <span className="h-1.5 w-1.5 rounded-full bg-amber-500"></span>
              <span className="truncate">Awaiting score submission</span>
            </div>
          </div>

          {/* Card 4: Flagged AST Plagiarism */}
          <div className="flex flex-col justify-between p-space-md bg-surface-container-lowest rounded-xl border border-outline-variant/40 shadow-xs hover:border-rose-400/40 transition-all">
            <div className="flex items-center justify-between">
              <span className="font-label-xs uppercase tracking-wider text-outline font-semibold">Integrity Flags</span>
              <div className="p-1.5 rounded-lg bg-rose-50 text-rose-600">
                <ShieldAlert className="w-4 h-4" />
              </div>
            </div>
            <div className="my-space-xs flex items-baseline gap-2">
              <span className={`font-display-lg text-display-lg font-bold ${flaggedCount > 0 ? 'text-rose-600' : 'text-on-surface'}`}>
                {plagiarismLoading ? '...' : flaggedCount}
              </span>
              <span className="font-label-xs text-xs text-rose-700 font-semibold px-1.5 py-0.5 rounded bg-rose-50">
                AST Alerts
              </span>
            </div>
            <div className="pt-space-xs text-on-surface-variant font-label-xs text-xs flex items-center gap-1.5 truncate">
              <span className="h-1.5 w-1.5 rounded-full bg-rose-500"></span>
              <span className="truncate">Similarity threshold ≥ 30%</span>
            </div>
          </div>

          {/* Card 5: Pending Appeals */}
          <div className="flex flex-col justify-between p-space-md bg-surface-container-lowest rounded-xl border border-outline-variant/40 shadow-xs hover:border-purple-400/40 transition-all">
            <div className="flex items-center justify-between">
              <span className="font-label-xs uppercase tracking-wider text-outline font-semibold">Pending Appeals</span>
              <div className="p-1.5 rounded-lg bg-purple-50 text-purple-600">
                <Scale className="w-4 h-4" />
              </div>
            </div>
            <div className="my-space-xs flex items-baseline gap-2">
              <span className="font-display-lg text-display-lg text-on-surface font-bold">
                {appealsLoading ? '...' : pendingAppealsCount}
              </span>
              <span className="font-label-xs text-xs text-purple-700 font-semibold px-1.5 py-0.5 rounded bg-purple-50">
                Under Review
              </span>
            </div>
            <div className="pt-space-xs text-on-surface-variant font-label-xs text-xs flex items-center gap-1.5 truncate">
              <span className="h-1.5 w-1.5 rounded-full bg-purple-500"></span>
              <span className="truncate">Student dispute requests</span>
            </div>
          </div>
        </div>

        {/* Workstation Quick Shortcuts */}
        <div className="flex flex-wrap items-center justify-between p-space-sm bg-surface-container-low rounded-xl border border-outline-variant/30 gap-space-sm">
          <div className="flex items-center gap-space-xs">
            <Sparkles className="w-4 h-4 text-primary ml-2" />
            <span className="font-label-xs text-outline uppercase font-bold tracking-wider">Quick Navigation:</span>
          </div>
          <div className="flex flex-wrap items-center gap-space-xs">
            <Link 
              className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-surface-container-lowest hover:bg-surface-container text-on-surface font-label-md text-xs transition-colors border border-outline-variant/40 shadow-xs" 
              href="/teacher/exams"
            >
              <BookOpen className="w-3.5 h-3.5 text-primary" />
              <span>Exam Management</span>
            </Link>
            <Link 
              className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-surface-container-lowest hover:bg-surface-container text-on-surface font-label-md text-xs transition-colors border border-outline-variant/40 shadow-xs" 
              href="/teacher/submissions"
            >
              <Code2 className="w-3.5 h-3.5 text-secondary" />
              <span>Submissions &amp; Grading</span>
            </Link>
            <Link 
              className="flex items-center gap-1.5 px-3.5 py-1.5 rounded-lg bg-rose-50 hover:bg-rose-100 text-rose-800 font-label-md text-xs transition-colors border border-rose-200/60 shadow-xs font-medium" 
              href="/teacher/plagiarism"
            >
              <ShieldAlert className="w-3.5 h-3.5 text-rose-600" />
              <span>AST Plagiarism Hub</span>
            </Link>
            <Link 
              className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-purple-50 hover:bg-purple-100 text-purple-800 font-label-md text-xs transition-colors border border-purple-200/60 shadow-xs font-medium" 
              href="/teacher/appeals"
            >
              <Scale className="w-3.5 h-3.5 text-purple-600" />
              <span>Review Appeals</span>
            </Link>
          </div>
        </div>

        {/* Live Recent Exams Table */}
        <div className="flex flex-col bg-surface-container-lowest rounded-xl border border-outline-variant/40 shadow-xs overflow-hidden w-full min-w-0">
          <div className="p-space-md flex flex-col sm:flex-row sm:items-center justify-between gap-space-md bg-surface-container-lowest border-b border-outline-variant/30">
            <div className="flex items-center gap-space-sm">
              <div className="p-1.5 rounded-lg bg-blue-50 text-blue-600">
                <FileCode2 className="w-4 h-4" />
              </div>
              <div>
                <h2 className="font-headline-sm text-headline-sm text-on-surface">Recent Active Exams</h2>
                <span className="font-label-xs text-on-surface-variant">Your course evaluation modules</span>
              </div>
            </div>
            <Link 
              href="/teacher/exams" 
              className="text-xs font-medium text-primary hover:underline flex items-center gap-1 self-start sm:self-center"
            >
              <span>View all {totalExams} exams</span>
              <ArrowRight className="w-3.5 h-3.5" />
            </Link>
          </div>

          <div className="w-full overflow-x-auto">
            <table className="w-full text-left font-body-md text-body-md border-collapse">
              <thead className="bg-surface-container-low text-on-surface-variant font-label-xs uppercase tracking-wider border-b border-outline-variant/30">
                <tr>
                  <th className="py-2.5 px-4 font-semibold">Exam Title</th>
                  <th className="py-2.5 px-4 font-semibold">Course Code</th>
                  <th className="py-2.5 px-4 font-semibold">Scheduled Date</th>
                  <th className="py-2.5 px-4 font-semibold">Duration</th>
                  <th className="py-2.5 px-4 font-semibold">Status</th>
                  <th className="py-2.5 px-4 text-right font-semibold">Action</th>
                </tr>
              </thead>
              <tbody className="text-on-surface divide-y divide-outline-variant/20 text-xs">
                {examsLoading ? (
                  <tr>
                    <td colSpan={6} className="py-8 text-center text-outline">Loading exams...</td>
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
                        {exam.examDate ? new Date(exam.examDate).toLocaleString() : 'Open Schedule'}
                      </td>
                      <td className="py-3 px-4 text-on-surface-variant">
                        {exam.duration || 90} mins
                      </td>
                      <td className="py-3 px-4">
                        <span className="inline-flex items-center px-2 py-0.5 rounded-full text-[10px] font-semibold bg-emerald-50 text-emerald-700 border border-emerald-200">
                          Active
                        </span>
                      </td>
                      <td className="py-3 px-4 text-right">
                        <Link 
                          href={`/teacher/exams/${exam.id}`}
                          className="inline-flex items-center gap-1 px-2.5 py-1 rounded bg-surface-container hover:bg-surface-container-high text-primary font-medium transition-colors"
                        >
                          <span>Manage</span>
                          <ExternalLink className="w-3 h-3" />
                        </Link>
                      </td>
                    </tr>
                  ))
                ) : (
                  <tr>
                    <td colSpan={6} className="py-8 text-center text-outline font-label-md">
                      No exams configured yet. Click &quot;+ Create New Exam&quot; to begin.
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
                <h3 className="font-headline-sm text-headline-sm text-on-surface">Recent Student Submissions</h3>
              </div>
              <Link href="/teacher/submissions" className="text-xs text-primary hover:underline font-medium">
                View all
              </Link>
            </div>

            <div className="flex flex-col gap-2">
              {submissionsLoading ? (
                <div className="py-8 text-center text-outline text-xs">Loading activity stream...</div>
              ) : submissions && submissions.length > 0 ? (
                submissions.slice(0, 4).map((sub) => (
                  <div key={sub.id} className="flex items-center justify-between p-2.5 rounded-lg bg-surface-container-low border border-outline-variant/30 text-xs hover:border-primary/30 transition-all">
                    <div className="flex flex-col">
                      <span className="font-semibold text-on-surface truncate max-w-[220px]">
                        Submission #{sub.id.slice(0, 8)}
                      </span>
                      <span className="text-[11px] text-on-surface-variant">
                        {sub.submittedAt ? new Date(sub.submittedAt).toLocaleString() : 'Just now'}
                      </span>
                    </div>

                    <div className="flex items-center gap-2">
                      <span className={`px-2 py-0.5 rounded font-mono text-[11px] font-semibold ${
                        sub.status === 'GRADED' 
                          ? 'bg-emerald-100 text-emerald-800' 
                          : 'bg-amber-100 text-amber-800'
                      }`}>
                        {sub.status || 'SUBMITTED'}
                      </span>
                      <Link 
                        href={`/teacher/submissions/${sub.id}`}
                        className="px-2 py-1 rounded bg-surface-container hover:bg-surface-container-high text-primary font-medium transition-colors"
                      >
                        Grade
                      </Link>
                    </div>
                  </div>
                ))
              ) : (
                <div className="py-8 text-center text-outline text-xs">
                  No submissions received yet for active exams.
                </div>
              )}
            </div>
          </div>

          {/* AST Integrity Forensics Box */}
          <div className="lg:col-span-5 flex flex-col bg-surface-container-lowest rounded-xl border border-outline-variant/40 p-space-md shadow-xs justify-between">
            <div className="flex flex-col gap-space-sm">
              <div className="flex items-center justify-between pb-2 border-b border-outline-variant/30">
                <div className="flex items-center gap-space-xs text-rose-600">
                  <ShieldAlert className="w-4 h-4" />
                  <span className="font-headline-sm text-headline-sm font-semibold">AST Integrity Forensics</span>
                </div>
                <span className="text-[10px] px-2 py-0.5 rounded-full bg-rose-50 text-rose-700 font-semibold border border-rose-200">
                  Automated Engine
                </span>
              </div>

              <div className="p-space-md rounded-xl bg-surface-container-low border border-outline-variant/30 flex flex-col gap-2 text-xs">
                <div className="flex justify-between items-center">
                  <span className="text-on-surface-variant font-medium">Flagged Pairs:</span>
                  <span className="font-bold text-rose-600">{flaggedCount} pairs</span>
                </div>
                <div className="flex justify-between items-center">
                  <span className="text-on-surface-variant font-medium">AST Algorithm:</span>
                  <span className="font-mono text-on-surface text-[11px]">Tree-Edit Distance + Tokenizer</span>
                </div>
                <div className="flex justify-between items-center">
                  <span className="text-on-surface-variant font-medium">Status:</span>
                  <span className="text-emerald-700 font-semibold flex items-center gap-1">
                    <CheckCircle2 className="w-3.5 h-3.5" /> Synchronized
                  </span>
                </div>
              </div>
            </div>

            <div className="pt-space-md flex items-center justify-between gap-space-sm border-t border-outline-variant/30 mt-space-md">
              <span className="font-label-xs text-outline">Real-time code comparison</span>
              <Link 
                className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-rose-600 hover:bg-rose-700 text-white font-label-md text-xs transition-colors shadow-sm font-medium" 
                href="/teacher/plagiarism"
              >
                <span>Open AST Tool</span>
                <ArrowRight className="w-3.5 h-3.5" />
              </Link>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
