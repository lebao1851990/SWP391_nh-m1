import { apiClient } from '@/lib/api-client';
import {
  GitRepository,
  RepositoryAnalytics,
  GitContributorMappingRequest,
  GitRepositoryRegistrationRequest,
} from '../types';

export const gitApi = {
  /**
   * Fetch repository analytics with contributor breakdown and contribution timeline
   */
  async getRepositoryAnalytics(
    repositoryId: string,
    since?: string,
    until?: string
  ): Promise<RepositoryAnalytics> {
    const params = new URLSearchParams();
    if (since) params.append('since', since);
    if (until) params.append('until', until);

    const res = await apiClient.get<RepositoryAnalytics>(
      `/git/repositories/${repositoryId}/analytics`,
      { params }
    );
    return res.data;
  },

  /**
   * Fetch all registered repositories (Teacher / Admin)
   */
  async getTeacherRepositories(projectId?: string): Promise<GitRepository[]> {
    const params = new URLSearchParams();
    if (projectId) params.append('projectId', projectId);

    const res = await apiClient.get<GitRepository[]>('/git/repositories', { params });
    return res.data;
  },

  /**
   * Fetch repositories accessible by the current student
   */
  async getStudentRepositories(): Promise<GitRepository[]> {
    const res = await apiClient.get<GitRepository[]>('/git/repositories');
    return res.data;
  },

  /**
   * Register a new Git repository
   */
  async registerRepository(
    data: GitRepositoryRegistrationRequest
  ): Promise<GitRepository> {
    const res = await apiClient.post<GitRepository>('/git/repositories', data);
    return res.data;
  },

  /**
   * Trigger synchronization of commits from Git provider
   */
  async syncRepository(
    repositoryId: string,
    branch?: string,
    forceResync = false
  ): Promise<{ syncedCount: number; message: string }> {
    const res = await apiClient.post<{ syncedCount: number; message: string }>(
      `/git/repositories/${repositoryId}/sync`,
      { branch, forceResync }
    );
    return res.data;
  },

  /**
   * Map or override an unmatched Git author email to a CodeAssess student
   */
  async mapContributor(
    repositoryId: string,
    mapping: GitContributorMappingRequest
  ): Promise<void> {
    await apiClient.post(
      `/git/repositories/${repositoryId}/contributors/map`,
      mapping
    );
  },
};

