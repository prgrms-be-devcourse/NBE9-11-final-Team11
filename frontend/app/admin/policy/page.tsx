"use client"

import { useState } from "react"
import { toast } from "sonner"
import { AdminShell } from "@/components/admin/admin-shell"
import { Card } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Separator } from "@/components/ui/separator"

interface PolicyRow {
  code: string
  flag: string
  name: string
  target: number
  floor: number
  ceiling: number
}

const INITIAL: PolicyRow[] = [
  { code: "KRW", flag: "🇰🇷", name: "원화 풀", target: 100, floor: 50, ceiling: 150 },
  { code: "USD", flag: "🇺🇸", name: "달러 풀", target: 60, floor: 40, ceiling: 90 },
  { code: "JPY", flag: "🇯🇵", name: "엔화 풀", target: 40, floor: 25, ceiling: 60 },
  { code: "EUR", flag: "🇪🇺", name: "유로 풀", target: 30, floor: 20, ceiling: 45 },
]

export default function AdminPolicyPage() {
  const [rows, setRows] = useState<PolicyRow[]>(INITIAL)
  const [fees, setFees] = useState({ exchange: "0.4", remittance: "0.5", remittanceFlat: "5000" })

  function update(code: string, key: "target" | "floor" | "ceiling", value: string) {
    const num = Number(value.replace(/[^\d]/g, "")) || 0
    setRows((rs) => rs.map((r) => (r.code === code ? { ...r, [key]: num } : r)))
  }

  return (
    <AdminShell title="정책 관리" active="/admin/policy">
      <div className="space-y-6">
        {/* Pool thresholds */}
        <Card className="p-5">
          <h2 className="text-base font-bold">유동성 풀 임계값</h2>
          <p className="mt-1 text-sm text-muted-foreground">통화별 목표·하한·상한 잔고를 억원 단위로 설정합니다.</p>
          <div className="mt-5 space-y-4">
            {rows.map((r) => (
              <div key={r.code} className="rounded-2xl border border-border p-4">
                <p className="mb-3 font-medium">
                  {r.flag} {r.code} · {r.name}
                </p>
                <div className="grid gap-3 sm:grid-cols-3">
                  <div className="space-y-1.5">
                    <Label className="text-xs text-muted-foreground">목표 (억원)</Label>
                    <Input
                      inputMode="numeric"
                      value={r.target}
                      onChange={(e) => update(r.code, "target", e.target.value)}
                    />
                  </div>
                  <div className="space-y-1.5">
                    <Label className="text-xs text-muted-foreground">하한 (억원)</Label>
                    <Input
                      inputMode="numeric"
                      value={r.floor}
                      onChange={(e) => update(r.code, "floor", e.target.value)}
                    />
                  </div>
                  <div className="space-y-1.5">
                    <Label className="text-xs text-muted-foreground">상한 (억원)</Label>
                    <Input
                      inputMode="numeric"
                      value={r.ceiling}
                      onChange={(e) => update(r.code, "ceiling", e.target.value)}
                    />
                  </div>
                </div>
              </div>
            ))}
          </div>
        </Card>

        {/* Fee policy */}
        <Card className="p-5">
          <h2 className="text-base font-bold">수수료 정책</h2>
          <div className="mt-5 grid gap-4 sm:grid-cols-3">
            <div className="space-y-1.5">
              <Label className="text-xs text-muted-foreground">환전 수수료율 (%)</Label>
              <Input
                inputMode="decimal"
                value={fees.exchange}
                onChange={(e) => setFees({ ...fees, exchange: e.target.value })}
              />
            </div>
            <div className="space-y-1.5">
              <Label className="text-xs text-muted-foreground">송금 수수료율 (%)</Label>
              <Input
                inputMode="decimal"
                value={fees.remittance}
                onChange={(e) => setFees({ ...fees, remittance: e.target.value })}
              />
            </div>
            <div className="space-y-1.5">
              <Label className="text-xs text-muted-foreground">송금 고정 수수료 (KRW)</Label>
              <Input
                inputMode="numeric"
                value={fees.remittanceFlat}
                onChange={(e) => setFees({ ...fees, remittanceFlat: e.target.value })}
              />
            </div>
          </div>
        </Card>

        <Separator />
        <div className="flex justify-end gap-2">
          <Button variant="outline" onClick={() => setRows(INITIAL)}>
            기본값 복원
          </Button>
          <Button onClick={() => toast.success("정책이 저장되었습니다.")}>정책 저장</Button>
        </div>

        <p className="text-center text-xs text-muted-foreground">
          정책 변경은 시뮬레이션 환경에만 적용되며 실제 거래에 영향을 주지 않습니다.
        </p>
      </div>
    </AdminShell>
  )
}
