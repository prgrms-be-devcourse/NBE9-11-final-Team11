"use client"

import { useState, useEffect } from "react"
import Link from "next/link"
import { Wallet, ArrowLeftRight, Send, Plus, Landmark } from "lucide-react"
import { AppShell } from "@/components/app/app-shell"
import { Card } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Progress } from "@/components/ui/progress"
import { TransactionRow } from "@/components/app/transaction-row"
import { useStore, type Transaction, type TxStatus } from "@/lib/store"
import { formatKRW, type CurrencyCode } from "@/lib/fx-data"
import { apiRequest, getLatestRate, getTransactionHistory, type FxRateLatest, type TransactionHistoryItem } from "@/lib/api"
import { formatFetchedAt } from "@/lib/utils"

function mapRemittanceStatus(status: string): TxStatus {
  if (status === "PENDING" || status === "FUNDED" || status === "PROCESSING") return "processing"
  if (status === "FAILED" || status === "REFUND_FAILED") return "failed"
  if (status === "CANCELED") return "canceled"
  if (status === "REFUNDED") return "refunded"
  return "completed"
}

function mapTransactionHistoryItem(tx: TransactionHistoryItem): Transaction | null {
  const isCredit = tx.direction === "CREDIT"
  const signedAmount = (amount: number) => Number(amount || 0) * (isCredit ? 1 : -1)
  const id = `${tx.transactionType}-${tx.journalId}-${tx.direction}-${tx.currency}`

  if (tx.transactionType === "CHARGE") {
    return {
      id,
      journalId: tx.journalId,
      type: "deposit",
      title: "KRW 입금",
      amountKRW: signedAmount(tx.amount),
      toCurrency: tx.currency as CurrencyCode,
      status: "completed",
      createdAt: tx.createdAt,
      detail: "모의계좌 입금",
    }
  }

  if (tx.transactionType === "WITHDRAW") {
    return {
      id,
      journalId: tx.journalId,
      type: "withdraw",
      title: "KRW 출금",
      amountKRW: signedAmount(tx.amount),
      toCurrency: tx.currency as CurrencyCode,
      status: "completed",
      createdAt: tx.createdAt,
      detail: "모의계좌 출금",
    }
  }

  if (tx.transactionType === "EXCHANGE") {
    return {
      id,
      journalId: tx.journalId,
      type: "exchange",
      title: `${tx.fromCurrency} → ${tx.toCurrency} 환전`,
      amountKRW: isCredit ? Number(tx.toAmount || 0) : -Number(tx.fromAmount || 0),
      fromCurrency: tx.fromCurrency as CurrencyCode,
      toCurrency: tx.toCurrency as CurrencyCode,
      rate: Number(tx.exchangeRate),
      fee: Number(tx.feeAmount),
      status: "completed",
      createdAt: tx.createdAt,
      fromAmount: Number(tx.fromAmount),
      toAmount: Number(tx.toAmount),
    }
  }

  if (tx.transactionType === "P2P_TRANSFER") {
    return {
      id,
      journalId: tx.journalId,
      type: "transfer",
      title: `이체 (${isCredit ? "받음" : "보냄"})`,
      amountKRW: signedAmount(tx.amount),
      toCurrency: tx.currency as CurrencyCode,
      status: "completed",
      createdAt: tx.createdAt,
      detail: tx.counterpartyEmail ? `${isCredit ? "보낸이" : "받는이"}: ${tx.counterpartyEmail}` : tx.memo,
    }
  }

  if (tx.transactionType === "REMITTANCE") {
    return {
      id,
      journalId: tx.journalId,
      type: "remittance",
      title: `해외송금 · ${tx.recipientName}`,
      amountKRW: signedAmount(tx.amount),
      toCurrency: tx.currency as CurrencyCode,
      status: mapRemittanceStatus(tx.status),
      createdAt: tx.createdAt,
      detail: `${tx.recipientBankName} · ${tx.recipientName} · ${tx.receiveAmount} ${tx.receiveCurrency}`,
    }
  }

  console.warn("Unsupported transaction type in dashboard recent transactions:", tx)
  return null
}

export default function DashboardPage() {
  const { user } = useStore()
  const [krwBalance, setKrwBalance] = useState<number>(0)
  const [transactions, setTransactions] = useState<Transaction[]>([])
  const [annualUsedUSD, setAnnualUsedUSD] = useState<number>(12000) // fallback
  const [limit, setLimit] = useState<number>(100000) // fallback
  const [loading, setLoading] = useState(true)
  // null = 확인 중, true = 연결됨, false = 미연결
  const [accountLinked, setAccountLinked] = useState<boolean | null>(null)
  const [rate, setRate] = useState<FxRateLatest | null>(null)

  useEffect(() => {
    async function loadDashboardData() {
      setLoading(true)
      try {
        const [rateResult, accountResult, walletResult, transactionsResult, limitResult] = await Promise.allSettled([
          getLatestRate("USD", "KRW"),
          apiRequest("GET", "/api/v1/users/me/mock-account"),
          apiRequest<{ walletResponseList: { currency: string; balance: number }[] }>("GET", "/api/v1/wallets"),
          getTransactionHistory({ page: 0, size: 5 }),
          apiRequest<{ annualLimitUsd: number; usedUsd: number }>("GET", "/api/v1/transfers/limit"),
        ])

        if (rateResult.status === "fulfilled") {
          setRate(rateResult.value)
        } else {
          console.error("Failed to load latest rate:", rateResult.reason)
        }

        setAccountLinked(accountResult.status === "fulfilled")

        if (walletResult.status === "fulfilled") {
          const krw = walletResult.value.walletResponseList?.find((w) => w.currency === "KRW")?.balance || 0
          setKrwBalance(krw)
        } else {
          console.error("Failed to load wallet data:", walletResult.reason)
        }

        if (transactionsResult.status === "fulfilled") {
          const mapped = (transactionsResult.value.data || [])
            .filter(Boolean)
            .map(mapTransactionHistoryItem)
            .filter((tx): tx is Transaction => tx !== null)
          setTransactions(mapped)
        } else {
          console.error("Failed to load recent transactions:", transactionsResult.reason)
          setTransactions([])
        }

        if (limitResult.status === "fulfilled") {
          setLimit(limitResult.value.annualLimitUsd || 100000)
          setAnnualUsedUSD(limitResult.value.usedUsd || 0)
        } else {
          console.error("Failed to load remittance limit:", limitResult.reason)
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
                  모의계좌를 연결하면 입출금, 환전, 해외송금을 이용할 수 있습니다.
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
            <Plus className="size-5" /> 충전하기
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
            <Send className="size-5" /> 해외송금
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
            <p className="mt-2 text-3xl font-bold tabular-nums">
              {rate ? formatKRW(rate.midRate) : loading ? "로딩 중..." : "-"}
            </p>
            {rate && <p className="text-sm text-muted-foreground">{formatFetchedAt(rate.fetchedAt)} 기준</p>}
          </Card>

          {/* Annual limit */}
          <Card className="gap-2 p-6">
            <span className="text-sm font-medium text-muted-foreground">연간 해외송금 한도</span>
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
