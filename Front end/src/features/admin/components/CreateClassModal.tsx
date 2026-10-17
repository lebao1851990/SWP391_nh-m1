'use client';

import React, { useState } from 'react';
import { X, Loader2, Plus, GraduationCap } from 'lucide-react';
import { toast } from 'sonner';
import { useCreateAdminClass, useAdminUsers } from '../hooks';

interface CreateClassModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export function CreateClassModal({ isOpen, onClose }: CreateClassModalProps) {
  const [name, setName] = useState('');
  const [code, setCode] = useState('');
  const [semester, setSemester] = useState('');
  const [teacherId, setTeacherId] = useState('');

  const { data: users, isLoading: isLoadingUsers } = useAdminUsers();
  const createClassMutation = useCreateAdminClass();

  const teachers = React.useMemo(() => {
    return users?.filter((u) => u.role === 'TEACHER') || [];
  }, [users]);

  if (!isOpen) return null;

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();

    if (!name.trim() || !code.trim() || !semester.trim() || !teacherId.trim()) {
      toast.error('All fields including teacher assignment are required.');
      return;
    }

    createClassMutation.mutate(
      {
        name: name.trim(),
        code: code.trim().toUpperCase(),
        semester: semester.trim(),
        teacherId: teacherId.trim(),
      },
      {
        onSuccess: () => {
          toast.success(`Class ${code.trim().toUpperCase()} created successfully!`);
          setName('');
          setCode('');
          setSemester('');
          setTeacherId('');
          onClose();
        },
        onError: (err: unknown) => {
          const res = (err as { response?: { status?: number; data?: { message?: string } } })?.response;
          const status = res?.status;
          let msg = res?.data?.message || (err as { message?: string })?.message || 'Failed to create class.';

          if (status === 400) {
            msg = res?.data?.message || 'Invalid class information or teacher is not assigned to TEACHER role.';
          } else if (status === 403) {
            msg = 'Permission denied. Admin role required.';
          } else if (status === 404) {
            msg = 'Selected teacher was not found.';
          } else if (status === 409) {
            msg = 'A course with this code already exists.';
          }

          toast.error(msg);
        },
      }
    );
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-xs p-4 animate-in fade-in duration-150">
      <div 
        className="bg-surface-container-lowest w-full max-w-lg rounded-2xl shadow-2xl border border-outline-variant/30 flex flex-col overflow-hidden animate-in zoom-in-95 duration-200"
        role="dialog"
        aria-modal="true"
        aria-labelledby="create-class-modal-title"
      >
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-outline-variant/30 bg-surface-container-low">
          <div className="flex items-center gap-2.5">
            <div className="p-2 rounded-lg bg-primary/10 text-primary">
              <GraduationCap className="w-5 h-5" />
            </div>
            <div>
              <h2 id="create-class-modal-title" className="font-headline-sm text-headline-sm font-semibold text-on-surface">
                Create New Class
              </h2>
              <p className="text-xs text-on-surface-variant">Add a course cohort and assign an instructor</p>
            </div>
          </div>
          <button
            onClick={onClose}
            disabled={createClassMutation.isPending}
            className="p-1.5 rounded-lg text-on-surface-variant hover:text-on-surface hover:bg-surface-container transition-colors disabled:opacity-50 cursor-pointer"
            aria-label="Close dialog"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Form Body */}
        <form onSubmit={handleSubmit} className="p-6 flex flex-col gap-4">
          <div className="flex flex-col gap-1.5">
            <label className="text-xs font-semibold uppercase tracking-wider text-outline">
              Course Name <span className="text-error">*</span>
            </label>
            <input
              type="text"
              required
              disabled={createClassMutation.isPending}
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="e.g. Data Structures & Algorithms"
              className="px-3.5 py-2.5 rounded-lg bg-surface-container-lowest border border-outline-variant/60 focus:border-primary focus:ring-1 focus:ring-primary text-sm text-on-surface outline-none transition-all disabled:opacity-50"
            />
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div className="flex flex-col gap-1.5">
              <label className="text-xs font-semibold uppercase tracking-wider text-outline">
                Course Code <span className="text-error">*</span>
              </label>
              <input
                type="text"
                required
                disabled={createClassMutation.isPending}
                value={code}
                onChange={(e) => setCode(e.target.value)}
                placeholder="e.g. CS102"
                className="px-3.5 py-2.5 rounded-lg bg-surface-container-lowest border border-outline-variant/60 focus:border-primary focus:ring-1 focus:ring-primary text-sm font-mono text-on-surface outline-none transition-all uppercase disabled:opacity-50"
              />
            </div>

            <div className="flex flex-col gap-1.5">
              <label className="text-xs font-semibold uppercase tracking-wider text-outline">
                Semester <span className="text-error">*</span>
              </label>
              <input
                type="text"
                required
                disabled={createClassMutation.isPending}
                value={semester}
                onChange={(e) => setSemester(e.target.value)}
                placeholder="e.g. Fall 2026"
                className="px-3.5 py-2.5 rounded-lg bg-surface-container-lowest border border-outline-variant/60 focus:border-primary focus:ring-1 focus:ring-primary text-sm text-on-surface outline-none transition-all disabled:opacity-50"
              />
            </div>
          </div>

          <div className="flex flex-col gap-1.5">
            <label className="text-xs font-semibold uppercase tracking-wider text-outline">
              Instructor / Teacher Assignment <span className="text-error">*</span>
            </label>
            {isLoadingUsers ? (
              <div className="px-3.5 py-2.5 rounded-lg bg-surface-container-low border border-outline-variant/40 text-xs text-on-surface-variant flex items-center gap-2">
                <Loader2 className="w-3.5 h-3.5 animate-spin text-primary" />
                <span>Loading available instructors...</span>
              </div>
            ) : teachers.length > 0 ? (
              <select
                required
                disabled={createClassMutation.isPending}
                value={teacherId}
                onChange={(e) => setTeacherId(e.target.value)}
                className="px-3.5 py-2.5 rounded-lg bg-surface-container-lowest border border-outline-variant/60 focus:border-primary focus:ring-1 focus:ring-primary text-sm text-on-surface outline-none transition-all disabled:opacity-50"
              >
                <option value="">-- Select Instructor --</option>
                {teachers.map((t) => (
                  <option key={t.id} value={t.id}>
                    {t.name} ({t.email})
                  </option>
                ))}
              </select>
            ) : (
              <input
                type="text"
                required
                disabled={createClassMutation.isPending}
                value={teacherId}
                onChange={(e) => setTeacherId(e.target.value)}
                placeholder="Enter Teacher User ID (UUID)"
                className="px-3.5 py-2.5 rounded-lg bg-surface-container-lowest border border-outline-variant/60 focus:border-primary focus:ring-1 focus:ring-primary text-sm font-mono text-on-surface outline-none transition-all disabled:opacity-50"
              />
            )}
          </div>

          {/* Action Buttons */}
          <div className="flex items-center justify-end gap-3 pt-4 border-t border-outline-variant/30 mt-2">
            <button
              type="button"
              onClick={onClose}
              disabled={createClassMutation.isPending}
              className="px-4 py-2 rounded-lg text-sm font-semibold text-on-surface-variant hover:bg-surface-container transition-colors disabled:opacity-50 cursor-pointer"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={createClassMutation.isPending}
              className="flex items-center gap-2 px-5 py-2 rounded-lg bg-primary text-on-primary text-sm font-semibold hover:bg-primary-container transition-colors shadow-sm disabled:opacity-50 disabled:cursor-not-allowed cursor-pointer"
            >
              {createClassMutation.isPending ? (
                <>
                  <Loader2 className="w-4 h-4 animate-spin" />
                  <span>Creating...</span>
                </>
              ) : (
                <>
                  <Plus className="w-4 h-4" />
                  <span>Create Class</span>
                </>
              )}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
