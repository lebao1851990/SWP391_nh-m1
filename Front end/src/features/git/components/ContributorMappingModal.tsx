'use client';

import React, { useState } from 'react';
import { X, UserPlus, Check, AlertCircle } from 'lucide-react';
import { ContributorSummary } from '../types';

interface ContributorMappingModalProps {
  isOpen: boolean;
  contributor: ContributorSummary | null;
  onClose: () => void;
  onSave: (gitAuthorEmail: string, gitAuthorName: string, userId: string) => Promise<void>;
}

export const ContributorMappingModal: React.FC<ContributorMappingModalProps> = ({
  isOpen,
  contributor,
  onClose,
  onSave,
}) => {
  const [userId, setUserId] = useState('');
  const [isSaving, setIsSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (!isOpen || !contributor) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!userId.trim()) {
      setError('Please provide a valid Student User ID.');
      return;
    }

    const authorEmail = contributor.authorEmail || (contributor as unknown as { gitAuthorEmail?: string }).gitAuthorEmail || '';
    const authorName = contributor.authorName || (contributor as unknown as { gitAuthorName?: string }).gitAuthorName || 'Unknown';

    setIsSaving(true);
    setError(null);
    try {
      await onSave(authorEmail, authorName, userId.trim());
      onClose();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to save mapping.';
      setError(msg);
    } finally {
      setIsSaving(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 backdrop-blur-xs p-4">
      <div className="bg-white rounded-xl shadow-xl border border-slate-200 w-full max-w-md overflow-hidden animate-in fade-in zoom-in-95 duration-200">
        <div className="flex items-center justify-between px-5 py-4 border-b border-slate-100">
          <div className="flex items-center gap-2">
            <UserPlus className="w-5 h-5 text-blue-600" />
            <h3 className="font-bold text-slate-900 text-sm">Map Git Contributor</h3>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="text-slate-400 hover:text-slate-600 p-1 rounded-md transition-colors cursor-pointer"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="p-5 space-y-4 text-xs">
          <div>
            <label className="block text-slate-500 font-medium mb-1">Git Author Email</label>
            <input
              type="text"
              readOnly
              value={contributor.authorEmail || (contributor as unknown as { gitAuthorEmail?: string }).gitAuthorEmail || ''}
              className="w-full bg-slate-100 border border-slate-200 rounded px-3 py-2 font-mono text-slate-700 cursor-not-allowed"
            />
          </div>

          <div>
            <label className="block text-slate-500 font-medium mb-1">Git Author Name</label>
            <input
              type="text"
              readOnly
              value={contributor.authorName || (contributor as unknown as { gitAuthorName?: string }).gitAuthorName || 'Unknown'}
              className="w-full bg-slate-100 border border-slate-200 rounded px-3 py-2 text-slate-700 cursor-not-allowed"
            />
          </div>

          <div>
            <label className="block text-slate-700 font-medium mb-1">
              CodeAssess Student User ID <span className="text-rose-500">*</span>
            </label>
            <input
              type="text"
              required
              placeholder="e.g. user-alice-123 or student UUID"
              value={userId}
              onChange={(e) => setUserId(e.target.value)}
              className="w-full bg-white border border-slate-300 rounded px-3 py-2 text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
            <p className="text-slate-500 text-[11px] mt-1">
              All future and existing commits authored by this email in this repository will be attributed to this student.
            </p>
          </div>

          {error && (
            <div className="p-2.5 rounded bg-rose-50 border border-rose-200 text-rose-700 flex items-start gap-2">
              <AlertCircle className="w-4 h-4 shrink-0 mt-0.5" />
              <span>{error}</span>
            </div>
          )}

          <div className="flex items-center justify-end gap-2 pt-3 border-t border-slate-100">
            <button
              type="button"
              onClick={onClose}
              disabled={isSaving}
              className="px-3.5 py-1.5 rounded text-slate-600 hover:bg-slate-100 font-medium transition-colors cursor-pointer"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={isSaving}
              className="inline-flex items-center gap-1.5 px-4 py-1.5 rounded bg-blue-600 hover:bg-blue-700 text-white font-medium shadow-xs disabled:opacity-50 transition-colors cursor-pointer"
            >
              <Check className="w-4 h-4" />
              {isSaving ? 'Saving...' : 'Confirm Mapping'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

