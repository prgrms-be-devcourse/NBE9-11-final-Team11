"use client"

import Link from "next/link"
import { useEffect, useState } from "react"
import { getLatestRate, type FxRateLatest } from "@/lib/api"
import { CURRENCY_META } from "@/lib/fx-data"
import { formatFetchedAt } from "@/lib/utils"

const POLL_INTERVAL_MS = 60_000 // 60초마다 최신 환율 폴링 (환율 페이지와 동일)

// 메인 페이지 hero의 "실시간 환율" 위젯. USD/KRW 실데이터를 환율 페이지와 동일하게 조회하고,
// 클릭하면 환율 페이지(/rates)로 이동한다.
export function LiveRateWidget() {
  const [rate, setRate] = useState<FxRateLatest | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let active = true
    let timerId: ReturnType<typeof setTimeout> | undefined

    // 이전 요청이 끝난 뒤 다음 폴링을 예약(재귀 setTimeout) — 요청 중첩 방지
    const load = async () => {
      try {
        const data = await getLatestRate("USD", "KRW")
        if (!active) return
        setRate(data)
      } catch {
        // 실패(404 등) 시 마지막 정상값 유지. 한 번도 못 받았으면 rate === null.
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

  return (
    <Link
      href="/rates"
      className="block rounded-3xl border border-border bg-card p-6 shadow-xl shadow-primary/5 transition-colors hover:border-primary/40"
    >
      <div className="mb-4 flex items-center justify-between">
        <p className="text-sm font-semibold">실시간 환율</p>
        <span className="inline-flex items-center gap-1.5 text-xs text-muted-foreground">
          <span className="size-2 animate-pulse rounded-full bg-accent" />
          2분마다 갱신
        </span>
      </div>
      <div className="flex flex-col gap-2">
        <div className="flex items-center justify-between rounded-2xl bg-secondary/60 px-4 py-3">
          <div className="flex items-center gap-3">
            <span className="text-2xl" aria-hidden>
              {usd.flag}
            </span>
            <div>
              <p className="text-sm font-semibold">USD/KRW</p>
              <p className="text-xs text-muted-foreground">{usd.name}</p>
            </div>
          </div>
          <div className="text-right">
            {loading ? (
              <p className="text-sm text-muted-foreground">불러오는 중...</p>
            ) : rate ? (
              <>
                <p className="text-sm font-semibold tabular-nums">
                  ₩{rate.midRate.toLocaleString("ko-KR", { maximumFractionDigits: 2 })}
                </p>
                <p className="text-xs text-muted-foreground tabular-nums">{formatFetchedAt(rate.fetchedAt)} 기준</p>
              </>
            ) : (
              <p className="text-sm text-muted-foreground">불러올 수 없음</p>
            )}
          </div>
        </div>
      </div>
    </Link>
  )
}
