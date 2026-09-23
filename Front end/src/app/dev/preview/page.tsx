import { notFound } from 'next/navigation';
import { DevPreviewView } from '@/features/dev-preview/components/DevPreviewView';

export default function DevPreviewPage() {
  if (process.env.NODE_ENV === 'production') {
    notFound();
  }

  return <DevPreviewView />;
}
