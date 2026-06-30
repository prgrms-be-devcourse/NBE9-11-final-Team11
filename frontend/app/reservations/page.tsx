"use client"

import { useCallback, useMemo, useState } from "react"
import { CalendarClock, AlertTriangle, Plus, X, RefreshCw } from "lucide-react"
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
import { getLocalIsoDate } from "@/lib/utils"
import {
  getLatestRate,
  getReservations,
  createReservation,
  cancelReservation as cancelReservationApi,
  type FxRateLatest,
  type ReservationResponse,
} from "@/lib/api"
import { useRequireKyc } from "@/lib/use-require-kyc"

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

// 예약 금액은 fromCurrency 기준 — 매도(USD)는 $, 매수(KRW)는 ₩ 로 표기
function formatAmount(r: ReservationResponse): string {
  return r.fromCurrency === "USD" ? formatCurrency(r.amount, "USD") : formatKRW(r.amount)
}

// 매수=파랑(primary) / 매도=빨강(destructive) 색상 뱃지 (아이콘 없이 라벨+색으로 구분)
function DirectionBadge({ direction }: { direction: Direction }) {
  const isBuy = direction === "BUY"
  return (
    <span
      className={
        "inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium " +
        (isBuy ? "bg-primary/10 text-primary" : "bg-destructive/10 text-destructive")
      }
    >
      {isBuy ? "USD 매수" : "USD 매도"}
    </span>
  )
}

export default function ReservationsPage() {
  const { blocked: kycBlocked, dialog: kycDialog } = useRequireKyc()
  const [rate, setRate] = useState<FxRateLatest | null>(null)
  const [reservations, setReservations] = useState<ReservationResponse[]>([])
  const [loadingList, setLoadingList] = useState(true)

  const [direction, setDirection] = useState<Direction>("BUY")
  const [targetRate, setTargetRate] = useState("")
  const [amount, setAmount] = useState("")
  const [indefinite, setIndefinite] = useState(true) // 기본 무기한
  const [expiresAt, setExpiresAt] = useState(() => {
    const d = new Date()
    d.setDate(d.getDate() + 14)
    return getLocalIsoDate(d)
  })
  const [submitting, setSubmitting] = useState(false)
  const [cancelTarget, setCancelTarget] = useState<ReservationResponse | null>(null)
  const [canceling, setCanceling] = useState(false)

  const todayStr = useMemo(() => getLocalIsoDate(), [])

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

  // 목표 환율 기준 예상 수령액 (단순 환산, 수수료 미반영)
  const estimateReceive =
    targetNum > 0 && amountNum > 0
      ? direction === "BUY"
        ? formatCurrency(amountNum / targetNum, "USD") // KRW로 USD 매수 → 수령 USD
        : formatKRW(amountNum * targetNum) // USD 매도 → 수령 KRW
      : null

  // 방향별 제약: 매수는 현재 환율보다 높게, 매도는 현재 환율보다 낮게 예약 불가
  const violatesDirection =
    currentRate > 0 &&
    targetNum > 0 &&
    ((direction === "BUY" && targetNum > currentRate) ||
      (direction === "SELL" && targetNum < currentRate))

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
    if (direction === "BUY" && targetNum > currentRate)
      return toast.error("매수 예약은 현재 환율보다 높은 목표 환율로 설정할 수 없습니다.")
    if (direction === "SELL" && targetNum < currentRate)
      return toast.error("매도 예약은 현재 환율보다 낮은 목표 환율로 설정할 수 없습니다.")

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
          // 무기한이면 expiresAt 미전송(백엔드에서 null = 만료 없음). 아니면 만료일 끝(23:59:59).
          ...(indefinite ? {} : { expiresAt: `${expiresAt}T23:59:59` }),
        },
        crypto.randomUUID(),
      )
      toast.success("예약이 등록되었습니다.")
      setTargetRate("")
      setAmount("")
      await loadReservations()
    } catch (err: any) {
      // 중복 예약(R-007)은 방향별 문구로 안내 — 메시지 텍스트가 아닌 에러 코드로 분기
      if (err?.code === "R-007") {
        toast.error(
          direction === "BUY"
            ? "이미 예약 중인 매수 신청이 있습니다."
            : "이미 예약 중인 매도 신청이 있습니다.",
        )
      } else {
        toast.error(err?.message || "예약 등록에 실패했습니다.")
      }
    } finally {
      setSubmitting(false)
    }
  }

  async function doCancel(id: number) {
    setCanceling(true)
    try {
      await cancelReservationApi(id)
      toast.success("예약이 취소되었습니다.")
      setCancelTarget(null)
      await loadReservations()
    } catch (err: any) {
      toast.error(err?.message || "예약 취소에 실패했습니다.")
    } finally {
      setCanceling(false)
    }
  }

  const sorted = useMemo(
    () => [...reservations].sort((a, b) => +new Date(b.createdAt) - +new Date(a.createdAt)),
    [reservations],
  )

  const usd = CURRENCY_META.USD

  if (kycBlocked) return kycDialog

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
                    ? "매수 예약은 현재 환율보다 높은 목표 환율로 설정할 수 없습니다."
                    : "매도 예약은 현재 환율보다 낮은 목표 환율로 설정할 수 없습니다."}
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
                value={
                  direction === "BUY" ? (amount ? Number(amount).toLocaleString("ko-KR") : "") : amount
                }
                onChange={(e) =>
                  setAmount(
                    direction === "BUY"
                      ? e.target.value.replace(/[^\d]/g, "")
                      : limitDecimals(e.target.value, 2),
                  )
                }
              />
            </div>

            {/* 만료일 (기본 무기한, 지정 시 날짜 입력) */}
            <div className="space-y-2">
              <Label>만료일</Label>
              {indefinite ? (
                <div className="flex items-center justify-between rounded-xl border border-border bg-secondary/40 px-3 py-2 text-sm">
                  <span className="font-medium">무기한</span>
                  <Button type="button" variant="outline" size="sm" onClick={() => setIndefinite(false)}>
                    만료일 지정하기
                  </Button>
                </div>
              ) : (
                <>
                  <Input
                    id="res-expires"
                    type="date"
                    min={todayStr}
                    value={expiresAt}
                    onChange={(e) => setExpiresAt(e.target.value)}
                  />
                  <button
                    type="button"
                    className="text-xs text-muted-foreground underline-offset-2 hover:underline"
                    onClick={() => setIndefinite(true)}
                  >
                    무기한으로 변경
                  </button>
                </>
              )}
              <p className="text-xs text-muted-foreground">
                {indefinite
                  ? "만료 없이 목표 환율 도달 시까지 유지됩니다."
                  : "지정한 날짜까지 도달하지 않으면 만료됩니다."}
              </p>
            </div>

            {/* 예상 수령액 (목표 환율 기준 단순 환산, 수수료 미반영) */}
            {estimateReceive && (
              <div className="flex items-center justify-between rounded-xl bg-primary/5 px-3 py-2.5 text-sm">
                <span className="text-muted-foreground">
                  예상 수령액 <span className="text-[11px]">(목표 환율 기준)</span>
                </span>
                <span className="font-bold tabular-nums text-primary">≈ {estimateReceive}</span>
              </div>
            )}

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
                    <TableHead className="text-right">신청 금액</TableHead>
                    <TableHead>상태</TableHead>
                    <TableHead>생성일</TableHead>
                    <TableHead>만료일</TableHead>
                    <TableHead className="text-right">관리</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {sorted.map((r) => (
                    <TableRow key={r.reservationId}>
                      <TableCell className="font-mono text-xs">#{r.reservationId}</TableCell>
                      <TableCell>
                        <DirectionBadge direction={directionOf(r)} />
                      </TableCell>
                      <TableCell className="text-right tabular-nums">
                        ₩{r.targetRate.toLocaleString("ko-KR", { maximumFractionDigits: 2 })}
                      </TableCell>
                      <TableCell className="text-right tabular-nums">{formatAmount(r)}</TableCell>
                      <TableCell title={r.status === "FAILED" ? r.failureReason : undefined}>
                        <ReservationStatusBadge status={r.status} />
                      </TableCell>
                      <TableCell className="text-sm text-muted-foreground tabular-nums">
                        {formatDate(r.createdAt)}
                      </TableCell>
                      <TableCell className="text-sm text-muted-foreground tabular-nums">
                        {r.expiresAt ? formatDate(r.expiresAt) : "무기한"}
                      </TableCell>
                      <TableCell>
                        <div className="flex justify-end gap-1">
                          {r.status === "ACTIVE" ? (
                            <Button
                              variant="ghost"
                              size="sm"
                              className="text-destructive hover:text-destructive"
                              onClick={() => setCancelTarget(r)}
                            >
                              <X className="size-4" />
                              <span className="sr-only">취소</span>
                            </Button>
                          ) : (
                            <span className="text-xs text-muted-foreground">—</span>
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

      {/* Cancel confirm dialog */}
      <Dialog open={!!cancelTarget} onOpenChange={(o) => !o && !canceling && setCancelTarget(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>예약 취소</DialogTitle>
            <DialogDescription>
              정말 예약을 취소하시겠습니까? 취소한 예약은 되돌릴 수 없습니다.
            </DialogDescription>
          </DialogHeader>
          <div className="flex justify-end gap-2">
            <Button variant="outline" onClick={() => setCancelTarget(null)} disabled={canceling}>
              닫기
            </Button>
            <Button
              className="bg-destructive text-white hover:bg-destructive/90"
              onClick={() => cancelTarget && doCancel(cancelTarget.reservationId)}
              disabled={canceling}
            >
              {canceling ? "취소 중..." : "예약 취소"}
            </Button>
          </div>
        </DialogContent>
      </Dialog>
    </AppShell>
  )
}