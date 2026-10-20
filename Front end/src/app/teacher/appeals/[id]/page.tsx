import { TeacherAppealDetailView } from '@/features/appeals/components/TeacherAppealDetailView';

export const metadata = {
  title: 'Appeal Review | Teacher Portal',
};

export default async function TeacherAppealDetailPage({ params }: { params: Promise<{ id: string }> }) {
  const resolvedParams = await params;
  return <TeacherAppealDetailView appealId={resolvedParams.id} />;
}
