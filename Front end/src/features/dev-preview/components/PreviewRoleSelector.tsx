'use client';

import React from 'react';

export type PreviewRole = 'Student' | 'Teacher' | 'Admin';

interface PreviewRoleSelectorProps {
  selectedRole: PreviewRole;
  onSelectRole: (role: PreviewRole) => void;
}

export function PreviewRoleSelector({ selectedRole, onSelectRole }: PreviewRoleSelectorProps) {
  const roles: PreviewRole[] = ['Student', 'Teacher', 'Admin'];

  return (
    <div className="flex flex-col space-y-3">
      <h3 className="font-label-lg text-label-lg text-on-surface font-semibold">Select Role</h3>
      <div className="flex items-center gap-2">
        {roles.map((role) => (
          <button
            key={role}
            onClick={() => onSelectRole(role)}
            className={`px-4 py-2 rounded-lg font-label-md text-label-md font-medium transition-colors ${
              selectedRole === role
                ? 'bg-primary text-on-primary shadow-sm'
                : 'bg-surface-container text-on-surface hover:bg-surface-container-high'
            }`}
          >
            {role}
          </button>
        ))}
      </div>
    </div>
  );
}
