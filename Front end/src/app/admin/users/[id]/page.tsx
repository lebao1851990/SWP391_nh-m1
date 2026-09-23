import { AdminUserDetailView } from '@/features/admin/components/AdminUserDetailView';

export default async function AdminUserDetailPage({ params }: { params: Promise<{ id: string }> }) {
  return <AdminUserDetailView userId={(await params).id} />;
}
