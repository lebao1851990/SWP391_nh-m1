'use client';

import { useSessionStore } from '@/stores/session.store';

export function Header() {
  const user = useSessionStore((state) => state.user);

  return (
    <header className="fixed top-0 left-[260px] right-0 h-16 bg-surface-container-lowest/95 backdrop-blur-md border-b border-outline-variant/30 z-40 px-space-lg flex items-center justify-between">
      {/* Search Bar */}
      <div className="flex items-center gap-space-md flex-1 max-w-xl">
        <div className="relative w-full">
          <span className="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-outline text-[18px]">search</span>
          <input 
            className="w-full pl-9 pr-12 py-1.5 bg-surface-container-low rounded border border-outline-variant text-on-surface font-body-sm text-body-sm focus:outline-none focus:border-primary-container" 
            placeholder="Search exams, student IDs, AST similarity reports..." 
            type="text"
          />
          <kbd className="absolute right-2.5 top-1/2 -translate-y-1/2 font-label-sm text-label-sm text-outline bg-surface-container px-1.5 py-0.5 rounded border border-outline-variant/40">
            ⌘K
          </kbd>
        </div>
      </div>

      {/* User Actions */}
      <div className="flex items-center gap-space-md">
        <div className="flex items-center gap-1.5 px-space-sm py-1 rounded bg-surface-container border border-outline-variant/30 text-on-surface">
          <span className="material-symbols-outlined text-[16px] text-primary">school</span>
          <span className="font-label-md text-label-md font-medium">Fall 2026</span>
          <span className="material-symbols-outlined text-[16px] text-outline">arrow_drop_down</span>
        </div>
        
        <button className="relative p-1.5 text-on-surface-variant hover:text-on-surface hover:bg-surface-container rounded transition-colors" title="Notifications">
          <span className="material-symbols-outlined text-[20px]">notifications</span>
          <span className="absolute top-1 right-1 h-2 w-2 rounded-full bg-error ring-2 ring-surface-container-lowest"></span>
        </button>
        
        <button className="p-1.5 text-on-surface-variant hover:text-on-surface hover:bg-surface-container rounded transition-colors" title="Docs & FAQ">
          <span className="material-symbols-outlined text-[20px]">help_outline</span>
        </button>
        
        <div className="h-5 w-[1px] bg-outline-variant/40"></div>
        
        {/* Profile */}
        <div className="flex items-center gap-space-sm pl-space-xs cursor-pointer hover:bg-surface-container-low p-1 rounded-md transition-colors">
          <div className="w-8 h-8 rounded-full bg-primary flex items-center justify-center">
            <span className="material-symbols-outlined text-on-primary text-[18px]">person</span>
          </div>
          <div className="flex flex-col text-left hidden sm:flex">
            <div className="flex items-center gap-1.5">
              <span className="font-label-md text-label-md font-semibold text-on-surface truncate max-w-[120px]">
                {user?.email ? user.email.split('@')[0] : 'User'}
              </span>
              <span className="font-label-sm text-label-sm px-1 rounded bg-primary-fixed text-on-primary-fixed leading-tight">
                {user?.role === 'ADMIN' ? 'Admin' : user?.role === 'TEACHER' ? 'Faculty' : 'Student'}
              </span>
            </div>
            <span className="font-label-sm text-label-sm text-outline truncate max-w-[150px]">
              {user?.id || 'ID Pending'}
            </span>
          </div>
          <span className="material-symbols-outlined text-[18px] text-outline">expand_more</span>
        </div>
      </div>
    </header>
  );
}
