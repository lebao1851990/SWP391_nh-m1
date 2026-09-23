import React from 'react';
import { StudentResultView } from '@/features/scoring/components';

export default async function StudentSubmissionResultPage({ params }: { params: Promise<{ id: string }> }) {
  const resolvedParams = await params;
  return <StudentResultView submissionId={resolvedParams.id} />;
}
