import { TeacherExamDetailView } from '@/features/exams/components/TeacherExamDetailView';

export default async function Page({ params }: { params: Promise<{ id: string }> }) {
  return <TeacherExamDetailView examId={(await params).id} />;
}
