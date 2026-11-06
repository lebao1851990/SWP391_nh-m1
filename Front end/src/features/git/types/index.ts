export interface GitRepository {
  id: string;
  name: string;
  projectId: string;
  classId?: string;
  repoUrl: string;
  defaultBranch: string;
  provider: 'GITHUB' | 'GITLAB' | 'MOCK' | string;
  syncStatus: 'PENDING' | 'SYNCED' | 'FAILED' | string;
  lastSyncedAt?: string;
  createdAt: string;
  memberUserIds: string[];
}

export interface GitCommit {
  id: string;
  repositoryId: string;
  commitHash: string;
  authorName: string;
  authorEmail: string;
  userId?: string;
  commitDate: string;
  commitMessage: string;
  linesAdded: number;
  linesDeleted: number;
  filesChanged: number;
  isMergeCommit: boolean;
  isRevertCommit: boolean;
  parentHashes: string[];
  createdAt: string;
}

export interface ContributorSummary {
  userId?: string;
  authorName: string;
  authorEmail: string;
  totalCommits: number;
  linesAdded: number;
  linesDeleted: number;
  netLines: number;
  filesChanged: number;
  activeDays: number;
  firstCommitAt?: string;
  lastCommitAt?: string;
  consistencyScore: number;
  isMappedUser: boolean;
}

export interface ContributionTimelinePoint {
  period: string; // ISO date YYYY-MM-DD
  commitCount: number;
  linesAdded: number;
  linesDeleted: number;
  netLines: number;
}

export interface CalculationMethodology {
  title: string;
  version: string;
  explanation: string;
  netLinesFormula: string;
  consistencyScoreFormula: string;
  ethicalGuidance: string;
  mergeCommitHandling: string;
  revertCommitHandling: string;
  rules: Record<string, string>;
}

export interface RepositoryAnalytics {
  repository: GitRepository;
  since?: string;
  until?: string;
  totalCommits: number;
  totalLinesAdded: number;
  totalLinesDeleted: number;
  totalNetLines: number;
  activeContributorCount: number;
  contributors: ContributorSummary[];
  timeline: ContributionTimelinePoint[];
  methodology: CalculationMethodology;
}

export interface GitContributorMappingRequest {
  gitAuthorEmail: string;
  gitAuthorName?: string;
  userId: string;
}

export interface GitRepositoryRegistrationRequest {
  name: string;
  projectId: string;
  classId?: string;
  repoUrl: string;
  defaultBranch?: string;
  provider?: string;
  accessToken?: string;
  memberUserIds?: string[];
}

