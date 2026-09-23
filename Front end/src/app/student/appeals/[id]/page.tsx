import { StudentAppealDetailView } from '@/features/appeals/components/StudentAppealDetailView';

export const metadata = {
  title: 'Appeal Detail | Student Portal',
};

export default async function AppealDetailPage({ params }: { params: Promise<{ id: string }> }) {
  return <StudentAppealDetailView appealId={(await params).id} />;
}
