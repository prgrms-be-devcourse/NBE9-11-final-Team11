"use client"

import { Clock } from "lucide-react"
import { cn } from "@/lib/utils"

const KYC_CODE_TTL_SECONDS = 5 * 60

function formatRemaining(seconds: number) {
  const m = Math.floor(seconds / 60)
  const s = seconds % 60
  return `${m}:${String(s).padStart(2, "0")}`
}

/**
 * 1원 인증 코드의 남은 유효 시간을 보여주는 카운트다운 배지.
 * 남은 시간이 30초 이하면 위험색으로 강조한다.
 */
export function KycTimer({ remainingSeconds }: { remainingSeconds: number }) {
  const urgent = remainingSeconds <= 30
  const progress = Math.max(0, Math.min(100, (remainingSeconds / KYC_CODE_TTL_SECONDS) * 100))

  return (
    <div
      className={cn(
        "flex items-center gap-2 rounded-full border px-3 py-1.5 text-sm font-semibold tabular-nums transition-colors",
        urgent
          ? "border-destructive/30 bg-destructive/10 text-destructive"
          : "border-primary/30 bg-primary/10 text-primary"
      )}
    >
      <Clock className={cn("size-4 shrink-0", urgent && "animate-pulse")} />
      <span className="tracking-wide">{formatRemaining(remainingSeconds)}</span>
      <div className="h-1.5 w-16 overflow-hidden rounded-full bg-current/15">
        <div
          className={cn("h-full rounded-full transition-all", urgent ? "bg-destructive" : "bg-primary")}
          style={{ width: `${progress}%` }}
        />
      </div>
    </div>
  )
}
