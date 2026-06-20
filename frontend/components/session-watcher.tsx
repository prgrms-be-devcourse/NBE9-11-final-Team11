"use client"

import { useEffect, useRef } from "react"
import { useRouter } from "next/navigation"
import { toast } from "sonner"
import { useStore } from "@/lib/store"

/**
 * 전역 세션 만료 감시 컴포넌트.
 * - apiRequest()가 백엔드로부터 401(UNAUTHORIZED, 쿠키 만료/미인증)을 받으면
 *   "fxflow:session-expired" 이벤트를 window에 발생시킨다.
 * - 이 컴포넌트가 그 이벤트를 받아 store를 초기화하고 로그인 페이지로 보낸다.
 * - StoreProvider 내부(layout.tsx)에 마운트되어야 useStore를 쓸 수 있다.
 */
export function SessionWatcher() {
  const router = useRouter()
  const { user, logout } = useStore()
  const handledRef = useRef(false)

  // 다시 로그인하면 같은 세션 안에서 또 한 번 만료를 감지할 수 있도록 플래그를 초기화한다.
  useEffect(() => {
    if (user) handledRef.current = false
  }, [user])

  useEffect(() => {
    function handleSessionExpired() {
      // 여러 API 요청이 동시에 401을 받아 이벤트가 여러 번 발생해도
      // 로그아웃/토스트/이동 처리가 한 번만 일어나게 막는다.
      if (handledRef.current) return
      handledRef.current = true

      if (typeof window !== "undefined") {
        localStorage.removeItem("fxflow-userId")
      }
      logout()
      toast.error("시간이 완료되어서 로그아웃됩니다.")
      router.push("/login")
    }

    window.addEventListener("fxflow:session-expired", handleSessionExpired)
    return () => window.removeEventListener("fxflow:session-expired", handleSessionExpired)
  }, [logout, router])

  return null
}
