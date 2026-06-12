"use client"

import { useMemo, useState } from "react"
import { CalendarClock, AlertTriangle, Plus, Eye, X } from "lucide-react"
import { toast } from "sonner"
import { AppShell } from "@/components/app/app-shell"
import { Card } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Separator } from "@/components/ui/separator"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { ReservationStatusBadge } from "@/components/app/status-badges"
import { useStore, type Reservation, type ReservationType } from "@/lib/store"
import { RATES, CURRENCY_META, formatKRW, krwPerUnit, type CurrencyCode } from "@/lib/fx-data"

const FX_CODES: Exclude<CurrencyCode, "KRW">[] = ["USD", "JPY", "EUR", "CNY"]

function formatDate(iso: string) {
  const d = new Date(iso)
  return `${d.getFullYear()}.${String(d.getMonth() + 1).padStart(2, "0")}.${String(d.getDate()).padStart(2, "0")}`
}

export default function ReservationsPage() {
  const { reservations, addReservation, cancelReservation } = useStore()

  const [type, setType] = useState<ReservationType>("exchange")
  const [currency, setCurrency] = useState<Exclude<CurrencyCode, "KRW">>("USD")
  const [targetRate, setTargetRate] = useState("")
  const [amount, setAmount] = useState("")
  const [expiresAt, setExpiresAt] = useState(() => {
    const d = new Date()
    d.setDate(d.getDate() + 14)
    return d.toISOString().slice(0, 10)
  })

  const [detail, setDetail] = useState<Reservation | null>(null)

  const currentRate = krwPerUnit(currency) * (currency === "JPY" ? 100 : 1)
  const targetNum = Number(targetRate.replace(/[^\d.]/g, "")) || 0
  // Warn when target is abnormally low (more than 5% below current market rate)
  const lowWarning = targetNum > 0 && targetNum < currentRate * 0.95

  function submit() {
    const amt = Number(amount.replace(/[^\d]/g, "")) || 0
    if (targetNum <= 0) return toast.error("목표 환율을 입력하세요.")
    if (amt <= 0) return toast.error("금액을 입력하세요.")
    addReservation({
      type,
      currency,
      targetRate: targetNum,
      amountKRW: amt,
      expiresAt: new Date(expiresAt).toISOString(),
    })
    toast.success("예약이 등록되었습니다.")
    setTargetRate("")
    setAmount("")
  }

  function handleCancel(id: string) {
    cancelReservation(id)
    toast.success("예약이 취소되었습니다.")
    setDetail(null)
  }

  const sorted = useMemo(
    () => [...reservations].sort((a, b) => +new Date(b.createdAt) - +new Date(a.createdAt)),
    [reservations],
  )

  return (
    <AppShell title="예약">
      <div className="grid gap-6 lg:grid-cols-[380px_1fr]">
        {/* Create reservation */}
        <Card className="h-fit p-5">
          <div className="flex items-center gap-2">
            <span className="flex size-9 items-center justify-center rounded-full bg-primary/10 text-primary">
              <CalendarClock className="size-4" />
            </span>
            <div>
              <h2 className="text-base font-bold">예약 등록</h2>
              <p className="text-xs text-muted-foreground">목표 환율 도달 시 자동 실행</p>
            </div>
          </div>

          <div className="mt-5 space-y-4">
            <div className="space-y-2">
              <Label>거래 유형</Label>
              <div className="grid grid-cols-2 gap-2">
                {(["exchange", "remittance"] as ReservationType[]).map((t) => (
                  <Button
                    key={t}
                    type="button"
                    variant={type === t ? "default" : "outline"}
                    onClick={() => setType(t)}
                  >
                    {t === "exchange" ? "환전" : "송금"}
                  </Button>
                ))}
              </div>
            </div>

            <div className="space-y-2">
              <Label htmlFor="res-currency">통화</Label>
              <Select value={currency} onValueChange={(v) => setCurrency((v as Exclude<CurrencyCode, "KRW">) ?? "USD")}>
                <SelectTrigger id="res-currency">
                  <SelectValue>
                    {CURRENCY_META[currency].flag} {currency} · {CURRENCY_META[currency].name}
                  </SelectValue>
                </SelectTrigger>
                <SelectContent>
                  {FX_CODES.map((c) => (
                    <SelectItem key={c} value={c}>
                      {CURRENCY_META[c].flag} {c} · {CURRENCY_META[c].name}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <p className="text-xs text-muted-foreground tabular-nums">
                현재 환율 ₩{currentRate.toLocaleString("ko-KR")} {RATES[currency].unit > 1 ? `/ ${RATES[currency].unit}` : ""}
              </p>
            </div>

            <div className="space-y-2">
              <Label htmlFor="res-target">목표 환율 (KRW)</Label>
              <Input
                id="res-target"
                inputMode="decimal"
                placeholder={String(Math.round(currentRate))}
                value={targetRate}
                onChange={(e) => setTargetRate(e.target.value.replace(/[^\d.]/g, ""))}
              />
              {lowWarning && (
                <div className="flex items-start gap-2 rounded-xl bg-chart-3/10 px-3 py-2 text-xs text-chart-3">
                  <AlertTriangle className="mt-0.5 size-3.5 shrink-0" />
                  현재 환율보다 비정상적으로 낮은 목표 환율입니다. 장기간 체결되지 않을 수 있습니다.
                </div>
              )}
            </div>

            <div className="space-y-2">
              <Label htmlFor="res-amount">금액 (KRW)</Label>
              <Input
                id="res-amount"
                inputMode="numeric"
                placeholder="0"
                value={amount ? Number(amount).toLocaleString("ko-KR") : ""}
                onChange={(e) => setAmount(e.target.value.replace(/[^\d]/g, ""))}
              />
            </div>

            <div className="space-y-2">
              <Label htmlFor="res-expires">만료일</Label>
              <Input
                id="res-expires"
                type="date"
                value={expiresAt}
                onChange={(e) => setExpiresAt(e.target.value)}
              />
            </div>

            <Button className="w-full" size="lg" onClick={submit}>
              <Plus className="size-4" /> 예약 등록
            </Button>
          </div>
        </Card>

        {/* Reservation list */}
        <Card className="overflow-hidden p-0">
          <div className="flex items-center justify-between px-5 py-4">
            <h2 className="text-base font-bold">예약 목록</h2>
            <span className="text-sm text-muted-foreground">{sorted.length}건</span>
          </div>
          <Separator />
          {sorted.length === 0 ? (
            <div className="px-5 py-16 text-center text-sm text-muted-foreground">등록된 예약이 없습니다.</div>
          ) : (
            <div className="overflow-x-auto">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>예약번호</TableHead>
                    <TableHead>유형</TableHead>
                    <TableHead className="text-right">목표환율</TableHead>
                    <TableHead>상태</TableHead>
                    <TableHead>생성일</TableHead>
                    <TableHead className="text-right">관리</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {sorted.map((r) => (
                    <TableRow key={r.id}>
                      <TableCell className="font-mono text-xs">{r.id.toUpperCase()}</TableCell>
                      <TableCell>
                        <span className="inline-flex items-center gap-1.5">
                          <span aria-hidden>{CURRENCY_META[r.currency].flag}</span>
                          {r.type === "exchange" ? "환전" : "송금"} · {r.currency}
                        </span>
                      </TableCell>
                      <TableCell className="text-right tabular-nums">₩{r.targetRate.toLocaleString("ko-KR")}</TableCell>
                      <TableCell>
                        <ReservationStatusBadge status={r.status} />
                      </TableCell>
                      <TableCell className="text-sm text-muted-foreground tabular-nums">
                        {formatDate(r.createdAt)}
                      </TableCell>
                      <TableCell>
                        <div className="flex justify-end gap-1">
                          <Button variant="ghost" size="sm" onClick={() => setDetail(r)}>
                            <Eye className="size-4" />
                            <span className="sr-only">상세보기</span>
                          </Button>
                          {r.status === "ACTIVE" && (
                            <Button
                              variant="ghost"
                              size="sm"
                              className="text-destructive hover:text-destructive"
                              onClick={() => handleCancel(r.id)}
                            >
                              <X className="size-4" />
                              <span className="sr-only">취소</span>
                            </Button>
                          )}
                        </div>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
          )}
        </Card>
      </div>

      {/* Detail dialog */}
      <Dialog open={!!detail} onOpenChange={(o) => !o && setDetail(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>예약 상세</DialogTitle>
            <DialogDescription className="font-mono text-xs">{detail?.id.toUpperCase()}</DialogDescription>
          </DialogHeader>
          {detail && (
            <dl className="space-y-2.5 text-sm">
              <Row label="유형" value={detail.type === "exchange" ? "환전" : "송금"} />
              <Row label="통화" value={`${CURRENCY_META[detail.currency].flag} ${detail.currency}`} />
              <Row label="목표 환율" value={`₩${detail.targetRate.toLocaleString("ko-KR")}`} />
              <Row label="예약 금액" value={formatKRW(detail.amountKRW)} />
              <Separator />
              <div className="flex items-center justify-between">
                <dt className="text-muted-foreground">상태</dt>
                <dd>
                  <ReservationStatusBadge status={detail.status} />
                </dd>
              </div>
              <Row label="생성일" value={formatDate(detail.createdAt)} />
              <Row label="만료일" value={formatDate(detail.expiresAt)} />
            </dl>
          )}
          {detail?.status === "ACTIVE" && (
            <Button
              variant="outline"
              className="w-full text-destructive hover:text-destructive"
              onClick={() => detail && handleCancel(detail.id)}
            >
              예약 취소
            </Button>
          )}
        </DialogContent>
      </Dialog>
    </AppShell>
  )
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between">
      <dt className="text-muted-foreground">{label}</dt>
      <dd className="font-medium tabular-nums">{value}</dd>
    </div>
  )
}
