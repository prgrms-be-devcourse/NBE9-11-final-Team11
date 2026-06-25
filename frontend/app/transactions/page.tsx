"use client"

import { useMemo, useState, useEffect } from "react"
import { ArrowDownLeft, ArrowUpRight, ArrowLeftRight, Send, ExternalLink } from "lucide-react"
import Link from "next/link"
import { AppShell } from "@/components/app/app-shell"
import { Card } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Separator } from "@/components/ui/separator"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { Sheet, SheetContent, SheetHeader, SheetTitle } from "@/components/ui/sheet"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { TxStatusBadge } from "@/components/app/status-badges"
import { timeAgo } from "@/components/app/transaction-row"
import type { Transaction, TxType, TxStatus } from "@/lib/store"
import { formatKRW, formatCurrency, type CurrencyCode } from "@/lib/fx-data"
import { apiRequest } from "@/lib/api"

const typeMeta: Record<TxType, { label: string; icon: typeof ArrowDownLeft }> = {
  deposit: { label: "입금", icon: ArrowDownLeft },
  withdraw: { label: "출금", icon: ArrowUpRight },
  exchange: { label: "환전", icon: ArrowLeftRight },
  remittance: { label: "해외송금", icon: Send },
  transfer: { label: "이체", icon: ArrowLeftRight },
}

const PAGE_SIZE = 20
const PAGE_GROUP_SIZE = 5

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
  const [activeTab, setActiveTab] = useState<"wallet" | "remittance">("wallet")
  const [transactions, setTransactions] = useState<Transaction[]>([])
  const [loading, setLoading] = useState(true)
  const [fromDate, setFromDate] = useState("")
  const [toDate, setToDate] = useState("")
  const [walletType, setWalletType] = useState<TxType | "all">("all")
  const [remitStatus, setRemitStatus] = useState<TxStatus | "all">("all")
  const [detail, setDetail] = useState<Transaction | null>(null)
  const [currentPage, setCurrentPage] = useState(1)
  const [totalCount, setTotalCount] = useState(0)

  // Reset page when tab, filter, or type changes
  useEffect(() => {
    setCurrentPage(1)
  }, [activeTab, fromDate, toDate, walletType, remitStatus])

  useEffect(() => {
    async function load() {
      setLoading(true)
      try {
        if (activeTab === "wallet") {
          let url = `/api/v1/wallets/transactions?page=${currentPage - 1}&size=${PAGE_SIZE}`
          
          if (walletType !== "all") {
            const typeMapping: Record<string, string> = {
              deposit: "CHARGE",
              withdraw: "WITHDRAW",
              exchange: "EXCHANGE",
              transfer: "TRANSFER",
            }
            url += `&type=${typeMapping[walletType]}`
          }

          if (fromDate) {
            url += `&from=${fromDate}`
          }
          if (toDate) {
            url += `&to=${toDate}`
          }

          const res = await apiRequest<{ totalCount: number; transactionResponseList: any[] }>("GET", url)
          setTotalCount(res.totalCount || 0)
          
          const rawList = (res.transactionResponseList || []).filter(Boolean)
          const exchangeCounts: Record<string, number> = {}

          const mapped = rawList.map((tx) => {
            let direction = tx.direction
            let currency = tx.currency

            // Fallback for exchanges if backend has not been restarted to return direction/currency
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
              
              // For exchanges, use toAmount or fromAmount depending on direction
              amountKRW = direction === "CREDIT" ? Number(tx.toAmount) : -Number(tx.fromAmount)
              fromCurrency = tx.fromCurrency as CurrencyCode
              toCurrency = tx.toCurrency as CurrencyCode
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
              detail: detailStr,
              fromAmount: tx.type === "EXCHANGE" ? Number(tx.fromAmount) : undefined,
              toAmount: tx.type === "EXCHANGE" ? Number(tx.toAmount) : undefined
            }
          })
          setTransactions(mapped)
        } else {
          // Remittance Tab
          const res = await apiRequest<any>("GET", "/api/v1/transfers?size=1000")
          const dataList = res && res.data ? res.data : (Array.isArray(res) ? res : [])
          let mapped: Transaction[] = dataList.map((tx: any) => {
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
              journalId: tx.journalId,
              type: "remittance" as TxType,
              title: `해외송금 · ${tx.recipientName}`,
              amountKRW: -(Number(tx.sendAmount) + Number(tx.feeAmount ?? 0)),
              fromCurrency: tx.sendCurrency as CurrencyCode,
              toCurrency: tx.receiveCurrency as CurrencyCode,
              rate: tx.appliedRate,
              fee: Number(tx.feeAmount),
              status,
              createdAt: tx.createdAt,
              detail: `${tx.recipientBankName} · ${tx.recipientName}`
            }
          })

          // Apply Period Filter in frontend
          if (fromDate) {
            const fromTime = new Date(fromDate).getTime()
            mapped = mapped.filter((tx) => new Date(tx.createdAt).getTime() >= fromTime)
          }
          if (toDate) {
            const toTime = new Date(toDate).getTime() + 86400000 - 1
            mapped = mapped.filter((tx) => new Date(tx.createdAt).getTime() <= toTime)
          }

          // Apply Status Filter in frontend
          if (remitStatus !== "all") {
            mapped = mapped.filter((tx) => tx.status === remitStatus)
          }

          setTotalCount(mapped.length)
          setTransactions(mapped)
        }
      } catch (err) {
        console.error("Failed to load transactions:", err)
        setTotalCount(0)
        setTransactions([])
      } finally {
        setLoading(false)
      }
    }
    load()
  }, [activeTab, fromDate, toDate, walletType, remitStatus, currentPage])

  const totalPages = Math.max(1, Math.ceil(totalCount / PAGE_SIZE))
  const pagedTransactions = activeTab === "wallet"
    ? transactions // Server already paginated
    : transactions.slice((currentPage - 1) * PAGE_SIZE, currentPage * PAGE_SIZE) // Frontend paginate

  const currentPageGroup = Math.floor((currentPage - 1) / PAGE_GROUP_SIZE)
  const firstPageInGroup = currentPageGroup * PAGE_GROUP_SIZE + 1
  const lastPageInGroup = Math.min(firstPageInGroup + PAGE_GROUP_SIZE - 1, totalPages)
  const visiblePages = Array.from(
    { length: lastPageInGroup - firstPageInGroup + 1 },
    (_, index) => firstPageInGroup + index
  )

  useEffect(() => {
    if (currentPage > totalPages) {
      setCurrentPage(totalPages)
    }
  }, [currentPage, totalPages])

  const received = (tx: Transaction) =>
    tx.rate ? Math.abs(tx.amountKRW) / (tx.rate / (tx.toCurrency === "JPY" ? 100 : 1)) : 0

  // 금액 표시 헬퍼: 해외송금은 항상 KRW로 표시
  function displayAmount(tx: Transaction) {
    const currency = tx.type === "exchange"
      ? (tx.amountKRW >= 0 ? tx.toCurrency : tx.fromCurrency)
      : (tx.toCurrency || "KRW");
    if (tx.type !== "remittance" && currency && currency !== "KRW") {
      return formatCurrency(Math.abs(tx.amountKRW), currency)
    }
    return formatKRW(Math.abs(tx.amountKRW))
  }

  return (
    <AppShell title="거래내역">
      <div className="space-y-6">
        <Tabs value={activeTab} onValueChange={(v) => setActiveTab(v as "wallet" | "remittance")} className="w-full">
          <TabsList className="grid w-full grid-cols-2 max-w-[400px]">
            <TabsTrigger value="wallet">지갑 거래 내역</TabsTrigger>
            <TabsTrigger value="remittance">해외송금 내역</TabsTrigger>
          </TabsList>
          
          <TabsContent value="wallet" className="mt-4 space-y-6">
            {/* Wallet Filters */}
            <Card className="p-4">
              <div className="grid gap-3 sm:grid-cols-4 items-end">
                <div className="space-y-1.5">
                  <label className="text-xs font-medium text-muted-foreground">조회 시작일</label>
                  <Input
                    type="date"
                    value={fromDate}
                    onChange={(e) => setFromDate(e.target.value)}
                  />
                </div>
                <div className="space-y-1.5">
                  <label className="text-xs font-medium text-muted-foreground">조회 종료일</label>
                  <Input
                    type="date"
                    value={toDate}
                    onChange={(e) => setToDate(e.target.value)}
                  />
                </div>
                <div className="space-y-1.5">
                  <label className="text-xs font-medium text-muted-foreground">거래 유형</label>
                  <Select value={walletType} onValueChange={(v) => setWalletType((v as TxType | "all") ?? "all")}>
                    <SelectTrigger>
                      <SelectValue>{walletType === "all" ? "전체 유형" : typeMeta[walletType].label}</SelectValue>
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="all">전체 유형</SelectItem>
                      <SelectItem value="deposit">입금</SelectItem>
                      <SelectItem value="withdraw">출금</SelectItem>
                      <SelectItem value="exchange">환전</SelectItem>
                      <SelectItem value="transfer">이체</SelectItem>
                    </SelectContent>
                  </Select>
                </div>
                <Button variant="outline" onClick={() => { setFromDate(""); setToDate(""); setWalletType("all"); }} className="w-full">
                  필터 초기화
                </Button>
              </div>
            </Card>
          </TabsContent>

          <TabsContent value="remittance" className="mt-4 space-y-6">
            {/* Remittance Filters */}
            <Card className="p-4">
              <div className="grid gap-3 sm:grid-cols-4 items-end">
                <div className="space-y-1.5">
                  <label className="text-xs font-medium text-muted-foreground">조회 시작일</label>
                  <Input
                    type="date"
                    value={fromDate}
                    onChange={(e) => setFromDate(e.target.value)}
                  />
                </div>
                <div className="space-y-1.5">
                  <label className="text-xs font-medium text-muted-foreground">조회 종료일</label>
                  <Input
                    type="date"
                    value={toDate}
                    onChange={(e) => setToDate(e.target.value)}
                  />
                </div>
                <div className="space-y-1.5">
                  <label className="text-xs font-medium text-muted-foreground">상태</label>
                  <Select value={remitStatus} onValueChange={(v) => setRemitStatus((v as TxStatus | "all") ?? "all")}>
                    <SelectTrigger>
                      <SelectValue>{statusLabels[remitStatus]}</SelectValue>
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
                <Button variant="outline" onClick={() => { setFromDate(""); setToDate(""); setRemitStatus("all"); }} className="w-full">
                  필터 초기화
                </Button>
              </div>
            </Card>
          </TabsContent>
        </Tabs>

        {/* Table */}
        <Card className="overflow-hidden p-0">
          <div className="flex items-center justify-between px-5 py-4">
            <h2 className="text-base font-bold">
              {activeTab === "wallet" ? "지갑 거래 내역" : "해외송금 거래 내역"}
            </h2>
            <span className="text-sm text-muted-foreground">
              {totalCount}건 · {currentPage}/{totalPages}페이지
            </span>
          </div>
          <Separator />
          {loading ? (
            <div className="px-5 py-16 text-center text-sm text-muted-foreground">거래 내역을 불러오는 중입니다...</div>
          ) : pagedTransactions.length === 0 ? (
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
                    {activeTab === "remittance" && <TableHead>상태</TableHead>}
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {pagedTransactions.map((t) => {
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
                          className={`text-right font-semibold tabular-nums whitespace-nowrap ${positive ? "text-accent" : "text-text-primary"}`}
                        >
                          {positive ? "+" : "-"}
                          {displayAmount(t)}
                        </TableCell>
                        <TableCell className="text-right text-sm tabular-nums">
                          {t.rate ? formatRate(t.rate) : "—"}
                        </TableCell>
                        <TableCell className="text-right text-sm tabular-nums">
                          {t.fee ? formatCurrency(t.fee, t.fromCurrency || "KRW") : "—"}
                        </TableCell>
                        {activeTab === "remittance" && (
                          <TableCell>
                            <TxStatusBadge status={t.status} />
                          </TableCell>
                        )}
                      </TableRow>
                    )
                  })}
                </TableBody>
              </Table>
            </div>
          )}
          {!loading && totalCount > PAGE_SIZE && (
            <div className="relative border-t px-5 py-3">
              <p className="mb-3 text-sm text-muted-foreground sm:absolute sm:left-5 sm:top-1/2 sm:mb-0 sm:-translate-y-1/2">
                최근순 {Math.min((currentPage - 1) * PAGE_SIZE + 1, totalCount)}-
                {Math.min(currentPage * PAGE_SIZE, totalCount)}건 표시
              </p>
              <div className="flex flex-wrap justify-center gap-1.5">
                <Button
                  variant="outline"
                  size="sm"
                  disabled={firstPageInGroup === 1}
                  onClick={() => setCurrentPage(Math.max(1, firstPageInGroup - PAGE_GROUP_SIZE))}
                >
                  이전
                </Button>
                {visiblePages.map((page) => (
                  <Button
                    key={page}
                    variant={page === currentPage ? "default" : "outline"}
                    size="sm"
                    className="min-w-9"
                    onClick={() => setCurrentPage(page)}
                  >
                    {page}
                  </Button>
                ))}
                <Button
                  variant="outline"
                  size="sm"
                  disabled={lastPageInGroup === totalPages}
                  onClick={() => setCurrentPage(Math.min(totalPages, lastPageInGroup + 1))}
                >
                  다음
                </Button>
              </div>
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
                  {displayAmount(detail)}
                </p>
                <div className="mt-2 flex justify-center">
                  <TxStatusBadge status={detail.status} />
                </div>
              </div>

              {/* Transaction info */}
              <section>
                <h3 className="mb-2 text-sm font-semibold">거래 정보</h3>
                <dl className="space-y-2.5 text-sm">
                  <Row label="거래번호" value={String(detail.journalId ?? detail.id).toUpperCase()} mono />
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
                      {detail.fromAmount && detail.fromCurrency ? (
                        <Row label="환전 금액" value={formatCurrency(detail.fromAmount, detail.fromCurrency)} />
                      ) : (
                        detail.fromCurrency && <Row label="환전 금액" value={formatCurrency(Math.abs(detail.amountKRW), detail.fromCurrency)} />
                      )}
                      {detail.toAmount && detail.toCurrency ? (
                        <Row label="환산 금액" value={formatCurrency(detail.toAmount, detail.toCurrency)} />
                      ) : (
                        detail.toCurrency && <Row label="환산 금액" value={formatCurrency(received(detail), detail.toCurrency)} />
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
                  <Row label="수수료" value={detail.fee ? formatCurrency(detail.fee, detail.fromCurrency || "KRW") : "무료"} />
                  <Row
                    label="총 금액"
                    value={displayAmount(detail)}
                    bold
                  />
                </dl>
              </section>

              {detail.type === "remittance" && (
                <Button render={<Link href={`/remittance/${detail.id.replace("r-", "")}`} />} variant="outline" className="w-full">
                  <ExternalLink className="size-4" /> 해외송금 추적 보기
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
