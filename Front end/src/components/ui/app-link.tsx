'use client';
import React from 'react';
import NextLink from 'next/link';
import { usePreviewNavigation } from '@/features/dev-preview/context/PreviewNavigationContext';

type AppLinkProps = React.ComponentProps<typeof NextLink> & React.AnchorHTMLAttributes<HTMLAnchorElement>;

export function AppLink({ href, children, onClick, ...props }: AppLinkProps) {
  const previewNav = usePreviewNavigation();

  // If we're inside the Dev Preview sandbox and it's a string href (internal route)
  if (previewNav && typeof href === 'string') {
    const isInternal = href.startsWith('/student') || href.startsWith('/teacher') || href.startsWith('/admin');
    
    if (isInternal) {
      return (
        <a 
          href={href}
          onClick={(e) => {
            e.preventDefault();
            if (onClick) onClick(e);
            previewNav.navigate(href);
          }}
          {...props}
        >
          {children}
        </a>
      );
    }
  }

  // Production fallback: normal Next.js Link
  return (
    <NextLink href={href} onClick={onClick} {...props}>
      {children}
    </NextLink>
  );
}
