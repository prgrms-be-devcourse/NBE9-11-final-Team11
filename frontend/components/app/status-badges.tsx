import { Badge } from "@/components/ui/badge"
import { cn } from "@/lib/utils"
import type { TxStatus, ReservationStatus } from "@/lib/store"

const txStatusMap: Record<TxStatus, { label: string; cls: string }> = {
  completed: { label: "완료", cls: "bg-accent/15 text-accent border-transparent" },
  processing: { label: "처리중", cls: "bg-primary/10 text-primary border-transparent" },
  failed: { label: "실패", cls: "bg-destructive/10 text-destructive border-transparent" },
  refunded: { label: "환불됨", cls: "bg-muted text-muted-foreground border-transparent" },
  canceled: { label: "취소", cls: "bg-muted text-muted-foreground border-transparent" },
}

export function TxStatusBadge({ status }: { status: TxStatus }) {
  const s = txStatusMap[status]
  return <Badge className={cn("rounded-full font-medium", s.cls)}>{s.label}</Badge>
}

const resStatusMap: Record<ReservationStatus, { label: string; cls: string }> = {
  ACTIVE: { label: "대기중", cls: "bg-primary/10 text-primary border-transparent" },
  TRIGGERED: { label: "체결중", cls: "bg-chart-3/15 text-chart-3 border-transparent" },
  COMPLETED: { label: "완료", cls: "bg-accent/15 text-accent border-transparent" },
  CANCELED: { label: "취소됨", cls: "bg-muted text-muted-foreground border-transparent" },
  FAILED: { label: "실패", cls: "bg-destructive/10 text-destructive border-transparent" },
  EXPIRED: { label: "만료", cls: "bg-muted text-muted-foreground border-transparent" },
}

export function ReservationStatusBadge({ status }: { status: ReservationStatus }) {
  const s = resStatusMap[status]
  return <Badge className={cn("rounded-full font-medium", s.cls)}>{s.label}</Badge>
}
