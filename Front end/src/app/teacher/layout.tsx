import { Sidebar } from '@/components/layout/sidebar';
import { Header } from '@/components/layout/header';
import { AuthGuard } from '@/components/auth/auth-guard';
import { WorkspaceAmbientBg } from '@/components/ui/hud-card';
import { CursorAura } from '@/components/ui/cursor-aura';

export default function TeacherLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <AuthGuard allowedRoles={['TEACHER']}>
      <div
        data-nc-theme="light"
        className="relative min-h-screen bg-[#F8FAFC] font-body-md text-on-surface antialiased"
      >
        <WorkspaceAmbientBg />
        <CursorAura />
        <Sidebar role="TEACHER" />
        <div className="relative z-10 lg:pl-[260px] pl-0 transition-all flex flex-col min-h-screen min-w-0 max-w-full">
          <Header />
          <main className="page-transition w-full max-w-full min-w-0 pt-16 flex-1 flex flex-col">
            {children}
          </main>
        </div>
      </div>
    </AuthGuard>
  );
}
