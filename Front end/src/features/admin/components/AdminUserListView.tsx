'use client';

import React from 'react';
import { useAdminUsers } from '../hooks';
import { AppLink as Link } from '@/components/ui/app-link';
import { useTranslation } from '@/i18n';

export function AdminUserListView() {
  const { t } = useTranslation();
  const { data: users, isLoading, isError } = useAdminUsers();
  const [searchQuery, setSearchQuery] = React.useState('');

  const filteredUsers = React.useMemo(() => {
    if (!users) return [];
    if (!searchQuery) return users;
    const lowerQuery = searchQuery.toLowerCase();
    return users.filter(
      (u) =>
        u.name?.toLowerCase().includes(lowerQuery) ||
        u.email?.toLowerCase().includes(lowerQuery) ||
        u.role?.toLowerCase().includes(lowerQuery)
    );
  }, [users, searchQuery]);

  return (
    <div className="flex flex-col w-full gap-space-lg px-space-lg py-space-lg">
      <div className="flex flex-col xl:flex-row xl:items-center justify-between gap-space-md pb-space-xs">
        <div className="flex flex-col gap-1">
          <div className="flex items-center gap-space-sm">
            <h1 className="font-display-lg text-display-lg tracking-tight text-on-surface font-bold">{t.admin.userManagementTitle}</h1>
          </div>
          <p className="font-body-md text-body-md text-on-surface-variant">Provision and manage system roles for students and faculty.</p>
        </div>
      </div>

      <div className="flex flex-col rounded-xl bg-surface-container-lowest shadow-sm overflow-hidden border border-outline-variant/30">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between p-space-md bg-surface-container-low gap-space-sm border-b border-outline-variant/30">
          <div className="flex flex-col">
            <div className="flex items-center gap-space-xs">
              <span className="material-symbols-outlined text-[20px] text-primary">group</span>
              <h2 className="font-headline-sm text-headline-sm text-on-surface font-semibold">{t.admin.userManagementTitle}</h2>
            </div>
          </div>
          <div className="flex items-center gap-space-xs">
            <div className="flex items-center bg-surface-container-lowest border border-outline-variant/50 px-2.5 py-1 rounded-lg">
              <span className="material-symbols-outlined text-[16px] text-on-surface-variant mr-1.5">search</span>
              <input 
                className="bg-transparent font-body-sm text-body-sm text-on-surface outline-none w-32 focus:w-44 transition-all placeholder:text-outline" 
                placeholder={t.common.search} 
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
            <span className="font-headline-sm font-semibold text-on-surface">{t.admin.integrationPending}</span>
            <span className="font-body-sm text-on-surface-variant">{t.admin.integrationPendingDesc}</span>
          </div>
        ) : filteredUsers?.length === 0 ? (
          <div className="p-10 flex flex-col items-center justify-center text-center">
            <span className="material-symbols-outlined text-[48px] text-on-surface-variant mb-2">person_off</span>
            <span className="font-headline-sm font-semibold text-on-surface">No Users Found</span>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left">
              <thead>
                <tr className="bg-surface-container-lowest font-label-sm text-label-sm uppercase tracking-wider text-on-surface-variant font-semibold border-b border-outline-variant/30">
                  <th className="py-2.5 px-space-md">Name</th>
                  <th className="py-2.5 px-space-md">Email</th>
                  <th className="py-2.5 px-space-md">{t.admin.userRole}</th>
                  <th className="py-2.5 px-space-md text-right">{t.common.actions}</th>
                </tr>
              </thead>
              <tbody className="font-body-md text-body-md divide-y divide-surface-container-high/40">
                {filteredUsers?.map(user => (
                  <tr key={user.id} className="hover:bg-surface-container-low/60 transition-colors group">
                    <td className="py-3 px-space-md font-semibold text-on-surface">{user.name}</td>
                    <td className="py-3 px-space-md text-on-surface-variant">{user.email}</td>
                    <td className="py-3 px-space-md">
                      <span className="px-2 py-0.5 rounded bg-surface-container text-primary font-label-sm text-label-sm font-medium font-mono">
                        {user.role}
                      </span>
                    </td>
                    <td className="py-3 px-space-md text-right">
                      <Link href={`/admin/users/${user.id}`} className="px-2.5 py-1 rounded bg-surface-container-low group-hover:bg-primary group-hover:text-on-primary text-on-surface-variant font-label-sm text-label-sm font-medium transition-colors">
                        {t.common.view}
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
