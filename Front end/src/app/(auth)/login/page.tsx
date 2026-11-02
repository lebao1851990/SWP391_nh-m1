import { LoginForm } from '@/features/auth/components/login-form';
import { CinematicIntro } from '@/components/ui/cinematic-intro';
import { Suspense } from 'react';

export default function LoginPage() {
  return (
    <Suspense fallback={<div className="text-slate-400 font-mono text-xs">Loading...</div>}>
      <CinematicIntro>
        <LoginForm />
      </CinematicIntro>
    </Suspense>
  );
}
