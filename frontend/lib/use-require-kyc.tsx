"use client"

import { useRouter } from "next/navigation"
import { useStore } from "@/lib/store"
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from "@/components/ui/dialog"
import { Button } from "@/components/ui/button"

// KYC(계좌연결) 미인증 사용자가 지갑/환전/해외송금/예약/거래내역 페이지에
// 접근하면 인증 여부를 묻는 다이얼로그를 띄운다. "예" → 계좌연결 페이지,
// "아니요"(또는 닫기) → 대시보드로 이동. user.verified는 로그인 응답에서
// 내려오는 실제 KYC 인증 여부 값이다(/login 참고).
export function useRequireKyc() {
  const router = useRouter()
  const { user, ready } = useStore()
  const blocked = ready && !!user && !user.verified

  const goBackToDashboard = () => router.replace("/dashboard")
  const goToLinkAccount = () => router.replace("/onboarding/link-account")

  const dialog = blocked ? (
    <Dialog open onOpenChange={(open) => { if (!open) goBackToDashboard() }}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>KYC 인증이 필요합니다</DialogTitle>
          <DialogDescription>
            이 서비스를 이용하려면 계좌 연결(KYC 인증)이 필요합니다. 지금 인증하시겠습니까?
          </DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <Button variant="outline" onClick={goBackToDashboard}>
            아니요
          </Button>
          <Button onClick={goToLinkAccount}>예</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  ) : null

  return { blocked, dialog }
}
