'use client';

import { Sidebar } from '@/components/layout/sidebar';
import { Header } from '@/components/layout/header';
import { AuthGuard } from '@/components/auth/auth-guard';
import { WorkspaceAmbientBg } from '@/components/ui/hud-card';
import { CursorAura } from '@/components/ui/cursor-aura';

export default function AdminLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <AuthGuard allowedRoles={['ADMIN']}>
      <div
        data-nc-theme="light"
        className="relative min-h-screen bg-[#F8FAFC] font-body-md text-on-surface antialiased"
      >
        <WorkspaceAmbientBg />
        <CursorAura />
        <Sidebar role="ADMIN" />
        <Header />
        <main className="page-transition relative z-10 lg:pl-[260px] pl-0 transition-all pt-16 min-h-screen flex flex-col min-w-0 max-w-full">
          {children}
        </main>
      </div>
    </AuthGuard>
  );
}
