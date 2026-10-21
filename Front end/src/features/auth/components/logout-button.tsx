'use client';

import { useMutation } from '@tanstack/react-query';
import { useRouter } from 'next/navigation';
import { authApi } from '../api/auth.api';
import { useSessionStore } from '@/stores/session.store';
import { LogOut, Loader2 } from 'lucide-react';
import { toast } from 'sonner';
import { useTranslation } from '@/i18n';

export function LogoutButton() {
  const router = useRouter();
  const clearSession = useSessionStore((state) => state.clearSession);
  const { t } = useTranslation();

  const mutation = useMutation({
    mutationFn: authApi.logout,
    onSettled: () => {
      clearSession();
      if (typeof document !== 'undefined') {
        document.cookie = 'codeassess_role=; path=/; max-age=0; SameSite=Lax';
      }
      toast.success(t('nav.signedOutSuccess'));
      router.push('/login');
    },
  });

  return (
    <button
      onClick={() => mutation.mutate()}
      disabled={mutation.isPending}
      className="flex items-center justify-center gap-2 px-3 py-2 rounded-lg text-xs font-medium text-slate-400 hover:text-rose-300 hover:bg-rose-500/10 border border-transparent hover:border-rose-500/20 transition-all w-full cursor-pointer disabled:opacity-50"
      title={t('nav.signOut')}
    >
      {mutation.isPending ? (
        <>
          <Loader2 className="w-3.5 h-3.5 animate-spin" />
          <span>{t('nav.signingOut')}</span>
        </>
      ) : (
        <>
          <LogOut className="w-3.5 h-3.5" />
          <span>{t('nav.signOut')}</span>
        </>
      )}
    </button>
  );
}
