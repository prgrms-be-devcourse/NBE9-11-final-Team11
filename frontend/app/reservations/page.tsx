"use client"

import { useCallback, useMemo, useState } from "react"
import { CalendarClock, AlertTriangle, Plus, Eye, X, RefreshCw } from "lucide-react"
import { toast } from "sonner"
import { AppShell } from "@/components/app/app-shell"
import { Card } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
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
import { usePollingRefresh } from "@/hooks/use-polling-refresh"
import { CURRENCY_META, formatKRW, formatCurrency } from "@/lib/fx-data"
import {
  getLatestRate,
  getReservations,
  createReservation,
  cancelReservation as cancelReservationApi,
  type FxRateLatest,
  type ReservationResponse,
} from "@/lib/api"

const POLL_INTERVAL_MS = 60_000 // 60초마다 최신 환율·예약 목록 폴링

// 거래 방향: 매수(KRW→USD) / 매도(USD→KRW). 송금은 추후 도입(현재 환전만 지원).
type Direction = "BUY" | "SELL"

function formatDate(iso: string) {
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return iso
  return `${d.getFullYear()}.${String(d.getMonth() + 1).padStart(2, "0")}.${String(d.getDate()).padStart(2, "0")}`
}

// 입력값을 소수점 maxDecimals 자리까지만 허용 (환율·USD 금액 입력 공용)
function limitDecimals(v: string, maxDecimals: number) {
  const cleaned = v.replace(/[^\d.]/g, "")
  const parts = cleaned.split(".")
  if (parts.length === 1) return parts[0]
  return parts[0] + "." + parts.slice(1).join("").slice(0, maxDecimals)
}

// 응답의 통화쌍으로 매수/매도 방향을 판별 (from=USD면 매도)
function directionOf(r: ReservationResponse): Direction {
  return r.fromCurrency === "USD" ? "SELL" : "BUY"
}

// 로컬 타임존 오프셋을 반영한 ISO 스트링 생성 유틸 함수
function getLocalIsoDate(date: Date) {
  const offset = date.getTimezoneOffset() * 60000
  return new Date(date.getTime() - offset).toISOString().slice(0, 10)
}

export default function ReservationsPage() {
  const [rate, setRate] = useState<FxRateLatest | null>(null)
  const [reservations, setReservations] = useState<ReservationResponse[]>([])
  const [loadingList, setLoadingList] = useState(true)

  const [direction, setDirection] = useState<Direction>("BUY")
  const [targetRate, setTargetRate] = useState("")
  const [amount, setAmount] = useState("")
  const [expiresAt, setExpiresAt] = useState(() => {
    const d = new Date()
    d.setDate(d.getDate() + 14)
    return getLocalIsoDate(d)
  })
  const [submitting, setSubmitting] = useState(false)
  const [detail, setDetail] = useState<ReservationResponse | null>(null)

  const todayStr = useMemo(() => getLocalIsoDate(new Date()), [])

  const loadReservations = useCallback(async () => {
    try {
      const res = await getReservations(0, 100)
      setReservations(res.data)
    } catch {
      // 목록 조회 실패 시 마지막 정상값을 유지한다.
    } finally {
      setLoadingList(false)
    }
  }, [])

  // 현재 환율(rates 페이지와 동일한 최신 매매기준율) + 예약 목록 갱신
  const refresh = useCallback(async () => {
    try {
      const data = await getLatestRate("USD", "KRW")
      setRate(data)
    } catch {
      // 환율 조회 실패 시 마지막 정상값을 유지한다.
    }
    await loadReservations()
  }, [loadReservations])

  // 60초 폴링 + 화면 복귀 시 재조회(5초 throttle) — 상태 변경을 근실시간으로 반영
  usePollingRefresh(refresh, { intervalMs: POLL_INTERVAL_MS, throttleMs: 5_000 })

  const currentRate = rate?.midRate ?? 0
  const targetNum = Number(targetRate) || 0
  const amountNum = Number(amount) || 0
  const amountCurrency = direction === "BUY" ? "KRW" : "USD"

  // 방향별 제약: 매수는 현재 환율보다 낮게, 매도는 현재 환율보다 높게 설정해야 함 (현재가와 동일한 예약 방지)
  const violatesDirection =
    currentRate > 0 &&
    targetNum > 0 &&
    ((direction === "BUY" && targetNum >= currentRate) ||
      (direction === "SELL" && targetNum <= currentRate))

  function switchDirection(d: Direction) {
    if (d === direction) return
    setDirection(d)
    setTargetRate("")
    setAmount("")
  }

  async function submit() {
    if (!rate || currentRate <= 0)
      return toast.error("현재 환율을 불러오는 중입니다. 잠시 후 다시 시도하세요.")
    if (targetNum <= 0) return toast.error("목표 환율을 입력하세요.")
    if (amountNum <= 0) return toast.error("금액을 입력하세요.")
    if (direction === "BUY" && targetNum >= currentRate)
      return toast.error("매수 예약은 현재 환율보다 낮은 목표 환율이어야 합니다.")
    if (direction === "SELL" && targetNum <= currentRate)
      return toast.error("매도 예약은 현재 환율보다 높은 목표 환율이어야 합니다.")

    const [fromCurrency, toCurrency] = direction === "BUY" ? ["KRW", "USD"] : ["USD", "KRW"]

    setSubmitting(true)
    try {
      await createReservation(
        {
          action: "EXCHANGE",
          fromCurrency,
          toCurrency,
          amount: amountNum,
          targetRate: targetNum,
          // 만료일 끝(23:59:59)으로 보내 @Future 검증과 사용자 기대를 맞춘다.
          expiresAt: `${expiresAt}T23:59:59`,
        },
        crypto.randomUUID(),
      )
      toast.success("예약이 등록되었습니다.")
      setTargetRate("")
      setAmount("")
      await loadReservations()
    } catch (err: any) {
      toast.error(err?.message || "예약 등록에 실패했습니다.")
    } finally {
      setSubmitting(false)
    }
  }

  async function handleCancel(id: number) {
    try {
      await cancelReservationApi(id)
      toast.success("예약이 취소되었습니다.")
      setDetail(null)
      await loadReservations()
    } catch (err: any) {
      toast.error(err?.message || "예약 취소에 실패했습니다.")
    }
  }

  const sorted = useMemo(
    () => [...reservations].sort((a, b) => +new Date(b.createdAt) - +new Date(a.createdAt)),
    [reservations],
  )

  const usd = CURRENCY_META.USD

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
              <p className="text-xs text-muted-foreground">목표 환율 도달 시 자동 체결</p>
            </div>
          </div>

          <div className="mt-5 space-y-4">
            {/* 거래 방향 (매수/매도) */}
            <div className="space-y-2">
              <Label>거래 방향</Label>
              <div className="grid grid-cols-2 gap-2">
                <Button
                  type="button"
                  variant={direction === "BUY" ? "default" : "outline"}
                  onClick={() => switchDirection("BUY")}
                >
                  USD 매수
                </Button>
                <Button
                  type="button"
                  variant={direction === "SELL" ? "default" : "outline"}
                  onClick={() => switchDirection("SELL")}
                >
                  USD 매도
                </Button>
              </div>
              <p className="text-xs text-muted-foreground">
                {direction === "BUY"
                  ? "원화로 달러를 목표 환율 이하에 매수합니다."
                  : "보유 달러를 목표 환율 이상에 매도합니다."}
              </p>
            </div>

            {/* 통화 (USD 고정) */}
            <div className="space-y-2">
              <Label>통화</Label>
              <div className="flex items-center justify-between rounded-xl border border-border bg-secondary/40 px-3 py-2.5 text-sm font-medium">
                <span className="inline-flex items-center gap-2">
                  <span aria-hidden>{usd.flag}</span> USD · {usd.name}
                </span>
                <span className="text-xs text-muted-foreground tabular-nums">
                  현재 ₩{currentRate > 0 ? currentRate.toLocaleString("ko-KR", { maximumFractionDigits: 2 }) : "—"}
                </span>
              </div>
            </div>

            {/* 목표 환율 */}
            <div className="space-y-2">
              <Label htmlFor="res-target">목표 환율 (₩/USD)</Label>
              <Input
                id="res-target"
                inputMode="decimal"
                placeholder={currentRate > 0 ? currentRate.toFixed(2) : "0.00"}
                value={targetRate}
                onChange={(e) => setTargetRate(limitDecimals(e.target.value, 2))}
              />
              {violatesDirection && (
                <div className="flex items-start gap-2 rounded-xl bg-destructive/10 px-3 py-2 text-xs text-destructive">
                  <AlertTriangle className="mt-0.5 size-3.5 shrink-0" />
                  {direction === "BUY"
                    ? "매수 예약은 현재 환율보다 낮은 목표 환율로 설정해야 합니다."
                    : "매도 예약은 현재 환율보다 높은 목표 환율로 설정해야 합니다."}
                </div>
              )}
            </div>

            {/* 금액 */}
            <div className="space-y-2">
              <Label htmlFor="res-amount">금액 ({amountCurrency})</Label>
              <Input
                id="res-amount"
                inputMode={direction === "BUY" ? "numeric" : "decimal"}
                placeholder="0"
                value={amount}
                onChange={(e) =>
                  setAmount(
                    direction === "BUY"
                      ? e.target.value.replace(/[^\d]/g, "")
                      : limitDecimals(e.target.value, 2),
                  )
                }
              />
              {direction === "BUY" && amount && (
                <p className="text-right text-xs text-muted-foreground tabular-nums">
                  {Number(amount).toLocaleString("ko-KR")} 원
                </p>
              )}
            </div>

            {/* 만료일 */}
            <div className="space-y-2">
              <Label htmlFor="res-expires">만료일</Label>
              <Input
                id="res-expires"
                type="date"
                min={todayStr}
                value={expiresAt}
                onChange={(e) => setExpiresAt(e.target.value)}
              />
            </div>

            <Button
              className="w-full"
              size="lg"
              onClick={submit}
              disabled={submitting || !rate || violatesDirection}
            >
              <Plus className="size-4" /> {submitting ? "등록 중..." : "예약 등록"}
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
          {loadingList ? (
            <div className="flex items-center justify-center gap-2 px-5 py-16 text-sm text-muted-foreground">
              <RefreshCw className="size-4 animate-spin" /> 예약을 불러오는 중...
            </div>
          ) : sorted.length === 0 ? (
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
                    <TableRow key={r.reservationId}>
                      <TableCell className="font-mono text-xs">#{r.reservationId}</TableCell>
                      <TableCell>
                        <span className="inline-flex items-center gap-1.5">
                          <span aria-hidden>{usd.flag}</span>
                          {directionOf(r) === "BUY" ? "USD 매수" : "USD 매도"}
                        </span>
                      </TableCell>
                      <TableCell className="text-right tabular-nums">
                        ₩{r.targetRate.toLocaleString("ko-KR", { maximumFractionDigits: 2 })}
                      </TableCell>
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
                              onClick={() => handleCancel(r.reservationId)}
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
            <DialogDescription className="font-mono text-xs">#{detail?.reservationId}</DialogDescription>
          </DialogHeader>
          {detail && (
            <dl className="space-y-2.5 text-sm">
              <Row label="유형" value={`${usd.flag} ${directionOf(detail) === "BUY" ? "USD 매수" : "USD 매도"}`} />
              <Row
                label="목표 환율"
                value={`₩${detail.targetRate.toLocaleString("ko-KR", { maximumFractionDigits: 2 })}`}
              />
              <Row
                label="예약 금액"
                value={detail.fromCurrency === "USD" ? formatCurrency(detail.amount, "USD") : formatKRW(detail.amount)}
              />
              <Separator />
              <div className="flex items-center justify-between">
                <dt className="text-muted-foreground">상태</dt>
                <dd>
                  <ReservationStatusBadge status={detail.status} />
                </dd>
              </div>
              <Row label="생성일" value={formatDate(detail.createdAt)} />
              <Row label="만료일" value={formatDate(detail.expiresAt)} />
              {detail.status === "FAILED" && detail.failureReason && (
                <Row label="실패 사유" value={detail.failureReason} />
              )}
            </dl>
          )}
          {detail?.status === "ACTIVE" && (
            <Button
              variant="outline"
              className="w-full text-destructive hover:text-destructive"
              onClick={() => detail && handleCancel(detail.reservationId)}
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
