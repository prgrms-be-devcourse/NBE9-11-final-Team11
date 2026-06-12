"use client"

import { useState } from "react"
import Link from "next/link"
import { ArrowUpRight, ArrowDownRight, TrendingUp, ArrowLeft } from "lucide-react"
import { Card } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { RateChart } from "@/components/app/rate-chart"
import { RATES, type CurrencyCode } from "@/lib/fx-data"
import { cn } from "@/lib/utils"

const FX_CODES: Exclude<CurrencyCode, "KRW">[] = ["USD", "JPY", "EUR", "CNY"]

export default function RatesPage() {
  const [selected, setSelected] = useState<Exclude<CurrencyCode, "KRW">>("USD")
  const info = RATES[selected]

  return (
    <div className="min-h-screen bg-secondary/20">
      <header className="sticky top-0 z-40 border-b border-border bg-background/80 backdrop-blur-md">
        <div className="mx-auto flex h-16 max-w-5xl items-center justify-between px-4 sm:px-6">
          <Link href="/" className="flex items-center gap-2">
            <span className="flex size-8 items-center justify-center rounded-xl bg-primary text-primary-foreground">
              <TrendingUp className="size-4" />
            </span>
            <span className="text-lg font-bold tracking-tight">FXFlow</span>
          </Link>
          <Button render={<Link href="/dashboard" />} size="sm">
            대시보드
          </Button>
        </div>
      </header>

      <main className="mx-auto w-full max-w-5xl px-4 py-8 sm:px-6">
        <Link
          href="/"
          className="mb-4 inline-flex items-center gap-1 text-sm text-muted-foreground transition-colors hover:text-foreground"
        >
          <ArrowLeft className="size-4" /> 홈으로
        </Link>
        <h1 className="text-2xl font-bold tracking-tight">실시간 환율</h1>
        <p className="mt-1 text-sm text-muted-foreground">시뮬레이션용 환율 데이터입니다. 매매기준율 기준.</p>

        {/* Rate cards */}
        <div className="mt-6 grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          {FX_CODES.map((c) => {
            const r = RATES[c]
            const up = r.change >= 0
            return (
              <button
                key={c}
                onClick={() => setSelected(c)}
                className={cn(
                  "rounded-2xl border bg-card p-4 text-left transition-all hover:border-primary/40",
                  selected === c ? "border-primary ring-2 ring-primary/20" : "border-border",
                )}
              >
                <div className="flex items-center gap-2">
                  <span className="text-2xl" aria-hidden>
                    {r.flag}
                  </span>
                  <div>
                    <p className="text-sm font-semibold">
                      {c}
                      {r.unit > 1 ? `/${r.unit}` : ""}/KRW
                    </p>
                    <p className="text-xs text-muted-foreground">{r.name}</p>
                  </div>
                </div>
                <p className="mt-3 text-xl font-bold tabular-nums">₩{r.rate.toLocaleString("ko-KR")}</p>
                <p
                  className={cn(
                    "mt-1 inline-flex items-center gap-1 text-xs font-medium",
                    up ? "text-accent" : "text-destructive",
                  )}
                >
                  {up ? <ArrowUpRight className="size-3" /> : <ArrowDownRight className="size-3" />}
                  {up ? "+" : ""}
                  {r.change}%
                </p>
              </button>
            )
          })}
        </div>

        {/* Chart */}
        <Card className="mt-6 p-5">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div className="flex items-center gap-3">
              <span className="text-3xl" aria-hidden>
                {info.flag}
              </span>
              <div>
                <h2 className="text-lg font-bold">
                  {info.code}/KRW {info.unit > 1 ? `(${info.unit}단위)` : ""}
                </h2>
                <p className="text-sm text-muted-foreground">최근 30일 추이</p>
              </div>
            </div>
            <p className="text-2xl font-bold tabular-nums">₩{info.rate.toLocaleString("ko-KR")}</p>
          </div>
          <div className="mt-4 h-72 w-full">
            <RateChart code={selected} />
          </div>
          <div className="mt-4 flex flex-wrap gap-2">
            <Button render={<Link href="/exchange" />} className="flex-1 sm:flex-none">
              환전하기
            </Button>
            <Button render={<Link href="/reservations" />} variant="outline" className="flex-1 sm:flex-none">
              목표 환율 예약
            </Button>
          </div>
        </Card>

        <p className="mt-6 text-center text-xs text-muted-foreground">
          본 서비스는 실제 금융 거래가 아닌 시뮬레이션입니다.
        </p>
      </main>
    </div>
  )
}
