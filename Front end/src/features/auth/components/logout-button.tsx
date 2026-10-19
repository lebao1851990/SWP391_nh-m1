'use client';

import { useMutation } from '@tanstack/react-query';
import { useRouter } from 'next/navigation';
import { authApi } from '../api/auth.api';
import { useSessionStore } from '@/stores/session.store';
import { LogOut, Loader2 } from 'lucide-react';
import { toast } from 'sonner';

export function LogoutButton() {
  const router = useRouter();
  const clearSession = useSessionStore((state) => state.clearSession);

  const mutation = useMutation({
    mutationFn: authApi.logout,
    onSettled: () => {
      clearSession();
      toast.success('Đăng xuất thành công');
      router.push('/login');
    },
  });

  return (
    <button
      onClick={() => mutation.mutate()}
      disabled={mutation.isPending}
      className="flex items-center justify-center gap-2 px-3 py-2 rounded-lg text-xs font-medium text-slate-400 hover:text-rose-300 hover:bg-rose-500/10 border border-transparent hover:border-rose-500/20 transition-all w-full cursor-pointer disabled:opacity-50"
      title="Đăng xuất khỏi hệ thống"
    >
      {mutation.isPending ? (
        <>
          <Loader2 className="w-3.5 h-3.5 animate-spin" />
          <span>Đang đăng xuất...</span>
        </>
      ) : (
        <>
          <LogOut className="w-3.5 h-3.5" />
          <span>Đăng xuất</span>
        </>
      )}
    </button>
  );
}
