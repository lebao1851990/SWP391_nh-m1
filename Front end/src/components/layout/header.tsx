'use client';

import React, { useState, useEffect, useRef } from 'react';
import { useRouter, usePathname } from 'next/navigation';
import { useSessionStore } from '@/stores/session.store';
import { authApi } from '@/features/auth/api/auth.api';
import { 
  Search, 
  Bell, 
  HelpCircle, 
  GraduationCap, 
  User, 
  LogOut, 
  ChevronDown, 
  CheckCircle2, 
  ShieldAlert, 
  X,
  Code2,
  Calendar,
  BookOpen,
  Menu
} from 'lucide-react';
import { toast } from 'sonner';
import { useUiStore } from '@/stores/ui.store';
import { useTranslation } from '@/i18n';
import { LocaleThemeBar } from '@/components/ui/LocaleThemeBar';

export function Header() {
  const router = useRouter();
  const pathname = usePathname() || '';
  const user = useSessionStore((state) => state.user);
  const clearSession = useSessionStore((state) => state.clearSession);
  const { toggleMobileNav } = useUiStore();
  const { t } = useTranslation();

  const [searchQuery, setSearchQuery] = useState('');
  const [profileOpen, setProfileOpen] = useState(false);
  const [notificationsOpen, setNotificationsOpen] = useState(false);
  const [helpOpen, setHelpOpen] = useState(false);

  const searchInputRef = useRef<HTMLInputElement>(null);
  const profileRef = useRef<HTMLDivElement>(null);
  const notificationsRef = useRef<HTMLDivElement>(null);

  // Keyboard shortcut Ctrl+K or Cmd+K
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key === 'k') {
        e.preventDefault();
        searchInputRef.current?.focus();
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, []);

  // Click outside listener for dropdowns
  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (profileRef.current && !profileRef.current.contains(e.target as Node)) {
        setProfileOpen(false);
      }
      if (notificationsRef.current && !notificationsRef.current.contains(e.target as Node)) {
        setNotificationsOpen(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    if (!searchQuery.trim()) return;

    const query = encodeURIComponent(searchQuery.trim());
    if (user?.role === 'TEACHER') {
      if (pathname.startsWith('/teacher/submissions')) {
        router.push(`/teacher/submissions?search=${query}`);
      } else if (pathname.startsWith('/teacher/plagiarism')) {
        router.push(`/teacher/plagiarism?search=${query}`);
      } else if (pathname.startsWith('/teacher/appeals')) {
        router.push(`/teacher/appeals?search=${query}`);
      } else {
        router.push(`/teacher/exams?search=${query}`);
      }
    } else {
      if (pathname.startsWith('/student/submissions')) {
        router.push(`/student/submissions?search=${query}`);
      } else if (pathname.startsWith('/student/appeals')) {
        router.push(`/student/appeals?search=${query}`);
      } else {
        router.push(`/student/exams?search=${query}`);
      }
    }
  };

  const handleLogout = async () => {
    try {
      await authApi.logout();
    } catch {
      // ignore network errors on logout
    } finally {
      clearSession();
      if (typeof document !== 'undefined') {
        document.cookie = 'codeassess_role=; path=/; max-age=0; SameSite=Lax';
      }
      toast.success(t('nav.signedOutSuccess'));
      router.push('/login');
    }
  };

  return (
    <>
      <header className="fixed top-0 left-0 lg:left-[260px] right-0 h-16 bg-surface-container-lowest/95 backdrop-blur-md border-b border-outline-variant/30 z-40 px-4 lg:px-space-lg flex items-center justify-between transition-all">
        {/* Mobile Menu Toggle & Search Bar */}
        <div className="flex items-center gap-2 flex-1 max-w-xl min-w-0">
          <button
            type="button"
            onClick={toggleMobileNav}
            className="p-1.5 -ml-1 text-on-surface-variant hover:text-on-surface hover:bg-surface-container rounded-lg lg:hidden transition-colors cursor-pointer shrink-0"
            aria-label="Toggle navigation menu"
          >
            <Menu className="w-5 h-5 text-on-surface" />
          </button>
          <form onSubmit={handleSearch} className="flex items-center gap-space-md flex-1 min-w-0">
            <div className="relative w-full min-w-0">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-outline w-4 h-4" />
            <input 
              ref={searchInputRef}
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="w-full pl-9 pr-14 py-1.5 bg-surface-container-low rounded border border-outline-variant text-on-surface font-body-sm text-body-sm placeholder:text-outline focus:outline-none focus:border-primary-container focus:ring-1 focus:ring-primary-container transition-all" 
              placeholder={t('header.searchPlaceholder')} 
              type="text"
            />
            <kbd className="absolute right-2.5 top-1/2 -translate-y-1/2 font-label-xs text-[10px] text-outline bg-surface-container px-1.5 py-0.5 rounded border border-outline-variant/40 select-none">
              {t('header.searchShortcut')}
            </kbd>
          </div>
        </form>
      </div>

        {/* User Actions */}
        <div className="flex items-center gap-2 sm:gap-space-md shrink-0">
          {/* Semester Badge */}
          <div className="hidden md:flex items-center gap-1.5 px-space-sm py-1 rounded bg-surface-container border border-outline-variant/30 text-on-surface text-xs font-medium">
            <GraduationCap className="w-3.5 h-3.5 text-primary" />
            <span className="font-label-md text-label-md">{t('header.termBadge')}</span>
          </div>
          
          {/* Notifications Trigger */}
          <div className="relative" ref={notificationsRef}>
            <button 
              onClick={() => {
                setNotificationsOpen(!notificationsOpen);
                setProfileOpen(false);
              }}
              className="relative p-1.5 text-on-surface-variant hover:text-on-surface hover:bg-surface-container rounded transition-colors cursor-pointer" 
              title={t('header.systemNotifications')}
            >
              <Bell className="w-4 h-4" />
              <span className="absolute top-1 right-1 h-2 w-2 rounded-full bg-emerald-500 ring-2 ring-surface-container-lowest"></span>
            </button>

            {/* Notifications Popover */}
            {notificationsOpen && (
              <div className="absolute right-0 mt-2 w-80 bg-surface-container-lowest border border-outline-variant/40 rounded-xl shadow-xl z-50 p-space-md flex flex-col gap-space-sm">
                <div className="flex items-center justify-between border-b border-outline-variant/30 pb-2">
                  <span className="font-label-md font-semibold text-on-surface">{t('header.telemetryTitle')}</span>
                  <span className="font-label-xs text-xs px-1.5 py-0.5 rounded bg-emerald-100 text-emerald-800 font-semibold">{t('header.telemetryActive')}</span>
                </div>
                <div className="space-y-2 text-xs">
                  <div className="flex items-start gap-2 p-2 rounded bg-surface-container-low">
                    <CheckCircle2 className="w-4 h-4 text-emerald-600 shrink-0 mt-0.5" />
                    <div>
                      <p className="font-semibold text-on-surface">{t('header.astEngineReady')}</p>
                      <p className="text-on-surface-variant text-[11px]">{t('header.astEngineDesc')}</p>
                    </div>
                  </div>
                  <div className="flex items-start gap-2 p-2 rounded bg-surface-container-low">
                    <CheckCircle2 className="w-4 h-4 text-blue-600 shrink-0 mt-0.5" />
                    <div>
                      <p className="font-semibold text-on-surface">{t('header.dockerSandboxReady')}</p>
                      <p className="text-on-surface-variant text-[11px]">{t('header.dockerSandboxDesc')}</p>
                    </div>
                  </div>
                </div>
                <button 
                  onClick={() => setNotificationsOpen(false)}
                  className="w-full py-1 text-center font-label-xs text-primary hover:underline cursor-pointer"
                >
                  {t('header.closeTelemetry')}
                </button>
              </div>
            )}
          </div>
          
          {/* Academic Integrity Guide Trigger */}
          <button 
            onClick={() => setHelpOpen(true)}
            className="p-1.5 text-on-surface-variant hover:text-on-surface hover:bg-surface-container rounded transition-colors cursor-pointer" 
            title={t('header.guidelinesTitle')}
          >
            <HelpCircle className="w-4 h-4" />
          </button>
          
          <div className="h-5 w-[1px] bg-outline-variant/40"></div>

          {/* Shared Language & Theme Selector */}
          <LocaleThemeBar />

          <div className="h-5 w-[1px] bg-outline-variant/40"></div>
          
          {/* User Profile Menu */}
          <div className="relative" ref={profileRef}>
            <button 
              onClick={() => {
                setProfileOpen(!profileOpen);
                setNotificationsOpen(false);
              }}
              className="flex items-center gap-space-sm pl-space-xs cursor-pointer hover:bg-surface-container-low p-1.5 rounded-lg border border-transparent hover:border-outline-variant/30 transition-all text-left"
            >
              <div className="w-7 h-7 rounded-full bg-primary/10 border border-primary/20 flex items-center justify-center text-primary font-bold text-xs">
                {user?.email ? user.email.charAt(0).toUpperCase() : 'U'}
              </div>
              <div className="flex flex-col hidden sm:flex">
                <div className="flex items-center gap-1.5">
                  <span className="font-label-md text-label-md font-semibold text-on-surface truncate max-w-[120px]">
                    {user?.email ? user.email.split('@')[0] : 'User'}
                  </span>
                  <span className={`font-label-xs text-[10px] px-1.5 py-0.2 rounded font-semibold ${
                    user?.role === 'TEACHER' 
                      ? 'bg-blue-100 dark:bg-blue-950/80 text-blue-800 dark:text-blue-300' 
                      : user?.role === 'ADMIN' 
                      ? 'bg-purple-100 dark:bg-purple-950/80 text-purple-800 dark:text-purple-300' 
                      : 'bg-emerald-100 dark:bg-emerald-950/80 text-emerald-800 dark:text-emerald-300'
                  }`}>
                    {user?.role === 'ADMIN' ? 'Admin' : user?.role === 'TEACHER' ? 'Faculty' : 'Student'}
                  </span>
                </div>
              </div>
              <ChevronDown className="w-3.5 h-3.5 text-outline ml-0.5" />
            </button>

            {/* Profile Dropdown */}
            {profileOpen && (
              <div className="absolute right-0 mt-2 w-64 bg-surface-container-lowest border border-outline-variant/40 rounded-xl shadow-xl z-50 p-2 flex flex-col gap-1">
                <div className="px-3 py-2 border-b border-outline-variant/20 mb-1">
                  <p className="font-label-xs text-outline uppercase tracking-wider">{t('header.signedInAs')}</p>
                  <p className="font-label-md font-semibold text-on-surface truncate">{user?.email || 'N/A'}</p>
                  <p className="font-label-xs text-outline-variant truncate">ID: {user?.id || 'Pending'}</p>
                </div>

                <button
                  onClick={() => {
                    setProfileOpen(false);
                    router.push(user?.role === 'TEACHER' ? '/teacher/dashboard' : '/student/dashboard');
                  }}
                  className="flex items-center gap-2 px-3 py-2 text-xs text-on-surface hover:bg-surface-container rounded-lg transition-colors cursor-pointer"
                >
                  <User className="w-3.5 h-3.5 text-outline" />
                  <span>{t('header.dashboardOverview')}</span>
                </button>

                <button
                  onClick={() => {
                    setProfileOpen(false);
                    setHelpOpen(true);
                  }}
                  className="flex items-center gap-2 px-3 py-2 text-xs text-on-surface hover:bg-surface-container rounded-lg transition-colors cursor-pointer"
                >
                  <BookOpen className="w-3.5 h-3.5 text-outline" />
                  <span>{t('header.honorCodeTitle')}</span>
                </button>

                <div className="border-t border-outline-variant/20 my-1"></div>

                <button
                  onClick={() => {
                    setProfileOpen(false);
                    handleLogout();
                  }}
                  className="flex items-center gap-2 px-3 py-2 text-xs text-rose-600 hover:bg-rose-50 rounded-lg transition-colors cursor-pointer font-medium"
                >
                  <LogOut className="w-3.5 h-3.5 text-rose-500" />
                  <span>{t('nav.signOut')}</span>
                </button>
              </div>
            )}
          </div>
        </div>
      </header>

      {/* Academic Integrity Modal */}
      {helpOpen && (
        <div className="fixed inset-0 bg-black/50 backdrop-blur-xs z-50 flex items-center justify-center p-4">
          <div className="bg-surface-container-lowest border border-outline-variant/40 rounded-2xl shadow-2xl max-w-lg w-full p-space-lg flex flex-col gap-space-md animate-in fade-in zoom-in-95 duration-150">
            <div className="flex items-center justify-between border-b border-outline-variant/30 pb-3">
              <div className="flex items-center gap-2 text-primary font-semibold">
                <BookOpen className="w-5 h-5" />
                <h3 className="font-headline-sm text-headline-sm text-on-surface">{t('header.academicStandardsTitle')}</h3>
              </div>
              <button 
                onClick={() => setHelpOpen(false)} 
                className="p-1 rounded-lg text-outline hover:text-on-surface hover:bg-surface-container transition-colors cursor-pointer"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            <div className="space-y-3 text-xs text-on-surface-variant leading-relaxed">
              <div className="p-3 rounded-lg bg-surface-container-low border border-outline-variant/30">
                <p className="font-semibold text-on-surface mb-1 flex items-center gap-1.5">
                  <ShieldAlert className="w-4 h-4 text-amber-600" />
                  {t('header.astPolicyTitle')}
                </p>
                <p>{t('header.astPolicyDesc')}</p>
              </div>

              <div className="p-3 rounded-lg bg-surface-container-low border border-outline-variant/30">
                <p className="font-semibold text-on-surface mb-1 flex items-center gap-1.5">
                  <Calendar className="w-4 h-4 text-blue-600" />
                  {t('header.appealsPolicyTitle')}
                </p>
                <p>{t('header.appealsPolicyDesc')}</p>
              </div>

              <div className="p-3 rounded-lg bg-surface-container-low border border-outline-variant/30">
                <p className="font-semibold text-on-surface mb-1 flex items-center gap-1.5">
                  <Code2 className="w-4 h-4 text-emerald-600" />
                  {t('header.dockerPolicyTitle')}
                </p>
                <p>{t('header.dockerPolicyDesc')}</p>
              </div>
            </div>

            <div className="pt-2 flex justify-end">
              <button
                onClick={() => setHelpOpen(false)}
                className="px-4 py-2 bg-primary text-on-primary rounded-lg text-xs font-medium hover:bg-primary/90 transition-colors cursor-pointer shadow-sm"
              >
                {t('header.understoodButton')}
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  );
}
