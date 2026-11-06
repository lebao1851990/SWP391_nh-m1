import React, { Suspense } from 'react';
import { ExamBuilderView } from '@/features/exams/components/builder/ExamBuilderView';
import { Loader2 } from 'lucide-react';

export const metadata = {
  title: 'Trình soạn đề thi AI (AI Exam Builder) | CodeAssess',
  description: 'Trình xây dựng đề thi đa câu hỏi và quản lý bộ test cases thông minh với AI',
};

export default function ExamBuilderPage() {
  return (
    <Suspense
      fallback={
        <div className="flex h-screen items-center justify-center bg-surface-container-lowest">
          <div className="flex flex-col items-center gap-2">
            <Loader2 className="w-6 h-6 animate-spin text-primary" />
            <span className="text-xs font-semibold text-outline">Đang tải trình soạn thảo đề thi...</span>
          </div>
        </div>
      }
    >
      <ExamBuilderView />
    </Suspense>
  );
}

