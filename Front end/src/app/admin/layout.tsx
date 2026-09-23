'use client';

import { Sidebar } from '@/components/layout/sidebar';
import { Header } from '@/components/layout/header';

export default function AdminLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <div className="min-h-screen bg-background">
      <Sidebar role="ADMIN" />
      <Header />
      <main className="pl-[260px] pt-16 min-h-screen flex flex-col">
        {children}
      </main>
    </div>
  );
}
