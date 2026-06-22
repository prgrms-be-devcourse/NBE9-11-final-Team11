"use client"

import { useState, useEffect } from "react"
import Link from "next/link"
import { Wallet, ArrowLeftRight, Send, Plus, TrendingUp, TrendingDown, Landmark } from "lucide-react"
import { AppShell } from "@/components/app/app-shell"
import { Card } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Progress } from "@/components/ui/progress"
import { TransactionRow } from "@/components/app/transaction-row"
import { useStore, type Transaction, type TxType, type TxStatus } from "@/lib/store"
import { RATES, formatKRW } from "@/lib/fx-data"
import { apiRequest } from "@/lib/api"

export default function DashboardPage() {
  const { user } = useStore()
  const [krwBalance, setKrwBalance] = useState<number>(0)
  const [transactions, setTransactions] = useState<Transaction[]>([])
  const [annualUsedUSD, setAnnualUsedUSD] = useState<number>(12000) // fallback
  const [limit, setLimit] = useState<number>(100000) // fallback
  const [loading, setLoading] = useState(true)
  // null = 확인 중, true = 연결됨, false = 미연결
  const [accountLinked, setAccountLinked] = useState<boolean | null>(null)

  useEffect(() => {
    async function loadDashboardData() {
      setLoading(true)
      try {
        // 0. 모의계좌 연결 여부 확인 — 미연결(404)이어도 나머지 대시보드는 정상 진행
        try {
          await apiRequest("GET", "/api/v1/users/me/mock-account")
          setAccountLinked(true)
        } catch (err) {
          setAccountLinked(false)
        }

        // 1. Fetch Wallets for KRW Balance
        const walletData = await apiRequest<{ walletResponseList: { currency: string; balance: number }[] }>(
          "GET",
          "/api/v1/wallets"
        )
        const krw = walletData.walletResponseList?.find((w) => w.currency === "KRW")?.balance || 0
        setKrwBalance(krw)

        // 2. Fetch Recent Transactions
        const txData = await apiRequest<{ transactionResponseList: any[] }>(
          "GET",
          "/api/v1/wallets/transactions?size=5"
        )
        const rawList = (txData.transactionResponseList || []).filter(Boolean)
        const exchangeCounts: Record<string, number> = {}

        const mapped: Transaction[] = rawList.map((tx) => {
          let direction = tx.direction
          let currency = tx.currency

          // Fallback for exchanges if backend has not been restarted
          if (tx.type === "EXCHANGE" && (!direction || !currency)) {
            const count = exchangeCounts[tx.journalId] || 0
            exchangeCounts[tx.journalId] = count + 1
            
            if (count % 2 === 0) {
              direction = "DEBIT"
              currency = tx.fromCurrency || "KRW"
            } else {
              direction = "CREDIT"
              currency = tx.toCurrency || "USD"
            }
          }

          const isCredit = direction === "CREDIT"
          const amountSign = isCredit ? 1 : -1
          
          let txType: TxType = "transfer"
          let title = ""
          let amountKRW = Number(tx.amount || 0) * amountSign
          let fromCurrency: CurrencyCode | undefined = undefined
          let toCurrency: CurrencyCode | undefined = currency as CurrencyCode
          let rate: number | undefined = undefined
          let fee: number | undefined = undefined
          let detailStr = tx.memo || undefined

          if (tx.type === "CHARGE") {
            txType = "deposit"
            title = "KRW 입금"
            detailStr = "모의계좌 입금"
          } else if (tx.type === "WITHDRAW") {
            txType = "withdraw"
            title = "KRW 출금"
            detailStr = "모의계좌 출금"
          } else if (tx.type === "EXCHANGE") {
            txType = "exchange"
            title = `${tx.fromCurrency} → ${tx.toCurrency} 환전`
            
            amountKRW = direction === "CREDIT" ? Number(tx.toAmount) : -Number(tx.fromAmount)
            fromCurrency = tx.fromCurrency as CurrencyCode
            toCurrency = (direction === "CREDIT" ? tx.toCurrency : tx.fromCurrency) as CurrencyCode
            rate = Number(tx.exchangeRate)
            fee = Number(tx.feeAmount)
          } else if (tx.type === "TRANSFER") {
            txType = "transfer"
            title = `이체 (${isCredit ? "받음" : "보냄"})`
            detailStr = tx.counterpartyEmail ? `${isCredit ? "보낸이" : "받는이"}: ${tx.counterpartyEmail}` : tx.memo
          }

          return {
            id: `w-${tx.journalId || Math.random()}-${currency || "KRW"}`,
            journalId: tx.journalId,
            type: txType,
            title,
            amountKRW,
            fromCurrency,
            toCurrency,
            rate,
            fee,
            status: "completed" as TxStatus,
            createdAt: tx.createdAt,
            detail: detailStr
          }
        })
        setTransactions(mapped)

        // 3. Fetch Remittance Limit
        const limitData = await apiRequest<{ annualLimitUsd: number; usedUsd: number }>(
          "GET",
          "/api/v1/transfers/limit"
        )
        if (limitData) {
          setLimit(limitData.annualLimitUsd || 100000)
          setAnnualUsedUSD(limitData.usedUsd || 0)
        }
      } catch (err) {
        console.error("Failed to load dashboard data:", err)
      } finally {
        setLoading(false)
      }
    }
    loadDashboardData()
  }, [])

  const pct = Math.min(100, (annualUsedUSD / limit) * 100)
  const usd = RATES.USD

  return (
    <AppShell title="대시보드">
      <div className="flex flex-col gap-6">
        <div>
          <h2 className="text-2xl font-bold tracking-tight">안녕하세요, {user?.name ?? "사용자"}님</h2>
          <p className="mt-1 text-sm text-muted-foreground">오늘의 자산 현황과 환율을 확인하세요.</p>
        </div>

        {/* Account not linked banner */}
        {accountLinked === false && (
          <Card className="flex flex-row items-center justify-between gap-4 border-2 border-primary/40 bg-primary/5 p-5">
            <div className="flex items-center gap-3">
              <span className="flex size-10 items-center justify-center rounded-full bg-primary/10 text-primary">
                <Landmark className="size-5" />
              </span>
              <div>
                <p className="font-semibold">연결된 계좌가 없어요</p>
                <p className="text-sm text-muted-foreground">
                  모의계좌를 연결하면 입출금, 환전, 송금을 이용할 수 있습니다.
                </p>
              </div>
            </div>
            <Button render={<Link href="/onboarding/link-account" />} size="sm">
              계좌 연결하기
            </Button>
          </Card>
        )}

        {/* Quick actions */}
        <div className="grid gap-3 sm:grid-cols-3">
          <Button
            render={<Link href="/wallet" />}
            size="lg"
            className="h-auto justify-start gap-3 rounded-2xl py-4"
            disabled={accountLinked === false}
          >
            <Plus className="size-5" /> 입금하기
          </Button>
          <Button
            render={<Link href="/exchange" />}
            size="lg"
            variant="outline"
            className="h-auto justify-start gap-3 rounded-2xl py-4"
            disabled={accountLinked === false}
          >
            <ArrowLeftRight className="size-5" /> 환전하기
          </Button>
          <Button
            render={<Link href="/remittance" />}
            size="lg"
            variant="outline"
            className="h-auto justify-start gap-3 rounded-2xl py-4"
            disabled={accountLinked === false}
          >
            <Send className="size-5" /> 송금하기
          </Button>
        </div>

        <div className="grid gap-4 lg:grid-cols-3">
          {/* Wallet balance */}
          <Card className="gap-2 bg-primary p-6 text-primary-foreground">
            <span className="flex items-center gap-2 text-sm font-medium opacity-90">
              <Wallet className="size-4" /> KRW 지갑
            </span>
            <p className="mt-2 text-3xl font-bold tabular-nums">
              {loading ? "로딩 중..." : formatKRW(krwBalance)}
            </p>
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
              {loading ? "..." : `$${annualUsedUSD.toLocaleString()}`}{" "}
              <span className="text-base font-normal text-muted-foreground">/ ${limit.toLocaleString()}</span>
            </p>
            <Progress value={pct} className="mt-2 h-2" />
            <p className="text-xs text-muted-foreground">
              잔여 한도 {loading ? "..." : `$${(limit - annualUsedUSD).toLocaleString()}`}
            </p>
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
            {loading ? (
              <div className="py-6 text-center text-sm text-muted-foreground">최근 거래를 불러오는 중입니다...</div>
            ) : transactions.length === 0 ? (
              <div className="py-6 text-center text-sm text-muted-foreground">최근 거래 내역이 없습니다.</div>
            ) : (
              transactions.map((tx) => (
                <TransactionRow key={tx.id} tx={tx} />
              ))
            )}
          </div>
        </Card>
      </div>
    </AppShell>
  )
}
