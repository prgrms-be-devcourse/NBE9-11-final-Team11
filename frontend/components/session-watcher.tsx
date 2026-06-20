"use client"

import { useEffect, useRef, useState } from "react"
import { useRouter } from "next/navigation"
import { AlertCircle } from "lucide-react"
import { useStore } from "@/lib/store"
import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"

/**
 * 전역 세션 만료 감시 컴포넌트.
 * - apiRequest()가 백엔드로부터 401(UNAUTHORIZED, 쿠키 만료/미인증)을 받으면
 *   "fxflow:session-expired" 이벤트를 window에 발생시킨다.
 * - 이 컴포넌트가 그 이벤트를 받아 안내 다이얼로그를 띄운다.
 * - 사용자가 "확인"을 눌러야만 실제로 store를 정리하고 로그인 페이지로 이동한다.
 *   (바깥 영역 클릭/ESC로는 닫히지 않도록 onOpenChange를 막아둠)
 * - StoreProvider 내부(layout.tsx)에 마운트되어야 useStore를 쓸 수 있다.
 */
export function SessionWatcher() {
  const router = useRouter()
  const { user, logout } = useStore()
  const [open, setOpen] = useState(false)
  const handledRef = useRef(false)

  // 다시 로그인하면 같은 세션 안에서 또 한 번 만료를 감지할 수 있도록 플래그를 초기화한다.
  useEffect(() => {
    if (user) handledRef.current = false
  }, [user])

  useEffect(() => {
    function handleSessionExpired() {
      // 여러 API 요청이 동시에 401을 받아 이벤트가 여러 번 발생해도
      // 다이얼로그가 중복으로 뜨지 않게 막는다.
      if (handledRef.current) return
      handledRef.current = true
      setOpen(true)
    }

    window.addEventListener("fxflow:session-expired", handleSessionExpired)
    return () => window.removeEventListener("fxflow:session-expired", handleSessionExpired)
  }, [])

  function handleConfirm() {
    if (typeof window !== "undefined") {
      localStorage.removeItem("fxflow-userId")
    }
    logout()
    setOpen(false)
    router.push("/login")
  }

  return (
    <Dialog open={open} onOpenChange={() => {}}>
      <DialogContent showCloseButton={false}>
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <AlertCircle className="size-5 text-destructive" />
            세션 만료
          </DialogTitle>
          <DialogDescription>
            시간이 완료되어서 로그아웃됩니다. 다시 로그인해주세요.
          </DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <Button onClick={handleConfirm} className="w-full">
            확인
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
