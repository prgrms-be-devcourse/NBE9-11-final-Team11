"use client"

import { useState } from "react"
import { AdminShell } from "@/components/admin/admin-shell"
import { Card } from "@/components/ui/card"
import { Separator } from "@/components/ui/separator"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { useAdminTransactions } from "@/hooks/useAdminTransactions"
import type { AdminTransactionItem } from "@/lib/api"
import { formatCurrency } from "@/lib/fx-data"
import type { CurrencyCode } from "@/lib/fx-data"

const TRIGGER_LABEL: Record<string, string> = {
  AUTO: "자동",
  MANUAL: "수동",
  SCHEDULER: "스케줄러",
}

const ACCOUNT_ROLE_LABEL: Record<string, string> = {
  WALLET: "지갑",
  BANK: "연결계좌",
  KRW_POOL: "원화풀",
  USD_POOL: "달러풀",
}


function AccountFlow({
  sourceType,
  mainCurrency,
  accountEntries,
}: {
  sourceType: string
  mainCurrency: string | null
  accountEntries: GroupedTx["accountEntries"]
}) {
  if (sourceType === "REBALANCING") {
    if (!mainCurrency) return <span className="text-muted-foreground text-sm">—</span>
    const from = mainCurrency === "KRW" ? "달러풀" : "원화풀"
    const to   = mainCurrency === "KRW" ? "원화풀" : "달러풀"
    return (
      <span className="text-sm tabular-nums">
        <span className="text-muted-foreground">{from}</span>
        <span className="mx-1 text-muted-foreground">→</span>
        <span>{to}</span>
      </span>
    )
  }

  const walletCurrencies = new Set(
    accountEntries.filter((e) => e.accountRole === "WALLET").map((e) => e.currencyCode)
  )
  const multiWalletCurrency = walletCurrencies.size > 1

  function label(e: GroupedTx["accountEntries"][number]) {
    const base = ACCOUNT_ROLE_LABEL[e.accountRole ?? ""] ?? e.accountRole ?? "?"
    if (e.accountRole === "WALLET" && multiWalletCurrency && e.currencyCode) {
      return `${base}(${e.currencyCode})`
    }
    return base
  }

  const froms = accountEntries.filter((e) => e.direction === "DEBIT"  && e.accountRole).map(label)
  const tos   = accountEntries.filter((e) => e.direction === "CREDIT" && e.accountRole).map(label)

  if (froms.length === 0 && tos.length === 0) {
    return <span className="text-muted-foreground text-sm">—</span>
  }

  return (
    <span className="text-sm whitespace-nowrap">
      {froms.length > 0 && <span className="text-muted-foreground">{froms.join(", ")}</span>}
      {froms.length > 0 && tos.length > 0 && <span className="mx-1.5 text-muted-foreground">→</span>}
      {tos.length > 0 && <span>{tos.join(", ")}</span>}
    </span>
  )
}

type CategoryKey = "지갑충전" | "지갑출금" | "P2P이체" | "환전" | "해외송금" | "리밸런싱"

const CATEGORY_COLOR: Record<CategoryKey, string> = {
  지갑충전: "bg-emerald-100 text-emerald-700",
  지갑출금: "bg-red-100 text-red-700",
  P2P이체: "bg-sky-100 text-sky-700",
  환전: "bg-violet-100 text-violet-700",
  해외송금: "bg-amber-100 text-amber-700",
  리밸런싱: "bg-indigo-100 text-indigo-700",
}

function getCategory(sourceType: string, subType: string): CategoryKey | string {
  if (sourceType === "REBALANCING") return "리밸런싱"
  const map: Record<string, CategoryKey> = {
    CHARGE: "지갑충전",
    WITHDRAW: "지갑출금",
    TRANSFER: "P2P이체",
    EXCHANGE: "환전",
    REMITTANCE: "해외송금",
  }
  return map[subType] ?? subType
}

function CategoryBadge({ sourceType, subType }: { sourceType: string; subType: string }) {
  const category = getCategory(sourceType, subType)
  const color = CATEGORY_COLOR[category as CategoryKey] ?? "bg-muted text-muted-foreground"
  return (
    <span className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-semibold ${color}`}>
      {category}
    </span>
  )
}

function formatAmount(amount: number | null | undefined, currencyCode: string | null | undefined): string {
  if (amount == null || currencyCode == null) return "—"
  return formatCurrency(amount, currencyCode as CurrencyCode)
}

function formatDateTime(iso: string): string {
  const d = new Date(iso)
  return `${d.getFullYear()}.${String(d.getMonth() + 1).padStart(2, "0")}.${String(d.getDate()).padStart(2, "0")} ${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}`
}

interface GroupedTx {
  key: string
  journalId: string | null
  sourceType: string
  subType: string
  createdAt: string
  mainAmount: number | null
  mainCurrency: string | null
  krwPoolChange: number | null
  usdPoolChange: number | null
  triggerType: string | null
  accountEntries: Array<{ accountRole: string | null; direction: string | null; currencyCode: string | null }>
}

function buildGroups(items: AdminTransactionItem[]): GroupedTx[] {
  const map = new Map<string, AdminTransactionItem[]>()
  items.forEach((item) => {
    const key = item.journalId ?? `reb-${item.id}`
    if (!map.has(key)) map.set(key, [])
    map.get(key)!.push(item)
  })

  const result: GroupedTx[] = []
  map.forEach((entries, key) => {
    const first = entries[0]
    const walletEntry =
      entries.find((e) => e.accountRole === "WALLET" && e.direction === "DEBIT") ??
      entries.find((e) => e.accountRole === "WALLET")

    const krwChange = entries.find((e) => e.krwPoolChange != null)?.krwPoolChange ?? null
    const usdChange = entries.find((e) => e.usdPoolChange != null)?.usdPoolChange ?? null

    const subType = entries.some((e) => e.subType === "REMITTANCE") ? "REMITTANCE" : first.subType

    result.push({
      key,
      journalId: first.journalId,
      sourceType: first.sourceType,
      subType,
      createdAt: first.createdAt,
      mainAmount: walletEntry?.amount ?? first.amount ?? null,
      mainCurrency: walletEntry?.currencyCode ?? first.currencyCode ?? null,
      krwPoolChange: krwChange,
      usdPoolChange: usdChange,
      triggerType: first.triggerType,
      accountEntries: entries.map((e) => ({
        accountRole: e.accountRole,
        direction: e.direction,
        currencyCode: e.currencyCode,
      })),
    })
  })

  return result
}

function PoolChange({ amount, currency }: { amount: number | null; currency: string }) {
  if (amount == null) return <span className="text-muted-foreground">—</span>
  const positive = amount >= 0
  return (
    <span className={`tabular-nums font-medium ${positive ? "text-emerald-600" : "text-red-500"}`}>
      {positive ? "+" : "-"}{formatAmount(Math.abs(amount), currency)}
    </span>
  )
}

function TransactionTable({ items }: { items: AdminTransactionItem[] }) {
  const grouped = buildGroups(items)

  if (grouped.length === 0) {
    return (
      <div className="py-16 text-center text-sm text-muted-foreground">
        조회된 거래내역이 없습니다.
      </div>
    )
  }
  return (
    <div className="overflow-x-auto">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead className="text-center">유형</TableHead>
            <TableHead className="text-center">계정 흐름</TableHead>
            <TableHead className="text-center">금액</TableHead>
            <TableHead className="text-center">원화풀 변화</TableHead>
            <TableHead className="text-center">달러풀 변화</TableHead>
            <TableHead className="text-center">저널 ID</TableHead>
            <TableHead className="text-center">트리거</TableHead>
            <TableHead className="text-center">일시</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {grouped.map((t) => (
            <TableRow key={t.key}>
              <TableCell className="text-center">
                <CategoryBadge sourceType={t.sourceType} subType={t.subType} />
              </TableCell>
              <TableCell className="text-center">
                <AccountFlow
                  sourceType={t.sourceType}
                  mainCurrency={t.mainCurrency}
                  accountEntries={t.accountEntries}
                />
              </TableCell>
              <TableCell className="text-center font-semibold tabular-nums">
                {formatAmount(t.mainAmount, t.mainCurrency)}
              </TableCell>
              <TableCell className="text-center text-sm">
                <PoolChange amount={t.krwPoolChange} currency="KRW" />
              </TableCell>
              <TableCell className="text-center text-sm">
                <PoolChange amount={t.usdPoolChange} currency="USD" />
              </TableCell>
              <TableCell className="text-center font-mono text-xs text-muted-foreground">
                {t.journalId ?? `#${t.key}`}
              </TableCell>
              <TableCell className="text-center text-sm">
                {t.triggerType ? (TRIGGER_LABEL[t.triggerType] ?? t.triggerType) : "—"}
              </TableCell>
              <TableCell className="text-center text-sm text-muted-foreground tabular-nums">
                {formatDateTime(t.createdAt)}
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </div>
  )
}

export default function AdminTransactionsPage() {
  const [fromInput, setFromInput] = useState("")
  const [toInput, setToInput] = useState("")
  const [appliedFrom, setAppliedFrom] = useState("")
  const [appliedTo, setAppliedTo] = useState("")
  const [page, setPage] = useState(0)
  const PAGE_SIZE = 30

  const { data, loading, error } = useAdminTransactions({
    page,
    size: PAGE_SIZE,
    from: appliedFrom,
    to: appliedTo,
  })

  function handleSearch() {
    setPage(0)
    setAppliedFrom(fromInput)
    setAppliedTo(toInput)
  }

  function handleReset() {
    setFromInput("")
    setToInput("")
    setPage(0)
    setAppliedFrom("")
    setAppliedTo("")
  }

  const totalPages = data?.totalPages ?? 1

  return (
    <AdminShell title="전체 거래내역" active="/admin/transactions">
      <div className="space-y-6">
        <Card className="overflow-hidden p-0">
          <div className="flex flex-wrap items-center gap-3 px-5 py-4">
            <h2 className="text-base font-bold">거래 모니터링</h2>
            <div className="ml-auto flex flex-wrap items-center gap-2">
              <Input
                type="date"
                value={fromInput}
                onChange={(e) => setFromInput(e.target.value)}
                className="w-36"
                placeholder="시작일"
              />
              <span className="text-sm text-muted-foreground">~</span>
              <Input
                type="date"
                value={toInput}
                onChange={(e) => setToInput(e.target.value)}
                className="w-36"
                placeholder="종료일"
              />
              <Button size="sm" onClick={handleSearch}>
                조회
              </Button>
              {(appliedFrom || appliedTo) && (
                <Button size="sm" variant="outline" onClick={handleReset}>
                  초기화
                </Button>
              )}
            </div>
          </div>
          <Separator />

          {error ? (
            <div className="py-16 text-center text-sm text-destructive">{error}</div>
          ) : loading ? (
            <div className="py-16 text-center text-sm text-muted-foreground">불러오는 중...</div>
          ) : (
            <TransactionTable items={data?.data ?? []} />
          )}

          {!loading && !error && totalPages > 1 && (
            <>
              <Separator />
              <div className="flex items-center justify-between px-5 py-3">
                <Button
                  size="sm"
                  variant="outline"
                  disabled={page === 0}
                  onClick={() => setPage((p) => p - 1)}
                >
                  이전
                </Button>
                <span className="text-sm text-muted-foreground tabular-nums">
                  {page + 1} / {totalPages}
                </span>
                <Button
                  size="sm"
                  variant="outline"
                  disabled={page >= totalPages - 1}
                  onClick={() => setPage((p) => p + 1)}
                >
                  다음
                </Button>
              </div>
            </>
          )}
        </Card>
      </div>
    </AdminShell>
  )
}
