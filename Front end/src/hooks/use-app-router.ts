'use client';
import { useRouter as useNextRouter } from 'next/navigation';
import { usePreviewNavigation } from '@/features/dev-preview/context/PreviewNavigationContext';

export function useAppRouter() {
  const nextRouter = useNextRouter();
  const previewNav = usePreviewNavigation();

  if (previewNav) {
    return {
      push: (path: string) => previewNav.navigate(path),
      replace: (path: string) => previewNav.navigate(path),
      back: () => previewNav.goBack(),
    };
  }

  return nextRouter;
}
