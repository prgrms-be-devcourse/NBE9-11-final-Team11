"use client"

import { useState } from "react"
import { Search, AlertCircle, DollarSign, ChevronLeft, ChevronRight } from "lucide-react"
import { MarketingHeader } from "@/components/marketing/marketing-header"
import { Card } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Separator } from "@/components/ui/separator"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { apiRequest } from "@/lib/api"
import { formatKRW } from "@/lib/fx-data"

// ── 타입 ─────────────────────────────────────────────────────────

interface RemittanceReceipt {
  transactionId: number
  senderName: string
  sendAmount: number
  receiveAmount: number
  exchangeRate: number
  createdAt: string
}

interface PagedReceiptResponse {
  content: RemittanceReceipt[]
  currentPage: number
  totalPages: number
  totalElements: number
}

interface UsdInquiryResponse {
  balance: number
  currencyCode: string
  remittanceReceipts: PagedReceiptResponse
}

// ── 헬퍼 ────────────────────────────────────────────────────────

function formatUSD(amount: number) {
  return "$" + Number(amount).toLocaleString("ko-KR", { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function formatRate(rate: number) {
  return "₩" + Number(rate).toLocaleString("ko-KR", { maximumFractionDigits: 2 })
}

function formatDateTime(iso: string) {
  const d = new Date(iso)
  return `${d.getFullYear()}.${String(d.getMonth() + 1).padStart(2, "0")}.${String(d.getDate()).padStart(2, "0")} ${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}`
}

// ── 메인 페이지 ──────────────────────────────────────────────────

export default function UsdInquiryPage() {
  const [bankName, setBankName] = useState("")
  const [accountNumber, setAccountNumber] = useState("")
  const [name, setName] = useState("")

  const [result, setResult] = useState<UsdInquiryResponse | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState("")

  const [page, setPage] = useState(0)
  const [pageLoading, setPageLoading] = useState(false)
  const PAGE_SIZE = 10

  const [lastQuery, setLastQuery] = useState({ bankName: "", accountNumber: "", name: "" })

  async function fetchPage(targetPage: number, query = lastQuery) {
    const isInitial = targetPage === 0 && query !== lastQuery
    isInitial ? setLoading(true) : setPageLoading(true)
    setError("")

    try {
      const data = await apiRequest<UsdInquiryResponse>(
        "POST",
        `/api/v1/mockbank/inquiry/usd?page=${targetPage}&size=${PAGE_SIZE}`,
        { bankName: query.bankName, accountNumber: query.accountNumber, name: query.name }
      )
      setResult(data)
      setPage(targetPage)
    } catch (err: any) {
      setError(err.message || "조회에 실패했습니다. 입력 정보를 확인해주세요.")
      if (isInitial) setResult(null)
    } finally {
      setLoading(false)
      setPageLoading(false)
    }
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (!bankName.trim() || !accountNumber.trim() || !name.trim()) {
      setError("모든 항목을 입력해주세요.")
      return
    }
    const query = { bankName: bankName.trim(), accountNumber: accountNumber.trim(), name: name.trim() }
    setLastQuery(query)
    setPage(0)
    setResult(null)
    await fetchPage(0, query)
  }

  const receipts = result?.remittanceReceipts
  const totalPages = receipts?.totalPages ?? 0
  const totalElements = receipts?.totalElements ?? 0

  return (
    <div className="flex min-h-screen flex-col bg-secondary/20">
      <MarketingHeader />

      <main className="mx-auto w-full max-w-3xl flex-1 px-4 py-8 sm:px-6">
        <div className="mb-6">
          <h1 className="text-2xl font-bold tracking-tight">USD 수취 내역 조회</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            수취인 본인의 정보를 입력하면 USD 모의계좌 잔액과 수취 내역을 확인할 수 있습니다.
          </p>
        </div>

        {/* 조회 폼 */}
        <Card className="p-5">
          <form onSubmit={handleSubmit} className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="name">이름</Label>
              <Input id="name" placeholder="홍길동" value={name} onChange={(e) => setName(e.target.value)} autoComplete="name" />
            </div>
            <div className="grid gap-4 sm:grid-cols-2">
              <div className="space-y-2">
                <Label htmlFor="bankName">은행명</Label>
                <Input id="bankName" placeholder="Chase Bank" value={bankName} onChange={(e) => setBankName(e.target.value)} />
              </div>
              <div className="space-y-2">
                <Label htmlFor="accountNumber">계좌번호</Label>
                <Input id="accountNumber" placeholder="계좌번호 입력" value={accountNumber} onChange={(e) => setAccountNumber(e.target.value)} />
              </div>
            </div>

            {error && (
              <div className="flex items-center gap-2 rounded-xl bg-destructive/10 px-4 py-3 text-sm text-destructive">
                <AlertCircle className="size-4 shrink-0" />
                {error}
              </div>
            )}

            <Button type="submit" className="w-full" disabled={loading}>
              {loading ? "조회 중..." : <><Search className="size-4" />잔액 및 수취 내역 조회</>}
            </Button>
          </form>
        </Card>

        {/* 조회 결과 */}
        {result && (
          <div className="mt-6 space-y-4">
            {/* 잔액 카드 */}
            <Card className="overflow-hidden border-0 bg-primary p-6 text-primary-foreground">
              <div className="flex items-center gap-2 text-sm text-primary-foreground/80">
                <DollarSign className="size-4" /> USD 모의계좌 잔액
              </div>
              <p className="mt-2 text-4xl font-bold tabular-nums">{formatUSD(result.balance)}</p>
              <p className="mt-1 text-sm text-primary-foreground/60">{result.currencyCode} · 수취 전용 계좌</p>
            </Card>

            {/* 수취 내역 */}
            <Card className="overflow-hidden p-0">
              <div className="flex items-center justify-between px-5 py-4">
                <div>
                  <h2 className="text-base font-bold">수취 내역</h2>
                  {totalElements > 0 && <p className="mt-0.5 text-xs text-muted-foreground">총 {totalElements}건</p>}
                </div>
              </div>
              <Separator />

              {receipts?.content.length === 0 ? (
                <p className="px-5 py-12 text-center text-sm text-muted-foreground">수취 내역이 없습니다.</p>
              ) : (
                <>
                  {/* 모바일: 카드형 */}
                  <div className="divide-y divide-border sm:hidden">
                    {receipts?.content.map((r) => (
                      <div key={r.transactionId} className="px-5 py-4">
                        <div className="flex items-center justify-between">
                          <span className="text-sm font-medium">{r.senderName}</span>
                          <span className="text-base font-bold tabular-nums text-accent">{formatUSD(r.receiveAmount)}</span>
                        </div>
                        <div className="mt-1 flex items-center justify-between text-xs text-muted-foreground">
                          <span>해외송금액 {formatKRW(r.sendAmount)} · {formatRate(r.exchangeRate)}</span>
                          <span>{formatDateTime(r.createdAt)}</span>
                        </div>
                      </div>
                    ))}
                  </div>

                  {/* 데스크탑: Table 컴포넌트 사용 */}
                  <div className="hidden sm:block">
                    <Table>
                      <TableHeader>
                        <TableRow>
                          <TableHead>해외송금인</TableHead>
                          <TableHead className="text-right">해외송금액 (KRW)</TableHead>
                          <TableHead className="text-right">수취액 (USD)</TableHead>
                          <TableHead className="text-right">적용 환율</TableHead>
                          <TableHead className="text-right">수취 일시</TableHead>
                        </TableRow>
                      </TableHeader>
                      <TableBody>
                        {receipts?.content.map((r) => (
                          <TableRow key={r.transactionId}>
                            <TableCell className="font-medium">{r.senderName}</TableCell>
                            <TableCell className="text-right tabular-nums text-muted-foreground">{formatKRW(r.sendAmount)}</TableCell>
                            <TableCell className="text-right font-semibold tabular-nums text-accent">{formatUSD(r.receiveAmount)}</TableCell>
                            <TableCell className="text-right tabular-nums text-muted-foreground">{formatRate(r.exchangeRate)}</TableCell>
                            <TableCell className="text-right tabular-nums text-muted-foreground">{formatDateTime(r.createdAt)}</TableCell>
                          </TableRow>
                        ))}
                      </TableBody>
                    </Table>
                  </div>

                  {/* 페이지네이션 */}
                  {totalPages > 1 && (
                    <div className="flex items-center justify-between border-t border-border px-5 py-3">
                      <p className="text-xs text-muted-foreground">{page + 1} / {totalPages} 페이지</p>
                      <div className="flex items-center gap-1">
                        <Button variant="outline" size="icon-sm" onClick={() => fetchPage(page - 1)} disabled={page === 0 || pageLoading}>
                          <ChevronLeft className="size-4" />
                          <span className="sr-only">이전</span>
                        </Button>
                        <Button variant="outline" size="icon-sm" onClick={() => fetchPage(page + 1)} disabled={page >= totalPages - 1 || pageLoading}>
                          <ChevronRight className="size-4" />
                          <span className="sr-only">다음</span>
                        </Button>
                      </div>
                    </div>
                  )}
                </>
              )}
            </Card>
          </div>
        )}

        <p className="mt-8 text-center text-xs text-muted-foreground">
          본 서비스는 시뮬레이션 전용 데모입니다. 실제 자금이 이동하지 않습니다.
        </p>
      </main>
    </div>
  )
}
