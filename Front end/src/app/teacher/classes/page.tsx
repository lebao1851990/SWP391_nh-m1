import { TeacherClassesView } from '@/features/classes/components/TeacherClassesView';

export const metadata = {
  title: 'Quản lý Lớp học | AITA Intelligent',
  description: 'Quản lý các lớp học và phân công danh sách sinh viên',
};

export default function TeacherClassesPage() {
  return <TeacherClassesView />;
}
