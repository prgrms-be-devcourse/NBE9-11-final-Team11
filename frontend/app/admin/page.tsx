"use client"

import { useMemo } from "react"
import { Droplets, TrendingUp, AlertTriangle, CheckCircle2, ArrowUpRight, ArrowDownRight } from "lucide-react"
import { AdminShell } from "@/components/admin/admin-shell"
import { Card } from "@/components/ui/card"
import { Separator } from "@/components/ui/separator"
import { PoolBalanceChart, RebalancingChart } from "@/components/admin/pool-charts"
import { cn } from "@/lib/utils"

interface Pool {
  code: string
  flag: string
  name: string
  balance: number // 억원
  target: number
  floor: number
  ceiling: number
}

const POOLS: Pool[] = [
  { code: "KRW", flag: "🇰🇷", name: "원화 풀", balance: 112, target: 100, floor: 50, ceiling: 150 },
  { code: "USD", flag: "🇺🇸", name: "달러 풀", balance: 47, target: 60, floor: 40, ceiling: 90 },
  { code: "JPY", flag: "🇯🇵", name: "엔화 풀", balance: 38, target: 40, floor: 25, ceiling: 60 },
  { code: "EUR", flag: "🇪🇺", name: "유로 풀", balance: 22, target: 30, floor: 20, ceiling: 45 },
]

type Health = "정상" | "주의" | "위험"

function healthOf(p: Pool): Health {
  if (p.balance <= p.floor * 1.05 || p.balance >= p.ceiling * 0.97) return "위험"
  const band = p.ceiling - p.floor
  const dist = Math.abs(p.balance - p.target)
  if (dist > band * 0.25) return "주의"
  return "정상"
}

const healthMeta: Record<Health, { cls: string; icon: typeof CheckCircle2 }> = {
  정상: { cls: "bg-accent/15 text-accent", icon: CheckCircle2 },
  주의: { cls: "bg-chart-3/15 text-chart-3", icon: AlertTriangle },
  위험: { cls: "bg-destructive/10 text-destructive", icon: AlertTriangle },
}

function poolTrend(seed: number, base: number) {
  const out: { date: string; balance: number }[] = []
  let s = seed * 7
  for (let i = 29; i >= 0; i--) {
    s = (s * 9301 + 49297) % 233280
    const noise = (s / 233280 - 0.5) * base * 0.12
    const trend = Math.sin((29 - i) / 7) * base * 0.06
    const d = new Date()
    d.setDate(d.getDate() - i)
    out.push({
      date: `${d.getMonth() + 1}/${d.getDate()}`,
      balance: Math.round((base + noise + trend) * 10) / 10,
    })
  }
  return out
}

const REBALANCING_EVENTS = [
  { date: "6/3", amount: 12 },
  { date: "6/4", amount: -8 },
  { date: "6/5", amount: 5 },
  { date: "6/6", amount: -15 },
  { date: "6/7", amount: 9 },
  { date: "6/8", amount: -6 },
  { date: "6/9", amount: 14 },
]

export default function AdminPage() {
  const krw = POOLS[0]
  const krwTrend = useMemo(() => poolTrend(krw.code.charCodeAt(0), krw.target), [krw])

  return (
    <AdminShell title="유동성 풀 현황" active="/admin">
      <div className="space-y-6">
        {/* KRW pool spotlight */}
        <Card className="overflow-hidden border-0 bg-primary p-6 text-primary-foreground">
          <div className="flex flex-wrap items-start justify-between gap-4">
            <div>
              <span className="flex items-center gap-2 text-sm font-medium text-primary-foreground/80">
                <Droplets className="size-4" /> KRW Pool · 원화 유동성
              </span>
              <p className="mt-2 text-4xl font-bold tabular-nums">{krw.balance}억원</p>
              <p className="mt-1 text-sm text-primary-foreground/80">
                목표 {krw.target}억 · 하한 {krw.floor}억 · 상한 {krw.ceiling}억
              </p>
            </div>
            <span className="rounded-full bg-primary-foreground/15 px-3 py-1 text-sm font-semibold">
              {healthOf(krw)}
            </span>
          </div>
          {/* Floor / target / ceiling visualization */}
          <div className="mt-6">
            <div className="relative h-3 w-full rounded-full bg-primary-foreground/20">
              <div
                className="absolute inset-y-0 left-0 rounded-full bg-primary-foreground"
                style={{ width: `${Math.min(100, (krw.balance / krw.ceiling) * 100)}%` }}
              />
              <div
                className="absolute -top-1 h-5 w-0.5 bg-primary-foreground/60"
                style={{ left: `${(krw.target / krw.ceiling) * 100}%` }}
              />
            </div>
            <div className="mt-2 flex justify-between text-xs text-primary-foreground/70 tabular-nums">
              <span>0</span>
              <span>목표 {krw.target}억</span>
              <span>{krw.ceiling}억</span>
            </div>
          </div>
        </Card>

        {/* Per-currency pools */}
        <div>
          <h2 className="mb-3 text-sm font-semibold text-muted-foreground">통화별 풀 현황</h2>
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
            {POOLS.map((p) => {
              const health = healthOf(p)
              const meta = healthMeta[health]
              const pct = Math.min(100, (p.balance / p.ceiling) * 100)
              return (
                <Card key={p.code} className="p-4">
                  <div className="flex items-center justify-between">
                    <span className="flex items-center gap-2">
                      <span className="text-xl" aria-hidden>
                        {p.flag}
                      </span>
                      <span className="text-sm font-semibold">{p.code} Pool</span>
                    </span>
                    <span className={cn("rounded-full px-2 py-0.5 text-[11px] font-semibold", meta.cls)}>{health}</span>
                  </div>
                  <p className="mt-3 text-2xl font-bold tabular-nums">{p.balance}억</p>
                  <div className="mt-2 h-2 w-full rounded-full bg-secondary">
                    <div
                      className={cn(
                        "h-full rounded-full",
                        health === "위험" ? "bg-destructive" : health === "주의" ? "bg-chart-3" : "bg-accent",
                      )}
                      style={{ width: `${pct}%` }}
                    />
                  </div>
                  <p className="mt-2 text-xs text-muted-foreground tabular-nums">
                    목표 {p.target}억 · {p.floor}~{p.ceiling}억
                  </p>
                </Card>
              )
            })}
          </div>
        </div>

        {/* Rebalancing recommendations */}
        <Card className="p-5">
          <h2 className="text-base font-bold">리밸런싱 권고</h2>
          <p className="mt-1 text-sm text-muted-foreground">목표 잔고 대비 편차에 따른 자동 권고입니다.</p>
          <div className="mt-4 space-y-2">
            {POOLS.map((p) => {
              const health = healthOf(p)
              const meta = healthMeta[health]
              const Icon = meta.icon
              const diff = p.target - p.balance
              const action =
                health === "정상"
                  ? "조치 불필요"
                  : diff > 0
                    ? `${Math.abs(diff)}억원 유입 권고`
                    : `${Math.abs(diff)}억원 유출 권고`
              return (
                <div
                  key={p.code}
                  className="flex items-center justify-between rounded-2xl border border-border p-4"
                >
                  <div className="flex items-center gap-3">
                    <span className={cn("flex size-10 items-center justify-center rounded-full", meta.cls)}>
                      <Icon className="size-[18px]" />
                    </span>
                    <div>
                      <p className="font-medium">
                        {p.flag} {p.code} Pool
                      </p>
                      <p className="text-sm text-muted-foreground">{action}</p>
                    </div>
                  </div>
                  <span
                    className={cn(
                      "inline-flex items-center gap-1 text-sm font-semibold tabular-nums",
                      diff > 0 ? "text-accent" : diff < 0 ? "text-destructive" : "text-muted-foreground",
                    )}
                  >
                    {diff !== 0 &&
                      (diff > 0 ? <ArrowUpRight className="size-4" /> : <ArrowDownRight className="size-4" />)}
                    {diff > 0 ? "+" : ""}
                    {diff}억
                  </span>
                </div>
              )
            })}
          </div>
        </Card>

        {/* Charts */}
        <div className="grid gap-4 lg:grid-cols-2">
          <Card className="p-5">
            <div className="flex items-center justify-between">
              <h3 className="text-sm font-semibold">
                <TrendingUp className="mr-1 inline size-4 text-primary" />
                KRW Pool 잔고 추이
              </h3>
              <span className="text-xs text-muted-foreground">최근 30일</span>
            </div>
            <div className="mt-4 h-64 w-full">
              <PoolBalanceChart data={krwTrend} floor={krw.floor} ceiling={krw.ceiling} />
            </div>
            <div className="mt-3 flex flex-wrap gap-4 text-xs text-muted-foreground">
              <span className="inline-flex items-center gap-1.5">
                <span className="h-0.5 w-4 bg-chart-3" /> 상한 {krw.ceiling}억
              </span>
              <span className="inline-flex items-center gap-1.5">
                <span className="h-0.5 w-4 bg-destructive" /> 하한 {krw.floor}억
              </span>
            </div>
          </Card>

          <Card className="p-5">
            <div className="flex items-center justify-between">
              <h3 className="text-sm font-semibold">최근 리밸런싱 이벤트</h3>
              <span className="text-xs text-muted-foreground">최근 7건</span>
            </div>
            <div className="mt-4 h-64 w-full">
              <RebalancingChart data={REBALANCING_EVENTS} />
            </div>
            <Separator className="my-3" />
            <div className="flex flex-wrap gap-4 text-xs text-muted-foreground">
              <span className="inline-flex items-center gap-1.5">
                <span className="size-2.5 rounded-sm bg-accent" /> 유입
              </span>
              <span className="inline-flex items-center gap-1.5">
                <span className="size-2.5 rounded-sm bg-destructive" /> 유출
              </span>
            </div>
          </Card>
        </div>

        <p className="text-center text-xs text-muted-foreground">
          본 관리자 대시보드의 모든 수치는 시뮬레이션용 목업 데이터입니다.
        </p>
      </div>
    </AdminShell>
  )
}
