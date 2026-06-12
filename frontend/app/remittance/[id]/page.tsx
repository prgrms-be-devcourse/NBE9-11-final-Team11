"use client"

import { use, useMemo } from "react"
import Link from "next/link"
import { ArrowLeft, Check, Loader2, Package, Building2, Globe, CheckCircle2 } from "lucide-react"
import { AppShell } from "@/components/app/app-shell"
import { Card } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { useStore } from "@/lib/store"
import { formatKRW, formatCurrency } from "@/lib/fx-data"
import { cn } from "@/lib/utils"

const STAGES = [
  { key: "received", label: "송금 신청 접수", icon: Package, desc: "송금 요청이 정상적으로 접수되었습니다." },
  { key: "processing", label: "환전 및 출금 처리", icon: Building2, desc: "KRW를 외화로 환전하고 있습니다." },
  { key: "transit", label: "해외 송금 전송", icon: Globe, desc: "수취 은행으로 송금이 전송 중입니다." },
  { key: "completed", label: "수취인 입금 완료", icon: CheckCircle2, desc: "수취인 계좌로 입금이 완료되었습니다." },
]

export default function RemittanceTrackingPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params)
  const { transactions } = useStore()
  const tx = transactions.find((t) => t.id === id)

  // Determine progress from elapsed time since creation (simulation)
  const stageIndex = useMemo(() => {
    if (!tx) return 0
    if (tx.status === "completed") return 3
    const ageMin = (Date.now() - new Date(tx.createdAt).getTime()) / 60000
    if (ageMin > 60 * 24) return 3
    if (ageMin > 60 * 6) return 2
    if (ageMin > 30) return 1
    return 0
  }, [tx])

  if (!tx) {
    return (
      <AppShell title="송금 추적">
        <Card className="p-10 text-center">
          <p className="text-sm text-muted-foreground">송금 내역을 찾을 수 없습니다.</p>
          <Button render={<Link href="/transactions" />} className="mt-4">
            거래내역으로
          </Button>
        </Card>
      </AppShell>
    )
  }

  const received = tx.rate ? Math.abs(tx.amountKRW) / (tx.rate / (tx.toCurrency === "JPY" ? 100 : 1)) : 0

  return (
    <AppShell title="송금 추적">
      <div className="mx-auto max-w-2xl space-y-6">
        <Link
          href="/transactions"
          className="inline-flex items-center gap-1 text-sm text-muted-foreground transition-colors hover:text-foreground"
        >
          <ArrowLeft className="size-4" /> 거래내역
        </Link>

        {/* Summary header */}
        <Card className="p-5">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm text-muted-foreground">{tx.title}</p>
              <p className="mt-1 text-2xl font-bold tabular-nums">{formatKRW(Math.abs(tx.amountKRW))}</p>
              {tx.toCurrency && (
                <p className="text-sm text-muted-foreground tabular-nums">
                  수취 {formatCurrency(received, tx.toCurrency)}
                </p>
              )}
            </div>
            <span
              className={cn(
                "inline-flex items-center gap-1.5 rounded-full px-3 py-1 text-xs font-semibold",
                stageIndex === 3 ? "bg-accent/15 text-accent" : "bg-primary/10 text-primary",
              )}
            >
              {stageIndex === 3 ? <Check className="size-3.5" /> : <Loader2 className="size-3.5 animate-spin" />}
              {stageIndex === 3 ? "완료" : "처리중"}
            </span>
          </div>
          {tx.detail && <p className="mt-3 text-sm text-muted-foreground">{tx.detail}</p>}
        </Card>

        {/* Timeline */}
        <Card className="p-5">
          <h2 className="text-sm font-semibold">처리 현황</h2>
          <ol className="mt-4">
            {STAGES.map((stage, i) => {
              const done = i < stageIndex
              const active = i === stageIndex
              const Icon = stage.icon
              return (
                <li key={stage.key} className="flex gap-4">
                  <div className="flex flex-col items-center">
                    <span
                      className={cn(
                        "flex size-10 items-center justify-center rounded-full border-2 transition-colors",
                        done && "border-accent bg-accent text-accent-foreground",
                        active && "border-primary bg-primary text-primary-foreground",
                        !done && !active && "border-border bg-muted text-muted-foreground",
                      )}
                    >
                      {done ? <Check className="size-5" /> : <Icon className="size-5" />}
                    </span>
                    {i < STAGES.length - 1 && (
                      <span className={cn("my-1 w-0.5 flex-1", done ? "bg-accent" : "bg-border")} style={{ minHeight: 32 }} />
                    )}
                  </div>
                  <div className={cn("pb-6", i === STAGES.length - 1 && "pb-0")}>
                    <p className={cn("font-semibold", active && "text-primary")}>{stage.label}</p>
                    <p className="mt-0.5 text-sm text-muted-foreground">{stage.desc}</p>
                    {active && (
                      <p className="mt-1 text-xs font-medium text-primary">진행 중…</p>
                    )}
                  </div>
                </li>
              )
            })}
          </ol>
        </Card>

        <p className="text-center text-xs text-muted-foreground">
          처리 단계는 신청 후 경과 시간에 따라 시뮬레이션됩니다.
        </p>
      </div>
    </AppShell>
  )
}
