"use client"

import { useState } from "react"
import { Search, AlertCircle, Banknote } from "lucide-react"
import { MarketingHeader } from "@/components/marketing/marketing-header"
import { Card } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { apiRequest } from "@/lib/api"
import { KOREAN_BANKS } from "@/lib/fx-data"

interface KycInquiryResponse {
  depositorName: string
  amount: number
  depositedAt: string
}

function formatDateTime(iso: string) {
  const d = new Date(iso)
  return `${d.getFullYear()}.${String(d.getMonth() + 1).padStart(2, "0")}.${String(d.getDate()).padStart(2, "0")} ${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}`
}

export default function KycInquiryPage() {
  const [bankName, setBankName] = useState(KOREAN_BANKS[0])
  const [accountNumber, setAccountNumber] = useState("")
  const [accountHolderName, setAccountHolderName] = useState("")

  const [result, setResult] = useState<KycInquiryResponse | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState("")

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (!bankName.trim() || !accountNumber.trim() || !accountHolderName.trim()) {
      setError("모든 항목을 입력해주세요.")
      return
    }
    setLoading(true)
    setError("")
    setResult(null)
    try {
      const data = await apiRequest<KycInquiryResponse>(
        "POST",
        "/api/v1/mockbank/kyc/inquiry",
        {
          bankName: bankName.trim(),
          accountNumber: accountNumber.trim(),
          accountHolderName: accountHolderName.trim(),
        }
      )
      setResult(data)
    } catch (err: any) {
      setError(err.message || "조회에 실패했습니다. 입력 정보를 확인해주세요.")
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="flex min-h-screen flex-col bg-secondary/20">
      <MarketingHeader />

      <main className="mx-auto w-full max-w-md flex-1 px-4 py-8 sm:px-6">
        <div className="mb-6">
          <h1 className="text-2xl font-bold tracking-tight">계좌번호 조회</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            1원 인증을 위해 등록한 은행명·계좌번호·예금주명을 입력하면 입금자명을 확인할 수 있습니다.
          </p>
        </div>

        <Card className="p-5">
          <form onSubmit={handleSubmit} className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="accountHolderName">예금주명</Label>
              <Input
                id="accountHolderName"
                placeholder="홍길동"
                value={accountHolderName}
                onChange={(e) => setAccountHolderName(e.target.value)}
                autoComplete="name"
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="bankName">은행명</Label>
              <Select value={bankName} onValueChange={(v) => setBankName(v ?? KOREAN_BANKS[0])}>
                <SelectTrigger id="bankName">
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
            </div>
            <div className="space-y-2">
              <Label htmlFor="accountNumber">계좌번호</Label>
              <Input
                id="accountNumber"
                placeholder="숫자 12자리"
                value={accountNumber}
                onChange={(e) => setAccountNumber(e.target.value.replace(/[^\d]/g, ""))}
                maxLength={12}
              />
            </div>

            {error && (
              <div className="flex items-center gap-2 rounded-xl bg-destructive/10 px-4 py-3 text-sm text-destructive">
                <AlertCircle className="size-4 shrink-0" />
                {error}
              </div>
            )}

            <Button type="submit" className="w-full" disabled={loading}>
              {loading ? "조회 중..." : <><Search className="size-4" />입금 내역 조회</>}
            </Button>
          </form>
        </Card>

        {result && (
          <Card className="mt-6 overflow-hidden border-0 bg-primary p-6 text-primary-foreground">
            <div className="flex items-center gap-2 text-sm text-primary-foreground/80">
              <Banknote className="size-4" /> 입금 내역
            </div>
            <p className="mt-2 text-3xl font-bold tracking-wide tabular-nums">{result.depositorName}</p>
            <p className="mt-1 text-sm text-primary-foreground/60">
              {result.amount.toLocaleString("ko-KR")}원 · {formatDateTime(result.depositedAt)}
            </p>
            <p className="mt-3 text-xs text-primary-foreground/70">
              입금자명 뒤 4자리 숫자가 인증코드입니다. 계좌 연결 화면으로 돌아가 입력해주세요.
            </p>
          </Card>
        )}

        <p className="mt-8 text-center text-xs text-muted-foreground">
          본 서비스는 시뮬레이션 전용 데모입니다. 실제 자금이 이동하지 않습니다.
        </p>
      </main>
    </div>
  )
}
