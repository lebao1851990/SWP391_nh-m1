'use client';

import React, { useEffect, useState, useCallback } from 'react';
import { useRouter, usePathname } from 'next/navigation';
import { useSessionStore, UserRole, UserSession } from '@/stores/session.store';
import { authApi } from '@/features/auth/api/auth.api';
import { AlertTriangle, ArrowRight, LogIn } from 'lucide-react';

interface AuthGuardProps {
  allowedRoles: UserRole[];
  children: React.ReactNode;
}

interface SessionConflict {
  activeUser: UserSession;
  expectedRole: UserRole;
}

export function AuthGuard({ allowedRoles, children }: AuthGuardProps) {
  const router = useRouter();
  const pathname = usePathname();
  const [isAuthorized, setIsAuthorized] = useState(false);
  const [isVerifying, setIsVerifying] = useState(true);
  const [sessionConflict, setSessionConflict] = useState<SessionConflict | null>(null);

  const setSession = useSessionStore((state) => state.setSession);
  const clearSession = useSessionStore((state) => state.clearSession);

  const getRoleLabel = (r: UserRole): string => {
    switch (r) {
      case 'TEACHER': return 'Giảng viên';
      case 'STUDENT': return 'Sinh viên';
      case 'ADMIN': return 'Quản trị viên';
      default: return r;
    }
  };

  const verifyActiveSession = useCallback(async () => {
    try {
      // Reconcile directly against backend database profile via HttpOnly cookie
      const profile = await authApi.getProfile();

      if (allowedRoles.includes(profile.role)) {
        // Active server session matches the route's expected role
        setSession(profile);
        setIsAuthorized(true);
        setSessionConflict(null);
      } else {
        // Session was replaced by another tab or login (e.g., student logged in while teacher tab open)
        // Strictly DO NOT silently redirect; protect the tab state and notify the user
        setIsAuthorized(false);
        setSessionConflict({
          activeUser: profile,
          expectedRole: allowedRoles[0],
        });
      }
    } catch {
      // Profile request failed (401 Unauthorized / session invalid / unauthenticated)
      clearSession();
      setIsAuthorized(false);
      setSessionConflict(null);
      const redirectPath = pathname ? `?redirect=${encodeURIComponent(pathname)}` : '';
      router.replace(`/login${redirectPath}`);
    } finally {
      setIsVerifying(false);
    }
  }, [allowedRoles, pathname, router, setSession, clearSession]);

  useEffect(() => {
    // 1. Initial verification on mount
    // eslint-disable-next-line react-hooks/set-state-in-effect -- Asynchronous session verification via HttpOnly cookie; state updates occur after network response, not synchronously.
    verifyActiveSession();

    // 2. Multi-tab awareness: Re-verify when tab regains focus or visibility
    const handleFocus = () => {
      verifyActiveSession();
    };

    const handleVisibilityChange = () => {
      if (document.visibilityState === 'visible') {
        verifyActiveSession();
      }
    };

    window.addEventListener('focus', handleFocus);
    document.addEventListener('visibilitychange', handleVisibilityChange);

    return () => {
      window.removeEventListener('focus', handleFocus);
      document.removeEventListener('visibilitychange', handleVisibilityChange);
    };
  }, [verifyActiveSession]);

  // Handle switching to the new active session
  const handleSwitchToNewSession = () => {
    if (!sessionConflict) return;
    const { activeUser } = sessionConflict;
    setSession(activeUser);
    setSessionConflict(null);

    if (activeUser.role === 'TEACHER') {
      router.replace('/teacher/dashboard');
    } else if (activeUser.role === 'ADMIN') {
      router.replace('/admin/dashboard');
    } else {
      router.replace('/student/dashboard');
    }
  };

  // Handle logging out and re-authenticating as the expected role
  const handleReLogin = async () => {
    try {
      await authApi.logout();
    } catch {
      // ignore network errors
    } finally {
      clearSession();
      setSessionConflict(null);
      const redirectPath = pathname ? `?redirect=${encodeURIComponent(pathname)}` : '';
      router.replace(`/login${redirectPath}`);
    }
  };

  // Case 1: Session conflict detected (Another tab logged in with a different role/account)
  // Freeze and conceal the view to prevent silent role morphing or cross-identity API calls
  if (sessionConflict) {
    const { activeUser, expectedRole } = sessionConflict;
    return (
      <div className="min-h-screen bg-slate-950/80 backdrop-blur-md flex items-center justify-center p-4 z-50">
        <div className="bg-surface border border-outline-variant/60 rounded-2xl shadow-2xl max-w-lg w-full p-6 sm:p-8 flex flex-col items-center text-center gap-4 animate-in fade-in zoom-in-95 duration-200">
          <div className="w-14 h-14 rounded-full bg-amber-500/10 border border-amber-500/30 flex items-center justify-center text-amber-500">
            <AlertTriangle className="w-7 h-7" />
          </div>

          <div className="flex flex-col gap-1.5">
            <span className="font-label-xs text-xs px-2.5 py-0.5 rounded-full bg-amber-100 text-amber-800 font-semibold self-center">
              Phiên đăng nhập đã thay đổi
            </span>
            <h2 className="font-headline-sm text-headline-sm text-on-surface font-bold mt-1">
              Phát hiện tài khoản khác trên trình duyệt
            </h2>
            <p className="font-body-md text-on-surface-variant text-xs leading-relaxed mt-1">
              Trình duyệt của bạn hiện đang có phiên đăng nhập của{' '}
              <strong className="text-on-surface font-semibold">
                {activeUser.fullName || activeUser.email}
              </strong>{' '}
              ({getRoleLabel(activeUser.role)}).
            </p>
            <p className="font-body-sm text-on-surface-variant text-xs leading-relaxed">
              Tab này đang mở giao diện <strong className="text-primary font-semibold">{getRoleLabel(expectedRole)}</strong>.
              Để bảo vệ dữ liệu và tránh gửi yêu cầu nhầm danh tính, giao diện này đã tạm khóa.
            </p>
          </div>

          <div className="w-full border-t border-outline-variant/30 my-1"></div>

          <div className="flex flex-col sm:flex-row items-center gap-3 w-full">
            <button
              onClick={handleSwitchToNewSession}
              className="w-full sm:flex-1 py-2.5 px-4 bg-primary text-on-primary rounded-xl text-xs font-semibold hover:bg-primary/90 transition-all flex items-center justify-center gap-2 cursor-pointer shadow-sm"
            >
              <span>Chuyển sang {getRoleLabel(activeUser.role)}</span>
              <ArrowRight className="w-4 h-4" />
            </button>
            <button
              onClick={handleReLogin}
              className="w-full sm:flex-1 py-2.5 px-4 bg-surface-container-high hover:bg-surface-container-highest text-on-surface border border-outline-variant/40 rounded-xl text-xs font-semibold transition-all flex items-center justify-center gap-2 cursor-pointer"
            >
              <LogIn className="w-4 h-4" />
              <span>Đăng nhập lại {getRoleLabel(expectedRole)}</span>
            </button>
          </div>
        </div>
      </div>
    );
  }

  // Case 2: Verification in progress
  if (isVerifying || !isAuthorized) {
    return (
      <div className="min-h-screen bg-surface flex flex-col items-center justify-center gap-3 select-none">
        <div className="w-9 h-9 rounded-full border-3 border-primary border-t-transparent animate-spin" />
        <p className="text-on-surface-variant font-label-md text-xs tracking-wide">
          Đang xác thực phiên làm việc...
        </p>
      </div>
    );
  }

  // Case 3: Authorized
  return <>{children}</>;
}
