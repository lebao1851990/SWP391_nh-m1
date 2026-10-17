export default function TeacherLoading() {
  return (
    <div className="max-w-7xl mx-auto px-space-lg py-space-lg w-full flex flex-col gap-space-lg animate-pulse">
      {/* Top Banner Skeleton */}
      <div className="h-28 rounded-2xl bg-surface-container-low border border-outline-variant/30" />
      {/* Metric Cards Skeleton */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-space-md">
        <div className="h-24 rounded-xl bg-surface-container-low border border-outline-variant/30" />
        <div className="h-24 rounded-xl bg-surface-container-low border border-outline-variant/30" />
        <div className="h-24 rounded-xl bg-surface-container-low border border-outline-variant/30" />
        <div className="h-24 rounded-xl bg-surface-container-low border border-outline-variant/30" />
      </div>
      {/* Table Skeleton */}
      <div className="h-72 rounded-2xl bg-surface-container-low border border-outline-variant/30" />
    </div>
  );
}
