'use client';

import React, { useState } from 'react';
import { PreviewRoleSelector, PreviewRole } from './PreviewRoleSelector';
import { PreviewRouteList } from './PreviewRouteList';

// Admin Views
import { AdminDashboardView } from '@/features/admin/components/AdminDashboardView';
import { AdminUserListView } from '@/features/admin/components/AdminUserListView';
import { AdminUserDetailView } from '@/features/admin/components/AdminUserDetailView';
import { AdminClassListView } from '@/features/admin/components/AdminClassListView';
import { AdminClassDetailView } from '@/features/admin/components/AdminClassDetailView';
import { AdminExamListView } from '@/features/admin/components/AdminExamListView';
import { AdminExamDetailView } from '@/features/admin/components/AdminExamDetailView';

// Student Views (Appeals & Plagiarism)
import { StudentAppealsListView } from '@/features/appeals/components/StudentAppealsListView';
import { StudentAppealCreateView } from '@/features/appeals/components/StudentAppealCreateView';
import { StudentAppealDetailView } from '@/features/appeals/components/StudentAppealDetailView';
import { StudentPlagiarismDetailView } from '@/features/plagiarism/components/StudentPlagiarismDetailView';
import { StudentResultView } from '@/features/scoring/components/student-result-view';

// Teacher Views
import { TeacherScoringView } from '@/features/scoring/components/teacher-scoring-view';
import { TeacherPlagiarismDashboardView } from '@/features/plagiarism/components/TeacherPlagiarismDashboardView';
import { TeacherPlagiarismDetailView } from '@/features/plagiarism/components/TeacherPlagiarismDetailView';
import { TeacherPlagiarismCompareView } from '@/features/plagiarism/components/TeacherPlagiarismCompareView';

import { StudentDashboardView } from '@/features/dashboard/components/StudentDashboardView';
import { TeacherDashboardView } from '@/features/dashboard/components/TeacherDashboardView';
import { StudentExamsView } from '@/features/exams/components/StudentExamsView';
import { TeacherExamsView } from '@/features/exams/components/TeacherExamsView';
import { TeacherExamDetailView } from '@/features/exams/components/TeacherExamDetailView';
import { StudentSubmissionsView } from '@/features/submissions/components/StudentSubmissionsView';
import { PreviewErrorBoundary } from './PreviewErrorBoundary';
import { PreviewNavigationContext } from '../context/PreviewNavigationContext';

export function DevPreviewView() {
  const [selectedRole, setSelectedRole] = useState<PreviewRole>('Student');
  const [activePreviewPath, setActivePreviewPath] = useState<string | null>(null);

  const handleSelectRoute = (path: string) => {
    setActivePreviewPath(path);
  };

  const handleBack = () => {
    setActivePreviewPath(null);
  };

  const renderActiveComponent = () => {
    switch (activePreviewPath) {
      // Admin
      case '/admin/dashboard': return <AdminDashboardView />;
      case '/admin/users': return <AdminUserListView />;
      case '/admin/users/user_preview_001': return <AdminUserDetailView userId="user_preview_001" />;
      case '/admin/classes': return <AdminClassListView />;
      case '/admin/classes/class_preview_001': return <AdminClassDetailView classId="class_preview_001" />;
      case '/admin/exams': return <AdminExamListView />;
      case '/admin/exams/exam_preview_001': return <AdminExamDetailView examId="exam_preview_001" />;

      // Student
      case '/student/dashboard': return <StudentDashboardView />;
      case '/student/exams': return <StudentExamsView />;
      case '/student/submissions': return <StudentSubmissionsView submissionId="sub_preview_001" />;
      case '/student/submissions/sub_preview_001': return <StudentResultView submissionId="sub_preview_001" />;
      case '/student/plagiarism/plg_preview_001': return <StudentPlagiarismDetailView recordId="plg_preview_001" />;
      case '/student/appeals': return <StudentAppealsListView />;
      case '/student/appeals/new': return <StudentAppealCreateView />;
      case '/student/appeals/apl_preview_001': return <StudentAppealDetailView appealId="apl_preview_001" />;

      // Teacher
      case '/teacher/dashboard': return <TeacherDashboardView />;
      case '/teacher/exams': return <TeacherExamsView />;
      case '/teacher/exams/exam_preview_001': return <TeacherExamDetailView examId="exam_preview_001" />;
      case '/teacher/submissions/sub_preview_001': return <TeacherScoringView submissionId="sub_preview_001" />;
      case '/teacher/scoring/sub_preview_001': return <TeacherScoringView submissionId="sub_preview_001" />;
      case '/teacher/plagiarism': return <TeacherPlagiarismDashboardView />;
      case '/teacher/plagiarism/plg_preview_001': return <TeacherPlagiarismDetailView recordId="plg_preview_001" />;
      case '/teacher/plagiarism/plg_preview_001/compare': return <TeacherPlagiarismCompareView recordId="plg_preview_001" />;

      default:
        return <div className="p-8 text-center text-error">Preview path not mapped: {activePreviewPath}</div>;
    }
  };

  return (
    <PreviewNavigationContext.Provider value={{ navigate: handleSelectRoute, goBack: handleBack }}>
      <div className="min-h-screen bg-surface flex flex-col">
      {/* Header */}
      <header className="bg-surface-container-lowest border-b border-outline-variant/30 px-space-lg py-space-sm flex items-center justify-between shadow-sm sticky top-0 z-50">
        <div className="flex items-center gap-space-sm">
          <div className="w-8 h-8 rounded bg-primary text-on-primary flex items-center justify-center font-bold font-mono">
            CA
          </div>
          <h1 className="font-headline-sm text-headline-sm text-on-surface font-semibold">
            CodeAssess
          </h1>
          <span className="font-label-sm text-label-sm text-outline px-2">|</span>
          <span className="font-label-md text-label-md text-on-surface-variant">
            Development Preview
          </span>
        </div>
        <div className="flex items-center gap-3">
          {activePreviewPath && (
            <button
              onClick={handleBack}
              className="px-3 py-1.5 rounded-lg border border-outline-variant hover:bg-surface-container text-on-surface font-label-sm text-label-sm font-semibold transition-colors flex items-center gap-1"
            >
              <span className="material-symbols-outlined text-[16px]">arrow_back</span>
              Back to Menu
            </button>
          )}
          <span className="px-2 py-1 rounded bg-error-container text-on-error-container font-label-sm text-label-sm font-bold uppercase tracking-wider flex items-center gap-1.5">
            <span className="material-symbols-outlined text-[14px]">warning</span>
            Development Only
          </span>
        </div>
      </header>

      {/* Main Content */}
      <main className="flex-1 w-full flex flex-col">
        {!activePreviewPath ? (
          <div className="max-w-5xl w-full mx-auto p-space-lg flex flex-col space-y-space-xl pt-space-xl">
            <PreviewRoleSelector selectedRole={selectedRole} onSelectRole={setSelectedRole} />
            
            <PreviewRouteList selectedRole={selectedRole} onSelectRoute={handleSelectRoute} />

            {/* Integration Status Footer */}
            <div className="mt-space-xl p-space-md rounded-xl bg-surface-container-low border-l-4 border-secondary/50">
              <h4 className="font-label-lg text-label-lg text-on-surface font-semibold mb-1">
                Integration Status
              </h4>
              <p className="font-body-sm text-body-sm text-on-surface-variant flex flex-col gap-1">
                <span>Backend APIs are required for live data.</span>
                <span>Preview mode acts solely as a component sandbox. It does not navigate away, create fake data, or bypass production Auth guards.</span>
              </p>
            </div>
          </div>
        ) : (
          <div className="flex-1 overflow-auto bg-surface relative">
             <PreviewErrorBoundary activePath={activePreviewPath} onBack={handleBack}>
               {renderActiveComponent()}
             </PreviewErrorBoundary>
          </div>
        )}
      </main>
    </div>
    </PreviewNavigationContext.Provider>
  );
}
