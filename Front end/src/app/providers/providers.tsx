'use client';

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ReactNode, useState, useEffect } from 'react';
import { Toaster } from 'sonner';
import { useSessionStore } from '@/stores/session.store';
import { authApi } from '@/features/auth/api/auth.api';

function AuthSynchronizer() {
  const isAuthenticated = useSessionStore((state) => state.isAuthenticated);
  const setSession = useSessionStore((state) => state.setSession);
  const clearSession = useSessionStore((state) => state.clearSession);

  useEffect(() => {
    // Only attempt sync if in browser and session state is active
    if (typeof window !== 'undefined' && isAuthenticated) {
      authApi.getMe()
        .then((user) => {
          setSession(user);
          // Ensure role cookie matches authoritative backend identity
          document.cookie = `codeassess_role=${user.role}; path=/; max-age=86400; SameSite=Lax`;
        })
        .catch((err) => {
          if (err?.response?.status === 401) {
            clearSession();
            document.cookie = 'codeassess_role=; path=/; max-age=0; SameSite=Lax';
          }
        });
    }
  }, [isAuthenticated, setSession, clearSession]);

  return null;
}

import { useThemeStore } from '@/stores/theme.store';
import { useLocaleStore } from '@/i18n';

function ThemeSynchronizer() {
  const initTheme = useThemeStore((state) => state.initTheme);
  const locale = useLocaleStore((state) => state.locale);

  useEffect(() => {
    initTheme();
    if (typeof document !== 'undefined') {
      document.documentElement.lang = locale;
    }
  }, [initTheme, locale]);

  return null;
}

export function Providers({ children }: { children: ReactNode }) {
  const resolvedTheme = useThemeStore((state) => state.resolvedTheme);
  const [queryClient] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: {
            staleTime: 60 * 1000, // 1 minute
            refetchOnWindowFocus: false,
          },
        },
      })
  );

  return (
    <QueryClientProvider client={queryClient}>
      <ThemeSynchronizer />
      <AuthSynchronizer />
      {children}
      <Toaster position="top-right" richColors theme={resolvedTheme} />
    </QueryClientProvider>
  );
}
