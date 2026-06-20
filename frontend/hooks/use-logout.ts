"use client"
 
import { useRouter } from "next/navigation"
import { useStore } from "@/lib/store"
import { apiRequest } from "@/lib/api"
 
/**
 * 로그아웃 공통 로직.
 * - 백엔드 /api/v1/auth/logout 호출 (JWT 블랙리스트 등록 + 쿠키 삭제)
 * - 호출이 실패해도 사용자 의도는 "로그아웃"이므로 클라이언트 상태는 항상 정리한다.
 * - store.tsx의 logout()이 전체 상태를 defaultState()로 리셋하므로,
 *   여기서는 store가 관리하지 않는 fxflow-userId만 별도로 지운다.
 */
export function useLogout() {
  const router = useRouter()
  const { logout } = useStore()
 
  return async function handleLogout() {
    try {
      await apiRequest("POST", "/api/v1/auth/logout")
    } catch (err) {
      console.error("로그아웃 API 호출 실패", err)
    } finally {
      if (typeof window !== "undefined") {
        localStorage.removeItem("fxflow-userId")
      }
      logout()
      router.push("/login")
    }
  }
}