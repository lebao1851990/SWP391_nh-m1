'use client';

import React from 'react';
import { LanguageSwitcher } from './LanguageSwitcher';
import { ThemeSwitcher } from './ThemeSwitcher';

export function LocaleThemeBar({ className = '' }: { className?: string }) {
  return (
    <div className={`flex items-center gap-1.5 ${className}`}>
      <LanguageSwitcher />
      <ThemeSwitcher />
    </div>
  );
}
