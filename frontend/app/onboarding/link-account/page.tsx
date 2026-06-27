"use client"

import { useState } from "react"
import { useRouter } from "next/navigation"
import Link from "next/link"
import { Check, AlertCircle, Loader2, CircleCheck, ExternalLink } from "lucide-react"
import { toast } from "sonner"
import { AppShell } from "@/components/app/app-shell"
import { Card } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { apiRequest } from "@/lib/api"
import { KOREAN_BANKS } from "@/lib/fx-data"
import { useStore } from "@/lib/store"

type CheckStatus = "idle" | "checking" | "available" | "unavailable"
type KycState = "idle" | "starting" | "waiting" | "failed" | "verified"

interface KycStartResponse {
  verificationId: number
  expiresAt: string
}

export default function LinkAccountPage() {
  const router = useRouter()
  const { setVerified } = useStore()

  const [bankName, setBankName] = useState(KOREAN_BANKS[0])
  const [accountNumber, setAccountNumber] = useState("")
  const [accountHolderName, setAccountHolderName] = useState("")
  const [check, setCheck] = useState<{ status: CheckStatus; message?: string }>({ status: "idle" })

  // --- 1원 인증(KYC) 단계 ---
  const [kyc, setKyc] = useState<KycState>("idle")
  const [verificationId, setVerificationId] = useState<number | null>(null)
  const [code, setCode] = useState("")
  const [verifying, setVerifying] = useState(false)
  const [errorMessage, setErrorMessage] = useState("")

  function handleAccountNumberChange(value: string) {
    setAccountNumber(value.replace(/[^\d]/g, ""))
    // 번호가 바뀌면 이전 확인 결과는 더 이상 유효하지 않으므로 초기화
    setCheck({ status: "idle" })
  }

  async function handleCheck() {
    const clean = accountNumber.replace(/[^\d]/g, "")
    if (clean.length !== 12) {
      setCheck({ status: "unavailable", message: "계좌번호는 정확히 12자리 숫자여야 합니다." })
      return
    }
    setCheck({ status: "checking" })
    try {
      const res = await apiRequest<{ available: boolean; message: string }>(
        "POST",
        "/api/v1/mockbank/check",
        { accountNumber: clean }
      )
      setCheck({
        status: res.available ? "available" : "unavailable",
        message: res.message,
      })
    } catch (err: any) {
      console.error(err)
      setCheck({ status: "unavailable", message: err.message || "계좌번호 확인에 실패했습니다." })
    }
  }

  // 계좌번호 확인 통과 → 1원 입금 요청 (인증코드 입력 단계로 전환)
  async function startKyc() {
    if (check.status !== "available" || !accountHolderName.trim()) return
    setKyc("starting")
    setErrorMessage("")
    try {
      const res = await apiRequest<KycStartResponse>("POST", "/api/v1/mockbank/kyc/start", {
        bankName,
        accountNumber: accountNumber.replace(/[^\d]/g, ""),
        accountHolderName: accountHolderName.trim(),
      })
      setVerificationId(res.verificationId)
      setKyc("waiting")
    } catch (err: any) {
      console.error(err)
      setKyc("idle")
      toast.error(err.message || "1원 인증 요청에 실패했습니다.")
    }
  }

  // 인증코드 검증 → 성공 시 백엔드가 실제 계좌 연결까지 완료한다
  async function verifyKyc(ev: React.FormEvent) {
    ev.preventDefault()
    if (!verificationId) return

    setVerifying(true)
    setErrorMessage("")
    try {
      await apiRequest("POST", "/api/v1/mockbank/kyc/verify", {
        verificationId,
        code,
      })
      setKyc("verified")
      setVerified()
      toast.success("인증이 완료되었습니다. 지갑이 생성되었어요.")
      setTimeout(() => router.push("/dashboard"), 1200)
    } catch (err: any) {
      console.error(err)
      setKyc("failed")
      setErrorMessage(err.message || "인증코드가 일치하지 않습니다.")
    } finally {
      setVerifying(false)
    }
  }

  return (
    <AppShell title="계좌 연결">
      <div className="mx-auto max-w-md">
        <Card className="space-y-5 p-6">
          <div>
            <h2 className="text-lg font-bold">모의계좌 연결</h2>
            <p className="mt-1 text-sm text-muted-foreground">
              입출금·환전·해외송금을 이용하려면 KRW 모의계좌 연결이 필요합니다.
            </p>
          </div>

          {/* 계좌 정보 입력은 1원 인증 시작 전까지만 수정 가능 */}
          <div className="space-y-2">
            <Label htmlFor="bank">은행</Label>
            {kyc === "idle" ? (
              <Select value={bankName} onValueChange={(v) => setBankName(v ?? KOREAN_BANKS[0])}>
                <SelectTrigger id="bank">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {KOREAN_BANKS.map((b) => (
                    <SelectItem key={b} value={b}>
                      {b}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            ) : (
              <p className="font-medium">{bankName}</p>
            )}
          </div>

          <div className="space-y-2">
            <Label htmlFor="account">계좌번호</Label>
            {kyc === "idle" ? (
              <>
                <Input
                  id="account"
                  placeholder="숫자 12자리"
                  value={accountNumber}
                  onChange={(e) => handleAccountNumberChange(e.target.value)}
                  maxLength={12}
                />
                <div className="flex items-center justify-between gap-2">
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    onClick={handleCheck}
                    disabled={check.status === "checking"}
                  >
                    {check.status === "checking" ? (
                      <span className="flex items-center gap-1.5">
                        <Loader2 className="size-3.5 animate-spin" />
                        확인 중...
                      </span>
                    ) : (
                      "계좌번호 확인하기"
                    )}
                  </Button>
                  {check.status === "available" && (
                    <span className="flex items-center gap-1 text-xs text-accent">
                      <Check className="size-3.5" />
                      {check.message ?? "사용 가능한 계좌번호입니다."}
                    </span>
                  )}
                  {check.status === "unavailable" && (
                    <span className="flex items-center gap-1 text-xs text-destructive">
                      <AlertCircle className="size-3.5" />
                      {check.message ?? "사용할 수 없는 계좌번호입니다."}
                    </span>
                  )}
                </div>
              </>
            ) : (
              <span className="font-mono font-medium">
                {accountNumber.replace(/(\d{3})(\d{3})(\d{6})/, "$1-$2-$3")}
              </span>
            )}
          </div>

          <div className="space-y-2">
            <Label htmlFor="holderName">예금주명</Label>
            {kyc === "idle" ? (
              <Input
                id="holderName"
                placeholder="홍길동"
                value={accountHolderName}
                onChange={(e) => setAccountHolderName(e.target.value)}
                autoComplete="name"
              />
            ) : (
              <p className="font-medium">{accountHolderName}</p>
            )}
          </div>

          {kyc === "idle" && (
            <div className="space-y-1.5">
              <Button
                className="w-full"
                onClick={startKyc}
                disabled={check.status !== "available" || !accountHolderName.trim()}
              >
                1원 입금 요청
              </Button>
              {(check.status !== "available" || !accountHolderName.trim()) && (
                <p className="text-center text-xs text-muted-foreground">
                  계좌번호 확인과 예금주명 입력을 먼저 진행해주세요.
                </p>
              )}
            </div>
          )}

          {kyc === "starting" && (
            <div className="flex items-center justify-center gap-2 py-4 text-sm text-muted-foreground">
              <Loader2 className="size-4 animate-spin" />
              1원 입금 요청 중...
            </div>
          )}

          {(kyc === "waiting" || kyc === "failed") && (
            <form onSubmit={verifyKyc} className="space-y-3">
              <div className="space-y-2 rounded-xl bg-primary/10 px-3 py-2.5 text-xs font-medium text-primary">
                <p>입력하신 계좌에 1원이 입금되었습니다. 계좌번호 조회 화면에서 입금자명을 확인하세요.</p>
                <Link
                  href="/mockbank/kyc-inquiry"
                  target="_blank"
                  className="inline-flex items-center gap-1 underline underline-offset-2"
                >
                  계좌번호 조회하기 <ExternalLink className="size-3" />
                </Link>
              </div>
              <div className="space-y-2">
                <Label htmlFor="code">인증코드 (입금자명 뒤 4자리)</Label>
                <Input
                  id="code"
                  inputMode="numeric"
                  maxLength={4}
                  value={code}
                  onChange={(e) => setCode(e.target.value.replace(/\D/g, ""))}
                  placeholder="4자리 숫자"
                  className="text-center text-lg tracking-[0.5em]"
                />
              </div>
              {kyc === "failed" && (
                <div className="flex items-center gap-2 rounded-xl bg-destructive/10 px-3 py-2 text-sm text-destructive">
                  <AlertCircle className="size-4" />
                  {errorMessage || "인증에 실패했습니다. 코드를 다시 확인해주세요."}
                </div>
              )}
              <Button type="submit" className="w-full" disabled={verifying || code.length !== 4}>
                {verifying ? "연결 중..." : "인증 확인"}
              </Button>
            </form>
          )}

          {kyc === "verified" && (
            <div className="flex flex-col items-center gap-3 py-4 text-center">
              <span className="flex size-14 items-center justify-center rounded-full bg-accent/15 text-accent">
                <CircleCheck className="size-8" />
              </span>
              <p className="font-semibold">인증 완료</p>
              <p className="text-sm text-muted-foreground">KRW 지갑이 생성되었습니다. 대시보드로 이동합니다…</p>
            </div>
          )}
        </Card>
      </div>
    </AppShell>
  )
}
