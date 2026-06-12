"use client"

import { useMemo, useState } from "react"
import { Bell, CalendarClock, Target, ArrowLeftRight, Send, AlertCircle, CheckCheck } from "lucide-react"
import { AppShell } from "@/components/app/app-shell"
import { Card } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { useStore, type NotifType } from "@/lib/store"
import { timeAgo } from "@/components/app/transaction-row"
import { cn } from "@/lib/utils"

const notifMeta: Record<NotifType, { icon: typeof Bell; cls: string }> = {
  reservation: { icon: CalendarClock, cls: "bg-primary/10 text-primary" },
  trigger: { icon: Target, cls: "bg-chart-3/15 text-chart-3" },
  exchange: { icon: ArrowLeftRight, cls: "bg-accent/15 text-accent" },
  remittance: { icon: Send, cls: "bg-accent/15 text-accent" },
  fail: { icon: AlertCircle, cls: "bg-destructive/10 text-destructive" },
}

export default function NotificationsPage() {
  const { notifications, markAllRead } = useStore()
  const [filter, setFilter] = useState<"all" | "unread">("all")

  const unread = notifications.filter((n) => !n.read).length
  const list = useMemo(
    () => (filter === "unread" ? notifications.filter((n) => !n.read) : notifications),
    [notifications, filter],
  )

  return (
    <AppShell title="알림">
      <div className="mx-auto max-w-2xl space-y-5">
        <Card className="flex flex-row items-center justify-between p-4">
          <div className="flex items-center gap-3">
            <span className="flex size-10 items-center justify-center rounded-full bg-primary/10 text-primary">
              <Bell className="size-5" />
            </span>
            <div>
              <p className="font-semibold">알림 센터</p>
              <p className="text-sm text-muted-foreground">
                읽지 않은 알림 {unread}건
              </p>
            </div>
          </div>
          <Button variant="outline" size="sm" onClick={markAllRead} disabled={unread === 0}>
            <CheckCheck className="size-4" /> 모두 읽음
          </Button>
        </Card>

        <Tabs value={filter} onValueChange={(v) => setFilter((v as "all" | "unread") ?? "all")}>
          <TabsList>
            <TabsTrigger value="all">전체</TabsTrigger>
            <TabsTrigger value="unread">읽지 않음{unread > 0 ? ` (${unread})` : ""}</TabsTrigger>
          </TabsList>
        </Tabs>

        {list.length === 0 ? (
          <Card className="p-16 text-center text-sm text-muted-foreground">
            {filter === "unread" ? "읽지 않은 알림이 없습니다." : "알림이 없습니다."}
          </Card>
        ) : (
          <div className="space-y-2.5">
            {list.map((n) => {
              const meta = notifMeta[n.type]
              const Icon = meta.icon
              return (
                <Card
                  key={n.id}
                  className={cn(
                    "flex flex-row items-start gap-3 p-4 transition-colors",
                    !n.read && "border-primary/30 bg-primary/[0.03]",
                  )}
                >
                  <span className={cn("flex size-10 shrink-0 items-center justify-center rounded-full", meta.cls)}>
                    <Icon className="size-[18px]" />
                  </span>
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2">
                      <p className="font-semibold">{n.title}</p>
                      {!n.read && <span className="size-2 shrink-0 rounded-full bg-primary" aria-label="읽지 않음" />}
                    </div>
                    <p className="mt-0.5 text-sm text-muted-foreground">{n.body}</p>
                    <p className="mt-1 text-xs text-muted-foreground">{timeAgo(n.createdAt)}</p>
                  </div>
                </Card>
              )
            })}
          </div>
        )}
      </div>
    </AppShell>
  )
}
