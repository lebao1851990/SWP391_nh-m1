'use client';

import { useEffect, useRef } from 'react';

interface UseModalA11yOptions {
  isOpen: boolean;
  onClose: () => void;
  modalRef: React.RefObject<HTMLElement | null>;
  initialFocusRef?: React.RefObject<HTMLElement | null>;
  closeOnEscape?: boolean;
  lockScroll?: boolean;
}

const FOCUSABLE_SELECTOR = [
  'a[href]',
  'button:not([disabled])',
  'textarea:not([disabled])',
  'input:not([disabled])',
  'select:not([disabled])',
  '[tabindex]:not([tabindex="-1"])',
].join(', ');

// Global Reference Counting for Scroll Lock to prevent conflicts with multiple/nested modals
let activeModalCount = 0;
let originalBodyOverflow = '';

function acquireScrollLock() {
  if (typeof document === 'undefined') return;
  if (activeModalCount === 0) {
    originalBodyOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
  }
  activeModalCount++;
}

function releaseScrollLock() {
  if (typeof document === 'undefined') return;
  activeModalCount = Math.max(0, activeModalCount - 1);
  if (activeModalCount === 0) {
    document.body.style.overflow = originalBodyOverflow;
  }
}

export function useModalA11y({
  isOpen,
  onClose,
  modalRef,
  initialFocusRef,
  closeOnEscape = true,
  lockScroll = true,
}: UseModalA11yOptions) {
  const triggerElementRef = useRef<HTMLElement | null>(null);

  useEffect(() => {
    if (!isOpen) return;

    // 1. Capture the specific element that had focus when this modal opened
    if (typeof document !== 'undefined' && document.activeElement instanceof HTMLElement) {
      triggerElementRef.current = document.activeElement;
    }

    // 2. Lock body scroll with global reference counting
    if (lockScroll) {
      acquireScrollLock();
    }

    // 3. Set initial focus into modal
    const focusTimer = setTimeout(() => {
      if (initialFocusRef?.current) {
        initialFocusRef.current.focus();
        return;
      }

      if (!modalRef.current) return;

      // In forms, prioritize the first visible text input, textarea, or select
      const inputs = modalRef.current.querySelectorAll<HTMLElement>(
        'input:not([type="hidden"]):not([disabled]), textarea:not([disabled]), select:not([disabled])'
      );
      const visibleInputs = Array.from(inputs).filter((el) => el.offsetParent !== null);

      if (visibleInputs.length > 0) {
        visibleInputs[0].focus();
        return;
      }

      // Otherwise fall back to the first visible focusable control (e.g. Cancel button in alertdialog)
      const focusable = Array.from(
        modalRef.current.querySelectorAll<HTMLElement>(FOCUSABLE_SELECTOR)
      ).filter((el) => el.offsetParent !== null);

      if (focusable.length > 0) {
        focusable[0].focus();
      } else {
        modalRef.current.focus();
      }
    }, 50);

    // 4. Keyboard trap and Escape listener
    const handleKeyDown = (e: KeyboardEvent) => {
      if (!modalRef.current) return;

      if (closeOnEscape && e.key === 'Escape') {
        e.stopPropagation();
        e.preventDefault();
        onClose();
        return;
      }

      if (e.key === 'Tab') {
        const focusableElements = Array.from(
          modalRef.current.querySelectorAll<HTMLElement>(FOCUSABLE_SELECTOR)
        ).filter((el) => el.offsetParent !== null);

        if (focusableElements.length === 0) {
          e.preventDefault();
          return;
        }

        const firstElement = focusableElements[0];
        const lastElement = focusableElements[focusableElements.length - 1];

        if (e.shiftKey) {
          if (document.activeElement === firstElement || !modalRef.current.contains(document.activeElement)) {
            e.preventDefault();
            lastElement.focus();
          }
        } else {
          if (document.activeElement === lastElement || !modalRef.current.contains(document.activeElement)) {
            e.preventDefault();
            firstElement.focus();
          }
        }
      }
    };

    document.addEventListener('keydown', handleKeyDown);

    return () => {
      clearTimeout(focusTimer);
      document.removeEventListener('keydown', handleKeyDown);

      if (lockScroll) {
        releaseScrollLock();
      }

      // Restore focus to the exact trigger element that opened this modal
      const trigger = triggerElementRef.current;
      if (trigger && typeof trigger.focus === 'function') {
        setTimeout(() => {
          trigger.focus();
        }, 0);
      }
    };
  }, [isOpen, onClose, modalRef, initialFocusRef, closeOnEscape, lockScroll]);
}
