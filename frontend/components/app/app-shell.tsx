"use client"

import type React from "react"
import { useEffect } from "react"
import { useRouter } from "next/navigation"
import { AppSidebar } from "./app-sidebar"
import { AppTopbar } from "./app-topbar"
import { useStore } from "@/lib/store"

export function AppShell({ title, children }: { title: string; children: React.ReactNode }) {
  const router = useRouter()
  const { user, ready } = useStore()

  useEffect(() => {
    if (ready && !user) router.replace("/login")
  }, [ready, user, router])

  if (!ready || !user) {
    return (
      <div className="flex min-h-screen items-center justify-center text-sm text-muted-foreground">
        불러오는 중…
      </div>
    )
  }

  return (
    <div className="flex min-h-screen bg-secondary/20">
      <aside className="sticky top-0 hidden h-screen w-64 shrink-0 border-r border-border lg:block">
        <AppSidebar />
      </aside>
      <div className="flex min-w-0 flex-1 flex-col">
        <AppTopbar title={title} />
        <main className="flex-1 px-4 py-6 sm:px-6 lg:px-8">
          <div className="mx-auto w-full max-w-6xl">{children}</div>
        </main>
      </div>
    </div>
  )
}
