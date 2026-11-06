import { Metadata } from 'next';
import { StudentClassesView } from '@/features/classes/components/StudentClassesView';

export const metadata: Metadata = {
  title: 'Lớp học của tôi - CodeAssess',
  description: 'Danh sách các lớp học sinh viên đang tham gia',
};

export default function StudentClassesPage() {
  return <StudentClassesView />;
}

