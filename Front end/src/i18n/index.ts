'use client';

import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import { en } from './locales/en';
import { vi } from './locales/vi';
import { Locale, Translations } from './types';

export * from './types';

export const translations: Record<Locale, Translations> = {
  en,
  vi,
};

interface LocaleState {
  locale: Locale;
  setLocale: (locale: Locale) => void;
}

export const useLocaleStore = create<LocaleState>()(
  persist(
    (set) => ({
      locale: 'en',
      setLocale: (locale) => {
        if (typeof document !== 'undefined') {
          document.cookie = `codeassess_locale=${locale}; path=/; max-age=31536000; SameSite=Lax`;
          document.documentElement.lang = locale;
        }
        set({ locale });
      },
    }),
    {
      name: 'codeassess_locale',
    }
  )
);

// Type-safe nested key path resolver
type NestedKeyOf<ObjectType extends object> = {
  [Key in keyof ObjectType & (string | number)]: ObjectType[Key] extends object
    ? `${Key}.${NestedKeyOf<ObjectType[Key]>}`
    : `${Key}`;
}[keyof ObjectType & (string | number)];

export type TranslationKey = NestedKeyOf<Translations>;

export function getTranslation(
  locale: Locale,
  keyPath: string,
  params?: Record<string, string | number>
): string {
  const dict = translations[locale] || translations.en;
  const parts = keyPath.split('.');
  
  let current: unknown = dict;
  for (const part of parts) {
    if (current && typeof current === 'object' && part in current) {
      current = (current as Record<string, unknown>)[part];
    } else {
      // Fallback to English if missing in target locale
      current = undefined;
      break;
    }
  }

  if (typeof current !== 'string') {
    let fallback: unknown = translations.en;
    for (const part of parts) {
      if (fallback && typeof fallback === 'object' && part in fallback) {
        fallback = (fallback as Record<string, unknown>)[part];
      } else {
        fallback = undefined;
        break;
      }
    }
    current = typeof fallback === 'string' ? fallback : keyPath;
  }

  if (params && typeof current === 'string') {
    return Object.entries(params).reduce((str, [paramKey, val]) => {
      return str.replace(new RegExp(`\\{${paramKey}\\}`, 'g'), String(val));
    }, current);
  }

  return typeof current === 'string' ? current : keyPath;
}

export type TranslationFunction = ((key: TranslationKey, params?: Record<string, string | number>) => string) & Translations;

export function useTranslation() {
  const locale = useLocaleStore((state) => state.locale);
  const setLocale = useLocaleStore((state) => state.setLocale);

  const currentTranslations = translations[locale] || translations.en;
  const fn = (key: TranslationKey, params?: Record<string, string | number>): string => {
    return getTranslation(locale, key, params);
  };
  const t = Object.assign(fn, currentTranslations) as TranslationFunction;

  const formatDate = (
    date: Date | string | number,
    options?: Intl.DateTimeFormatOptions
  ): string => {
    try {
      const d = typeof date === 'string' || typeof date === 'number' ? new Date(date) : date;
      const intlLocale = locale === 'vi' ? 'vi-VN' : 'en-US';
      const defaultOptions: Intl.DateTimeFormatOptions = {
        year: 'numeric',
        month: 'short',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
        ...options,
      };
      return new Intl.DateTimeFormat(intlLocale, defaultOptions).format(d);
    } catch {
      return String(date);
    }
  };

  const formatNumber = (
    value: number,
    options?: Intl.NumberFormatOptions
  ): string => {
    try {
      const intlLocale = locale === 'vi' ? 'vi-VN' : 'en-US';
      return new Intl.NumberFormat(intlLocale, options).format(value);
    } catch {
      return String(value);
    }
  };

  const formatPercent = (value: number, decimals: number = 1): string => {
    try {
      const intlLocale = locale === 'vi' ? 'vi-VN' : 'en-US';
      return new Intl.NumberFormat(intlLocale, {
        style: 'percent',
        minimumFractionDigits: decimals,
        maximumFractionDigits: decimals,
      }).format(value > 1 ? value / 100 : value);
    } catch {
      return `${value}%`;
    }
  };

  return {
    t,
    locale,
    setLocale,
    formatDate,
    formatNumber,
    formatPercent,
    isVietnamese: locale === 'vi',
    isEnglish: locale === 'en',
  };
}
