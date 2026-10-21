import { NextResponse } from 'next/server';
import type { NextRequest } from 'next/server';

const AUTH_ROUTES = ['/login', '/register', '/forgot-password', '/reset-password'];

function isSafeInternalRedirect(url: string | null | undefined): boolean {
  if (!url) return false;
  // Must be a relative path starting with single slash
  if (!url.startsWith('/') || url.startsWith('//') || url.startsWith('/\\')) {
    return false;
  }
  try {
    const dummyOrigin = 'http://localhost';
    const parsed = new URL(url, dummyOrigin);
    // Must remain on dummyOrigin (rejects schema-relative or host injection)
    if (parsed.origin !== dummyOrigin) return false;
    const cleanPath = parsed.pathname.toLowerCase();
    // Do not allow self-redirect to auth routes
    if (AUTH_ROUTES.includes(cleanPath)) {
      return false;
    }
    return true;
  } catch {
    return false;
  }
}

export function proxy(request: NextRequest) {
  const { pathname } = request.nextUrl;
  
  // NOTE: This is UX Protection, not the absolute security boundary.
  // The actual security boundary is the backend API.
  
  // Assuming 'codeassess_session' exists if authenticated.
  const sessionCookie = request.cookies.get('codeassess_session')?.value;
  const roleCookie = request.cookies.get('codeassess_role')?.value; // 'TEACHER' | 'STUDENT' | 'ADMIN'
  
  // Protected Routes Configuration
  const isTeacherRoute = pathname.startsWith('/teacher');
  const isStudentRoute = pathname.startsWith('/student');
  const isAdminRoute = pathname.startsWith('/admin');
  const isProtectedRoute = isTeacherRoute || isStudentRoute || isAdminRoute;
  const isAuthRoute = AUTH_ROUTES.includes(pathname);

  // 0. Self-redirect loop prevention on auth routes:
  // If user visits an auth route with an unsafe or self-referencing redirect param (?redirect=/login),
  // strip the invalid parameter to prevent loop chains.
  if (isAuthRoute) {
    const currentRedirect = request.nextUrl.searchParams.get('redirect');
    if (currentRedirect && !isSafeInternalRedirect(currentRedirect)) {
      const cleanUrl = new URL(pathname, request.url);
      return NextResponse.redirect(cleanUrl);
    }
  }
  
  // 1. Unauthenticated users trying to access protected routes
  if (!sessionCookie && isProtectedRoute) {
    const loginUrl = new URL('/login', request.url);
    if (isSafeInternalRedirect(pathname)) {
      loginUrl.searchParams.set('redirect', pathname);
    }
    return NextResponse.redirect(loginUrl);
  }
  
  // 2. Authenticated users / Session handling (UX Protection)
  if (sessionCookie) {
    const role = roleCookie;
    const validRoles = ['STUDENT', 'TEACHER', 'ADMIN'];
    const hasValidRole = role && validRoles.includes(role);

    // If role cookie is missing or invalid despite session cookie existing:
    if (!hasValidRole) {
      // If trying to access a protected route without a valid role:
      if (isProtectedRoute) {
        const loginUrl = new URL('/login', request.url);
        if (isSafeInternalRedirect(pathname)) {
          loginUrl.searchParams.set('redirect', pathname);
        }
        const response = NextResponse.redirect(loginUrl);
        // Clear dangling cookies on redirect response so browser arrives at /login in clean state
        response.cookies.delete('codeassess_session');
        response.cookies.delete('codeassess_role');
        return response;
      }

      // If already on an auth route (or public page), DO NOT redirect to /login!
      // Simply allow access and clear any dangling stale cookies on the response.
      if (isAuthRoute) {
        const response = NextResponse.next();
        response.cookies.delete('codeassess_session');
        response.cookies.delete('codeassess_role');
        return response;
      }

      return NextResponse.next();
    }
    
    // Prevent role cross-contamination on protected routes (UX Protection ONLY)
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
