import { LivingAuthBackground } from '@/components/ui/living-auth-background';
import { CursorAura } from '@/components/ui/cursor-aura';

export default function AuthLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <div
      data-nc-theme="dark"
      className="relative flex min-h-dvh w-full flex-col items-center justify-center overflow-x-hidden bg-[#010409] text-slate-100 p-4 sm:p-8"
    >
      <LivingAuthBackground />
      <CursorAura />
      <div className="relative z-10 flex w-full flex-col items-center justify-center">
        {children}
      </div>
    </div>
  );
}
