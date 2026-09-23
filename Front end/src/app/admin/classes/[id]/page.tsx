import { AdminClassDetailView } from '@/features/admin/components/AdminClassDetailView';

export default async function AdminClassDetailPage({ params }: { params: Promise<{ id: string }> }) {
  return <AdminClassDetailView classId={(await params).id} />;
}
