export default function StudentTemplate({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <div className="page-transition flex-1 flex flex-col w-full">
      {children}
    </div>
  );
}
