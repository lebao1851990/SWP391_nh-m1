import React from 'react';
import { StudentPlagiarismDetailView } from '@/features/plagiarism/components';

export default async function StudentPlagiarismDetailPage({ params }: { params: Promise<{ id: string }> }) {
  const resolvedParams = await params;
  return <StudentPlagiarismDetailView recordId={resolvedParams.id} />;
}
