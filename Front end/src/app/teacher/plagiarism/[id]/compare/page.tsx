import React from 'react';
import { TeacherPlagiarismCompareView } from '@/features/plagiarism/components';

export default async function TeacherPlagiarismComparePage({ params }: { params: Promise<{ id: string }> }) {
  const resolvedParams = await params;
  return <TeacherPlagiarismCompareView recordId={resolvedParams.id} />;
}
