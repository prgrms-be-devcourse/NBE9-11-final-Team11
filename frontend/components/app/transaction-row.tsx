"use client"

import { ArrowDownLeft, ArrowUpRight, ArrowLeftRight, Send } from "lucide-react"
import type { Transaction, TxType } from "@/lib/store"
import { formatKRW } from "@/lib/fx-data"
import { TxStatusBadge } from "./status-badges"

const iconMap: Record<TxType, typeof ArrowDownLeft> = {
  deposit: ArrowDownLeft,
  withdraw: ArrowUpRight,
  exchange: ArrowLeftRight,
  remittance: Send,
}

function timeAgo(iso: string) {
  const diff = Date.now() - new Date(iso).getTime()
  const m = Math.floor(diff / 60000)
  if (m < 1) return "방금 전"
  if (m < 60) return `${m}분 전`
  const h = Math.floor(m / 60)
  if (h < 24) return `${h}시간 전`
  return `${Math.floor(h / 24)}일 전`
}

export function TransactionRow({ tx, onClick }: { tx: Transaction; onClick?: () => void }) {
  const Icon = iconMap[tx.type]
  const positive = tx.amountKRW >= 0
  return (
    <button
      onClick={onClick}
      className="flex w-full items-center gap-3 rounded-2xl px-3 py-3 text-left transition-colors hover:bg-secondary/60"
    >
      <span
        className={`flex size-10 shrink-0 items-center justify-center rounded-full ${
          positive ? "bg-accent/15 text-accent" : "bg-secondary text-muted-foreground"
        }`}
      >
        <Icon className="size-[18px]" />
      </span>
      <div className="min-w-0 flex-1">
        <p className="truncate text-sm font-medium">{tx.title}</p>
        <p className="truncate text-xs text-muted-foreground">{timeAgo(tx.createdAt)}</p>
      </div>
      <div className="flex flex-col items-end gap-1">
        <span className={`text-sm font-semibold tabular-nums ${positive ? "text-accent" : "text-foreground"}`}>
          {positive ? "+" : "-"}
          {formatKRW(Math.abs(tx.amountKRW))}
        </span>
        <TxStatusBadge status={tx.status} />
      </div>
    </button>
  )
}

export { timeAgo }
