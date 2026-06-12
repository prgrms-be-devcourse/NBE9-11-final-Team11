"use client"

import type React from "react"
import Link from "next/link"
import { useRouter } from "next/navigation"
import { TrendingUp, Droplets, Receipt, SlidersHorizontal, ArrowLeft } from "lucide-react"
import { Button } from "@/components/ui/button"

const adminNav = [
  { href: "/admin", label: "풀 현황", icon: Droplets },
  { href: "/admin/transactions", label: "거래내역", icon: Receipt },
  { href: "/admin/policy", label: "정책관리", icon: SlidersHorizontal },
]

export function AdminShell({
  title,
  active,
  children,
}: {
  title: string
  active: string
  children: React.ReactNode
}) {
  const router = useRouter()

  return (
    <div className="flex min-h-screen bg-secondary/20">
      <aside className="sticky top-0 hidden h-screen w-64 shrink-0 flex-col border-r border-border bg-sidebar lg:flex">
        <div className="flex h-16 items-center gap-2 px-5">
          <span className="flex size-8 items-center justify-center rounded-xl bg-foreground text-background">
            <TrendingUp className="size-4" />
          </span>
          <div className="flex flex-col leading-tight">
            <span className="text-sm font-bold tracking-tight text-sidebar-foreground">FXFlow</span>
            <span className="text-[11px] font-medium text-muted-foreground">관리자 콘솔</span>
          </div>
        </div>
        <nav className="flex flex-1 flex-col gap-1 px-3 py-2">
          {adminNav.map((item) => {
            const isActive = item.href === active
            return (
              <Link
                key={item.href}
                href={item.href}
                className={
                  "flex items-center gap-3 rounded-2xl px-3 py-2.5 text-sm font-medium transition-colors " +
                  (isActive
                    ? "bg-sidebar-primary text-sidebar-primary-foreground"
                    : "text-sidebar-foreground/70 hover:bg-sidebar-accent hover:text-sidebar-accent-foreground")
                }
              >
                <item.icon className="size-[18px]" />
                {item.label}
              </Link>
            )
          })}
        </nav>
        <div className="px-3 pb-4">
          <button
            onClick={() => router.push("/dashboard")}
            className="flex w-full items-center gap-3 rounded-2xl px-3 py-2.5 text-sm font-medium text-sidebar-foreground/70 transition-colors hover:bg-sidebar-accent hover:text-sidebar-accent-foreground"
          >
            <ArrowLeft className="size-[18px]" />
            사용자 화면
          </button>
        </div>
      </aside>

      <div className="flex min-w-0 flex-1 flex-col">
        <header className="sticky top-0 z-30 flex h-16 items-center justify-between border-b border-border bg-background/80 px-4 backdrop-blur-md sm:px-6">
          <h1 className="text-lg font-bold tracking-tight">{title}</h1>
          <Button render={<Link href="/dashboard" />} variant="outline" size="sm">
            사용자 대시보드
          </Button>
        </header>
        <main className="flex-1 px-4 py-6 sm:px-6 lg:px-8">
          <div className="mx-auto w-full max-w-6xl">{children}</div>
        </main>
      </div>
    </div>
  )
}
