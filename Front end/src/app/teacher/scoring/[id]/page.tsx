import React from 'react';
import { TeacherScoringView } from '@/features/scoring/components';

export default async function TeacherScoringPage({ params }: { params: Promise<{ id: string }> }) {
  const resolvedParams = await params;
  return <TeacherScoringView submissionId={resolvedParams.id} />;
}
