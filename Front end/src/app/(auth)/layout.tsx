import { LocaleThemeBar } from '@/components/ui/LocaleThemeBar';

export default function AuthLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <div className="relative flex min-h-screen flex-col items-center justify-center bg-background p-4 sm:p-8">
      <div className="absolute top-4 right-4 z-20">
        <LocaleThemeBar />
      </div>
      {children}
    </div>
  );
}
