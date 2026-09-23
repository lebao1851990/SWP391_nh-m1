'use client';

import { useMutation } from '@tanstack/react-query';
import { useRouter } from 'next/navigation';
import { authApi } from '../api/auth.api';
import { useSessionStore } from '@/stores/session.store';
import { LogOut } from 'lucide-react';

export function LogoutButton() {
  const router = useRouter();
  const clearSession = useSessionStore((state) => state.clearSession);

  const mutation = useMutation({
    mutationFn: authApi.logout,
    onSettled: () => {
      // Regardless of API success/fail, clear local session UI metadata
      clearSession();
      // The backend is responsible for clearing the HttpOnly cookie via Set-Cookie header.
      router.push('/login');
    },
  });

  return (
    <button
      onClick={() => mutation.mutate()}
      disabled={mutation.isPending}
      className="flex items-center gap-2 px-3 py-2 rounded-md text-sm font-medium text-muted-foreground hover:text-foreground hover:bg-muted transition-colors w-full"
    >
      <LogOut className="w-4 h-4" />
      <span>{mutation.isPending ? 'Logging out...' : 'Log out'}</span>
    </button>
  );
}
