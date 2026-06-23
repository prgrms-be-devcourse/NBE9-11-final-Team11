"use client"

import { useEffect, useState } from "react"
import Link from "next/link"
import { TrendingUp, ArrowLeft, RefreshCw } from "lucide-react"
import { Card } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { RateChart } from "@/components/app/rate-chart"
import { getLatestRate, type FxRateLatest } from "@/lib/api"
import { CURRENCY_META } from "@/lib/fx-data"
import { formatFetchedAt } from "@/lib/utils"
import { useStore } from "@/lib/store"
import { AppShell } from "@/components/app/app-shell"
import { MarketingHeader } from "@/components/marketing/marketing-header"

const POLL_INTERVAL_MS = 60_000 // 60초마다 최신 환율 폴링

export default function RatesPage() {
  const { user, ready } = useStore()
  const [rate, setRate] = useState<FxRateLatest | null>(null)
  const [loading, setLoading] = useState(true)

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

  const usd = CURRENCY_META.USD

  // 렌더링할 환율 차트 및 정보 콘텐츠
  const rateContent = (
    <main className="mx-auto w-full max-w-5xl px-4 py-8 sm:px-6">
      {/* 회원일 경우 대시보드 내에서는 '홈으로' 링크를 생략하거나 유지할 수 있습니다. */}
      {/* 여기서는 디자인 통일성을 위해 필요 시 추가/삭제 가능합니다. */}
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
        <div>
          <h2 className="text-lg font-bold">USD/KRW 추이</h2>
          <p className="text-sm text-muted-foreground">최근 30일 (샘플 데이터)</p>
        </div>
        <div className="mt-4 h-72 w-full">
          <RateChart code="USD" />
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