"use client"

import { useState } from "react"
import { useRouter } from "next/navigation"
import { Check, AlertCircle, Loader2, CircleCheck } from "lucide-react"
import { toast } from "sonner"
import { AppShell } from "@/components/app/app-shell"
import { Card } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { apiRequest } from "@/lib/api"
import { KOREAN_BANKS } from "@/lib/fx-data"

type CheckStatus = "idle" | "checking" | "available" | "unavailable"
type KycState = "idle" | "waiting" | "verified" | "failed"

export default function LinkAccountPage() {
  const router = useRouter()
  const [bankName, setBankName] = useState(KOREAN_BANKS[0])
  const [accountNumber, setAccountNumber] = useState("")
  const [check, setCheck] = useState<{ status: CheckStatus; message?: string }>({ status: "idle" })

  // --- 1원 인증(KYC) 단계 ---
  const [kyc, setKyc] = useState<KycState>("idle")
  const [code, setCode] = useState("")
  const [linking, setLinking] = useState(false)
  const correctCode = "1234"

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
  function startKyc() {
    if (check.status !== "available") return
    setKyc("waiting")
  }

  // 인증코드 검증 통과 시에만 실제 계좌 연결을 호출한다
  async function verifyKyc(ev: React.FormEvent) {
    ev.preventDefault()

    if (code !== correctCode) {
      setKyc("failed")
      return
    }

    setLinking(true)
    try {
      await apiRequest("POST", "/api/v1/mockbank/link", {
        bankName,
        accountNumber: accountNumber.replace(/[^\d]/g, ""),
      })
      setKyc("verified")
      toast.success("인증이 완료되었습니다. 지갑이 생성되었어요.")
      setTimeout(() => router.push("/dashboard"), 1200)
    } catch (err: any) {
      console.error(err)
      setKyc("failed")
      toast.error(err.message || "계좌 연결에 실패했습니다. 잠시 후 다시 시도해주세요.")
    } finally {
      setLinking(false)
    }
  }

  return (
    <AppShell title="계좌 연결">
      <div className="mx-auto max-w-md">
        <Card className="space-y-5 p-6">
          <div>
            <h2 className="text-lg font-bold">모의계좌 연결</h2>
            <p className="mt-1 text-sm text-muted-foreground">
              입출금·환전·송금을 이용하려면 KRW 모의계좌 연결이 필요합니다.
            </p>
          </div>

          {/* 계좌번호 입력은 인증 시작 전까지만 수정 가능 */}
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

          {kyc === "idle" && (
            <div className="space-y-1.5">
              <Button className="w-full" onClick={startKyc} disabled={check.status !== "available"}>
                1원 입금 요청
              </Button>
              {check.status !== "available" && (
                <p className="text-center text-xs text-muted-foreground">
                  계좌번호 확인을 먼저 진행해주세요.
                </p>
              )}
            </div>
          )}

          {(kyc === "waiting" || kyc === "failed") && (
            <form onSubmit={verifyKyc} className="space-y-3">
              <div className="flex items-center gap-2 rounded-xl bg-primary/10 px-3 py-2 text-xs font-medium text-primary">
                <Loader2 className="size-3.5 animate-spin" />
                인증 대기 중 · 입금자명 코드를 확인하세요 (데모 코드: 1234)
              </div>
              <div className="space-y-2">
                <Label htmlFor="code">인증코드</Label>
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
                  인증에 실패했습니다. 코드를 다시 확인해주세요.
                </div>
              )}
              <Button type="submit" className="w-full" disabled={linking}>
                {linking ? "연결 중..." : "인증 확인"}
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
