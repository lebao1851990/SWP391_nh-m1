'use client';

import React, { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { 
  GraduationCap, 
  Users, 
  Plus, 
  Search, 
  Trash2, 
  X, 
  Loader2, 
  AlertCircle, 
  CheckCircle2, 
  UserPlus,
  BookOpen
} from 'lucide-react';
import { toast } from 'sonner';
import { teacherExamsApi, TeacherClassResponse } from '@/features/exams/api/teacher-exams.api';

export function TeacherClassesView() {
  const queryClient = useQueryClient();
  const [searchQuery, setSearchQuery] = useState('');
  const [isCreateModalOpen, setIsCreateModalOpen] = useState(false);
  const [selectedClassForRoster, setSelectedClassForRoster] = useState<TeacherClassResponse | null>(null);

  // Form states for creating class
  const [newClassName, setNewClassName] = useState('');
  const [newClassCode, setNewClassCode] = useState('');
  const [newClassSemester, setNewClassSemester] = useState('Fall 2026');

  // Fetch teacher's classes
  const { data: classes = [], isLoading, isError, refetch } = useQuery({
    queryKey: ['teacher-classes'],
    queryFn: teacherExamsApi.getMyClasses,
  });

  // Create class mutation
  const createClassMutation = useMutation({
    mutationFn: teacherExamsApi.createClass,
    onSuccess: (newClass) => {
      toast.success(`Đã tạo lớp ${newClass.code} thành công!`);
      queryClient.invalidateQueries({ queryKey: ['teacher-classes'] });
      setIsCreateModalOpen(false);
      setNewClassName('');
      setNewClassCode('');
    },
    onError: (err: unknown) => {
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message || 'Không thể tạo lớp học.';
      toast.error(msg);
    },
  });

  const handleCreateSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!newClassName.trim() || !newClassCode.trim()) {
      toast.error('Vui lòng nhập đầy đủ Tên lớp và Mã lớp.');
      return;
    }
    createClassMutation.mutate({
      name: newClassName.trim(),
      code: newClassCode.trim().toUpperCase(),
      semester: newClassSemester.trim(),
    });
  };

  const filteredClasses = classes.filter((c) => {
    const q = searchQuery.toLowerCase();
    return c.name.toLowerCase().includes(q) || c.code.toLowerCase().includes(q);
  });

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8 space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 border-b border-border/40 pb-5">
        <div>
          <div className="flex items-center gap-2 text-primary font-semibold text-xs tracking-wider uppercase mb-1">
            <GraduationCap className="w-4 h-4" />
            <span>Giảng viên / Lớp học</span>
          </div>
          <h1 className="text-2xl font-bold tracking-tight text-foreground">Quản lý Lớp học của tôi</h1>
          <p className="text-sm text-muted-foreground mt-0.5">
            Xem danh sách lớp phụ trách, quản lý danh sách sinh viên và liên kết làm bài thi.
          </p>
        </div>

        <button
          onClick={() => setIsCreateModalOpen(true)}
          className="inline-flex items-center justify-center gap-2 px-4 py-2.5 rounded-lg bg-primary text-primary-foreground font-semibold text-xs shadow-sm hover:bg-primary/90 transition-colors cursor-pointer"
        >
          <Plus className="w-4 h-4" />
          <span>Tạo lớp học mới</span>
        </button>
      </div>

      {/* Filter and Search Bar */}
      <div className="flex items-center justify-between gap-4">
        <div className="relative flex-1 max-w-md">
          <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground" />
          <input
            type="text"
            placeholder="Tìm theo tên lớp hoặc mã lớp..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="w-full pl-9 pr-4 py-2 bg-background border border-border rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary"
          />
        </div>
        <div className="text-xs text-muted-foreground font-medium">
          Tổng cộng: <span className="font-semibold text-foreground">{classes.length}</span> lớp
        </div>
      </div>

      {/* Classes Grid */}
      {isLoading ? (
        <div className="h-64 flex flex-col items-center justify-center gap-2 text-muted-foreground">
          <Loader2 className="w-7 h-7 animate-spin text-primary" />
          <span className="text-xs">Đang tải danh sách lớp học...</span>
        </div>
      ) : isError ? (
        <div className="p-8 text-center bg-destructive/10 rounded-xl border border-destructive/20 text-destructive space-y-2">
          <AlertCircle className="w-8 h-8 mx-auto" />
          <p className="font-semibold text-sm">Không thể tải danh sách lớp học</p>
          <button
            onClick={() => refetch()}
            className="px-3 py-1.5 rounded-md bg-destructive text-destructive-foreground text-xs font-medium hover:bg-destructive/90 transition-colors"
          >
            Thử lại
          </button>
        </div>
      ) : filteredClasses.length === 0 ? (
        <div className="p-12 text-center bg-card border border-border/40 rounded-xl space-y-3">
          <div className="w-12 h-12 rounded-full bg-muted flex items-center justify-center mx-auto text-muted-foreground">
            <BookOpen className="w-6 h-6" />
          </div>
          <h3 className="font-semibold text-foreground text-base">Chưa có lớp học nào</h3>
          <p className="text-xs text-muted-foreground max-w-sm mx-auto">
            {searchQuery ? 'Không tìm thấy lớp học phù hợp với từ khóa.' : 'Bắt đầu bằng cách tạo lớp học đầu tiên của bạn.'}
          </p>
          {!searchQuery && (
            <button
              onClick={() => setIsCreateModalOpen(true)}
              className="mt-2 inline-flex items-center gap-1.5 px-4 py-2 rounded-lg bg-primary text-primary-foreground text-xs font-medium hover:bg-primary/90 transition-colors"
            >
              <Plus className="w-3.5 h-3.5" />
              <span>Tạo lớp học</span>
            </button>
          )}
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
          {filteredClasses.map((cls) => (
            <div
              key={cls.id}
              className="bg-card border border-border/50 rounded-xl p-5 shadow-xs hover:border-primary/40 transition-all flex flex-col justify-between gap-4"
            >
              <div className="space-y-2">
                <div className="flex items-start justify-between gap-2">
                  <span className="px-2.5 py-0.5 rounded-md bg-primary/10 text-primary text-xs font-mono font-bold">
                    {cls.code}
                  </span>
                  <span className="text-[11px] text-muted-foreground font-medium">
                    Học kỳ: Fall 2026
                  </span>
                </div>
                <h2 className="text-base font-bold text-foreground leading-snug line-clamp-2">
                  {cls.name}
                </h2>
              </div>

              <div className="pt-3 border-t border-border/40 flex items-center justify-between">
                <div className="flex items-center gap-1.5 text-xs text-muted-foreground">
                  <Users className="w-4 h-4 text-primary" />
                  <span>
                    <strong className="text-foreground">{cls.studentCount ?? 0}</strong> sinh viên
                  </span>
                </div>

                <button
                  onClick={() => setSelectedClassForRoster(cls)}
                  className="px-3 py-1.5 rounded-lg bg-muted hover:bg-primary hover:text-primary-foreground text-xs font-medium text-foreground transition-colors cursor-pointer flex items-center gap-1.5"
                >
                  <Users className="w-3.5 h-3.5" />
                  <span>DS Sinh viên</span>
                </button>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Modal Tạo Lớp */}
      {isCreateModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-xs p-4">
          <div className="bg-card rounded-xl max-w-md w-full p-6 shadow-2xl border border-border flex flex-col gap-4 animate-in zoom-in-95 duration-150">
            <div className="flex items-center justify-between border-b border-border/40 pb-3">
              <h3 className="text-base font-bold text-foreground flex items-center gap-2">
                <GraduationCap className="w-5 h-5 text-primary" />
                <span>Tạo lớp học mới</span>
              </h3>
              <button
                onClick={() => setIsCreateModalOpen(false)}
                className="text-muted-foreground hover:text-foreground"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <form onSubmit={handleCreateSubmit} className="space-y-4">
              <div className="space-y-1.5">
                <label className="text-xs font-semibold text-foreground">
                  Mã lớp (Course Code) <span className="text-destructive">*</span>
                </label>
                <input
                  type="text"
                  placeholder="Ví dụ: PRJ301-SE1701"
                  value={newClassCode}
                  onChange={(e) => setNewClassCode(e.target.value)}
                  className="w-full px-3 py-2 bg-background border border-border rounded-lg text-sm uppercase font-mono focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary"
                  required
                />
              </div>

              <div className="space-y-1.5">
                <label className="text-xs font-semibold text-foreground">
                  Tên môn học / Tên lớp <span className="text-destructive">*</span>
                </label>
                <input
                  type="text"
                  placeholder="Ví dụ: Lập trình Java Web Thực hành"
                  value={newClassName}
                  onChange={(e) => setNewClassName(e.target.value)}
                  className="w-full px-3 py-2 bg-background border border-border rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary"
                  required
                />
              </div>

              <div className="space-y-1.5">
                <label className="text-xs font-semibold text-foreground">Học kỳ</label>
                <input
                  type="text"
                  value={newClassSemester}
                  onChange={(e) => setNewClassSemester(e.target.value)}
                  className="w-full px-3 py-2 bg-background border border-border rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary"
                />
              </div>

              <div className="flex justify-end gap-3 pt-3 border-t border-border/40">
                <button
                  type="button"
                  onClick={() => setIsCreateModalOpen(false)}
                  className="px-4 py-2 rounded-lg border border-border text-xs font-medium hover:bg-muted transition-colors"
                >
                  Hủy bỏ
                </button>
                <button
                  type="submit"
                  disabled={createClassMutation.isPending}
                  className="px-4 py-2 rounded-lg bg-primary text-primary-foreground text-xs font-semibold hover:bg-primary/90 transition-colors flex items-center gap-2 cursor-pointer disabled:opacity-50"
                >
                  {createClassMutation.isPending && <Loader2 className="w-3.5 h-3.5 animate-spin" />}
                  <span>Tạo lớp học</span>
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Modal Quản lý Danh sách Sinh viên */}
      {selectedClassForRoster && (
        <ClassRosterModal
          cls={selectedClassForRoster}
          onClose={() => {
            setSelectedClassForRoster(null);
            queryClient.invalidateQueries({ queryKey: ['teacher-classes'] });
          }}
        />
      )}
    </div>
  );
}

function ClassRosterModal({
  cls,
  onClose,
}: {
  cls: TeacherClassResponse;
  onClose: () => void;
}) {
  const queryClient = useQueryClient();
  const [studentInput, setStudentInput] = useState('');
  const [filterStudent, setFilterStudent] = useState('');

  // Fetch students in class
  const { data: students = [], isLoading, refetch } = useQuery({
    queryKey: ['teacher-class-students', cls.id],
    queryFn: () => teacherExamsApi.getClassStudents(cls.id),
  });

  // Enroll student
  const enrollMutation = useMutation({
    mutationFn: (payload: { email?: string; studentId?: string }) =>
      teacherExamsApi.enrollStudent(cls.id, payload),
    onSuccess: (added) => {
      toast.success(`Đã thêm sinh viên ${added.fullName || added.email} vào lớp!`);
      setStudentInput('');
      refetch();
      queryClient.invalidateQueries({ queryKey: ['teacher-classes'] });
    },
    onError: (err: unknown) => {
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message || 'Không thể thêm sinh viên vào lớp.';
      toast.error(msg);
    },
  });

  // Unenroll student
  const unenrollMutation = useMutation({
    mutationFn: (studentId: string) => teacherExamsApi.unenrollStudent(cls.id, studentId),
    onSuccess: () => {
      toast.success('Đã xóa sinh viên khỏi lớp học.');
      refetch();
      queryClient.invalidateQueries({ queryKey: ['teacher-classes'] });
    },
    onError: (err: unknown) => {
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message || 'Không thể xóa sinh viên khỏi lớp.';
      toast.error(msg);
    },
  });

  const handleEnrollSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    const raw = studentInput.trim();
    if (!raw) {
      toast.error('Vui lòng nhập Email hoặc Mã sinh viên.');
      return;
    }
    const isEmail = raw.includes('@');
    enrollMutation.mutate(isEmail ? { email: raw } : { studentId: raw });
  };

  const filtered = students.filter((s) => {
    const q = filterStudent.toLowerCase();
    return (
      (s.fullName && s.fullName.toLowerCase().includes(q)) ||
      (s.email && s.email.toLowerCase().includes(q)) ||
      (s.studentId && s.studentId.toLowerCase().includes(q))
    );
  });

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-xs p-4">
      <div className="bg-card rounded-2xl max-w-2xl w-full p-6 shadow-2xl border border-border flex flex-col gap-5 max-h-[90vh] overflow-hidden animate-in zoom-in-95 duration-150">
        {/* Header */}
        <div className="flex items-center justify-between border-b border-border/40 pb-3">
          <div>
            <span className="text-[11px] font-mono font-bold text-primary px-2 py-0.5 rounded bg-primary/10">
              {cls.code}
            </span>
            <h3 className="text-lg font-bold text-foreground mt-1">Danh sách sinh viên — {cls.name}</h3>
          </div>
          <button onClick={onClose} className="p-1 rounded-lg text-muted-foreground hover:text-foreground">
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Form thêm sinh viên */}
        <form onSubmit={handleEnrollSubmit} className="flex gap-2">
          <div className="relative flex-1">
            <input
              type="text"
              placeholder="Nhập Email hoặc Mã sinh viên cần thêm..."
              value={studentInput}
              onChange={(e) => setStudentInput(e.target.value)}
              className="w-full px-3 py-2 bg-background border border-border rounded-lg text-xs focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary"
              disabled={enrollMutation.isPending}
            />
          </div>
          <button
            type="submit"
            disabled={enrollMutation.isPending || !studentInput.trim()}
            className="px-4 py-2 bg-primary text-primary-foreground rounded-lg text-xs font-semibold hover:bg-primary/90 transition-colors flex items-center gap-1.5 disabled:opacity-50 cursor-pointer shadow-sm"
          >
            {enrollMutation.isPending ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <UserPlus className="w-3.5 h-3.5" />}
            <span>Thêm vào lớp</span>
          </button>
        </form>

        {/* Tìm kiếm nhanh trong danh sách */}
        <div className="flex items-center justify-between gap-4">
          <div className="relative flex-1">
            <Search className="w-3.5 h-3.5 absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground" />
            <input
              type="text"
              placeholder="Lọc sinh viên trong lớp..."
              value={filterStudent}
              onChange={(e) => setFilterStudent(e.target.value)}
              className="w-full pl-8 pr-3 py-1.5 bg-background border border-border rounded-md text-xs focus:outline-none focus:ring-1 focus:ring-primary"
            />
          </div>
          <span className="text-xs text-muted-foreground shrink-0">
            Sĩ số: <strong className="text-foreground">{students.length}</strong> sinh viên
          </span>
        </div>

        {/* Danh sách sinh viên */}
        <div className="flex-1 overflow-y-auto border border-border/50 rounded-lg divide-y divide-border/40">
          {isLoading ? (
            <div className="p-8 text-center text-xs text-muted-foreground flex flex-col items-center gap-2">
              <Loader2 className="w-5 h-5 animate-spin text-primary" />
              <span>Đang tải danh sách sinh viên...</span>
            </div>
          ) : filtered.length === 0 ? (
            <div className="p-8 text-center text-xs text-muted-foreground">
              {filterStudent ? 'Không tìm thấy sinh viên phù hợp với từ khóa.' : 'Lớp học chưa có sinh viên nào. Hãy thêm sinh viên ở trên.'}
            </div>
          ) : (
            filtered.map((student, idx) => (
              <div key={student.id || student.studentId} className="p-3 flex items-center justify-between hover:bg-muted/40 transition-colors text-xs">
                <div className="flex items-center gap-3">
                  <span className="w-6 text-center font-mono text-muted-foreground">{idx + 1}</span>
                  <div>
                    <div className="font-semibold text-foreground">{student.fullName || 'Chưa cập nhật tên'}</div>
                    <div className="text-muted-foreground font-mono text-[11px]">{student.email}</div>
                  </div>
                </div>

                <button
                  type="button"
                  onClick={() => {
                    if (confirm(`Bạn có chắc chắn muốn xóa sinh viên ${student.fullName || student.email} khỏi lớp này?`)) {
                      unenrollMutation.mutate(student.studentId || student.id);
                    }
                  }}
                  className="p-1.5 text-muted-foreground hover:text-destructive hover:bg-destructive/10 rounded transition-colors cursor-pointer"
                  title="Xóa khỏi lớp"
                >
                  <Trash2 className="w-4 h-4" />
                </button>
              </div>
            ))
          )}
        </div>

        {/* Footer */}
        <div className="flex justify-end pt-2 border-t border-border/40">
          <button
            type="button"
            onClick={onClose}
            className="px-4 py-2 rounded-lg bg-muted hover:bg-muted/80 text-foreground text-xs font-medium transition-colors"
          >
            Đóng
          </button>
        </div>
      </div>
    </div>
  );
}
