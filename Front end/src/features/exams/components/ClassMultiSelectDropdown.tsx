'use client';

import React, { useState, useRef, useEffect, useId } from 'react';
import { 
  ChevronDown, 
  Check, 
  Search, 
  AlertCircle, 
  Loader2, 
  Users, 
  X,
  RotateCcw
} from 'lucide-react';
import { TeacherClassResponse } from '../api/teacher-exams.api';

export interface ClassMultiSelectDropdownProps {
  id?: string;
  classes?: TeacherClassResponse[];
  selectedClassIds: string[];
  onChange: (selectedIds: string[]) => void;
  isLoading?: boolean;
  isError?: boolean;
  errorMessage?: string;
  onRetry?: () => void;
  disabled?: boolean;
  required?: boolean;
  hasError?: boolean;
  isOpen?: boolean;
  onOpenChange?: (open: boolean) => void;
  placeholder?: string;
}

export function ClassMultiSelectDropdown({
  id = 'assigned-classes-trigger',
  classes = [],
  selectedClassIds = [],
  onChange,
  isLoading = false,
  isError = false,
  errorMessage,
  onRetry,
  disabled = false,
  required = false,
  hasError = false,
  isOpen: controlledIsOpen,
  onOpenChange,
  placeholder = 'Click to select course classes...'
}: ClassMultiSelectDropdownProps) {
  const [internalIsOpen, setInternalIsOpen] = useState(false);
  const [searchFilter, setSearchFilter] = useState('');
  const containerRef = useRef<HTMLDivElement>(null);
  const searchInputRef = useRef<HTMLInputElement>(null);
  const listboxId = useId();

  const isControlled = controlledIsOpen !== undefined;
  const isOpen = isControlled ? controlledIsOpen : internalIsOpen;

  const setIsOpen = (nextOpen: boolean) => {
    if (!isControlled) {
      setInternalIsOpen(nextOpen);
    }
    onOpenChange?.(nextOpen);
    if (!nextOpen) {
      setSearchFilter('');
    }
  };

  // Close dropdown on click outside
  useEffect(() => {
    if (!isOpen) return;

    const handlePointerDown = (event: MouseEvent | TouchEvent) => {
      if (containerRef.current && !containerRef.current.contains(event.target as Node)) {
        setIsOpen(false);
      }
    };

    document.addEventListener('mousedown', handlePointerDown);
    document.addEventListener('touchstart', handlePointerDown);
    return () => {
      document.removeEventListener('mousedown', handlePointerDown);
      document.removeEventListener('touchstart', handlePointerDown);
    };
  }, [isOpen]);

  // Focus search input when opened
  useEffect(() => {
    if (isOpen && classes.length >= 4 && searchInputRef.current) {
      searchInputRef.current.focus();
    }
  }, [isOpen, classes.length]);

  const handleToggle = () => {
    if (disabled) return;
    setIsOpen(!isOpen);
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (disabled) return;
    if (e.key === 'Enter' || e.key === ' ' || e.key === 'ArrowDown') {
      e.preventDefault();
      if (!isOpen) {
        setIsOpen(true);
      }
    } else if (e.key === 'Escape' && isOpen) {
      e.preventDefault();
      setIsOpen(false);
    }
  };

  const handleToggleClass = (classId: string) => {
    if (selectedClassIds.includes(classId)) {
      onChange(selectedClassIds.filter(id => id !== classId));
    } else {
      onChange([...selectedClassIds, classId]);
    }
  };

  const handleSelectAll = (e: React.MouseEvent) => {
    e.stopPropagation();
    if (filteredClasses.length > 0) {
      const allFilteredIds = filteredClasses.map(c => c.id);
      const merged = Array.from(new Set([...selectedClassIds, ...allFilteredIds]));
      onChange(merged);
    }
  };

  const handleClearAll = (e: React.MouseEvent) => {
    e.stopPropagation();
    onChange([]);
  };

  const filteredClasses = classes.filter(cls => {
    if (!searchFilter.trim()) return true;
    const q = searchFilter.toLowerCase();
    return (
      cls.name.toLowerCase().includes(q) ||
      cls.code.toLowerCase().includes(q) ||
      (cls.semester && cls.semester.toLowerCase().includes(q))
    );
  });

  const selectedClasses = classes.filter(c => selectedClassIds.includes(c.id));

  return (
    <div ref={containerRef} className="relative w-full">
      {/* Interactive Trigger Button */}
      <button
        id={id}
        type="button"
        disabled={disabled}
        onClick={handleToggle}
        onKeyDown={handleKeyDown}
        role="combobox"
        aria-expanded={isOpen}
        aria-haspopup="listbox"
        aria-controls={listboxId}
        aria-required={required}
        aria-invalid={hasError}
        data-testid="assigned-classes-trigger"
        className={`w-full min-h-[38px] px-3 py-2 rounded-lg text-xs flex items-center justify-between gap-2 transition-all cursor-pointer select-none text-left ${
          disabled
            ? 'bg-surface-container-low/50 border-outline-variant/20 text-outline cursor-not-allowed opacity-60'
            : hasError
            ? 'bg-rose-50/30 border-rose-400 text-on-surface hover:border-rose-500 focus:outline-none focus:ring-2 focus:ring-rose-400/40'
            : isOpen
            ? 'bg-surface-container-low border-primary ring-2 ring-primary/20 text-on-surface'
            : 'bg-surface-container-low border border-outline-variant/40 hover:border-primary/50 text-on-surface focus:outline-none focus:ring-2 focus:ring-primary/40 focus:border-primary'
        }`}
      >
        <div className="flex items-center gap-1.5 flex-wrap flex-1 min-w-0">
          {isLoading ? (
            <div className="flex items-center gap-2 text-outline">
              <Loader2 className="w-3.5 h-3.5 animate-spin text-primary" />
              <span>Loading authorized classes...</span>
            </div>
          ) : isError ? (
            <div className="flex items-center gap-1.5 text-rose-600 font-medium">
              <AlertCircle className="w-3.5 h-3.5 shrink-0" />
              <span className="truncate">Failed to load classes (Click to retry)</span>
            </div>
          ) : selectedClasses.length > 0 ? (
            <div className="flex items-center gap-1.5 flex-wrap">
              {selectedClasses.slice(0, 2).map(cls => (
                <span
                  key={cls.id}
                  className="inline-flex items-center gap-1 px-2 py-0.5 rounded bg-primary/10 text-primary border border-primary/20 text-[11px] font-mono font-semibold"
                >
                  <span>{cls.code}</span>
                  <span className="text-primary/70 font-sans font-normal truncate max-w-[100px]">{cls.name}</span>
                </span>
              ))}
              {selectedClasses.length > 2 && (
                <span className="px-1.5 py-0.5 rounded bg-surface-container-high text-on-surface-variant font-medium text-[11px]">
                  +{selectedClasses.length - 2} more
                </span>
              )}
            </div>
          ) : !isLoading && !isError && classes.length === 0 ? (
            <span className="text-outline italic">No authorized classes assigned</span>
          ) : (
            <span className="text-outline">{placeholder}</span>
          )}
        </div>

        <div className="flex items-center gap-1.5 shrink-0">
          {selectedClassIds.length > 0 && !disabled && (
            <span
              onClick={handleClearAll}
              role="button"
              tabIndex={-1}
              aria-label="Clear all selected classes"
              className="text-outline hover:text-on-surface p-0.5 rounded transition-colors"
            >
              <X className="w-3.5 h-3.5" />
            </span>
          )}
          <ChevronDown
            className={`w-4 h-4 text-outline transition-transform duration-200 shrink-0 ${
              isOpen ? 'rotate-180 text-primary' : ''
            }`}
          />
        </div>
      </button>

      {/* Dropdown Popover */}
      {isOpen && (
        <div
          id={listboxId}
          role="listbox"
          aria-multiselectable="true"
          data-testid="assigned-classes-dropdown"
          className="absolute z-50 left-0 right-0 mt-1 bg-surface-container-lowest border border-outline-variant/40 rounded-xl shadow-xl overflow-hidden flex flex-col animate-in fade-in-50 zoom-in-95 duration-150"
        >
          {/* Top Bar: Action Buttons & Filter */}
          <div className="p-2 border-b border-outline-variant/20 bg-surface-container-low/50 flex flex-col gap-2">
            <div className="flex items-center justify-between text-xs">
              <span className="font-semibold text-on-surface text-[11px] uppercase tracking-wider">
                Authorized Classes ({classes.length})
              </span>
              {classes.length > 1 && !isLoading && !isError && (
                <div className="flex items-center gap-2 text-[11px]">
                  <button
                    type="button"
                    onClick={handleSelectAll}
                    className="text-primary hover:underline font-semibold cursor-pointer"
                  >
                    Select All
                  </button>
                  <span className="text-outline">|</span>
                  <button
                    type="button"
                    onClick={handleClearAll}
                    className="text-outline hover:underline cursor-pointer"
                  >
                    Clear
                  </button>
                </div>
              )}
            </div>

            {classes.length >= 4 && (
              <div className="relative">
                <Search className="w-3.5 h-3.5 text-outline absolute left-2.5 top-1/2 -translate-y-1/2" />
                <input
                  ref={searchInputRef}
                  type="text"
                  placeholder="Filter classes by code or name..."
                  value={searchFilter}
                  onChange={(e) => setSearchFilter(e.target.value)}
                  className="w-full pl-8 pr-3 py-1 bg-surface-container-lowest border border-outline-variant/30 rounded-md text-xs text-on-surface focus:outline-none focus:border-primary transition-colors"
                />
              </div>
            )}
          </div>

          {/* Body Content */}
          <div className="max-h-56 overflow-y-auto p-1.5 flex flex-col gap-1">
            {isLoading ? (
              <div className="py-6 flex flex-col items-center justify-center gap-2 text-outline text-xs">
                <Loader2 className="w-5 h-5 animate-spin text-primary" />
                <span>Loading your authorized course classes...</span>
              </div>
            ) : isError ? (
              <div className="p-4 text-center flex flex-col items-center gap-2 text-xs bg-rose-50/50 rounded-lg">
                <AlertCircle className="w-5 h-5 text-rose-500" />
                <p className="font-semibold text-rose-800">Unable to load classes</p>
                <p className="text-[11px] text-rose-600 max-w-[260px]">
                  {errorMessage || 'There was an error communicating with the server.'}
                </p>
                {onRetry && (
                  <button
                    type="button"
                    onClick={onRetry}
                    className="mt-1 inline-flex items-center gap-1.5 px-3 py-1 bg-white border border-rose-200 rounded-md text-xs font-medium text-rose-700 hover:bg-rose-50 transition-colors shadow-xs cursor-pointer"
                  >
                    <RotateCcw className="w-3 h-3" />
                    <span>Retry</span>
                  </button>
                )}
              </div>
            ) : classes.length === 0 ? (
              <div className="py-6 px-4 text-center flex flex-col items-center gap-2 text-xs">
                <div className="w-9 h-9 rounded-full bg-surface-container flex items-center justify-center text-outline">
                  <Users className="w-5 h-5" />
                </div>
                <p className="font-semibold text-on-surface">No authorized course classes found</p>
                <p className="text-[11px] text-on-surface-variant max-w-[280px]">
                  Only classes where your teacher account is assigned as the course instructor can be selected for exams. Please contact your administrator if you need a class assigned.
                </p>
              </div>
            ) : filteredClasses.length === 0 ? (
              <div className="py-4 text-center text-xs text-outline">
                No classes match &quot;{searchFilter}&quot;
              </div>
            ) : (
              filteredClasses.map((cls) => {
                const isChecked = selectedClassIds.includes(cls.id);
                return (
                  <label
                    key={cls.id}
                    role="option"
                    aria-selected={isChecked}
                    data-testid={`class-option-${cls.code}`}
                    className={`flex items-center justify-between p-2 rounded-lg border text-xs cursor-pointer transition-colors select-none ${
                      isChecked
                        ? 'bg-primary/5 border-primary/40 text-on-surface font-medium'
                        : 'bg-surface-container-lowest border-outline-variant/20 hover:bg-surface-container-high/40 text-on-surface-variant'
                    }`}
                  >
                    <div className="flex items-center gap-2.5 min-w-0">
                      <input
                        type="checkbox"
                        checked={isChecked}
                        onChange={() => handleToggleClass(cls.id)}
                        className="w-4 h-4 rounded text-primary focus:ring-primary cursor-pointer accent-primary shrink-0"
                      />
                      <span className="font-mono font-bold text-primary shrink-0 px-1.5 py-0.5 rounded bg-primary/5">
                        {cls.code}
                      </span>
                      <span className="text-outline shrink-0">—</span>
                      <span className="truncate max-w-[200px] text-on-surface font-medium">
                        {cls.name}
                      </span>
                    </div>

                    <div className="flex items-center gap-2 shrink-0 ml-2">
                      <span className="text-[11px] text-outline px-1.5 py-0.5 rounded bg-surface-container-high/50 font-medium">
                        {cls.studentCount != null ? `${cls.studentCount} students` : '0 students'}
                      </span>
                      {isChecked && <Check className="w-3.5 h-3.5 text-primary shrink-0" />}
                    </div>
                  </label>
                );
              })
            )}
          </div>

          {/* Footer Bar */}
          <div className="px-3 py-1.5 bg-surface-container-low/60 border-t border-outline-variant/20 flex items-center justify-between text-[11px] text-outline">
            <span>
              <strong className="text-on-surface font-semibold">{selectedClassIds.length}</strong> of{' '}
              <strong className="text-on-surface font-semibold">{classes.length}</strong> classes selected
            </span>
            <button
              type="button"
              onClick={() => setIsOpen(false)}
              className="text-primary hover:underline font-medium cursor-pointer"
            >
              Done
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
