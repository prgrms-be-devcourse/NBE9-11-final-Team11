"use client"

import Link from "next/link"
import { usePathname } from "next/navigation"
import {
  LayoutDashboard,
  Wallet,
  ArrowLeftRight,
  Send,
  CalendarClock,
  Receipt,
  Settings,
  TrendingUp,
  ShieldCheck,
} from "lucide-react"
import { cn } from "@/lib/utils"

const nav = [
  { href: "/dashboard", label: "대시보드", icon: LayoutDashboard },
  { href: "/wallet", label: "지갑", icon: Wallet },
  { href: "/exchange", label: "환전", icon: ArrowLeftRight },
  { href: "/remittance", label: "해외송금", icon: Send },
  { href: "/reservations", label: "예약", icon: CalendarClock },
  { href: "/transactions", label: "거래내역", icon: Receipt },
  { href: "/settings", label: "설정", icon: Settings },
]

export function AppSidebar({ onNavigate }: { onNavigate?: () => void }) {
  const pathname = usePathname()

  return (
    <div className="flex h-full flex-col gap-1 bg-sidebar">
      <div className="flex h-16 items-center gap-2 px-5">
        <span className="flex size-8 items-center justify-center rounded-xl bg-primary text-primary-foreground">
          <TrendingUp className="size-4" />
        </span>
        <span className="text-lg font-bold tracking-tight text-sidebar-foreground">FXFlow</span>
      </div>
      <nav className="flex flex-1 flex-col gap-1 overflow-y-auto px-3 py-2">
        {nav.map((item) => {
          const active = pathname === item.href
          return (
            <Link
              key={item.href}
              href={item.href}
              onClick={onNavigate}
              className={cn(
                "flex items-center justify-between rounded-2xl px-3 py-2.5 text-sm font-medium transition-colors",
                active
                  ? "bg-sidebar-primary text-sidebar-primary-foreground"
                  : "text-sidebar-foreground/70 hover:bg-sidebar-accent hover:text-sidebar-accent-foreground",
              )}
            >
              <span className="flex items-center gap-3">
                <item.icon className="size-[18px]" />
                {item.label}
              </span>
            </Link>
          )
        })}
      </nav>
      <div className="px-3 pb-4">
        <Link
          href="/admin"
          onClick={onNavigate}
          className={cn(
            "flex items-center gap-3 rounded-2xl px-3 py-2.5 text-sm font-medium transition-colors",
            pathname.startsWith("/admin")
              ? "bg-sidebar-primary text-sidebar-primary-foreground"
              : "text-sidebar-foreground/70 hover:bg-sidebar-accent hover:text-sidebar-accent-foreground",
          )}
        >
          <ShieldCheck className="size-[18px]" />
          관리자
        </Link>
      </div>
    </div>
  )
}
