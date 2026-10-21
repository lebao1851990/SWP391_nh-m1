'use client';

import React, { useState, useRef, useEffect } from 'react';
import { useTranslation } from '@/i18n';
import { Languages, Check } from 'lucide-react';

export function LanguageSwitcher({ className = '' }: { className?: string }) {
  const { locale, setLocale, t } = useTranslation();
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

  return (
    <div className={`relative ${className}`} ref={dropdownRef}>
      <button
        type="button"
        onClick={() => setIsOpen(!isOpen)}
        aria-label={t('locale.languageLabel')}
        aria-expanded={isOpen}
        data-testid="language-switcher-trigger"
        className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg border border-outline-variant/40 hover:border-outline-variant bg-surface-container-lowest hover:bg-surface-container-low text-on-surface text-xs font-medium transition-all shadow-xs cursor-pointer focus:outline-none focus:ring-2 focus:ring-primary/40"
      >
        <Languages className="w-4 h-4 text-primary shrink-0" />
        <span className="uppercase font-mono font-semibold text-[11px] tracking-wide">
          {locale}
        </span>
      </button>

      {isOpen && (
        <div
          role="menu"
          aria-orientation="vertical"
          data-testid="language-dropdown-menu"
          className="absolute right-0 mt-1.5 w-36 bg-surface-container-lowest border border-outline-variant/50 rounded-xl shadow-lg z-50 p-1 flex flex-col gap-0.5 animate-in fade-in zoom-in-95 duration-100"
        >
          <button
            type="button"
            role="menuitem"
            data-testid="language-select-en"
            onClick={() => {
              setLocale('en');
              setIsOpen(false);
            }}
            className={`flex items-center justify-between w-full px-2.5 py-1.5 text-xs rounded-lg transition-colors cursor-pointer text-left ${
              locale === 'en'
                ? 'bg-primary/10 text-primary font-semibold'
                : 'text-on-surface hover:bg-surface-container-low'
            }`}
          >
            <span>English</span>
            {locale === 'en' && <Check className="w-3.5 h-3.5 text-primary" />}
          </button>

          <button
            type="button"
            role="menuitem"
            data-testid="language-select-vi"
            onClick={() => {
              setLocale('vi');
              setIsOpen(false);
            }}
            className={`flex items-center justify-between w-full px-2.5 py-1.5 text-xs rounded-lg transition-colors cursor-pointer text-left ${
              locale === 'vi'
                ? 'bg-primary/10 text-primary font-semibold'
                : 'text-on-surface hover:bg-surface-container-low'
            }`}
          >
            <span>Tiếng Việt</span>
            {locale === 'vi' && <Check className="w-3.5 h-3.5 text-primary" />}
          </button>
        </div>
      )}
    </div>
  );
}
