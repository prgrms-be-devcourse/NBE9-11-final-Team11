"use client"

import { useState, useEffect } from "react"
import Link from "next/link"
import { Check, X, ArrowRight } from "lucide-react"
import { Button } from "@/components/ui/button"
import { formatKRW } from "@/lib/fx-data"
import { getLatestRate } from "@/lib/api"

export function SavingsComparison() {
  const [rate, setRate] = useState<number>(1387.5)

  useEffect(() => {
    getLatestRate("USD", "KRW")
      .then((data) => {
        if (data && data.midRate) {
          setRate(data.midRate)
        }
      })
      .catch((err) => {
        console.error("Failed to fetch exchange rate for homepage comparison", err)
      })
  }, [])

  const sampleKRW = 1000 * rate
  const bankFlat = 15000
  const bankPercent = Math.round(sampleKRW * 0.0175)
  const bank = bankFlat + bankPercent

  const fxFlat = 5000
  const fxPercent = Math.round(sampleKRW * 0.005)
  const fxflow = fxFlat + fxPercent

  const savings = bank - fxflow

  return (
    <section id="savings" className="mx-auto max-w-6xl px-4 py-16 sm:px-6">
      <div className="mx-auto mb-12 max-w-2xl text-center">
        <h2 className="text-balance text-3xl font-bold tracking-tight sm:text-4xl">은행보다 투명한 수수료</h2>
        <p className="mt-3 text-pretty text-muted-foreground">
          $1,000 (약 {formatKRW(sampleKRW)}) 해외송금 시 예상 수수료를 비교했습니다.
        </p>
      </div>

      <div className="mx-auto grid max-w-3xl gap-4 sm:grid-cols-2">
        <div className="rounded-3xl border border-border bg-card p-6">
          <p className="text-sm font-semibold text-muted-foreground">일반 은행 해외송금</p>
          <p className="mt-2 text-3xl font-bold tabular-nums">{formatKRW(bank)}</p>
          <p className="text-[11px] text-muted-foreground/80 mt-1">
            기본 수수료 {formatKRW(bankFlat)} + 스프레드 수수료 {formatKRW(bankPercent)}
          </p>
          <ul className="mt-5 flex flex-col gap-3 text-sm">
            <li className="flex items-center gap-2 text-muted-foreground">
              <X className="size-4 text-destructive" /> 높은 송금 수수료 (기본 15,000원)
            </li>
            <li className="flex items-center gap-2 text-muted-foreground">
              <X className="size-4 text-destructive" /> 비싼 환전 스프레드 (1.75%)
            </li>
            <li className="flex items-center gap-2 text-muted-foreground">
              <X className="size-4 text-destructive" /> 불투명한 중개/수취 수수료
            </li>
          </ul>
        </div>

        <div className="rounded-3xl border-2 border-primary bg-primary/5 p-6">
          <p className="text-sm font-semibold text-primary">FXFlow</p>
          <p className="mt-2 text-3xl font-bold tabular-nums text-primary">{formatKRW(fxflow)}</p>
          <p className="text-[11px] text-primary/80 mt-1 font-medium">
            기본 수수료 {formatKRW(fxFlat)} + 송금 수수료율 {formatKRW(fxPercent)}
          </p>
          <ul className="mt-5 flex flex-col gap-3 text-sm">
            <li className="flex items-center gap-2">
              <Check className="size-4 text-accent" /> 저렴한 송금 수수료 (기본 5,000원)
            </li>
            <li className="flex items-center gap-2">
              <Check className="size-4 text-accent" /> 합리적인 송금 수수료율 (0.5%)
            </li>
            <li className="flex items-center gap-2">
              <Check className="size-4 text-accent" /> 실시간 시장 환율 (숨은 비용 없음)
            </li>
          </ul>
        </div>
      </div>

      <div className="mx-auto mt-8 max-w-3xl rounded-3xl bg-accent/10 p-6 text-center">
        <p className="text-sm text-muted-foreground">예상 절약 금액</p>
        <p className="mt-1 text-4xl font-bold tabular-nums text-accent">{formatKRW(savings)}</p>
        <Button render={<Link href="/signup" />} className="mt-5">
          지금 시작하기
          <ArrowRight className="size-4" />
        </Button>
      </div>
    </section>
  )
}
