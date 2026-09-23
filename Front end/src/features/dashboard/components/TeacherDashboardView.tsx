'use client';

import { AppLink as Link } from '@/components/ui/app-link';
export function TeacherDashboardView() {
  return (
    <div className="max-w-7xl mx-auto px-space-lg py-space-lg">
      <div className="flex flex-col w-full gap-space-lg">


<div className="flex flex-col md:flex-row md:items-center justify-between gap-space-md">
<div className="flex flex-col">
<div className="flex items-center gap-space-xs">
<span className="font-label-sm text-label-sm uppercase tracking-widest text-primary font-semibold">Faculty Workspace</span>
<span className="text-outline-variant font-label-sm text-label-sm">/</span>
<span className="font-label-sm text-label-sm text-outline">Terminal View</span>
</div>
<h1 className="font-display-lg text-display-lg text-on-surface tracking-tight">Dashboard</h1>
<p className="font-body-md text-body-md text-on-surface-variant">Overview of your exams, submissions, grading, and academic integrity reviews.</p>
</div>
<div className="flex flex-wrap items-center gap-space-sm">
<div className="flex items-center gap-space-xs px-3 py-2 bg-surface-container rounded shadow-sm text-on-surface">
<span className="material-symbols-outlined text-[18px] text-primary">calendar_today</span>
<span className="font-label-md text-label-md font-semibold">Academic Term: Fall 2026</span>
<span className="material-symbols-outlined text-[16px] text-outline ml-1">unfold_more</span>
</div>
<button className="flex items-center gap-1.5 px-3.5 py-2 bg-surface-container-high hover:bg-surface-container-highest text-on-surface rounded font-label-md text-label-md transition-colors shadow-sm">
<span className="material-symbols-outlined text-[18px] text-on-surface-variant">file_download</span>
<span>Export Reports</span>
</button>
<button className="flex items-center gap-1.5 px-4 py-2 bg-primary-container hover:bg-primary text-on-primary rounded font-label-md text-label-md transition-all shadow-md">
<span className="material-symbols-outlined text-[18px]">add_circle</span>
<span className="font-medium">+ Create New Exam</span>
</button>
</div>
</div>

<div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-space-md">

<div className="flex flex-col justify-between p-space-md bg-surface-container-lowest rounded-xl shadow-sm hover:shadow-md transition-shadow">
<div className="flex items-center justify-between text-on-surface-variant">
<span className="font-label-sm text-label-sm font-semibold tracking-wider uppercase text-outline">Total Exams</span>
<div className="p-1.5 rounded-lg bg-surface-container text-primary">
<span className="material-symbols-outlined text-[18px]">assignment</span>
</div>
</div>
<div className="my-space-xs flex items-baseline gap-2">
<span className="font-display-lg text-display-lg text-on-surface font-bold">N/A</span>
<span className="font-label-sm text-label-sm text-tertiary-container font-semibold flex items-center">
<span className="material-symbols-outlined text-[14px]">trending_up</span> Integration Pending
        </span>
</div>
<div className="pt-space-xs text-on-surface-variant font-label-sm text-label-sm flex items-center gap-1.5 truncate">
<span className="h-1.5 w-1.5 rounded-full bg-primary-container"></span>
<span className="truncate">Cohorts: SE18A, SE18B, CS301</span>
</div>
</div>

<div className="flex flex-col justify-between p-space-md bg-surface-container-lowest rounded-xl shadow-sm hover:shadow-md transition-shadow">
<div className="flex items-center justify-between text-on-surface-variant">
<span className="font-label-sm text-label-sm font-semibold tracking-wider uppercase text-outline">Submissions</span>
<div className="p-1.5 rounded-lg bg-surface-container text-secondary">
<span className="material-symbols-outlined text-[18px]">code</span>
</div>
</div>
<div className="my-space-xs flex items-baseline gap-2">
<span className="font-display-lg text-display-lg text-on-surface font-bold">N/A</span>
<span className="font-label-sm text-label-sm text-tertiary-container font-medium">Integration Pending</span>
</div>
<div className="pt-space-xs text-on-surface-variant font-label-sm text-label-sm flex items-center gap-1.5 truncate">
<span className="h-1.5 w-1.5 rounded-full bg-secondary"></span>
<span className="truncate">Across 4 active cohorts</span>
</div>
</div>

<div className="flex flex-col justify-between p-space-md bg-surface-container-lowest rounded-xl shadow-sm hover:shadow-md transition-shadow relative overflow-hidden">
<div className="flex items-center justify-between text-on-surface-variant">
<span className="font-label-sm text-label-sm font-semibold tracking-wider uppercase text-outline">Pending Grading</span>
<span className="px-2 py-0.5 rounded font-label-sm text-label-sm bg-surface-container-high text-on-surface-variant font-semibold">Action Needed</span>
</div>
<div className="my-space-xs flex items-baseline gap-2">
<span className="font-display-lg text-display-lg text-on-surface font-bold">N/A</span>
<span className="font-label-sm text-label-sm text-outline">tasks</span>
</div>
<div className="pt-space-xs text-on-surface-variant font-label-sm text-label-sm flex items-center gap-1.5 truncate">
<span className="h-1.5 w-1.5 rounded-full bg-amber-500"></span>
<span className="truncate">Integration Pending</span>
</div>
</div>

<div className="flex flex-col justify-between p-space-md bg-surface-container-lowest rounded-xl shadow-sm hover:shadow-md transition-shadow">
<div className="flex items-center justify-between">
<span className="font-label-sm text-label-sm font-semibold tracking-wider uppercase text-outline">Flagged Submissions</span>
<span className="px-2 py-0.5 rounded font-label-sm text-label-sm bg-error-container text-on-error-container font-semibold">Critical Review</span>
</div>
<div className="my-space-xs flex items-baseline gap-2">
<span className="font-display-lg text-display-lg text-error font-bold">N/A</span>
<span className="font-label-sm text-label-sm text-error font-medium">AST alerts</span>
</div>
<div className="pt-space-xs text-on-surface-variant font-label-sm text-label-sm flex items-center gap-1.5 truncate">
<span className="h-1.5 w-1.5 rounded-full bg-error"></span>
<span className="font-mono text-error font-semibold">Integration Pending</span>
</div>
</div>

<div className="flex flex-col justify-between p-space-md bg-surface-container-lowest rounded-xl shadow-sm hover:shadow-md transition-shadow">
<div className="flex items-center justify-between">
<span className="font-label-sm text-label-sm font-semibold tracking-wider uppercase text-outline">Pending Appeals</span>
<span className="px-2 py-0.5 rounded font-label-sm text-label-sm bg-surface-variant text-on-surface-variant font-semibold">5 New</span>
</div>
<div className="my-space-xs flex items-baseline gap-2">
<span className="font-display-lg text-display-lg text-on-surface font-bold">N/A</span>
<span className="font-label-sm text-label-sm text-outline">active</span>
</div>
<div className="pt-space-xs text-on-surface-variant font-label-sm text-label-sm flex items-center gap-1.5 truncate">
<span className="h-1.5 w-1.5 rounded-full bg-primary"></span>
<span className="truncate">Integration Pending</span>
</div>
</div>
</div>

<div className="flex flex-wrap items-center justify-between p-space-sm bg-surface-container-low rounded-xl shadow-sm gap-space-sm">
<div className="flex items-center gap-space-xs">
<span className="font-label-sm text-label-sm font-bold text-outline uppercase px-2">Workstation Shortcuts:</span>
</div>
<div className="flex flex-wrap items-center gap-space-xs">
<Link className="flex items-center gap-2 px-3 py-1.5 rounded-lg bg-surface-container-lowest hover:bg-surface-container-high text-on-surface font-label-md text-label-md transition-colors shadow-sm" href="/teacher/exams">
<span className="material-symbols-outlined text-[18px] text-primary">menu_book</span>
<span>View Exams</span>
</Link>
<Link className="flex items-center gap-2 px-3.5 py-1.5 rounded-lg bg-error-container hover:bg-red-200 text-on-error-container font-label-md text-label-md transition-colors shadow-sm" href="/teacher/plagiarism">
<span className="material-symbols-outlined text-[18px]">difference</span>
<span className="font-semibold">Review Plagiarism (AST Engine)</span>
</Link>
</div>
</div>

<div className="flex flex-col bg-surface-container-lowest rounded-xl shadow-sm overflow-hidden">

<div className="p-space-md flex flex-col sm:flex-row sm:items-center justify-between gap-space-md bg-surface-container-lowest">
<div className="flex items-center gap-space-sm">
<span className="material-symbols-outlined text-primary text-[22px]">view_list</span>
<div>
<h2 className="font-headline-sm text-headline-sm text-on-surface">Recent Exams</h2>
<span className="font-label-sm text-label-sm text-on-surface-variant">Active modules in the current evaluation window</span>
</div>
</div>
</div>

<div className="w-full overflow-x-auto">
<table className="w-full text-left font-body-md text-body-md border-collapse">
<thead className="bg-surface-container-low text-on-surface-variant font-label-sm text-label-sm uppercase tracking-wider">
<tr>
<th className="py-3 px-4 font-semibold">Exam Name &amp; Code</th>
<th className="py-3 px-4 font-semibold">Cohort</th>
<th className="py-3 px-4 font-semibold">Deadline</th>
<th className="py-3 px-4 font-semibold">Submissions</th>
<th className="py-3 px-4 font-semibold">Evaluation Status</th>
<th className="py-3 px-4 font-semibold">Integrity / AST</th>
<th className="py-3 px-4 font-semibold">State</th>
<th className="py-3 px-4 text-right font-semibold">Action</th>
</tr>
</thead>
<tbody className="text-on-surface"><tr><td colSpan={8} className="py-8 text-center text-outline font-label-md">Integration Pending</td></tr></tbody>
</table>
</div>

<div className="px-space-md py-3 flex items-center justify-between bg-surface-container-low font-label-sm text-label-sm text-on-surface-variant">
<span>Integration Pending</span>
</div>
</div>

<div className="grid grid-cols-1 lg:grid-cols-12 gap-space-lg">

<div className="lg:col-span-7 flex flex-col bg-surface-container-lowest rounded-xl p-space-md shadow-sm">
<div className="flex items-center justify-between mb-space-md">
<div className="flex items-center gap-space-xs">
<span className="material-symbols-outlined text-primary text-[20px]">history</span>
<h3 className="font-headline-sm text-headline-sm text-on-surface">Recent Assessment Activity</h3>
</div>
<span className="font-label-sm text-label-sm text-outline">Real-time Stream</span>
</div>
<div className="flex flex-col gap-space-sm"><div className="py-8 text-center text-outline font-label-md">Integration Pending</div></div></div>

<div className="lg:col-span-5 flex flex-col bg-surface-container-lowest rounded-xl p-space-md shadow-sm justify-between">
<div className="flex flex-col gap-space-sm">
<div className="flex items-center justify-between">
<div className="flex items-center gap-space-xs text-error">
<span className="material-symbols-outlined text-[22px]">policy</span>
<span className="font-headline-sm text-headline-sm font-semibold">Integrity Forensics</span>
</div>
<span className="font-label-sm text-label-sm px-2 py-0.5 rounded bg-error-container text-on-error-container font-semibold">High Priority</span>
</div>

<div className="p-space-md rounded-xl bg-surface-container flex items-center justify-center min-h-[160px] text-outline font-label-md">Integration Pending</div></div>
</div>

<div className="pt-space-md flex items-center justify-between gap-space-sm">
<span className="font-label-sm text-label-sm text-outline">Evidence packet generated</span>
<Link className="inline-flex items-center gap-2 px-4 py-2 rounded-lg bg-error hover:bg-red-700 text-on-error font-label-md text-label-md transition-colors shadow-md opacity-50" href="/teacher/plagiarism">
<span>Open Code Comparison Diff Tool</span>
<span className="material-symbols-outlined text-[16px]">arrow_forward</span>
</Link>
</div></div></div></div>
  );
}
