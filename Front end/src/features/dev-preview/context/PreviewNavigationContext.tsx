'use client';
import { createContext, useContext } from 'react';

interface PreviewNavigationContextType {
  navigate: (path: string) => void;
  goBack: () => void;
}

export const PreviewNavigationContext = createContext<PreviewNavigationContextType | null>(null);

export function usePreviewNavigation() {
  return useContext(PreviewNavigationContext);
}
