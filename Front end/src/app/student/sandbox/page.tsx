import { Metadata } from 'next';
import { StudentSandboxView } from '@/features/sandbox/components/StudentSandboxView';

export const metadata: Metadata = {
  title: 'Docker Sandbox - CodeAssess',
  description: 'Môi trường thực thi mã nguồn Java biệt lập trong Docker container',
};

export default function StudentSandboxPage() {
  return <StudentSandboxView />;
}

