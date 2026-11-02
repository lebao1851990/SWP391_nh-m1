'use client';

import React, { useState, useRef, useEffect } from 'react';
import { useTranslation, Language } from '@/i18n';
import { Globe, Check, ChevronDown } from 'lucide-react';

export function LanguageSwitcher() {
  const { language, setLanguage, t } = useTranslation();
  const [isOpen, setIsOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setIsOpen(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const handleSelect = (lang: Language) => {
    setLanguage(lang);
    setIsOpen(false);
  };

  return (
    <div className="relative inline-block text-left" ref={containerRef} id="language-switcher">
      <button
        type="button"
        onClick={() => setIsOpen(!isOpen)}
        className="inline-flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg border border-outline-variant/40 bg-surface-container-low hover:bg-surface-container text-on-surface text-xs font-medium transition-colors cursor-pointer shadow-2xs"
        aria-label="Select language"
        aria-expanded={isOpen}
      >
        <Globe className="w-3.5 h-3.5 text-primary" />
        <span className="font-semibold">{language === 'vi' ? '🇻🇳 VI' : '🇬🇧 EN'}</span>
        <ChevronDown className="w-3 h-3 text-outline opacity-70" />
      </button>

      {isOpen && (
        <div className="absolute right-0 mt-1.5 w-36 rounded-xl border border-outline-variant/40 bg-surface-container-lowest shadow-xl z-50 py-1 flex flex-col gap-0.5 animate-in fade-in zoom-in-95 duration-100">
          <button
            type="button"
            onClick={() => handleSelect('vi')}
            className={`flex items-center justify-between px-3 py-2 text-xs transition-colors cursor-pointer text-left ${
              language === 'vi'
                ? 'bg-primary/10 text-primary font-semibold'
                : 'text-on-surface hover:bg-surface-container'
            }`}
          >
            <span className="flex items-center gap-1.5">
              <span>🇻🇳</span>
              <span>Tiếng Việt</span>
            </span>
            {language === 'vi' && <Check className="w-3.5 h-3.5 text-primary" />}
          </button>

          <button
            type="button"
            onClick={() => handleSelect('en')}
            className={`flex items-center justify-between px-3 py-2 text-xs transition-colors cursor-pointer text-left ${
              language === 'en'
                ? 'bg-primary/10 text-primary font-semibold'
                : 'text-on-surface hover:bg-surface-container'
            }`}
          >
            <span className="flex items-center gap-1.5">
              <span>🇬🇧</span>
              <span>English</span>
            </span>
            {language === 'en' && <Check className="w-3.5 h-3.5 text-primary" />}
          </button>
        </div>
      )}
    </div>
  );
}
