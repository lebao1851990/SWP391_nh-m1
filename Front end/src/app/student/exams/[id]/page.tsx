import { StudentExamDetailView } from '@/features/exams/components/StudentExamDetailView';

export default async function StudentExamDetailPage({ params }: { params: Promise<{ id: string }> }) {
  return <StudentExamDetailView examId={(await params).id} />;
}
