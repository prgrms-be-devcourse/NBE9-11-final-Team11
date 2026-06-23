import { NextResponse } from 'next/server'
import type { NextRequest } from 'next/server'

export function middleware(request: NextRequest) {
  // 1. 쿠키에서 accessToken과 refreshToken을 모두 읽어옵니다.
  const accessToken = request.cookies.get('accessToken')?.value
  const refreshToken = request.cookies.get('refreshToken')?.value

  const { pathname } = request.nextUrl

  // 2. 두 토큰 중 하나라도 존재하면 인증 유효 상태(isLoggedIn)로 간주합니다.
  const isLoggedIn = !!(accessToken || refreshToken)

  // 3. 로그인 상태인데 로그인(/login) 또는 회원가입(/signup) 페이지에 접근하려는 경우
  if (isLoggedIn && (pathname === '/login' || pathname === '/signup')) {
    // 대시보드로 강제 리다이렉트
    return NextResponse.redirect(new URL('/dashboard', request.url))
  }

  return NextResponse.next()
}

// 미들웨어가 작동할 경로 설정
export const config = {
  matcher: ['/login', '/signup'],
}