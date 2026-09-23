'use client';

import { useStudentDashboard } from '../hooks/use-dashboard';
import { AppLink as Link } from '@/components/ui/app-link';

export function StudentDashboardView() {
  const { data, isLoading, isError, error } = useStudentDashboard();

  if (isLoading) {
    return (
      <div className="flex h-64 items-center justify-center">
        <div className="text-on-surface-variant font-label-md">Loading dashboard...</div>
      </div>
    );
  }

  if (isError) {
    return (
      <div className="flex flex-col h-64 items-center justify-center p-space-lg text-center gap-space-sm">
        <span className="material-symbols-outlined text-error text-[48px]">error</span>
        <h2 className="font-headline-md text-headline-md text-on-surface">Failed to load dashboard</h2>
        <p className="font-body-md text-on-surface-variant">{(error as Error).message}</p>
      </div>
    );
  }

  if (!data) return null;

  return (
    <div className="max-w-7xl mx-auto px-space-lg py-space-lg">
      <div className="flex flex-col w-full gap-space-lg">
<div className="max-w-7xl mx-auto px-space-lg py-space-lg"><div className="flex flex-col w-full gap-space-xl">

<section className="relative rounded-xl p-space-lg bg-surface-container-lowest shadow-sm overflow-hidden">
<div className="absolute -right-16 -top-16 w-80 h-80 rounded-full bg-primary-fixed/30 blur-3xl pointer-events-none"></div>
<div className="relative z-10 flex flex-col lg:flex-row lg:items-center lg:justify-between gap-space-md">
<div className="flex flex-col gap-space-xs">
<div className="flex flex-wrap items-center gap-space-sm">
<h1 className="font-display-lg text-display-lg text-on-surface tracking-tight">Welcome back, {data.fullName}</h1>
<span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded bg-surface-container text-on-surface-variant font-label-sm text-label-sm font-medium">
<span className="material-symbols-outlined text-primary text-[14px]">badge</span>
            Email: {data.email}
          </span>
</div>
<p className="font-body-md text-body-md text-on-surface-variant max-w-2xl">
          Track your programming assessments, upcoming exam deadlines, and submission results.
        </p>
</div>
<div className="flex items-center gap-space-sm self-start lg:self-center">
</div>
</div>
</section>

<section className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-space-md">

<div className="rounded-xl p-space-md bg-surface-container-lowest shadow-sm flex flex-col justify-between group hover:shadow-md transition-shadow">
<div className="flex items-center justify-between">
<span className="font-label-md text-label-md text-on-surface-variant font-medium">Available Exams</span>
<div className="w-8 h-8 rounded bg-primary-fixed flex items-center justify-center text-on-primary-fixed">
<span className="material-symbols-outlined text-[18px]">terminal</span>
</div>
</div>
<div className="mt-space-md flex flex-col">
<span className="font-display-lg text-display-lg font-bold text-on-surface leading-none">{data.activeExamsCount}</span>
<span className="font-body-sm text-body-sm text-on-surface-variant mt-1">Active assessment windows</span>
</div>
<div className="mt-space-sm pt-space-xs flex items-center gap-1 font-label-sm text-label-sm text-primary">
<span>2 mandatory practicals</span>
<span className="material-symbols-outlined text-[14px]">arrow_right_alt</span>
</div>
</div>

<div className="rounded-xl p-space-md bg-surface-container-lowest shadow-sm flex flex-col justify-between group hover:shadow-md transition-shadow">
<div className="flex items-center justify-between">
<span className="font-label-md text-label-md text-on-surface-variant font-medium">Submitted</span>
<div className="w-8 h-8 rounded bg-secondary-fixed flex items-center justify-center text-on-secondary-fixed">
<span className="material-symbols-outlined text-[18px]">task_alt</span>
</div>
</div>
<div className="mt-space-md flex flex-col">
<span className="font-display-lg text-display-lg font-bold text-on-surface leading-none">{data.submittedCount}</span>
<span className="font-body-sm text-body-sm text-on-surface-variant mt-1">Across all enrolled modules</span>
</div>
<div className="mt-space-sm pt-space-xs flex items-center gap-1 font-label-sm text-label-sm text-secondary">
<span>100% attendance recorded</span>
<span className="material-symbols-outlined text-[14px]">check</span>
</div>
</div>

<div className="rounded-xl p-space-md bg-surface-container-lowest shadow-sm flex flex-col justify-between group hover:shadow-md transition-shadow">
<div className="flex items-center justify-between">
<span className="font-label-md text-label-md text-on-surface-variant font-medium">Graded</span>
<div className="w-8 h-8 rounded bg-tertiary-fixed flex items-center justify-center text-on-tertiary-fixed">
<span className="material-symbols-outlined text-[18px]">verified</span>
</div>
</div>
<div className="mt-space-md flex flex-col">
<div className="flex items-baseline gap-1.5">
<span className="font-display-lg text-display-lg font-bold text-on-surface leading-none">{data.gradedCount}</span>
</div>
<span className="font-body-sm text-body-sm text-on-surface-variant mt-1">Assignments evaluated</span>
</div>
<div className="mt-space-sm pt-space-xs flex items-center gap-1 font-label-sm text-label-sm text-outline">
<span>Integration successful</span>
</div>
</div>

<div className="rounded-xl p-space-md bg-surface-container-lowest shadow-sm flex flex-col justify-between group hover:shadow-md transition-shadow">
<div className="flex items-center justify-between">
<span className="font-label-md text-label-md text-on-surface-variant font-medium">Pending Appeals</span>
<div className="w-8 h-8 rounded bg-error-container flex items-center justify-center text-on-error-container">
<span className="material-symbols-outlined text-[18px]">gavel</span>
</div>
</div>
<div className="mt-space-md flex flex-col">
<div className="flex items-center gap-2">
<span className="font-display-lg text-display-lg font-bold text-on-surface leading-none">{data.pendingAppealsCount}</span>
<span className="font-label-sm text-label-sm px-2 py-0.5 rounded bg-surface-container-highest text-on-surface font-semibold">In Review</span>
</div>
<span className="font-body-sm text-body-sm text-on-surface-variant mt-1 truncate">Appeals pending</span>
</div>
<div className="mt-space-sm pt-space-xs flex items-center gap-1 font-label-sm text-label-sm text-error">
<span>Committee SLA: &lt; 48h</span>
<span className="material-symbols-outlined text-[14px]">schedule</span>
</div>
</div>
</section>

<section className="flex flex-col gap-space-md">
<div className="flex items-center justify-between">
<div className="flex items-center gap-space-sm">
<span className="material-symbols-outlined text-primary text-[22px]">event_upcoming</span>
<h2 className="font-headline-lg text-headline-lg text-on-surface tracking-tight">Upcoming Deadlines</h2>
</div>
<div className="flex items-center gap-2">
<span className="font-label-sm text-label-sm text-outline">Timezone: Asia/Ho_Chi_Minh (UTC+7)</span>
</div>
</div>
<div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-space-md">
  {data.upcomingDeadlines.length > 0 ? data.upcomingDeadlines.map((exam, idx) => (
    <div key={idx} className="rounded-xl p-space-md bg-surface-container-lowest flex flex-col gap-2 border border-outline-variant/30">
      <span className="font-label-md font-semibold text-on-surface truncate">{exam.title}</span>
      <span className="font-label-sm text-primary">Deadline: {exam.examDate ? new Date(exam.examDate).toLocaleString() : 'TBD'}</span>
      <span className="font-label-sm text-on-surface-variant">Course: {exam.courseCode}</span>
    </div>
  )) : (
    <div className="col-span-full rounded-xl p-space-lg bg-surface-container-lowest text-outline text-center">No upcoming deadlines</div>
  )}
</div></section>

<section className="flex flex-col gap-space-md">
<div className="flex flex-col sm:flex-row sm:items-center justify-between gap-space-sm">
<div className="flex items-center gap-space-sm">
<span className="material-symbols-outlined text-primary text-[22px]">history_edu</span>
<h2 className="font-headline-lg text-headline-lg text-on-surface tracking-tight">Recent Submissions &amp; Results</h2>
</div>
</div>

<div className="rounded-xl bg-surface-container-lowest shadow-sm overflow-hidden">
<div className="overflow-x-auto">
<table className="w-full text-left border-collapse">
<thead>
<tr className="bg-surface-container-low font-label-sm text-label-sm text-outline uppercase tracking-wider select-none">
<th className="py-3 px-space-md font-semibold">Exam &amp; Course</th>
<th className="py-3 px-space-md font-semibold">Submitted At</th>
<th className="py-3 px-space-md font-semibold">Score</th>
<th className="py-3 px-space-md font-semibold">Status</th>
<th className="py-3 px-space-md font-semibold">AST Similarity &amp; Integrity</th>
<th className="py-3 px-space-md font-semibold text-right">Actions</th>
</tr>
</thead>
<tbody className="font-body-md text-body-md text-on-surface">
  {data.recentSubmissions && data.recentSubmissions.length > 0 ? data.recentSubmissions.map((sub, idx) => (
    <tr key={idx} className="border-b border-outline-variant/20 hover:bg-surface-container-low transition-colors">
      <td className="py-3 px-space-md font-semibold">{sub.examId}</td>
      <td className="py-3 px-space-md">{sub.submittedAt ? new Date(sub.submittedAt).toLocaleString() : 'N/A'}</td>
      <td className="py-3 px-space-md">
        <span className="font-mono bg-surface-container px-2 py-0.5 rounded font-bold">{sub.totalScore || 0}</span><span className="text-outline-variant text-[13px]">/100</span>
      </td>
      <td className="py-3 px-space-md">
        <span className={`inline-flex items-center px-2 py-0.5 rounded font-label-sm text-label-sm font-semibold ${sub.status === 'GRADED' ? 'bg-tertiary-fixed text-on-tertiary-fixed' : 'bg-surface-container text-on-surface-variant'}`}>{sub.status || 'PENDING'}</span>
      </td>
      <td className="py-3 px-space-md">
        <div className="flex items-center gap-1.5 text-tertiary">
          <span className="material-symbols-outlined text-[16px]">verified</span>
          <span className="font-label-sm text-label-sm font-medium">Passed</span>
        </div>
      </td>
      <td className="py-3 px-space-md text-right">
        <Link className="p-1.5 rounded hover:bg-surface-container text-on-surface-variant hover:text-primary transition-colors inline-flex" title="View Detail" href={`/student/submissions/${sub.id}`}>
          <span className="material-symbols-outlined text-[18px]">visibility</span>
        </Link>
      </td>
    </tr>
  )) : (
    <tr><td colSpan={6} className="py-8 text-center text-outline font-label-md">No recent submissions</td></tr>
  )}
</tbody>
</table>
</div>

</div>
</section>

<section className="grid grid-cols-1 lg:grid-cols-3 gap-space-md">

<div className="rounded-xl p-space-md bg-surface-container-lowest shadow-sm flex flex-col justify-between">
<div>
<div className="flex items-center justify-between">
<span className="font-label-sm text-label-sm uppercase tracking-wider text-outline font-semibold">IDE Sandbox Specs</span>
<span className="font-label-sm text-label-sm px-1.5 py-0.5 rounded bg-tertiary-fixed text-on-tertiary-fixed font-semibold">Online</span>
</div>
<h4 className="font-headline-sm text-headline-sm text-on-surface mt-2 font-bold">Isolated Docker Pod #409</h4>
<div className="mt-space-sm space-y-2 font-label-sm text-label-sm">
<div className="flex justify-between py-1 px-2 rounded bg-surface-container-low">
<span className="text-outline">Runtime Engine</span>
<span className="text-on-surface font-semibold">OpenJDK 17.0.9 + Clang 16</span>
</div>
<div className="flex justify-between py-1 px-2 rounded bg-surface-container-low">
<span className="text-outline">Memory Cap</span>
<span className="text-on-surface font-semibold">512MB cgroup v2</span>
</div>
<div className="flex justify-between py-1 px-2 rounded bg-surface-container-low">
<span className="text-outline">Timeout Threshold</span>
<span className="text-on-surface font-semibold">3.00s per Test Case</span>
      </div>
    </div>
  </div>
</div>

  <div className="lg:col-span-2 rounded-xl p-space-md bg-surface-container-lowest shadow-sm flex flex-col justify-between">
<div className="flex items-center justify-between">
<div className="flex flex-col">
<span className="font-label-sm text-label-sm uppercase tracking-wider text-outline font-semibold">Historical Integrity Record</span>
<h4 className="font-headline-sm text-headline-sm text-on-surface font-bold">AST Cleanliness Index</h4>
</div>
<span className="font-label-sm text-label-sm px-2 py-0.5 rounded bg-tertiary-fixed text-on-tertiary-fixed font-semibold">
          Integration Pending
        </span>
</div>
<div className="my-space-sm grid grid-cols-2 sm:grid-cols-4 gap-space-sm">
<div className="p-space-sm rounded bg-surface-container-low flex flex-col">
<span className="font-label-sm text-label-sm text-outline">Token Divergence</span>
<span className="font-headline-md text-headline-md font-bold text-on-surface mt-1">N/A</span>
<span className="font-label-sm text-label-sm text-tertiary">Missing Backend Contract</span>
</div>
<div className="p-space-sm rounded bg-surface-container-low flex flex-col">
<span className="font-label-sm text-label-sm text-outline">External Matches</span>
<span className="font-headline-md text-headline-md font-bold text-on-surface mt-1">N/A</span>
<span className="font-label-sm text-label-sm text-outline">Missing Backend Contract</span>
</div>
<div className="p-space-sm rounded bg-surface-container-low flex flex-col">
<span className="font-label-sm text-label-sm text-outline">Keystroke Cadence</span>
<span className="font-headline-md text-headline-md font-bold text-on-surface mt-1">N/A</span>
<span className="font-label-sm text-label-sm text-tertiary">Missing Backend Contract</span>
</div>
<div className="p-space-sm rounded bg-surface-container-low flex flex-col">
<span className="font-label-sm text-label-sm text-outline">Peer Overlap</span>
<span className="font-headline-md text-headline-md font-bold text-on-surface mt-1">N/A</span>
<span className="font-label-sm text-label-sm text-tertiary">Missing Backend Contract</span>
</div>
</div>
<p className="font-body-sm text-body-sm text-on-surface-variant">
        Your submissions comply with the Dean&apos;s Honor Code for the Department of Software Engineering.
      </p>
</div>
</section>

<section className="rounded-xl p-space-md bg-surface-container-low shadow-sm flex flex-col sm:flex-row items-start sm:items-center gap-space-md">
<div className="w-12 h-12 rounded-xl bg-primary-container text-on-primary flex items-center justify-center shrink-0 shadow-sm">
<span className="material-symbols-outlined text-[26px]">security</span>
</div>
<div className="flex-1 flex flex-col gap-0.5">
<div className="flex items-center gap-2">
<h4 className="font-headline-sm text-headline-sm font-bold text-on-surface leading-snug">
          CodeAssess Academic Integrity Standard
        </h4>
<span className="font-label-sm text-label-sm px-1.5 py-0.5 rounded bg-surface-container text-outline font-semibold">
          ISO/IEC 27001
        </span>
</div>
<p className="font-body-sm text-body-sm text-on-surface-variant">
        Submissions undergo AST (Abstract Syntax Tree) tokenization and static bytecode comparison. Plagiarism or unauthorized AI code generation results in automatic review.
      </p>
    </div>
</section>
</div>
</div>
      </div>
    </div>
  );
}
