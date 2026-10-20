import { Sidebar } from '@/components/layout/sidebar';
import { Header } from '@/components/layout/header';

export default function TeacherLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <div className="min-h-screen bg-surface font-body-md text-on-surface antialiased">
      <Sidebar role="TEACHER" />
      <div className="lg:pl-[260px] pl-0 transition-all flex flex-col min-h-screen min-w-0 max-w-full">
        <Header />
        <main className="w-full max-w-full min-w-0 pt-16 flex-1 flex flex-col">
          {children}
        </main>
      </div>
    </div>
  );
}
