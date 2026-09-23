'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { LogoutButton } from '@/features/auth/components/logout-button';

interface NavItem {
  name: string;
  href: string;
  icon: string;
  badge?: { text: string; type: 'AST' | 'Count' };
}

interface SidebarProps {
  role: 'ADMIN' | 'TEACHER' | 'STUDENT';
}

export function Sidebar({ role }: SidebarProps) {
  const pathname = usePathname();

  const teacherNav: NavItem[] = [
    { name: 'Dashboard', href: '/teacher/dashboard', icon: 'dashboard' },
    { name: 'Exams', href: '/teacher/exams', icon: 'terminal' },
    { name: 'Submissions', href: '/teacher/submissions', icon: 'code_blocks' },
    { name: 'Plagiarism Check', href: '/teacher/plagiarism', icon: 'compare_arrows', badge: { text: 'AST', type: 'AST' } },
  ];

  const studentNav: NavItem[] = [
    { name: 'Dashboard', href: '/student/dashboard', icon: 'dashboard' },
    { name: 'My Exams', href: '/student/exams', icon: 'terminal' },
    { name: 'My Submissions', href: '/student/submissions', icon: 'code_blocks' },
    { name: 'Appeals', href: '/student/appeals', icon: 'gavel' },
  ];

  const adminNav: NavItem[] = [
    { name: 'Dashboard', href: '/admin/dashboard', icon: 'dashboard' },
    { name: 'Users', href: '/admin/users', icon: 'group' },
    { name: 'Classes', href: '/admin/classes', icon: 'school' },
    { name: 'Exams', href: '/admin/exams', icon: 'assignment_turned_in' },
  ];

  const navItems = role === 'ADMIN' ? adminNav : role === 'TEACHER' ? teacherNav : studentNav;
  return (
    <aside className="fixed left-0 top-0 h-full w-[260px] bg-surface-container-low border-r border-outline-variant/30 z-50 flex flex-col justify-between select-none">
      <div className="flex flex-col">
        {/* Logo Area */}
        <div className="h-16 px-space-md border-b border-outline-variant/30 flex items-center justify-between bg-surface-container-lowest">
          <div className="flex items-center gap-space-sm">
            <div className="w-8 h-8 bg-primary rounded-md flex items-center justify-center font-bold text-on-primary">
              CA
            </div>
            <div className="flex flex-col">
              <span className="font-headline-sm text-headline-sm text-on-surface leading-tight">CodeAssess</span>
              <span className="font-label-sm text-label-sm text-on-surface-variant leading-none">AY 2025–2026</span>
            </div>
          </div>
          <span className="font-label-sm text-label-sm px-1.5 py-0.5 rounded bg-primary-fixed text-on-primary-fixed border border-primary/20">
            EDU
          </span>
        </div>

        {/* Navigation */}
        <div className="px-space-md py-space-sm">
          <span className="font-label-sm text-label-sm text-outline font-semibold uppercase tracking-wider">
            Assessment Portal
          </span>
        </div>
        <nav className="px-space-xs space-y-0.5">
          {navItems.map((item) => {
            const isActive = pathname.startsWith(item.href) || pathname === item.href;
            return (
              <Link
                key={item.href}
                href={item.href}
                className={`flex items-center justify-between px-space-sm py-2 rounded transition-colors ${
                  isActive
                    ? 'bg-primary-container text-on-primary font-semibold'
                    : 'text-on-surface-variant hover:bg-surface-container-high hover:text-on-surface'
                }`}
              >
                <div className="flex items-center gap-space-sm">
                  <span className="material-symbols-outlined text-[18px]">{item.icon}</span>
                  <span className="font-body-md text-body-md">{item.name}</span>
                </div>
                {item.badge && item.badge.type === 'AST' && (
                  <span className="font-label-sm text-label-sm px-1.5 py-0.5 rounded bg-secondary-fixed text-on-secondary-fixed border border-secondary/20">
                    {item.badge.text}
                  </span>
                )}
                {item.badge && item.badge.type === 'Count' && (
                  <span className="font-label-sm text-label-sm px-1.5 py-0.5 rounded bg-error-container text-on-error-container font-semibold">
                    {item.badge.text}
                  </span>
                )}
              </Link>
            );
          })}
        </nav>
      </div>

      {/* Footer System Status & Logout */}
      <div className="p-space-sm border-t border-outline-variant/30 bg-surface-container-lowest">
        <div className="flex items-center justify-between p-space-xs mb-space-xs rounded bg-surface-container-low">
          <div className="flex items-center gap-space-xs">
            <span className="h-2 w-2 rounded-full bg-tertiary-fixed-dim"></span>
            <span className="font-label-sm text-label-sm text-on-surface">AST Engine Online</span>
          </div>
          <span className="font-label-sm text-label-sm text-outline">99.9%</span>
        </div>
        <LogoutButton />
      </div>
    </aside>
  );
}
