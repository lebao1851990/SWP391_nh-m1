'use client';

import React from 'react';
import { useAdminClasses } from '../hooks';
import { AppLink as Link } from '@/components/ui/app-link';

export function AdminClassListView() {
  const { data: classes, isLoading, isError } = useAdminClasses();
  const [searchQuery, setSearchQuery] = React.useState('');

  const filteredClasses = React.useMemo(() => {
    if (!classes) return [];
    if (!searchQuery) return classes;
    const lowerQuery = searchQuery.toLowerCase();
    return classes.filter(
      (c) =>
        c.name?.toLowerCase().includes(lowerQuery) ||
        c.code?.toLowerCase().includes(lowerQuery) ||
        c.semester?.toLowerCase().includes(lowerQuery)
    );
  }, [classes, searchQuery]);

  return (
    <div className="flex flex-col w-full gap-space-lg px-space-lg py-space-lg">
      <div className="flex flex-col xl:flex-row xl:items-center justify-between gap-space-md pb-space-xs">
        <div className="flex flex-col gap-1">
          <div className="flex items-center gap-space-sm">
            <h1 className="font-display-lg text-display-lg tracking-tight text-on-surface font-bold">Manage Classes</h1>
          </div>
          <p className="font-body-md text-body-md text-on-surface-variant">Manage cohorts, course assignments, and class capacity.</p>
        </div>
      </div>

      <div className="flex flex-col rounded-xl bg-surface-container-lowest shadow-sm overflow-hidden border border-outline-variant/30">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between p-space-md bg-surface-container-low gap-space-sm border-b border-outline-variant/30">
          <div className="flex flex-col">
            <div className="flex items-center gap-space-xs">
              <span className="material-symbols-outlined text-[20px] text-primary">class</span>
              <h2 className="font-headline-sm text-headline-sm text-on-surface font-semibold">Class Roster</h2>
            </div>
          </div>
          <div className="flex items-center gap-space-xs">
            <div className="flex items-center bg-surface-container-lowest border border-outline-variant/50 px-2.5 py-1 rounded-lg">
              <span className="material-symbols-outlined text-[16px] text-on-surface-variant mr-1.5">search</span>
              <input 
                className="bg-transparent font-body-sm text-body-sm text-on-surface outline-none w-32 focus:w-44 transition-all" 
                placeholder="Search classes..." 
                type="text"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
              />
            </div>
          </div>
        </div>

        {isLoading ? (
          <div className="p-10 flex justify-center text-on-surface-variant">
            <span className="material-symbols-outlined animate-spin text-[32px]">progress_activity</span>
          </div>
        ) : isError ? (
          <div className="p-10 flex flex-col items-center justify-center text-center">
            <span className="material-symbols-outlined text-[48px] text-error mb-2">error</span>
            <span className="font-headline-sm font-semibold text-on-surface">Integration Pending</span>
            <span className="font-body-sm text-on-surface-variant">Backend API is currently unavailable.</span>
          </div>
        ) : filteredClasses?.length === 0 ? (
          <div className="p-10 flex flex-col items-center justify-center text-center">
            <span className="material-symbols-outlined text-[48px] text-on-surface-variant mb-2">domain_disabled</span>
            <span className="font-headline-sm font-semibold text-on-surface">No Classes Found</span>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left">
              <thead>
                <tr className="bg-surface-container-lowest font-label-sm text-label-sm uppercase tracking-wider text-on-surface-variant font-semibold border-b border-outline-variant/30">
                  <th className="py-2.5 px-space-md">Course Name</th>
                  <th className="py-2.5 px-space-md">Code</th>
                  <th className="py-2.5 px-space-md">Semester</th>
                  <th className="py-2.5 px-space-md">Students</th>
                  <th className="py-2.5 px-space-md text-right">Action</th>
                </tr>
              </thead>
              <tbody className="font-body-md text-body-md divide-y divide-surface-container-high/40">
                {filteredClasses?.map(cls => (
                  <tr key={cls.id} className="hover:bg-surface-container-low/60 transition-colors group">
                    <td className="py-3 px-space-md font-semibold text-on-surface">{cls.name}</td>
                    <td className="py-3 px-space-md text-on-surface-variant font-mono text-label-sm bg-surface-container-low rounded px-1.5 py-0.5 inline-block mt-2 ml-4">{cls.code}</td>
                    <td className="py-3 px-space-md">
                      <span className="px-2 py-0.5 rounded bg-surface-container text-on-surface-variant font-label-sm text-label-sm font-medium">
                        {cls.semester}
                      </span>
                    </td>
                    <td className="py-3 px-space-md font-mono text-label-sm">{cls.studentCount}</td>
                    <td className="py-3 px-space-md text-right">
                      <Link href={`/admin/classes/${cls.id}`} className="px-2.5 py-1 rounded bg-surface-container-low group-hover:bg-primary group-hover:text-on-primary text-on-surface-variant font-label-sm text-label-sm font-medium transition-colors">
                        View Details
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
