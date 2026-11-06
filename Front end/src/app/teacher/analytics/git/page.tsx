'use client';

import React, { useState, useEffect, useCallback } from 'react';
import { gitApi } from '@/features/git/api';
import { GitRepository, RepositoryAnalytics, ContributorSummary } from '@/features/git/types';
import {
  MethodologyInfoBanner,
  RepositoryOverviewCard,
  DateRangeFilter,
  ContributorSummaryTable,
  ContributionTimelineChart,
  ContributorMappingModal,
} from '@/features/git/components';
import { GitBranch, Plus, FolderGit2, AlertCircle, Loader2 } from 'lucide-react';
import { toast } from 'sonner';

export default function TeacherGitAnalyticsPage() {
  const [repositories, setRepositories] = useState<GitRepository[]>([]);
  const [selectedRepoId, setSelectedRepoId] = useState<string>('');
  const [analytics, setAnalytics] = useState<RepositoryAnalytics | null>(null);

  const [dateFilter, setDateFilter] = useState<{ since?: string; until?: string }>({});
  const [isLoadingRepos, setIsLoadingRepos] = useState<boolean>(true);
  const [isLoadingAnalytics, setIsLoadingAnalytics] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);

  // Mapping Modal state
  const [mappingModalOpen, setMappingModalOpen] = useState(false);
  const [contributorToMap, setContributorToMap] = useState<ContributorSummary | null>(null);

  // Fetch all repositories
  const loadRepositories = useCallback(async () => {
    setIsLoadingRepos(true);
    setError(null);
    try {
      const repos = await gitApi.getTeacherRepositories();
      setRepositories(repos);
      if (repos.length > 0 && !selectedRepoId) {
        setSelectedRepoId(repos[0].id);
      }
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed loading repositories';
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

  // Sync handler
  const handleSync = async () => {
    if (!selectedRepoId) return;
    try {
      const res = await gitApi.syncRepository(selectedRepoId);
      toast.success(`Successfully synced ${res.syncedCount} commits`);
      await loadAnalytics(selectedRepoId, dateFilter.since, dateFilter.until);
      await loadRepositories();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Sync failed';
      toast.error(msg);
    }
  };

  // Date Filter change
  const handleFilterChange = (since?: string, until?: string) => {
    setDateFilter({ since, until });
  };

  // Contributor mapping
  const handleOpenMapping = (contributor: ContributorSummary) => {
    setContributorToMap(contributor);
    setMappingModalOpen(true);
  };

  const handleSaveMapping = async (email: string, name: string, userId: string) => {
    if (!selectedRepoId) return;
    await gitApi.mapContributor(selectedRepoId, {
      gitAuthorEmail: email,
      gitAuthorName: name,
      userId,
    });
    toast.success(`Mapped ${email} to student ${userId}`);
    await loadAnalytics(selectedRepoId, dateFilter.since, dateFilter.until);
  };

  const selectedRepo = repositories.find((r) => r.id === selectedRepoId) || analytics?.repository;

  return (
    <div className="p-6 max-w-7xl mx-auto w-full">
      {/* Page Title */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 mb-6">
        <div>
          <h1 className="text-2xl font-bold text-slate-900 flex items-center gap-2.5">
            <FolderGit2 className="w-7 h-7 text-blue-600" />
            Git Contribution Analytics
          </h1>
          <p className="text-slate-500 text-xs mt-1">
            Track team commit histories, member contributions, line balances, and activity timelines.
          </p>
        </div>

        {/* Repository Selector */}
        {repositories.length > 0 && (
          <div className="flex items-center gap-2">
            <span className="text-xs font-medium text-slate-600">Repository:</span>
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

      {/* Loading Repos State */}
      {isLoadingRepos && (
        <div className="flex items-center justify-center py-16 gap-3 text-slate-500 text-sm">
          <Loader2 className="w-5 h-5 animate-spin text-blue-600" />
          Loading repositories...
        </div>
      )}

      {/* Error state */}
      {error && !isLoadingRepos && (
        <div className="bg-rose-50 border border-rose-200 text-rose-800 rounded-lg p-4 mb-6 flex items-start gap-3 text-xs">
          <AlertCircle className="w-5 h-5 text-rose-600 shrink-0 mt-0.5" />
          <div>
            <div className="font-semibold">Failed to load Git Analytics</div>
            <div className="mt-0.5">{error}</div>
          </div>
        </div>
      )}

      {/* Empty State: No Repositories */}
      {!isLoadingRepos && repositories.length === 0 && !error && (
        <div className="bg-white rounded-xl border border-slate-200 p-12 text-center shadow-xs">
          <GitBranch className="w-12 h-12 text-slate-400 mx-auto mb-3" />
          <h3 className="text-base font-semibold text-slate-900">No Git Repositories Registered</h3>
          <p className="text-xs text-slate-500 max-w-md mx-auto mt-1 mb-5">
            Register team Git repositories to track commit contributions and line distributions across project teams.
          </p>
        </div>
      )}

      {/* Repository Content */}
      {selectedRepo && (
        <>
          <RepositoryOverviewCard
            repository={selectedRepo}
            analytics={analytics || undefined}
            onSync={handleSync}
            canSync={true}
          />

          <DateRangeFilter
            initialSince={dateFilter.since}
            initialUntil={dateFilter.until}
            onFilterChange={handleFilterChange}
          />

          {isLoadingAnalytics ? (
            <div className="flex items-center justify-center py-12 gap-3 text-slate-500 text-xs">
              <Loader2 className="w-4 h-4 animate-spin text-blue-600" />
              Recalculating contribution metrics...
            </div>
          ) : analytics ? (
            <>
              <ContributorSummaryTable
                contributors={analytics.contributors}
                onOpenMappingModal={handleOpenMapping}
                canMap={true}
              />

              <ContributionTimelineChart timeline={analytics.timeline} />
            </>
          ) : null}
        </>
      )}

      {/* Mapping Dialog */}
      <ContributorMappingModal
        isOpen={mappingModalOpen}
        contributor={contributorToMap}
        onClose={() => setMappingModalOpen(false)}
        onSave={handleSaveMapping}
      />
    </div>
  );
}

