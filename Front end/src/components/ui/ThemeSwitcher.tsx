'use client';

import React, { useState, useRef, useEffect } from 'react';
import { useThemeStore, ThemeMode } from '@/stores/theme.store';
import { useTranslation } from '@/i18n';
import { Sun, Moon, Laptop, Check } from 'lucide-react';

export function ThemeSwitcher({ className = '' }: { className?: string }) {
  const { theme, resolvedTheme, setTheme } = useThemeStore();
  const { t } = useTranslation();
  const [isOpen, setIsOpen] = useState(false);
  const dropdownRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (dropdownRef.current && !dropdownRef.current.contains(e.target as Node)) {
        setIsOpen(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const currentIcon = () => {
    if (theme === 'system') {
      return <Laptop className="w-4 h-4 text-primary shrink-0" />;
    }
    if (resolvedTheme === 'dark') {
      return <Moon className="w-4 h-4 text-indigo-400 shrink-0" />;
    }
    return <Sun className="w-4 h-4 text-amber-500 shrink-0" />;
  };

  const options: { mode: ThemeMode; labelKey: 'theme.light' | 'theme.dark' | 'theme.system'; icon: React.ReactNode }[] = [
    { mode: 'light', labelKey: 'theme.light', icon: <Sun className="w-3.5 h-3.5 text-amber-500" /> },
    { mode: 'dark', labelKey: 'theme.dark', icon: <Moon className="w-3.5 h-3.5 text-indigo-400" /> },
    { mode: 'system', labelKey: 'theme.system', icon: <Laptop className="w-3.5 h-3.5 text-slate-400" /> },
  ];

  return (
    <div className={`relative ${className}`} ref={dropdownRef}>
      <button
        type="button"
        onClick={() => setIsOpen(!isOpen)}
        aria-label={t('theme.themeLabel')}
        aria-expanded={isOpen}
        data-testid="theme-switcher-trigger"
        className="flex items-center justify-center p-1.5 rounded-lg border border-outline-variant/40 hover:border-outline-variant bg-surface-container-lowest hover:bg-surface-container-low text-on-surface transition-all shadow-xs cursor-pointer focus:outline-none focus:ring-2 focus:ring-primary/40"
      >
        {currentIcon()}
      </button>

      {isOpen && (
        <div
          role="menu"
          aria-orientation="vertical"
          data-testid="theme-dropdown-menu"
          className="absolute right-0 mt-1.5 w-36 bg-surface-container-lowest border border-outline-variant/50 rounded-xl shadow-lg z-50 p-1 flex flex-col gap-0.5 animate-in fade-in zoom-in-95 duration-100"
        >
          {options.map((opt) => (
            <button
              key={opt.mode}
              type="button"
              role="menuitem"
              data-testid={`theme-select-${opt.mode}`}
              onClick={() => {
                setTheme(opt.mode);
                setIsOpen(false);
              }}
              className={`flex items-center justify-between w-full px-2.5 py-1.5 text-xs rounded-lg transition-colors cursor-pointer text-left ${
                theme === opt.mode
                  ? 'bg-primary/10 text-primary font-semibold'
                  : 'text-on-surface hover:bg-surface-container-low'
              }`}
            >
              <div className="flex items-center gap-2">
                {opt.icon}
                <span>{t(opt.labelKey)}</span>
              </div>
              {theme === opt.mode && <Check className="w-3.5 h-3.5 text-primary" />}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
