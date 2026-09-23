import { Sidebar } from '@/components/layout/sidebar';
import { Header } from '@/components/layout/header';

export default function StudentLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <div className="min-h-screen bg-surface font-body-md text-on-surface antialiased">
      <Sidebar role="STUDENT" />
      <div className="pl-[260px] flex flex-col min-h-screen">
        <Header />
        <main className="w-full pt-16 flex-1 flex flex-col">
          {children}
        </main>
      </div>
    </div>
  );
}
