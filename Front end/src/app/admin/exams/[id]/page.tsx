import { AdminExamDetailView } from '@/features/admin/components/AdminExamDetailView';

export default async function AdminExamDetailPage({ params }: { params: Promise<{ id: string }> }) {
  return <AdminExamDetailView examId={(await params).id} />;
}
