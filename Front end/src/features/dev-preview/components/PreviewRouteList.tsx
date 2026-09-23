'use client';

import React from 'react';
import { PreviewRole } from './PreviewRoleSelector';

interface PreviewRouteListProps {
  selectedRole: PreviewRole;
  onSelectRoute: (path: string) => void;
}

const STUDENT_ROUTES = [
  { path: '/student/dashboard', name: 'Student Dashboard' },
  { path: '/student/exams', name: 'Exams' },
  { path: '/student/submissions', name: 'Submissions List' },
  { path: '/student/submissions/sub_preview_001', name: 'Submission Detail' },
  { path: '/student/plagiarism/plg_preview_001', name: 'Plagiarism Detail' },
  { path: '/student/appeals', name: 'Appeals List' },
  { path: '/student/appeals/new', name: 'Create Appeal' },
  { path: '/student/appeals/apl_preview_001', name: 'Appeal Detail' },
];

const TEACHER_ROUTES = [
  { path: '/teacher/dashboard', name: 'Teacher Dashboard' },
  { path: '/teacher/exams', name: 'Exams List' },
  { path: '/teacher/exams/exam_preview_001', name: 'Exam Detail' },
  { path: '/teacher/submissions/sub_preview_001', name: 'Submission Detail' },
  { path: '/teacher/scoring/sub_preview_001', name: 'Scoring View' },
  { path: '/teacher/plagiarism', name: 'Plagiarism Dashboard' },
  { path: '/teacher/plagiarism/plg_preview_001', name: 'Plagiarism Detail' },
  { path: '/teacher/plagiarism/plg_preview_001/compare', name: 'Plagiarism Compare' },
];

const ADMIN_ROUTES = [
  { path: '/admin/dashboard', name: 'Admin Dashboard' },
  { path: '/admin/users', name: 'Users List' },
  { path: '/admin/users/user_preview_001', name: 'User Detail' },
  { path: '/admin/classes', name: 'Classes List' },
  { path: '/admin/classes/class_preview_001', name: 'Class Detail' },
  { path: '/admin/exams', name: 'Exams List' },
  { path: '/admin/exams/exam_preview_001', name: 'Exam Detail' },
];

export function PreviewRouteList({ selectedRole, onSelectRoute }: PreviewRouteListProps) {
  let routes = STUDENT_ROUTES;
  if (selectedRole === 'Teacher') routes = TEACHER_ROUTES;
  if (selectedRole === 'Admin') routes = ADMIN_ROUTES;

  return (
    <div className="flex flex-col space-y-3">
      <h3 className="font-label-lg text-label-lg text-on-surface font-semibold">Available Routes</h3>
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-3">
        {routes.map((route) => (
          <button
            key={route.path}
            onClick={() => onSelectRoute(route.path)}
            className="text-left p-4 rounded-xl bg-surface-container-lowest border border-outline-variant/30 hover:border-primary/50 hover:shadow-sm transition-all flex flex-col group focus:outline-none focus:ring-2 focus:ring-primary/50"
          >
            <span className="font-label-md text-label-md text-on-surface font-semibold group-hover:text-primary transition-colors">
              {route.name}
            </span>
            <span className="font-code-body text-[12px] text-on-surface-variant mt-1 break-all">
              {route.path}
            </span>
          </button>
        ))}
      </div>
    </div>
  );
}
