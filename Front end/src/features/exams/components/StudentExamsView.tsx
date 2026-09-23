'use client';

import { useState, useMemo } from 'react';
import { useStudentExams } from '../hooks/use-student-exams';
import { useStudentSubmissions } from '../../submissions/hooks';
import { AppLink as Link } from '@/components/ui/app-link';

export function StudentExamsView() {
  const { data: exams, isLoading: isLoadingExams, isError: isErrorExams } = useStudentExams();
  const { data: submissions, isLoading: isLoadingSubmissions } = useStudentSubmissions();

  const [activeTab, setActiveTab] = useState('all');
  const [searchQuery, setSearchQuery] = useState('');
  const [statusFilter, setStatusFilter] = useState('all');
  const [now] = useState(() => Date.now());
  
  const correlatedExams = useMemo(() => {
    if (!exams) return [];
    return exams.map(exam => {
      const submission = submissions?.find(s => s.examId === exam.id);
      let derivedStatus = 'UPCOMING';
      if (submission) {
        derivedStatus = submission.status === 'GRADED' ? 'COMPLETED' : 'SUBMITTED';
      } else if (exam.startTime && new Date(exam.startTime).getTime() < now) {
        derivedStatus = 'MISSED';
      }
      return { ...exam, submission, derivedStatus };
    });
  }, [exams, submissions, now]);

  const filteredExams = useMemo(() => {
    return correlatedExams.filter(exam => {
      // Tab filter
      if (activeTab === 'upcoming' && exam.derivedStatus !== 'UPCOMING') return false;
      if (activeTab === 'submitted' && exam.derivedStatus !== 'SUBMITTED') return false;
      if (activeTab === 'completed' && exam.derivedStatus !== 'COMPLETED') return false;
      
      // Status filter
      if (statusFilter !== 'all' && exam.derivedStatus.toLowerCase() !== statusFilter.toLowerCase()) return false;
      
      // Search filter
      if (searchQuery) {
        const query = searchQuery.toLowerCase();
        return exam.title.toLowerCase().includes(query) || (exam.courseClassCode && exam.courseClassCode.toLowerCase().includes(query));
      }
      
      return true;
    });
  }, [correlatedExams, activeTab, statusFilter, searchQuery]);

  const stats = useMemo(() => {
    const upcoming = correlatedExams.filter(e => e.derivedStatus === 'UPCOMING').length;
    const submitted = correlatedExams.filter(e => e.derivedStatus === 'SUBMITTED').length;
    const completed = correlatedExams.filter(e => e.derivedStatus === 'COMPLETED').length;
    return { all: correlatedExams.length, upcoming, submitted, completed };
  }, [correlatedExams]);

  if (isLoadingExams || isLoadingSubmissions) {
    return <div className="p-8 text-center text-on-surface-variant font-label-md">Loading assessments...</div>;
  }

  if (isErrorExams) {
    return <div className="p-8 text-center text-error font-label-md">Failed to load assessments.</div>;
  }

  console.log('Rendering exams:', filteredExams.length);

  return (
    <div className="w-full max-w-7xl mx-auto px-space-lg py-space-lg">
      <div className="flex flex-col w-full">
        <div className="flex flex-col lg:flex-row lg:items-end justify-between gap-space-md mb-space-lg">
          <div className="space-y-1">
            <h1 className="font-display-lg text-display-lg text-on-surface tracking-tight">My Exams</h1>
            <p className="font-body-md text-body-md text-on-surface-variant max-w-2xl">
              View your upcoming assessments, submitted programming tasks, and completed evaluation results.
            </p>
          </div>
        </div>

        <div className="bg-surface-container-lowest rounded-xl shadow-sm overflow-hidden flex flex-col">
          <div className="px-space-md pt-space-md bg-surface-container-low/50 flex flex-col lg:flex-row lg:items-center justify-between gap-space-md">
            <div className="flex items-center gap-1 overflow-x-auto pb-space-xs lg:pb-0">
              <button 
                className={`tab-btn px-4 py-2.5 rounded-t-lg font-label-md text-label-md transition-all flex items-center gap-2 ${activeTab === 'all' ? 'bg-surface-container-lowest text-primary font-semibold shadow-sm' : 'text-on-surface-variant hover:text-on-surface hover:bg-surface-container-high/60'}`}
                onClick={() => setActiveTab('all')}
              >
                <span>All Assessments</span>
                <span className={`px-1.5 py-0.5 rounded-full text-label-sm font-code-body ${activeTab === 'all' ? 'bg-primary-fixed text-on-primary-fixed' : 'bg-surface-container-high text-on-surface-variant'}`}>{stats.all}</span>
              </button>
              <button 
                className={`tab-btn px-4 py-2.5 rounded-t-lg font-label-md text-label-md transition-all flex items-center gap-2 ${activeTab === 'upcoming' ? 'bg-surface-container-lowest text-primary font-semibold shadow-sm' : 'text-on-surface-variant hover:text-on-surface hover:bg-surface-container-high/60'}`}
                onClick={() => setActiveTab('upcoming')}
              >
                <span>Upcoming</span>
                <span className={`px-1.5 py-0.5 rounded-full text-label-sm font-code-body ${activeTab === 'upcoming' ? 'bg-primary-fixed text-on-primary-fixed' : 'bg-surface-container-high text-on-surface-variant'}`}>{stats.upcoming}</span>
              </button>
              <button 
                className={`tab-btn px-4 py-2.5 rounded-t-lg font-label-md text-label-md transition-all flex items-center gap-2 ${activeTab === 'submitted' ? 'bg-surface-container-lowest text-primary font-semibold shadow-sm' : 'text-on-surface-variant hover:text-on-surface hover:bg-surface-container-high/60'}`}
                onClick={() => setActiveTab('submitted')}
              >
                <span>Submitted</span>
                <span className={`px-1.5 py-0.5 rounded-full text-label-sm font-code-body ${activeTab === 'submitted' ? 'bg-primary-fixed text-on-primary-fixed' : 'bg-surface-container-high text-on-surface-variant'}`}>{stats.submitted}</span>
              </button>
              <button 
                className={`tab-btn px-4 py-2.5 rounded-t-lg font-label-md text-label-md transition-all flex items-center gap-2 ${activeTab === 'completed' ? 'bg-surface-container-lowest text-primary font-semibold shadow-sm' : 'text-on-surface-variant hover:text-on-surface hover:bg-surface-container-high/60'}`}
                onClick={() => setActiveTab('completed')}
              >
                <span>Completed</span>
                <span className={`px-1.5 py-0.5 rounded-full text-label-sm font-code-body ${activeTab === 'completed' ? 'bg-primary-fixed text-on-primary-fixed' : 'bg-surface-container-high text-on-surface-variant'}`}>{stats.completed}</span>
              </button>
            </div>
          </div>

          <div className="p-space-md bg-surface-container-low/30 flex flex-col md:flex-row gap-3 items-center justify-between">
            <div className="relative w-full md:w-80 lg:w-96">
              <span className="material-symbols-outlined absolute left-3 top-2.5 text-[18px] text-outline">search</span>
              <input 
                className="w-full pl-9 pr-8 py-2 bg-surface-container-lowest rounded-lg font-body-md text-body-md text-on-surface placeholder:text-outline focus:outline-none shadow-sm focus:ring-1 focus:ring-primary-container" 
                placeholder="Search exams by title or course code..." 
                type="text"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
              />
              {searchQuery && (
                <button className="absolute right-2.5 top-2.5 text-outline hover:text-on-surface" onClick={() => setSearchQuery('')}>
                  <span className="material-symbols-outlined text-[16px]">close</span>
                </button>
              )}
            </div>
            
            <div className="flex items-center flex-wrap gap-2.5 w-full md:w-auto justify-end">
              <div className="relative">
                <select 
                  className="appearance-none bg-surface-container-lowest pl-3 pr-8 py-2 rounded-lg font-label-md text-label-md text-on-surface shadow-sm focus:outline-none cursor-pointer"
                  value={statusFilter}
                  onChange={(e) => setStatusFilter(e.target.value)}
                >
                  <option value="all">All Statuses</option>
                  <option value="upcoming">Upcoming</option>
                  <option value="submitted">Submitted</option>
                  <option value="completed">Completed</option>
                  <option value="missed">Missed</option>
                </select>
                <span className="material-symbols-outlined absolute right-2 top-2.5 text-[16px] text-outline pointer-events-none">expand_more</span>
              </div>
            </div>
          </div>

          <div className="overflow-x-auto w-full">
            <table className="w-full text-left font-body-md text-body-md select-text">
              <thead className="bg-surface-container text-on-surface-variant font-label-sm text-label-sm uppercase tracking-wider">
                <tr>
                  <th className="py-3 px-space-md font-semibold" scope="col">Exam Name & Course</th>
                  <th className="py-3 px-space-md font-semibold" scope="col">Date</th>
                  <th className="py-3 px-space-md font-semibold" scope="col">Status</th>
                  <th className="py-3 px-space-md font-semibold" scope="col">Score</th>
                  <th className="py-3 px-space-md font-semibold text-right" scope="col">Actions</th>
                </tr>
              </thead>
              <tbody className="text-on-surface">
                {filteredExams.length === 0 ? (
                  <tr><td colSpan={5} className="py-8 text-center text-outline font-label-md">No assessments found</td></tr>
                ) : (
                  filteredExams.map(exam => (
                    <tr key={exam.id} className="border-b border-outline-variant/20 hover:bg-surface-container-low transition-colors">
                      <td className="py-3 px-space-md">
                        <div className="font-semibold text-on-surface">{exam.title}</div>
                        <div className="text-label-sm text-on-surface-variant">{exam.courseClassCode}</div>
                      </td>
                      <td className="py-3 px-space-md">
                        {exam.startTime ? new Date(exam.startTime).toLocaleString() : 'TBD'}
                      </td>
                      <td className="py-3 px-space-md">
                        <span className={`px-2 py-0.5 rounded font-label-sm text-label-sm font-semibold ${exam.derivedStatus === 'UPCOMING' ? 'bg-primary-container text-on-primary-container' : exam.derivedStatus === 'COMPLETED' ? 'bg-tertiary-fixed text-on-tertiary-fixed' : 'bg-surface-container-high text-on-surface-variant'}`}>
                          {exam.derivedStatus}
                        </span>
                      </td>
                      <td className="py-3 px-space-md">
                        {exam.submission ? (
                          <span className="font-mono bg-surface-container px-2 py-0.5 rounded font-bold text-[14px]">
                            {exam.submission.totalScore || 0} / 100
                          </span>
                        ) : (
                          <span className="text-outline">-</span>
                        )}
                      </td>
                      <td className="py-3 px-space-md text-right">
                        {exam.submission && (
                          <Link href={`/student/submissions/${exam.submission.id}`} className="px-3 py-1.5 bg-tertiary text-on-tertiary rounded font-label-sm hover:bg-tertiary/90 transition-colors mr-2">
                            View Result
                          </Link>
                        )}
                        <Link href={`/student/exams/${exam.id}`} className="px-3 py-1.5 bg-primary-container text-on-primary rounded font-label-sm hover:bg-primary transition-colors">
                          View
                        </Link>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
          <div className="px-space-md py-space-sm bg-surface-container-low/40 flex items-center justify-between">
            <span className="text-on-surface-variant font-label-sm">
              Showing <strong className="text-on-surface">{filteredExams.length}</strong> of <strong className="text-on-surface">{correlatedExams.length}</strong> assessments
            </span>
          </div>
        </div>
      </div>
    </div>
  );
}
