import React from 'react';
import { TeacherPlagiarismDetailView } from '@/features/plagiarism/components';

export default async function TeacherPlagiarismDetailPage({ params }: { params: Promise<{ id: string }> }) {
  const resolvedParams = await params;
  return <TeacherPlagiarismDetailView recordId={resolvedParams.id} />;
}
