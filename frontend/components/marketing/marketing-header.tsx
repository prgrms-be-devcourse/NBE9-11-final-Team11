"use client"

import Link from "next/link"
import { ArrowRight, TrendingUp } from "lucide-react"
import { Button } from "@/components/ui/button"
import { useStore } from "@/lib/store"

export function MarketingHeader() {
  const { user, ready } = useStore()

  return (
    <header className="sticky top-0 z-40 w-full border-b border-border/60 bg-background/80 backdrop-blur-md">
      <div className="mx-auto flex h-16 max-w-6xl items-center justify-between px-4 sm:px-6">
        <Link href="/" className="flex items-center gap-2">
          <span className="flex size-8 items-center justify-center rounded-xl bg-primary text-primary-foreground">
            <TrendingUp className="size-4" />
          </span>
          <span className="text-lg font-bold tracking-tight">FXFlow</span>
        </Link>
        <nav className="hidden items-center gap-8 text-sm font-medium text-muted-foreground md:flex">
          <Link href="/rates" className="transition-colors hover:text-foreground">
            환율
          </Link>
          <a href="#features" className="transition-colors hover:text-foreground">
            기능
          </a>
          <a href="#how" className="transition-colors hover:text-foreground">
            이용방법
          </a>
          <a href="#savings" className="transition-colors hover:text-foreground">
            수수료 비교
          </a>
        </nav>
        <div className="flex items-center gap-2">
          {ready &&
            (user ? (
              <Button render={<Link href="/dashboard" />} size="sm">
                대시보드로 이동
                <ArrowRight className="size-4" />
              </Button>
            ) : (
              <>
                <Button render={<Link href="/login" />} variant="ghost" size="sm">
                  로그인
                </Button>
                <Button render={<Link href="/signup" />} size="sm">
                  시작하기
                  <ArrowRight className="size-4" />
                </Button>
              </>
            ))}
        </div>
      </div>
    </header>
  )
}
