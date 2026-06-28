"use client"

import { useEffect, useMemo, useState } from "react"
import Link from "next/link"
import { TrendingUp, ArrowLeft, RefreshCw } from "lucide-react"
import { Card } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { RateChart, type RateChartPoint } from "@/components/app/rate-chart"
import {
  getLatestRate,
  getFxRateHistory,
  type FxRateLatest,
  type FxRateHistory,
  type FxRateHistoryPeriod,
} from "@/lib/api"
import { CURRENCY_META } from "@/lib/fx-data"
import { formatFetchedAt } from "@/lib/utils"
import { useStore } from "@/lib/store"
import { AppShell } from "@/components/app/app-shell"
import { MarketingHeader } from "@/components/marketing/marketing-header"

const POLL_INTERVAL_MS = 60_000 // 60초마다 최신 환율 폴링

const PERIODS: { key: FxRateHistoryPeriod; label: string }[] = [
  { key: "1D", label: "1일" },
  { key: "1W", label: "1주일" },
  { key: "1M", label: "1달" },
]

// 기간별 X축 라벨 포맷 — 1일은 시:분, 그 외(주/월)는 월/일
function formatHistoryLabel(iso: string, period: FxRateHistoryPeriod): string {
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return iso
  if (period === "1D") {
    return d.toLocaleTimeString("ko-KR", { hour: "2-digit", minute: "2-digit" })
  }
  return `${d.getMonth() + 1}/${d.getDate()}`
}

export default function RatesPage() {
  const { user, ready } = useStore()
  const [rate, setRate] = useState<FxRateLatest | null>(null)
  const [loading, setLoading] = useState(true)
  const [period, setPeriod] = useState<FxRateHistoryPeriod>("1D")
  const [history, setHistory] = useState<FxRateHistory | null>(null)
  const [historyLoading, setHistoryLoading] = useState(true)

  // 최신 환율(현재가 + 전일 대비) 60초 폴링 — 이전 요청 종료 후 다음 폴링 예약(요청 중첩 방지)
  useEffect(() => {
    let active = true
    let timerId: ReturnType<typeof setTimeout> | undefined

    const load = async () => {
      try {
        const data = await getLatestRate("USD", "KRW")
        if (!active) return
        setRate(data)
      } catch {
        // 실패 시 마지막 정상값을 유지한다.
      } finally {
        if (active) {
          setLoading(false)
          timerId = setTimeout(load, POLL_INTERVAL_MS)
        }
      }
    }

    load()
    return () => {
      active = false
      clearTimeout(timerId)
    }
  }, [])

  // 기간 변경 시 이력 조회 — 늦게 도착한 이전 기간 응답이 최신 선택을 덮어쓰지 않도록 stale-guard
  useEffect(() => {
    let active = true
    setHistoryLoading(true)
    const load = async () => {
      try {
        const data = await getFxRateHistory("USD", "KRW", period)
        if (!active) return
        setHistory(data)
      } catch {
        if (active) setHistory(null)
      } finally {
        if (active) setHistoryLoading(false)
      }
    }
    load()
    return () => {
      active = false
    }
  }, [period])

  const usd = CURRENCY_META.USD
  const periodLabel = PERIODS.find((p) => p.key === period)?.label ?? ""

  // 이력 응답을 차트 포인트로 가공 (응답에 실린 history.period 기준으로 라벨 포맷)
  const chartData = useMemo<RateChartPoint[]>(() => {
    if (!history) return []
    return history.points.map((p) => ({ label: formatHistoryLabel(p.timestamp, history.period), rate: p.midRate }))
  }, [history])

  // 렌더링할 환율 차트 및 정보 콘텐츠
  const rateContent = (
    <main className="mx-auto w-full max-w-5xl px-4 py-8 sm:px-6">
      {!user && (
        <Link
          href="/"
          className="mb-4 inline-flex items-center gap-1 text-sm text-muted-foreground transition-colors hover:text-foreground"
        >
          <ArrowLeft className="size-4" /> 홈으로
        </Link>
      )}

      <h1 className="text-2xl font-bold tracking-tight">실시간 환율</h1>
      <p className="mt-1 text-sm text-muted-foreground">USD/KRW 매매기준율 · 60초마다 자동 갱신</p>

      {/* 현재가 카드 */}
      <Card className="mt-6 p-5">
        {loading ? (
          <div className="flex items-center gap-2 text-sm text-muted-foreground">
            <RefreshCw className="size-4 animate-spin" /> 환율을 불러오는 중...
          </div>
        ) : rate ? (
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div className="flex items-center gap-3">
              <span className="text-3xl" aria-hidden>
                {usd.flag}
              </span>
              <div>
                <h2 className="text-lg font-bold">USD/KRW</h2>
                <p className="text-sm text-muted-foreground">{usd.name} · 매매기준율</p>
              </div>
            </div>
            <div className="text-right">
              <p className="text-3xl font-bold tabular-nums">
                ₩{rate.midRate.toLocaleString("ko-KR", { maximumFractionDigits: 2 })}
              </p>
              {/* 전일(15:30 기준) 대비 변동률 — 상승 빨강 / 하락 파랑 (기준값 없으면 미표시) */}
              {rate.changePercent != null && (
                <p
                  className={`mt-1 text-sm font-semibold tabular-nums ${
                    rate.changePercent >= 0 ? "text-red-500" : "text-blue-500"
                  }`}
                >
                  <span className="mr-1 text-xs font-normal text-muted-foreground">전일대비</span>
                  {rate.changePercent >= 0 ? "▲" : "▼"} {rate.changePercent >= 0 ? "+" : ""}
                  {rate.changePercent.toFixed(2)}%
                  {rate.changeRate != null && (
                    <span className="ml-1 text-xs font-normal text-muted-foreground">
                      ({rate.changeRate >= 0 ? "+" : ""}₩
                      {rate.changeRate.toLocaleString("ko-KR", { maximumFractionDigits: 2 })})
                    </span>
                  )}
                </p>
              )}
              <p className="mt-1 text-xs text-muted-foreground">{formatFetchedAt(rate.fetchedAt)} 기준</p>
            </div>
          </div>
        ) : (
          <div className="flex flex-col gap-1">
            <p className="text-base font-semibold">환율 정보를 불러올 수 없습니다</p>
            <p className="text-sm text-muted-foreground">
              잠시 후 다시 시도해 주세요. (환율 데이터가 아직 수집되지 않았을 수 있습니다)
            </p>
          </div>
        )}
      </Card>

      {/* 추이 차트 */}
      <Card className="mt-6 p-5">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <h2 className="text-lg font-bold">USD/KRW 추이</h2>
            <p className="text-sm text-muted-foreground">최근 {periodLabel}</p>
          </div>
          {/* 기간 선택 토글 */}
          <div className="inline-flex gap-1 rounded-lg bg-secondary/60 p-1">
            {PERIODS.map((p) => (
              <button
                key={p.key}
                type="button"
                onClick={() => setPeriod(p.key)}
                className={`rounded-md px-3 py-1 text-sm font-medium transition-colors ${
                  period === p.key
                    ? "bg-background text-foreground shadow-sm"
                    : "text-muted-foreground hover:text-foreground"
                }`}
              >
                {p.label}
              </button>
            ))}
          </div>
        </div>
        <div className="mt-4 h-72 w-full">
          {historyLoading ? (
            <div className="flex h-full items-center justify-center gap-2 text-sm text-muted-foreground">
              <RefreshCw className="size-4 animate-spin" /> 추이를 불러오는 중...
            </div>
          ) : (
            <RateChart data={chartData} />
          )}
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
  )

  if (!ready) return null

  // 회원일 경우: 사이드바와 함께 AppShell 렌더링
  if (user) {
    return <AppShell title="실시간 환율">{rateContent}</AppShell>
  }

  // 비회원일 경우: 기존 MarketingHeader와 함께 렌더링
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
          <Button render={<Link href="/login" />} size="sm">
            로그인
          </Button>
        </div>
      </header>
      {rateContent}
    </div>
  )
}
