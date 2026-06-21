"use client"

import { useMemo, useState, useEffect } from "react"
import { ArrowDownLeft, ArrowUpRight, ArrowLeftRight, Send, ExternalLink } from "lucide-react"
import Link from "next/link"
import { AppShell } from "@/components/app/app-shell"
import { Card } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Separator } from "@/components/ui/separator"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { Sheet, SheetContent, SheetHeader, SheetTitle } from "@/components/ui/sheet"
import { TxStatusBadge } from "@/components/app/status-badges"
import { timeAgo } from "@/components/app/transaction-row"
import type { Transaction, TxType, TxStatus } from "@/lib/store"
import { formatKRW, formatCurrency } from "@/lib/fx-data"
import { apiRequest } from "@/lib/api"

const typeMeta: Record<TxType, { label: string; icon: typeof ArrowDownLeft }> = {
  deposit: { label: "입금", icon: ArrowDownLeft },
  withdraw: { label: "출금", icon: ArrowUpRight },
  exchange: { label: "환전", icon: ArrowLeftRight },
  remittance: { label: "해외송금", icon: Send },
  transfer: { label: "이체", icon: ArrowLeftRight },
}

const periodOptions = [
  { value: "all", label: "전체 기간" },
  { value: "7", label: "최근 7일" },
  { value: "30", label: "최근 30일" },
  { value: "90", label: "최근 90일" },
]

const statusLabels: Record<TxStatus | "all", string> = {
  all: "전체 상태",
  completed: "완료",
  processing: "처리중",
  failed: "실패",
  refunded: "환불됨",
  canceled: "취소",
}

function formatDateTime(iso: string) {
  const d = new Date(iso)
  return `${d.getFullYear()}.${String(d.getMonth() + 1).padStart(2, "0")}.${String(d.getDate()).padStart(2, "0")} ${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}`
}

function formatRate(rate: number) {
  return `₩${Number(rate).toLocaleString("ko-KR", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
}

export default function TransactionsPage() {
  const [transactions, setTransactions] = useState<Transaction[]>([])
  const [loading, setLoading] = useState(true)
  const [period, setPeriod] = useState("all")
  const [type, setType] = useState<TxType | "all">("all")
  const [status, setStatus] = useState<TxStatus | "all">("all")
  const [detail, setDetail] = useState<Transaction | null>(null)

  useEffect(() => {
    async function load() {
      setLoading(true)
      try {
        let fetchWallets = true
        let fetchRemittances = false

        if (type === "remittance") {
          fetchWallets = false
          fetchRemittances = true
        } else if (type === "all") {
          fetchWallets = true
          fetchRemittances = true
        } else {
          fetchWallets = true
          fetchRemittances = false
        }

        let walletList: Transaction[] = []
        let remittanceList: Transaction[] = []

        if (fetchWallets) {
          let url = "/api/v1/wallets/transactions?size=100"
          if (type !== "all" && type !== "remittance") {
            const typeMapping: Record<string, string> = {
              deposit: "CHARGE",
              withdraw: "WITHDRAW",
              exchange: "EXCHANGE",
              transfer: "TRANSFER",
            }
            url += `&type=${typeMapping[type]}`
          }

          if (period !== "all") {
            const days = Number(period)
            const d = new Date()
            const toStr = d.toISOString().split("T")[0]
            d.setDate(d.getDate() - days)
            const fromStr = d.toISOString().split("T")[0]
            url += `&from=${fromStr}&to=${toStr}`
          }

          const res = await apiRequest<{ transactionResponseList: any[] }>("GET", url)
          walletList = (res.transactionResponseList || []).map((tx) => {
            const isCredit = tx.direction === "CREDIT"
            const txType: TxType = 
              tx.type === "CHARGE" ? "deposit" :
              tx.type === "WITHDRAW" ? "withdraw" :
              tx.type === "EXCHANGE" ? "exchange" :
              "transfer"
              
            const amountSign = isCredit ? 1 : -1
            
            return {
              id: `w-${tx.transactionId}`,
              type: txType,
              title: tx.type === "CHARGE" ? "KRW 입금" :
                     tx.type === "WITHDRAW" ? "KRW 출금" :
                     tx.type === "EXCHANGE" ? `${tx.currency} 환전` :
                     `이체 (${isCredit ? "받음" : "보냄"})`,
              amountKRW: Number(tx.amount) * amountSign,
              fromCurrency: tx.type === "EXCHANGE" && !isCredit ? "KRW" : undefined,
              toCurrency: tx.currency,
              rate: tx.type === "EXCHANGE" ? 1380 : undefined,
              fee: 0,
              status: "completed" as TxStatus,
              createdAt: tx.createdAt,
              detail: tx.type === "CHARGE" ? "모의계좌 입금" :
                      tx.type === "WITHDRAW" ? "모의계좌 출금" : undefined
            }
          })
        }

        if (fetchRemittances) {
          const res = await apiRequest<any[]>("GET", "/api/v1/transfers")
          remittanceList = (res || []).map((tx) => {
            let status: TxStatus = "completed"
            if (tx.status === "PENDING" || tx.status === "FUNDED" || tx.status === "PROCESSING") {
              status = "processing"
            } else if (tx.status === "FAILED" || tx.status === "REFUND_FAILED") {
              status = "failed"
            } else if (tx.status === "CANCELED") {
              status = "canceled"
            } else if (tx.status === "REFUNDED") {
              status = "refunded"
            }

            return {
              id: `r-${tx.transferId}`,
              type: "remittance",
              title: `해외송금 · ${tx.recipientName}`,
              amountKRW: -(Number(tx.sendAmount) + Number(tx.feeAmount ?? 0)),
              fromCurrency: tx.sendCurrency,
              toCurrency: tx.receiveCurrency,
              rate: tx.appliedRate,
              fee: Number(tx.feeAmount),
              status,
              createdAt: tx.createdAt,
              detail: `${tx.recipientBankName} · ${tx.recipientName}`
            }
          })

          if (period !== "all") {
            const days = Number(period)
            const cutoff = Date.now() - days * 86400000
            remittanceList = remittanceList.filter((tx) => new Date(tx.createdAt).getTime() >= cutoff)
          }
        }

        const merged = [...walletList, ...remittanceList].sort(
          (a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
        )
        setTransactions(merged)
      } catch (err) {
        console.error("Failed to load transactions:", err)
      } finally {
        setLoading(false)
      }
    }
    load()
  }, [type, period])

  const filtered = useMemo(() => {
    return transactions.filter((t) => {
      if (status !== "all" && t.status !== status) return false
      return true
    })
  }, [transactions, status])

  const received = (tx: Transaction) =>
    tx.rate ? Math.abs(tx.amountKRW) / (tx.rate / (tx.toCurrency === "JPY" ? 100 : 1)) : 0

  return (
    <AppShell title="거래내역">
      <div className="space-y-6">
        {/* Filters */}
        <Card className="p-4">
          <div className="grid gap-3 sm:grid-cols-3">
            <div className="space-y-1.5">
              <label className="text-xs font-medium text-muted-foreground">기간</label>
              <Select value={period} onValueChange={(v) => setPeriod(v ?? "all")}>
                <SelectTrigger>
                  <SelectValue>{periodOptions.find((o) => o.value === period)?.label}</SelectValue>
                </SelectTrigger>
                <SelectContent>
                  {periodOptions.map((o) => (
                    <SelectItem key={o.value} value={o.value}>
                      {o.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-1.5">
              <label className="text-xs font-medium text-muted-foreground">거래 유형</label>
              <Select value={type} onValueChange={(v) => setType((v as TxType | "all") ?? "all")}>
                <SelectTrigger>
                  <SelectValue>{type === "all" ? "전체 유형" : typeMeta[type].label}</SelectValue>
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="all">전체 유형</SelectItem>
                  <SelectItem value="deposit">입금</SelectItem>
                  <SelectItem value="withdraw">출금</SelectItem>
                  <SelectItem value="exchange">환전</SelectItem>
                  <SelectItem value="remittance">해외송금</SelectItem>
                  <SelectItem value="transfer">이체</SelectItem>
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-1.5">
              <label className="text-xs font-medium text-muted-foreground">상태</label>
              <Select value={status} onValueChange={(v) => setStatus((v as TxStatus | "all") ?? "all")}>
                <SelectTrigger>
                  <SelectValue>{statusLabels[status]}</SelectValue>
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="all">전체 상태</SelectItem>
                  <SelectItem value="completed">완료</SelectItem>
                  <SelectItem value="processing">처리중</SelectItem>
                  <SelectItem value="failed">실패</SelectItem>
                  <SelectItem value="refunded">환불됨</SelectItem>
                  <SelectItem value="canceled">취소</SelectItem>
                </SelectContent>
              </Select>
            </div>
          </div>
        </Card>

        {/* Table */}
        <Card className="overflow-hidden p-0">
          <div className="flex items-center justify-between px-5 py-4">
            <h2 className="text-base font-bold">거래 내역</h2>
            <span className="text-sm text-muted-foreground">{filtered.length}건</span>
          </div>
          <Separator />
          {loading ? (
            <div className="px-5 py-16 text-center text-sm text-muted-foreground">거래 내역을 불러오는 중입니다...</div>
          ) : filtered.length === 0 ? (
            <div className="px-5 py-16 text-center text-sm text-muted-foreground">조건에 맞는 거래가 없습니다.</div>
          ) : (
            <div className="overflow-x-auto">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>일시</TableHead>
                    <TableHead>유형</TableHead>
                    <TableHead className="text-right">금액</TableHead>
                    <TableHead className="text-right">환율</TableHead>
                    <TableHead className="text-right">수수료</TableHead>
                    <TableHead>상태</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {filtered.map((t) => {
                    const Icon = typeMeta[t.type].icon
                    const positive = t.amountKRW >= 0
                    return (
                      <TableRow key={t.id} className="cursor-pointer" onClick={() => setDetail(t)}>
                        <TableCell className="text-sm text-muted-foreground tabular-nums whitespace-nowrap">
                          {formatDateTime(t.createdAt)}
                        </TableCell>
                        <TableCell>
                          <span className="inline-flex items-center gap-2 whitespace-nowrap">
                            <span className="flex size-7 items-center justify-center rounded-full bg-secondary text-muted-foreground">
                              <Icon className="size-3.5" />
                            </span>
                            {typeMeta[t.type].label}
                          </span>
                        </TableCell>
                        <TableCell
                          className={`text-right font-semibold tabular-nums whitespace-nowrap ${positive ? "text-accent" : "text-foreground"}`}
                        >
                          {positive ? "+" : "-"}
                          {formatKRW(Math.abs(t.amountKRW))}
                        </TableCell>
                        <TableCell className="text-right text-sm tabular-nums">
                          {t.rate ? formatRate(t.rate) : "—"}
                        </TableCell>
                        <TableCell className="text-right text-sm tabular-nums">
                          {t.fee ? formatKRW(t.fee) : "—"}
                        </TableCell>
                        <TableCell>
                          <TxStatusBadge status={t.status} />
                        </TableCell>
                      </TableRow>
                    )
                  })}
                </TableBody>
              </Table>
            </div>
          )}
        </Card>
      </div>

      {/* Detail drawer */}
      <Sheet open={!!detail} onOpenChange={(o) => !o && setDetail(null)}>
        <SheetContent side="right" className="w-full overflow-y-auto sm:max-w-md">
          <SheetHeader>
            <SheetTitle>거래 상세</SheetTitle>
          </SheetHeader>
          {detail && (
            <div className="space-y-6 px-4 pb-6">
              {/* Summary */}
              <div className="rounded-2xl bg-secondary/50 p-4 text-center">
                <p className="text-sm text-muted-foreground">{detail.title}</p>
                <p
                  className={`mt-1 text-3xl font-bold tabular-nums ${detail.amountKRW >= 0 ? "text-accent" : "text-foreground"}`}
                >
                  {detail.amountKRW >= 0 ? "+" : "-"}
                  {formatKRW(Math.abs(detail.amountKRW))}
                </p>
                <div className="mt-2 flex justify-center">
                  <TxStatusBadge status={detail.status} />
                </div>
              </div>

              {/* Transaction info */}
              <section>
                <h3 className="mb-2 text-sm font-semibold">거래 정보</h3>
                <dl className="space-y-2.5 text-sm">
                  <Row label="거래번호" value={detail.id.toUpperCase()} mono />
                  <Row label="유형" value={typeMeta[detail.type].label} />
                  <Row label="일시" value={formatDateTime(detail.createdAt)} />
                  <Row label="경과" value={timeAgo(detail.createdAt)} />
                  {detail.detail && <Row label="상세" value={detail.detail} />}
                </dl>
              </section>

              {/* Rate snapshot */}
              {detail.rate && (
                <>
                  <Separator />
                  <section>
                    <h3 className="mb-2 text-sm font-semibold">환율 스냅샷</h3>
                    <dl className="space-y-2.5 text-sm">
                      <Row
                        label="통화쌍"
                        value={`${detail.fromCurrency ?? "KRW"} → ${detail.toCurrency ?? "KRW"}`}
                      />
                      <Row label="체결 환율" value={formatRate(detail.rate)} />
                      {detail.toCurrency && (
                        <Row label="환산 금액" value={formatCurrency(received(detail), detail.toCurrency)} />
                      )}
                    </dl>
                  </section>
                </>
              )}

              {/* Fee info */}
              <Separator />
              <section>
                <h3 className="mb-2 text-sm font-semibold">수수료 정보</h3>
                <dl className="space-y-2.5 text-sm">
                  <Row label="수수료" value={detail.fee ? formatKRW(detail.fee) : "무료"} />
                  <Row label="총 금액" value={formatKRW(Math.abs(detail.amountKRW))} bold />
                </dl>
              </section>

              {detail.type === "remittance" && (
                <Button render={<Link href={`/remittance/${detail.id}`} />} variant="outline" className="w-full">
                  <ExternalLink className="size-4" /> 송금 추적 보기
                </Button>
              )}
            </div>
          )}
        </SheetContent>
      </Sheet>
    </AppShell>
  )
}

function Row({ label, value, mono, bold }: { label: string; value: string; mono?: boolean; bold?: boolean }) {
  return (
    <div className="flex items-center justify-between gap-4">
      <dt className="shrink-0 text-muted-foreground">{label}</dt>
      <dd className={`text-right tabular-nums ${mono ? "font-mono text-xs" : ""} ${bold ? "font-bold" : "font-medium"}`}>
        {value}
      </dd>
    </div>
  )
}
