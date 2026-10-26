import { redirect } from "next/navigation";
import { cookies } from "next/headers";

export default async function Home() {
  const cookieStore = await cookies();
  const session = cookieStore.get("codeassess_session")?.value;
  const role = cookieStore.get("codeassess_role")?.value;

  if (session) {
    if (role === "TEACHER") {
      redirect("/teacher/dashboard");
    } else if (role === "ADMIN") {
      redirect("/admin/dashboard");
    } else {
      redirect("/student/dashboard");
    }
  }

  redirect("/login");
}

