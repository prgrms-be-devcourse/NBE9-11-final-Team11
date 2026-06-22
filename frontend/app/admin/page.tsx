"use client"

import { useCallback, useEffect, useState } from "react"
import { AlertTriangle, CheckCircle2, RefreshCw, Clock, ArrowDownRight } from "lucide-react"
import { toast } from "sonner"
import { AdminShell } from "@/components/admin/admin-shell"
import { Card } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Separator } from "@/components/ui/separator"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { cn } from "@/lib/utils"
import {
  getPoolDashboard,
  triggerRebalance,
  getRebalanceHistory,
  type PoolDashboardResponse,
  type RebalanceHistoryItem,
  type ApiError,
  type PoolStatusRes,
} from "@/lib/api"

// ── 헬퍼 ────────────────────────────────────────────────────────

function formatCurrency(currency: "KRW" | "USD", amount: number) {
  if (currency === "KRW") return `₩${(amount / 1e8).toFixed(1)}억`
  return `$${(amount / 1e4).toFixed(1)}만`
}

function formatAmount(currency: "KRW" | "USD", amount: number) {
  if (currency === "KRW") return `${Math.round(amount / 1e8)}억`
  return `${Math.round(amount / 1e4)}만`
}

function formatDateTime(iso: string) {
  const d = new Date(iso)
  return `${d.getFullYear()}.${String(d.getMonth() + 1).padStart(2, "0")}.${String(d.getDate()).padStart(2, "0")} ${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}`
}

// ── 풀 카드 (바 통합) ────────────────────────────────────────────

const STATUS_COLOR: Record<string, string> = {
  NORMAL: "var(--color-accent)",
  BELOW_FLOOR: "var(--color-destructive)",
  ABOVE_CEILING: "var(--color-chart-3)",
}

const STATUS_META: Record<string, { label: string; cls: string; icon: typeof CheckCircle2 }> = {
  NORMAL: { label: "정상", cls: "bg-accent/15 text-accent", icon: CheckCircle2 },
  BELOW_FLOOR: { label: "부족", cls: "bg-destructive/10 text-destructive", icon: AlertTriangle },
  ABOVE_CEILING: { label: "초과", cls: "bg-chart-3/15 text-chart-3", icon: AlertTriangle },
}

function PoolCard({ pool, isSellSource, showRecommended, sellAmount, bothBelowFloor }: { pool: PoolStatusRes; isSellSource: boolean; showRecommended: boolean; sellAmount?: number | null; bothBelowFloor?: boolean }) {
  const meta = STATUS_META[pool.status] ?? STATUS_META.NORMAL
  const Icon = meta.icon
  const color = STATUS_COLOR[pool.status]

  // 바 기준: ceilingBalance = 100% 너비
  const ref = pool.ceilingBalance
  const balancePct = Math.min((pool.balance / ref) * 100, 100)
  const floorPct = (pool.floorBalance / ref) * 100
  const targetPct = (pool.targetBalance / ref) * 100

  const action = pool.recommendedAction
  const actionPct = action
    ? Math.min((action.amount / ref) * 100, 100 - balancePct)
    : 0

  const afterBalance = action
    ? action.type === "BUY"
      ? pool.balance + action.amount
      : pool.balance - action.amount
    : null

  return (
    <Card className="p-5">
      {/* 헤더 */}
      <div className="flex items-center justify-between">
        <span className="text-base font-bold">{pool.currencyCode} Pool</span>
        <span className={cn("flex items-center gap-1 rounded-full px-2.5 py-0.5 text-xs font-semibold", meta.cls)}>
          <Icon className="size-3" />
          {meta.label}
        </span>
      </div>

      {/* 현재 잔고 */}
      <p className="mt-3 text-3xl font-bold tabular-nums">
        {formatCurrency(pool.currencyCode, pool.balance)}
      </p>

      {/* 권장 조치 */}
      {bothBelowFloor ? (
        <p className="mt-1 flex items-center gap-1 text-sm font-medium text-destructive">
          <AlertTriangle className="size-3.5" />
          두 풀 모두 부족 — 관리자 개입 필요
        </p>
      ) : action ? (
        <div className="mt-1 text-sm tabular-nums">
          <span className="font-medium" style={{ color }}>
            {action.type === "BUY" ? "권장 매입" : "권장 매도"}{" "}
            {formatCurrency(pool.currencyCode, action.amount)}
          </span>
          {action.counterAmount != null && (
            <span className="ml-1 text-muted-foreground">
              {"/ "}
              {formatCurrency(pool.currencyCode === "KRW" ? "USD" : "KRW", action.counterAmount)} 매도
            </span>
          )}
          {afterBalance !== null && (
            <span className="ml-2 text-muted-foreground">
              → 조치 후{" "}
              <span style={{ color }}>{formatCurrency(pool.currencyCode, afterBalance)}</span>
            </span>
          )}
        </div>
      ) : isSellSource ? (
        <p className="mt-1 flex items-center gap-1 text-sm text-muted-foreground">
          <ArrowDownRight className="size-3.5" />
          리밸런싱 재원
          {sellAmount != null && (
            <span> — {formatCurrency(pool.currencyCode, sellAmount)} 매도 예정</span>
          )}
        </p>
      ) : null}

      {/* 바 영역 */}
      <div className="mt-5">
        {/* 마커가 바 위아래로 돌출되도록 py로 영역 확보 */}
        <div className="relative py-1.5">
          {/* 얇은 바 */}
          <div className="relative h-2 w-full overflow-hidden rounded-full bg-secondary">
            {/* 현재 보유 (진한) */}
            <div
              className="absolute inset-y-0 left-0 rounded-l-full"
              style={{ width: `${balancePct}%`, background: color }}
            />
            {/* BUY 권장 (연한, 오른쪽 연장) — 버튼 hover 시에만 표시 */}
            {showRecommended && action?.type === "BUY" && actionPct > 0 && (
              <div
                className="absolute inset-y-0"
                style={{ left: `${balancePct}%`, width: `${actionPct}%`, background: color, opacity: 0.3 }}
              />
            )}
            {/* SELL 권장 (연한) — 버튼 hover 시에만 표시 */}
            {showRecommended && action?.type === "SELL" && actionPct > 0 && (
              <div
                className="absolute inset-y-0"
                style={{
                  left: `${Math.max(0, balancePct - actionPct)}%`,
                  width: `${actionPct}%`,
                  background: color,
                  opacity: 0.3,
                }}
              />
            )}
          </div>

          {/* 마커 선 */}
          <div className="pointer-events-none absolute inset-0">
            <div
              className="absolute inset-y-0 w-0.5"
              style={{ left: `${floorPct}%`, background: "#52525b" }}
            />
            <div
              className="absolute inset-y-0 w-0.5"
              style={{ left: `${targetPct}%`, background: "#18181b" }}
            />
          </div>
        </div>

        {/* 스케일 라벨 */}
        <div className="relative mt-1 h-4">
          {/* 왼쪽 끝: 0 */}
          <span className="absolute left-0 text-[11px] text-muted-foreground">0</span>
          {/* 하한 */}
          <span
            className="absolute whitespace-nowrap text-[11px] font-medium"
            style={{ left: `${floorPct}%`, transform: "translateX(-50%)", color: "#52525b" }}
          >
            하한 {formatAmount(pool.currencyCode, pool.floorBalance)}
          </span>
          {/* 목표 */}
          <span
            className="absolute whitespace-nowrap text-[11px] font-medium"
            style={{ left: `${targetPct}%`, transform: "translateX(-50%)", color: "#18181b" }}
          >
            기준 {formatAmount(pool.currencyCode, pool.targetBalance)}
          </span>
          {/* 오른쪽 끝: 상한 */}
          <span className="absolute right-0 text-[11px] text-muted-foreground">
            {formatAmount(pool.currencyCode, pool.ceilingBalance)}
          </span>
        </div>
      </div>
    </Card>
  )
}

// ── 수동 리밸런싱 버튼 ───────────────────────────────────────────

type ResultType = "success" | "info" | "warning" | "error"

function RebalanceCard({ onDone, onHoverChange }: { onDone: () => void; onHoverChange: (v: boolean) => void }) {
  const [loading, setLoading] = useState(false)
  const [resultMsg, setResultMsg] = useState<string | null>(null)
  const [resultType, setResultType] = useState<ResultType>("info")

  async function handleRebalance() {
    setLoading(true)
    setResultMsg(null)
    try {
      const res = await triggerRebalance()
      if (res.executed && res.action) {
        const a = res.action
        const msg = `${a.buyCurrency} 매입 완료 · ${formatCurrency(a.buyCurrency, a.buyAmount)} · 환율 ${Number(a.appliedRate).toLocaleString("ko-KR")}${res.cappedBy ? ` (상한 적용: ${res.cappedBy})` : ""}`
        setResultMsg(msg)
        setResultType("success")
        toast.success("리밸런싱 실행됨")
      } else if (res.reason === "BOTH_BELOW_FLOOR") {
        setResultMsg("KRW/USD 모두 부족 — 환전 불가. 관리자 수동 개입이 필요합니다.")
        setResultType("warning")
        toast.warning("리밸런싱 불가 — 양쪽 모두 부족")
      } else {
        setResultMsg("정상 범위 내 — 조치 불필요 (WITHIN_THRESHOLD)")
        setResultType("info")
        toast.info("리밸런싱 불필요")
      }
      onDone()
    } catch (e) {
      const err = e as ApiError
      if (err?.code === "REBALANCE_IN_PROGRESS") {
        setResultMsg("이미 리밸런싱이 진행 중입니다. 잠시 후 다시 시도하세요.")
      } else if (err?.code === "RATE_UNAVAILABLE") {
        setResultMsg("환율 정보를 가져올 수 없어 리밸런싱을 실행하지 못했습니다.")
      } else {
        setResultMsg(err?.message ?? "알 수 없는 오류가 발생했습니다.")
      }
      setResultType("error")
      toast.error("리밸런싱 실패")
    } finally {
      setLoading(false)
    }
  }

  return (
    <Card className="p-5">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-base font-bold">수동 리밸런싱</h2>
          <p className="mt-0.5 text-sm text-muted-foreground">현재 풀 상태를 기반으로 즉시 리밸런싱을 실행합니다.</p>
        </div>
        <Button
          onClick={handleRebalance}
          disabled={loading}
          className="shrink-0"
          onMouseEnter={() => onHoverChange(true)}
          onMouseLeave={() => onHoverChange(false)}
        >
          {loading
            ? <><RefreshCw className="mr-2 size-4 animate-spin" />실행 중...</>
            : <><RefreshCw className="mr-2 size-4" />리밸런싱 실행</>}
        </Button>
      </div>
      {resultMsg && (
        <div className={cn(
          "mt-4 rounded-2xl px-4 py-3 text-sm font-medium",
          resultType === "success" && "bg-accent/10 text-accent",
          resultType === "info" && "bg-secondary text-muted-foreground",
          resultType === "warning" && "bg-chart-3/10 text-chart-3",
          resultType === "error" && "bg-destructive/10 text-destructive",
        )}>
          {resultMsg}
        </div>
      )}
    </Card>
  )
}

// ── 리밸런싱 이력 표 ─────────────────────────────────────────────

function RebalanceHistory({ items, loading, error }: {
  items: RebalanceHistoryItem[]
  loading: boolean
  error: string | null
}) {
  return (
    <Card className="overflow-hidden p-0">
      <div className="px-5 pt-5 pb-1">
        <h2 className="text-base font-bold">리밸런싱 이력</h2>
      </div>
      {loading ? (
        <p className="px-5 py-8 text-center text-sm text-muted-foreground">불러오는 중...</p>
      ) : error ? (
        <p className="px-5 py-8 text-center text-sm text-destructive">{error}</p>
      ) : items.length === 0 ? (
        <p className="px-5 py-8 text-center text-sm text-muted-foreground">이력이 없습니다.</p>
      ) : (
        <div className="overflow-x-auto pb-5">
          <Table className="[&_th]:px-5 [&_td]:px-5 [&_th]:h-8 [&_th]:py-1.5 [&_th]:text-center [&_td]:py-2.5 [&_td]:text-center [&_tr]:border-0 [&_thead_tr]:bg-secondary/60 [&_tbody_tr:nth-child(even)]:bg-secondary/30">
            <TableHeader>
              <TableRow>
                <TableHead>매입</TableHead>
                <TableHead>매도</TableHead>
                <TableHead>매입 금액</TableHead>
                <TableHead>적용 환율</TableHead>
                <TableHead>트리거</TableHead>
                <TableHead>실행 시각</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {items.map((item) => (
                <TableRow key={item.id}>
                  <TableCell className="font-semibold">{item.buyCurrency}</TableCell>
                  <TableCell className="text-muted-foreground">{item.sellCurrency}</TableCell>
                  <TableCell className="tabular-nums">
                    {formatCurrency(item.buyCurrency, item.buyAmount)}
                  </TableCell>
                  <TableCell className="tabular-nums">
                    {Number(item.appliedRate).toLocaleString("ko-KR")}
                  </TableCell>
                  <TableCell className="text-xs text-muted-foreground">
                    {({ MANUAL: "수동실행", AUTO: "거래 후 자동실행", SCHEDULER: "스케줄러" } as Record<string, string>)[item.triggerType] ?? item.triggerType}
                  </TableCell>
                  <TableCell className="text-sm text-muted-foreground tabular-nums">
                    <span className="inline-flex items-center gap-1.5">
                      <Clock className="size-3" />
                      {formatDateTime(item.executedAt)}
                    </span>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      )}
    </Card>
  )
}

// ── 메인 페이지 ──────────────────────────────────────────────────

export default function AdminPage() {
  const [dashboard, setDashboard] = useState<PoolDashboardResponse | null>(null)
  const [dashLoading, setDashLoading] = useState(true)
  const [dashError, setDashError] = useState<string | null>(null)

  const [history, setHistory] = useState<RebalanceHistoryItem[]>([])
  const [histLoading, setHistLoading] = useState(true)
  const [histError, setHistError] = useState<string | null>(null)
  const [rebalanceHover, setRebalanceHover] = useState(false)

  const fetchDashboard = useCallback(async () => {
    setDashLoading(true)
    setDashError(null)
    try {
      setDashboard(await getPoolDashboard())
    } catch (e) {
      setDashError((e as ApiError)?.message ?? "풀 현황을 불러오지 못했습니다.")
    } finally {
      setDashLoading(false)
    }
  }, [])

  const fetchHistory = useCallback(async () => {
    setHistLoading(true)
    setHistError(null)
    try {
      setHistory(await getRebalanceHistory())
    } catch (e) {
      setHistError((e as ApiError)?.message ?? "이력을 불러오지 못했습니다.")
    } finally {
      setHistLoading(false)
    }
  }, [])

  useEffect(() => {
    fetchDashboard()
    fetchHistory()
  }, [fetchDashboard, fetchHistory])

  const buyPool = dashboard?.pools.find((p) => p.recommendedAction?.type === "BUY")
  const bothBelowFloor = dashboard?.pools.every((p) => p.status === "BELOW_FLOOR") ?? false

  return (
    <AdminShell title="유동성 풀 현황" active="/admin">
      <div className="space-y-6">
        {/* 풀 카드 — 세로 배치 */}
        {dashLoading ? (
          <Card className="p-8 text-center text-sm text-muted-foreground">풀 현황 불러오는 중...</Card>
        ) : dashError ? (
          <Card className="p-8 text-center text-sm text-destructive">{dashError}</Card>
        ) : dashboard ? (
          <>
            <div className="flex flex-col gap-4">
              {dashboard.pools.map((p) => (
                <PoolCard
                  key={p.currencyCode}
                  pool={p}
                  isSellSource={!bothBelowFloor && buyPool !== undefined && p.currencyCode !== buyPool.currencyCode}
                  showRecommended={rebalanceHover}
                  sellAmount={
                    !bothBelowFloor && buyPool !== undefined && p.currencyCode !== buyPool.currencyCode
                      ? (buyPool.recommendedAction?.counterAmount ?? null)
                      : null
                  }
                  bothBelowFloor={bothBelowFloor && p.status === "BELOW_FLOOR"}
                />
              ))}
            </div>
            <p className="-mt-4 text-right text-xs text-muted-foreground">
              업데이트: {formatDateTime(dashboard.asOf)}
            </p>
          </>
        ) : null}

        <RebalanceCard onDone={() => { fetchDashboard(); fetchHistory() }} onHoverChange={setRebalanceHover} />

        <RebalanceHistory
          items={history}
          loading={histLoading}
          error={histError}
        />
      </div>
    </AdminShell>
  )
}
