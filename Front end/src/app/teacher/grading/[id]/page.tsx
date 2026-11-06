import React from 'react';
import { TeacherGradingDetailView } from '@/features/grading/components';

export const metadata = {
  title: 'Chi tiết Thẩm định & Phê duyệt Điểm | CodeAssess Teacher',
};

export default async function TeacherGradingDetailPage({ params }: { params: Promise<{ id: string }> }) {
  const resolvedParams = await params;
  return <TeacherGradingDetailView submissionId={resolvedParams.id} />;
}

