import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import { vi } from './locales/vi';
import { en } from './locales/en';

export type Language = 'vi' | 'en';

export const translations = {
  vi,
  en,
} as const;

interface LanguageState {
  language: Language;
  setLanguage: (lang: Language) => void;
}

export const useLanguageStore = create<LanguageState>()(
  persist(
    (set) => ({
      language: 'vi',
      setLanguage: (language: Language) => set({ language }),
    }),
    {
      name: 'codeassess-language',
    }
  )
);

// Helper to access nested keys safely (e.g., 'teacherExams.deleteModalTitle')
function getNestedValue(obj: Record<string, unknown>, path: string): string | undefined {
  const parts = path.split('.');
  let current: unknown = obj;
  for (const part of parts) {
    if (current && typeof current === 'object' && part in current) {
      current = (current as Record<string, unknown>)[part];
    } else {
      return undefined;
    }
  }
  return typeof current === 'string' ? current : undefined;
}

export function useTranslation() {
  const language = useLanguageStore((state) => state.language);
  const setLanguage = useLanguageStore((state) => state.setLanguage);

  const t = (key: string, params?: Record<string, string | number>): string => {
    const currentDict = translations[language] as unknown as Record<string, unknown>;
    const fallbackDict = translations.vi as unknown as Record<string, unknown>;

    let value = getNestedValue(currentDict, key) || getNestedValue(fallbackDict, key) || key;

    if (params && typeof value === 'string') {
      Object.entries(params).forEach(([paramKey, paramValue]) => {
        value = (value as string).replace(new RegExp(`\\{${paramKey}\\}`, 'g'), String(paramValue));
      });
    }

    return value;
  };

  return {
    t,
    language,
    setLanguage,
    isVi: language === 'vi',
    isEn: language === 'en',
  };
}
