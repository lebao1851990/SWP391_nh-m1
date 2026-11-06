'use client';

import React, { useState, useEffect, useCallback } from 'react';
import { gitApi } from '@/features/git/api';
import { GitRepository, RepositoryAnalytics } from '@/features/git/types';
import {
  MethodologyInfoBanner,
  RepositoryOverviewCard,
  DateRangeFilter,
  ContributorSummaryTable,
  ContributionTimelineChart,
} from '@/features/git/components';
import { GitBranch, FolderGit2, AlertCircle, Loader2 } from 'lucide-react';

export default function StudentGitAnalyticsPage() {
  const [repositories, setRepositories] = useState<GitRepository[]>([]);
  const [selectedRepoId, setSelectedRepoId] = useState<string>('');
  const [analytics, setAnalytics] = useState<RepositoryAnalytics | null>(null);

  const [dateFilter, setDateFilter] = useState<{ since?: string; until?: string }>({});
  const [isLoadingRepos, setIsLoadingRepos] = useState<boolean>(true);
  const [isLoadingAnalytics, setIsLoadingAnalytics] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);

  // Fetch student repositories
  const loadRepositories = useCallback(async () => {
    setIsLoadingRepos(true);
    setError(null);
    try {
      const repos = await gitApi.getStudentRepositories();
      setRepositories(repos);
      if (repos.length > 0 && !selectedRepoId) {
        setSelectedRepoId(repos[0].id);
      }
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed loading team repositories';
      setError(msg);
    } finally {
      setIsLoadingRepos(false);
    }
  }, [selectedRepoId]);

  useEffect(() => {
    loadRepositories();
  }, [loadRepositories]);

  // Fetch analytics for selected repo
  const loadAnalytics = useCallback(async (repoId: string, since?: string, until?: string) => {
    if (!repoId) return;
    setIsLoadingAnalytics(true);
    setError(null);
    try {
      const data = await gitApi.getRepositoryAnalytics(repoId, since, until);
      setAnalytics(data);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed loading repository analytics';
      setError(msg);
    } finally {
      setIsLoadingAnalytics(false);
    }
  }, []);

  useEffect(() => {
    if (selectedRepoId) {
      loadAnalytics(selectedRepoId, dateFilter.since, dateFilter.until);
    }
  }, [selectedRepoId, dateFilter, loadAnalytics]);

  const handleFilterChange = (since?: string, until?: string) => {
    setDateFilter({ since, until });
  };

  const selectedRepo = repositories.find((r) => r.id === selectedRepoId) || analytics?.repository;

  return (
    <div className="p-6 max-w-7xl mx-auto w-full">
      {/* Page Title */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 mb-6">
        <div>
          <h1 className="text-2xl font-bold text-slate-900 flex items-center gap-2.5">
            <FolderGit2 className="w-7 h-7 text-blue-600" />
            Team Git Analytics
          </h1>
          <p className="text-slate-500 text-xs mt-1">
            Review your project team's commit history, contribution balances, and consistency metrics.
          </p>
        </div>

        {/* Repository Selector */}
        {repositories.length > 0 && (
          <div className="flex items-center gap-2">
            <span className="text-xs font-medium text-slate-600">Team Repository:</span>
            <select
              value={selectedRepoId}
              onChange={(e) => setSelectedRepoId(e.target.value)}
              className="bg-white border border-slate-300 rounded-lg px-3 py-1.5 text-xs font-medium text-slate-800 shadow-xs focus:outline-none focus:ring-2 focus:ring-blue-500"
            >
              {repositories.map((repo) => (
                <option key={repo.id} value={repo.id}>
                  {repo.name} ({repo.provider})
                </option>
              ))}
            </select>
          </div>
        )}
      </div>

      {/* Methodology Info Banner */}
      <MethodologyInfoBanner methodology={analytics?.methodology} />

      {/* Loading State */}
      {isLoadingRepos && (
        <div className="flex items-center justify-center py-16 gap-3 text-slate-500 text-sm">
          <Loader2 className="w-5 h-5 animate-spin text-blue-600" />
          Loading your team repositories...
        </div>
      )}

      {/* Error state */}
      {error && !isLoadingRepos && (
        <div className="bg-rose-50 border border-rose-200 text-rose-800 rounded-lg p-4 mb-6 flex items-start gap-3 text-xs">
          <AlertCircle className="w-5 h-5 text-rose-600 shrink-0 mt-0.5" />
          <div>
            <div className="font-semibold">Unable to load team analytics</div>
            <div className="mt-0.5">{error}</div>
          </div>
        </div>
      )}

      {/* Empty State */}
      {!isLoadingRepos && repositories.length === 0 && !error && (
        <div className="bg-white rounded-xl border border-slate-200 p-12 text-center shadow-xs">
          <GitBranch className="w-12 h-12 text-slate-400 mx-auto mb-3" />
          <h3 className="text-base font-semibold text-slate-900">No Team Repositories Linked</h3>
          <p className="text-xs text-slate-500 max-w-md mx-auto mt-1">
            You are not currently linked to any project Git repositories. Your instructor or team leader can register your repository and add your student account.
          </p>
        </div>
      )}

      {/* Repository Content */}
      {selectedRepo && (
        <>
          <RepositoryOverviewCard
            repository={selectedRepo}
            analytics={analytics || undefined}
            onSync={async () => {}}
            canSync={false}
          />

          <DateRangeFilter
            initialSince={dateFilter.since}
            initialUntil={dateFilter.until}
            onFilterChange={handleFilterChange}
          />

          {isLoadingAnalytics ? (
            <div className="flex items-center justify-center py-12 gap-3 text-slate-500 text-xs">
              <Loader2 className="w-4 h-4 animate-spin text-blue-600" />
              Loading team analytics...
            </div>
          ) : analytics ? (
            <>
              <ContributorSummaryTable
                contributors={analytics.contributors}
                canMap={false}
              />

              <ContributionTimelineChart timeline={analytics.timeline} />
            </>
          ) : null}
        </>
      )}
    </div>
  );
}

