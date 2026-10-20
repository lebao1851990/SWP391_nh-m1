'use client';

import React from 'react';
import { useTeacherScoring } from '../hooks';
import { TeacherScoringForm } from './teacher-scoring-form';
import { AxiosError } from 'axios';

interface TeacherScoringViewProps {
  submissionId: string;
}

export function TeacherScoringView({ submissionId }: TeacherScoringViewProps) {
  const { data, isLoading, isError, error } = useTeacherScoring(submissionId);

  if (isLoading) {
    return (
      <div className="flex h-64 items-center justify-center">
        <div className="text-on-surface-variant font-label-md">Đang tải dữ liệu chấm điểm từ máy chủ...</div>
      </div>
    );
  }

  if (isError) {
    // Determine the type of error. If it's a 401, the interceptor already redirected.
    // We handle 403 and 404 explicitly as required.
    const status = (error as AxiosError)?.response?.status;
    
    if (status === 403) {
      return (
        <div className="flex flex-col h-64 items-center justify-center p-space-lg text-center gap-space-sm">
          <span className="material-symbols-outlined text-error text-[48px]">block</span>
          <h2 className="font-headline-md text-headline-md text-on-surface">Truy cập bị từ chối</h2>
          <p className="font-body-md text-on-surface-variant">Bạn không có quyền hạn chấm điểm bài nộp này.</p>
        </div>
      );
    }

    if (status === 404) {
      return (
        <div className="flex flex-col h-64 items-center justify-center p-space-lg text-center gap-space-sm">
          <span className="material-symbols-outlined text-outline text-[48px]">search_off</span>
          <h2 className="font-headline-md text-headline-md text-on-surface">Không tìm thấy</h2>
          <p className="font-body-md text-on-surface-variant">Không tìm thấy bản ghi chấm điểm hoặc bài nộp này.</p>
        </div>
      );
    }

    return (
      <div className="flex flex-col h-64 items-center justify-center p-space-lg text-center gap-space-sm">
        <span className="material-symbols-outlined text-error text-[48px]">error</span>
        <h2 className="font-headline-md text-headline-md text-on-surface">Lỗi kết nối máy chủ / Tích hợp</h2>
        <p className="font-body-md text-on-surface-variant">
          Dịch vụ chấm điểm hiện không thể kết nối hoặc gặp trục trặc kỹ thuật.
          <br />
          <span className="text-outline font-mono text-label-sm mt-2 block">{error?.message}</span>
        </p>
      </div>
    );
  }

  if (!data) {
    return (
      <div className="flex h-64 items-center justify-center">
        <div className="text-on-surface-variant font-label-md">Không có dữ liệu chấm điểm.</div>
      </div>
    );
  }

  return (
    <TeacherScoringForm submissionId={submissionId} initialData={data} />
  );
}
