import React from 'react';
import { AdminAppealsListView } from '@/features/appeals/components/AdminAppealsListView';

export const metadata = {
  title: 'Quản lý Khiếu nại Điểm số | CodeAssess Admin',
  description: 'Quản lý và giải quyết các đơn khiếu nại điểm số của sinh viên toàn trường.',
};

export default function AdminAppealsPage() {
  return <AdminAppealsListView />;
}
