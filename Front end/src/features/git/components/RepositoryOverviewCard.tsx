'use client';

import React, { useState } from 'react';
import { GitBranch, RefreshCw, ExternalLink, GitCommit, Users, FileCode, CheckCircle2, Clock, AlertCircle } from 'lucide-react';
import { GitRepository, RepositoryAnalytics } from '../types';

interface RepositoryOverviewCardProps {
  repository: GitRepository;
  analytics?: RepositoryAnalytics;
  onSync: () => Promise<void>;
  canSync?: boolean;
}

export const RepositoryOverviewCard: React.FC<RepositoryOverviewCardProps> = ({
  repository,
  analytics,
  onSync,
  canSync = true,
}) => {
  const [isSyncing, setIsSyncing] = useState(false);

  const handleSyncClick = async () => {
    setIsSyncing(true);
    try {
      await onSync();
    } finally {
      setIsSyncing(false);
    }
  };

  const getStatusBadge = (status: string) => {
    switch (status) {
      case 'SYNCED':
        return (
          <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-medium bg-emerald-100 text-emerald-800 border border-emerald-300">
            <CheckCircle2 className="w-3.5 h-3.5 text-emerald-600" />
            Synced
          </span>
        );
      case 'FAILED':
        return (
          <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-medium bg-rose-100 text-rose-800 border border-rose-300">
            <AlertCircle className="w-3.5 h-3.5 text-rose-600" />
            Sync Failed
          </span>
        );
      default:
        return (
          <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-medium bg-amber-100 text-amber-800 border border-amber-300">
            <Clock className="w-3.5 h-3.5 text-amber-600" />
            Pending Sync
          </span>
        );
    }
  };

  const formattedLastSynced = repository.lastSyncedAt
    ? new Date(repository.lastSyncedAt).toLocaleString()
    : 'Never';

  return (
    <div className="bg-white rounded-xl border border-slate-200 shadow-xs p-5 mb-6">
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 pb-4 border-b border-slate-100">
        <div>
          <div className="flex items-center gap-2.5 flex-wrap">
            <h2 className="text-xl font-bold text-slate-900">{repository.name}</h2>
            <span className="px-2 py-0.5 text-xs font-semibold rounded bg-slate-100 text-slate-700 border border-slate-200">
              {repository.provider}
            </span>
            {getStatusBadge(repository.syncStatus)}
          </div>
          <div className="flex items-center gap-4 text-xs text-slate-500 mt-2 flex-wrap">
            <a
              href={repository.repoUrl}
              target="_blank"
              rel="noopener noreferrer"
              className="flex items-center gap-1 hover:text-blue-600 transition-colors"
            >
              <span className="truncate max-w-xs">{repository.repoUrl}</span>
              <ExternalLink className="w-3 h-3 shrink-0" />
            </a>
            <span className="inline-flex items-center gap-1">
              <GitBranch className="w-3.5 h-3.5 text-slate-400" />
              {repository.defaultBranch || 'main'}
            </span>
            <span>Last synced: {formattedLastSynced}</span>
          </div>
        </div>

        {canSync && (
          <button
            type="button"
            onClick={handleSyncClick}
            disabled={isSyncing}
            className="inline-flex items-center justify-center gap-2 px-4 py-2 text-sm font-medium text-white bg-blue-600 rounded-lg hover:bg-blue-700 disabled:opacity-50 transition-colors shadow-xs shrink-0 cursor-pointer"
          >
            <RefreshCw className={`w-4 h-4 ${isSyncing ? 'animate-spin' : ''}`} />
            {isSyncing ? 'Syncing...' : 'Sync Commits'}
          </button>
        )}
      </div>

      {analytics && (
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4 pt-4">
          <div className="bg-slate-50 p-3.5 rounded-lg border border-slate-100">
            <div className="flex items-center gap-2 text-slate-500 text-xs font-medium">
              <GitCommit className="w-4 h-4 text-blue-500" />
              Total Commits
            </div>
            <div className="text-2xl font-bold text-slate-900 mt-1">
              {analytics.totalCommits.toLocaleString()}
            </div>
          </div>

          <div className="bg-slate-50 p-3.5 rounded-lg border border-slate-100">
            <div className="flex items-center gap-2 text-slate-500 text-xs font-medium">
              <FileCode className="w-4 h-4 text-emerald-500" />
              Lines Added
            </div>
            <div className="text-2xl font-bold text-emerald-600 mt-1">
              +{analytics.totalLinesAdded.toLocaleString()}
            </div>
          </div>

          <div className="bg-slate-50 p-3.5 rounded-lg border border-slate-100">
            <div className="flex items-center gap-2 text-slate-500 text-xs font-medium">
              <FileCode className="w-4 h-4 text-rose-500" />
              Lines Deleted
            </div>
            <div className="text-2xl font-bold text-rose-600 mt-1">
              -{analytics.totalLinesDeleted.toLocaleString()}
            </div>
          </div>

          <div className="bg-slate-50 p-3.5 rounded-lg border border-slate-100">
            <div className="flex items-center gap-2 text-slate-500 text-xs font-medium">
              <Users className="w-4 h-4 text-violet-500" />
              Active Contributors
            </div>
            <div className="text-2xl font-bold text-slate-900 mt-1">
              {analytics.activeContributorCount}
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

