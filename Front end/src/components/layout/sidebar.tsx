'use client';

import React from 'react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { LogoutButton } from '@/features/auth/components/logout-button';
import { useUiStore } from '@/stores/ui.store';
import {
  LayoutDashboard,
  FileCode2,
  Code2,
  GitCompare,
  Scale,
  Users,
  GraduationCap,
  BookOpen,
  Activity,
  ShieldCheck,
  X,
} from 'lucide-react';

interface NavItem {
  name: string;
  href: string;
  icon: React.ComponentType<{ className?: string }>;
  badge?: { text: string; type: 'AST' | 'Count' };
}

interface SidebarProps {
  role: 'ADMIN' | 'TEACHER' | 'STUDENT';
}

export function Sidebar({ role }: SidebarProps) {
  const pathname = usePathname();
  const { isMobileNavOpen, setMobileNavOpen } = useUiStore();

  const teacherNav: NavItem[] = [
    { name: 'Dashboard', href: '/teacher/dashboard', icon: LayoutDashboard },
    { name: 'Exams', href: '/teacher/exams', icon: FileCode2 },
    { name: 'Submissions', href: '/teacher/submissions', icon: Code2 },
    { name: 'Plagiarism Check', href: '/teacher/plagiarism', icon: GitCompare, badge: { text: 'AST', type: 'AST' } },
    { name: 'Appeals', href: '/teacher/appeals', icon: Scale },
  ];

  const studentNav: NavItem[] = [
    { name: 'Dashboard', href: '/student/dashboard', icon: LayoutDashboard },
    { name: 'My Exams', href: '/student/exams', icon: FileCode2 },
    { name: 'My Submissions', href: '/student/submissions', icon: Code2 },
    { name: 'Appeals', href: '/student/appeals', icon: Scale },
  ];

  const adminNav: NavItem[] = [
    { name: 'Dashboard', href: '/admin/dashboard', icon: LayoutDashboard },
    { name: 'Users', href: '/admin/users', icon: Users },
    { name: 'Classes', href: '/admin/classes', icon: GraduationCap },
    { name: 'Exams', href: '/admin/exams', icon: BookOpen },
  ];

  const navItems = role === 'ADMIN' ? adminNav : role === 'TEACHER' ? teacherNav : studentNav;

  return (
    <>
      {/* Mobile Backdrop Overlay */}
      {isMobileNavOpen && (
        <div
          data-testid="sidebar-backdrop"
          onClick={() => setMobileNavOpen(false)}
          className="fixed inset-0 bg-black/60 backdrop-blur-xs z-40 lg:hidden animate-in fade-in duration-150"
          aria-hidden="true"
        />
      )}

      <aside className={`fixed left-0 top-0 h-full w-[260px] bg-slate-900 border-r border-slate-800 z-50 flex flex-col justify-between select-none shadow-xl transition-transform duration-200 ease-in-out ${
        isMobileNavOpen ? 'translate-x-0' : '-translate-x-full lg:translate-x-0'
      }`}>
        <div className="flex flex-col">
          {/* Brand Header */}
          <div className="h-16 px-4 border-b border-slate-800 flex items-center justify-between bg-slate-950/80">
            <Link 
              href={role === 'TEACHER' ? '/teacher/dashboard' : role === 'STUDENT' ? '/student/dashboard' : '/admin/dashboard'} 
              onClick={() => setMobileNavOpen(false)}
              className="flex items-center gap-2.5 group"
            >
              <div className="w-8 h-8 bg-gradient-to-tr from-indigo-600 to-sky-500 rounded-lg flex items-center justify-center font-bold text-white shadow-md shadow-indigo-500/20 group-hover:scale-105 transition-transform">
                <ShieldCheck className="w-4 h-4 text-white" />
              </div>
              <div className="flex flex-col">
                <span className="font-semibold text-sm text-slate-100 tracking-tight leading-none group-hover:text-white transition-colors">
                  CodeAssess
                </span>
                <span className="text-[10px] text-slate-400 font-mono tracking-wider mt-1">
                  ACADEMIC PORTAL
                </span>
              </div>
            </Link>
            <div className="flex items-center gap-2">
              <span className="text-[10px] font-semibold px-2 py-0.5 rounded-full bg-indigo-950 border border-indigo-700/50 text-indigo-300 font-mono">
                {role === 'TEACHER' ? 'FACULTY' : role}
              </span>
              <button
                type="button"
                onClick={() => setMobileNavOpen(false)}
                className="p-1 rounded-lg text-slate-400 hover:text-white hover:bg-slate-800 lg:hidden transition-colors"
                aria-label="Close navigation"
              >
                <X className="w-5 h-5" />
              </button>
            </div>
          </div>

        {/* Section Title */}
        <div className="px-4 pt-5 pb-2">
          <span className="text-[10px] font-semibold text-slate-500 uppercase tracking-widest font-mono">
            Navigation
          </span>
        </div>

        {/* Nav Items */}
        <nav className="px-2 space-y-1">
          {navItems.map((item) => {
            const Icon = item.icon;
            const isActive = pathname === item.href || (item.href !== `/${role.toLowerCase()}/dashboard` && pathname.startsWith(item.href));
            return (
              <Link
                key={item.href}
                href={item.href}
                onClick={() => setMobileNavOpen(false)}
                className={`flex items-center justify-between px-3 py-2 rounded-lg text-xs font-medium transition-all ${
                  isActive
                    ? 'bg-indigo-600/90 text-white shadow-sm shadow-indigo-600/20 font-semibold'
                    : 'text-slate-400 hover:bg-slate-800/80 hover:text-slate-100'
                }`}
              >
                <div className="flex items-center gap-2.5">
                  <Icon className={`w-4 h-4 ${isActive ? 'text-white' : 'text-slate-400 group-hover:text-slate-200'}`} />
                  <span>{item.name}</span>
                </div>
                {item.badge && item.badge.type === 'AST' && (
                  <span className={`text-[10px] px-1.5 py-0.2 rounded font-mono font-semibold ${
                    isActive ? 'bg-white/20 text-white' : 'bg-indigo-950 text-indigo-300 border border-indigo-800/50'
                  }`}>
                    {item.badge.text}
                  </span>
                )}
                {item.badge && item.badge.type === 'Count' && (
                  <span className="text-[10px] px-1.5 py-0.2 rounded-full bg-rose-500/20 text-rose-300 font-semibold border border-rose-500/30">
                    {item.badge.text}
                  </span>
                )}
              </Link>
            );
          })}
        </nav>
      </div>

      {/* Footer System Status & Logout */}
      <div className="p-3 border-t border-slate-800 bg-slate-950/60">
        <div className="flex items-center justify-between px-2 py-1.5 mb-2 rounded-md bg-slate-900 border border-slate-800 text-xs">
          <div className="flex items-center gap-1.5 text-slate-300">
            <Activity className="w-3.5 h-3.5 text-emerald-400 animate-pulse" />
            <span className="text-[11px] text-slate-300 font-medium">AST Engine v2.0</span>
          </div>
          <span className="text-[10px] font-mono text-emerald-400 font-semibold bg-emerald-950/60 px-1.5 py-0.5 rounded border border-emerald-800/40">ONLINE</span>
        </div>
        <LogoutButton />
      </div>
    </aside>
  </>
  );
}
