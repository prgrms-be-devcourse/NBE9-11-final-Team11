"use client"

import { useMemo, useState } from "react"
import { AdminShell } from "@/components/admin/admin-shell"
import { Card } from "@/components/ui/card"
import { Separator } from "@/components/ui/separator"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { TxStatusBadge } from "@/components/app/status-badges"
import { formatKRW } from "@/lib/fx-data"
import type { TxStatus } from "@/lib/store"

interface AdminTx {
  id: string
  user: string
  type: "환전" | "송금" | "입금" | "출금"
  amountKRW: number
  status: TxStatus
  createdAt: string
}

const SEED: AdminTx[] = [
  { id: "atx-1042", user: "김민준", type: "송금", amountKRW: 1390275, status: "processing", createdAt: "2026-06-09T22:14:00Z" },
  { id: "atx-1041", user: "이서연", type: "환전", amountKRW: 693750, status: "completed", createdAt: "2026-06-09T21:02:00Z" },
  { id: "atx-1040", user: "박도윤", type: "입금", amountKRW: 3000000, status: "completed", createdAt: "2026-06-09T18:47:00Z" },
  { id: "atx-1039", user: "최지우", type: "송금", amountKRW: 2450000, status: "failed", createdAt: "2026-06-09T16:33:00Z" },
  { id: "atx-1038", user: "정하준", type: "환전", amountKRW: 456400, status: "completed", createdAt: "2026-06-09T15:10:00Z" },
  { id: "atx-1037", user: "강수아", type: "출금", amountKRW: 800000, status: "refunded", createdAt: "2026-06-09T12:21:00Z" },
  { id: "atx-1036", user: "윤지호", type: "송금", amountKRW: 5120000, status: "completed", createdAt: "2026-06-09T09:58:00Z" },
  { id: "atx-1035", user: "임채원", type: "환전", amountKRW: 1200000, status: "completed", createdAt: "2026-06-08T23:40:00Z" },
]

function formatDateTime(iso: string) {
  const d = new Date(iso)
  return `${d.getMonth() + 1}.${String(d.getDate()).padStart(2, "0")} ${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}`
}

export default function AdminTransactionsPage() {
  const [status, setStatus] = useState<TxStatus | "all">("all")

  const filtered = useMemo(() => (status === "all" ? SEED : SEED.filter((t) => t.status === status)), [status])
  const volume = filtered.reduce((s, t) => s + t.amountKRW, 0)

  return (
    <AdminShell title="전체 거래내역" active="/admin/transactions">
      <div className="space-y-6">
        <div className="grid gap-3 sm:grid-cols-3">
          <Card className="p-5">
            <p className="text-sm text-muted-foreground">총 거래 건수</p>
            <p className="mt-1 text-2xl font-bold tabular-nums">{filtered.length}건</p>
          </Card>
          <Card className="p-5">
            <p className="text-sm text-muted-foreground">총 거래 금액</p>
            <p className="mt-1 text-2xl font-bold tabular-nums">{formatKRW(volume)}</p>
          </Card>
          <Card className="p-5">
            <p className="text-sm text-muted-foreground">실패 / 환불</p>
            <p className="mt-1 text-2xl font-bold tabular-nums text-destructive">
              {SEED.filter((t) => t.status === "failed" || t.status === "refunded").length}건
            </p>
          </Card>
        </div>

        <Card className="overflow-hidden p-0">
          <div className="flex items-center justify-between px-5 py-4">
            <h2 className="text-base font-bold">거래 모니터링</h2>
            <Select value={status} onValueChange={(v) => setStatus((v as TxStatus | "all") ?? "all")}>
              <SelectTrigger className="w-36">
                <SelectValue>
                  {{ all: "전체 상태", completed: "완료", processing: "처리중", failed: "실패", refunded: "환불됨" }[status]}
                </SelectValue>
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">전체 상태</SelectItem>
                <SelectItem value="completed">완료</SelectItem>
                <SelectItem value="processing">처리중</SelectItem>
                <SelectItem value="failed">실패</SelectItem>
                <SelectItem value="refunded">환불됨</SelectItem>
              </SelectContent>
            </Select>
          </div>
          <Separator />
          <div className="overflow-x-auto">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>거래번호</TableHead>
                  <TableHead>사용자</TableHead>
                  <TableHead>유형</TableHead>
                  <TableHead className="text-right">금액</TableHead>
                  <TableHead>상태</TableHead>
                  <TableHead>일시</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {filtered.map((t) => (
                  <TableRow key={t.id}>
                    <TableCell className="font-mono text-xs">{t.id.toUpperCase()}</TableCell>
                    <TableCell className="font-medium">{t.user}</TableCell>
                    <TableCell>{t.type}</TableCell>
                    <TableCell className="text-right font-semibold tabular-nums">{formatKRW(t.amountKRW)}</TableCell>
                    <TableCell>
                      <TxStatusBadge status={t.status} />
                    </TableCell>
                    <TableCell className="text-sm text-muted-foreground tabular-nums">
                      {formatDateTime(t.createdAt)}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        </Card>

        <p className="text-center text-xs text-muted-foreground">
          본 데이터는 시뮬레이션용 목업이며 실제 사용자 거래가 아닙니다.
        </p>
      </div>
    </AdminShell>
  )
}
