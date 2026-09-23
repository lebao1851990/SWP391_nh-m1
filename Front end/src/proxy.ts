import { NextResponse } from 'next/server';
import type { NextRequest } from 'next/server';

export function proxy(request: NextRequest) {
  const { pathname } = request.nextUrl;
  
  // NOTE: This is UX Protection, not the absolute security boundary.
  // The actual security boundary is the backend API.
  
  // (The backend should set this cookie as HttpOnly upon successful login)
  // For role separation in middleware (UX protection), the backend might also set a non-HttpOnly 'codeassess_role' cookie,
  // or we just decode a JWT if the HttpOnly cookie is a plain JWT (not recommended for security).
  // Assuming 'codeassess_session' exists if authenticated.
  const sessionCookie = request.cookies.get('codeassess_session')?.value;
  const roleCookie = request.cookies.get('codeassess_role')?.value; // 'TEACHER' | 'STUDENT'
  
  // Protected Routes Configuration
  const isTeacherRoute = pathname.startsWith('/teacher');
  const isStudentRoute = pathname.startsWith('/student');
  const isAdminRoute = pathname.startsWith('/admin');

  
  // 1. Unauthenticated users trying to access protected routes
  if (!sessionCookie && (isTeacherRoute || isStudentRoute || isAdminRoute)) {
    const loginUrl = new URL('/login', request.url);
    loginUrl.searchParams.set('redirect', pathname);
    return NextResponse.redirect(loginUrl);
  }
  
  // 2. Authenticated users (UX Protection)
  if (sessionCookie) {
    const role = roleCookie;
    

    
    // Prevent role cross-contamination (UX Protection ONLY)
    // The backend API is the absolute security boundary that will return 403 if hacked.
    if (isTeacherRoute && role !== 'TEACHER') {
      const target = role === 'ADMIN' ? '/admin/dashboard' : '/student/dashboard';
      return NextResponse.redirect(new URL(target, request.url));
    }
    if (isStudentRoute && role !== 'STUDENT') {
      const target = role === 'ADMIN' ? '/admin/dashboard' : '/teacher/dashboard';
      return NextResponse.redirect(new URL(target, request.url));
    }
    if (isAdminRoute && role !== 'ADMIN') {
      const target = role === 'TEACHER' ? '/teacher/dashboard' : '/student/dashboard';
      return NextResponse.redirect(new URL(target, request.url));
    }
  }
  
  return NextResponse.next();
}

export const config = {
  matcher: [
    /*
     * Match all request paths except for the ones starting with:
     * - api (API routes)
     * - _next/static (static files)
     * - _next/image (image optimization files)
     * - favicon.ico (favicon file)
     */
    '/((?!api|_next/static|_next/image|favicon.ico).*)',
  ],
};
