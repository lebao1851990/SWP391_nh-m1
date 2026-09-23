import { Suspense } from 'react';
import { StudentAppealCreateView } from '@/features/appeals/components/StudentAppealCreateView';

export const metadata = {
  title: 'Submit Appeal | Student Portal',
};

export default function NewAppealPage() {
  return (
    <Suspense fallback={<div className="p-8 text-center">Loading...</div>}>
      <StudentAppealCreateView />
    </Suspense>
  );
}
