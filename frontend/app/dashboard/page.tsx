"use client"

import Link from "next/link"
import { Wallet, ArrowLeftRight, Send, Plus, TrendingUp, TrendingDown } from "lucide-react"
import { AppShell } from "@/components/app/app-shell"
import { Card } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Progress } from "@/components/ui/progress"
import { TransactionRow } from "@/components/app/transaction-row"
import { useStore } from "@/lib/store"
import { RATES, formatKRW } from "@/lib/fx-data"

export default function DashboardPage() {
  const { krwBalance, transactions, annualUsedUSD, user } = useStore()
  const limit = 100000
  const pct = Math.min(100, (annualUsedUSD / limit) * 100)
  const usd = RATES.USD

  return (
    <AppShell title="대시보드">
      <div className="flex flex-col gap-6">
        <div>
          <h2 className="text-2xl font-bold tracking-tight">안녕하세요, {user?.name ?? "사용자"}님</h2>
          <p className="mt-1 text-sm text-muted-foreground">오늘의 자산 현황과 환율을 확인하세요.</p>
        </div>

        {/* Quick actions */}
        <div className="grid gap-3 sm:grid-cols-3">
          <Button render={<Link href="/wallet" />} size="lg" className="h-auto justify-start gap-3 rounded-2xl py-4">
            <Plus className="size-5" /> 입금하기
          </Button>
          <Button render={<Link href="/exchange" />} size="lg" variant="outline" className="h-auto justify-start gap-3 rounded-2xl py-4">
            <ArrowLeftRight className="size-5" /> 환전하기
          </Button>
          <Button render={<Link href="/remittance" />} size="lg" variant="outline" className="h-auto justify-start gap-3 rounded-2xl py-4">
            <Send className="size-5" /> 송금하기
          </Button>
        </div>

        <div className="grid gap-4 lg:grid-cols-3">
          {/* Wallet balance */}
          <Card className="gap-2 bg-primary p-6 text-primary-foreground">
            <span className="flex items-center gap-2 text-sm font-medium opacity-90">
              <Wallet className="size-4" /> KRW 지갑
            </span>
            <p className="mt-2 text-3xl font-bold tabular-nums">{formatKRW(krwBalance)}</p>
            <Link href="/wallet" className="mt-1 text-sm font-medium underline-offset-4 hover:underline">
              지갑 관리 →
            </Link>
          </Card>

          {/* Today rate */}
          <Card className="gap-2 p-6">
            <span className="text-sm font-medium text-muted-foreground">오늘의 환율 · USD/KRW</span>
            <p className="mt-2 text-3xl font-bold tabular-nums">{formatKRW(usd.rate)}</p>
            <span
              className={`flex items-center gap-1 text-sm font-medium ${
                usd.change >= 0 ? "text-accent" : "text-destructive"
              }`}
            >
              {usd.change >= 0 ? <TrendingUp className="size-4" /> : <TrendingDown className="size-4" />}
              {usd.change >= 0 ? "+" : ""}
              {usd.change}% 오늘
            </span>
          </Card>

          {/* Annual limit */}
          <Card className="gap-2 p-6">
            <span className="text-sm font-medium text-muted-foreground">연간 송금 한도</span>
            <p className="mt-2 text-2xl font-bold tabular-nums">
              ${annualUsedUSD.toLocaleString()}{" "}
              <span className="text-base font-normal text-muted-foreground">/ ${limit.toLocaleString()}</span>
            </p>
            <Progress value={pct} className="mt-2 h-2" />
            <p className="text-xs text-muted-foreground">잔여 한도 ${(limit - annualUsedUSD).toLocaleString()}</p>
          </Card>
        </div>

        {/* Recent transactions */}
        <Card className="p-5">
          <div className="mb-2 flex items-center justify-between">
            <h3 className="text-lg font-semibold">최근 거래</h3>
            <Link href="/transactions" className="text-sm font-medium text-primary hover:underline">
              전체보기
            </Link>
          </div>
          <div className="flex flex-col">
            {transactions.slice(0, 5).map((tx) => (
              <TransactionRow key={tx.id} tx={tx} />
            ))}
          </div>
        </Card>
      </div>
    </AppShell>
  )
}
