import React from 'react';
import { AdminAppealDetailView } from '@/features/appeals/components/AdminAppealDetailView';

export const metadata = {
  title: 'Chi tiết Phán quyết Khiếu nại | CodeAssess Admin',
  description: 'Xem xét chi tiết và đưa ra phán quyết khiếu nại điểm số của sinh viên.',
};

export default async function AdminAppealDetailPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const resolvedParams = await params;
  return <AdminAppealDetailView appealId={resolvedParams.id} />;
}
