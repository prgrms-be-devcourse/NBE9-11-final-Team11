"use client"

import { useState } from "react"
import { Menu, Bell, LogOut, User, Settings } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Sheet, SheetContent, SheetTrigger, SheetTitle } from "@/components/ui/sheet"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import { Avatar, AvatarFallback } from "@/components/ui/avatar"
import { AppSidebar } from "./app-sidebar"
import { useStore } from "@/lib/store"
import { useLogout } from "@/hooks/use-logout"
import Link from "next/link"

export function AppTopbar({ title }: { title: string }) {
  const { user, notifications } = useStore()
  const handleLogout = useLogout()
  const [open, setOpen] = useState(false)
  const unread = notifications.filter((n) => !n.read).length
  const initial = user?.name?.charAt(0) ?? "U"

  return (
    <header className="sticky top-0 z-30 flex h-16 items-center justify-between border-b border-border bg-background/80 px-4 backdrop-blur-md sm:px-6">
      <div className="flex items-center gap-3">
        <Sheet open={open} onOpenChange={setOpen}>
          <SheetTrigger
            render={
              <Button variant="ghost" size="icon" className="lg:hidden">
                <Menu className="size-5" />
                <span className="sr-only">메뉴 열기</span>
              </Button>
            }
          />
          <SheetContent side="left" className="data-[side=left]:w-56 p-0">
            <SheetTitle className="sr-only">내비게이션</SheetTitle>
            <AppSidebar onNavigate={() => setOpen(false)} />
          </SheetContent>
        </Sheet>
        <h1 className="text-lg font-bold tracking-tight">{title}</h1>
      </div>

      <div className="flex items-center gap-1">
        <Button render={<Link href="/notifications" />} variant="ghost" size="icon" className="relative">
          <Bell className="size-5" />
          {unread > 0 && (
            <span className="absolute right-1.5 top-1.5 flex size-2 rounded-full bg-primary" />
          )}
          <span className="sr-only">알림</span>
        </Button>
        <DropdownMenu>
          <DropdownMenuTrigger
            render={
              <Button variant="ghost" className="gap-2 px-2">
                <Avatar className="size-8">
                  <AvatarFallback className="bg-primary/10 text-sm font-semibold text-primary">
                    {initial}
                  </AvatarFallback>
                </Avatar>
                <span className="hidden text-sm font-medium sm:inline">{user?.name ?? "사용자"}</span>
              </Button>
            }
          />
          <DropdownMenuContent align="end" className="w-56">
            <DropdownMenuGroup>
              <DropdownMenuLabel className="flex flex-col">
                <span>{user?.name ?? "사용자"}</span>
                <span className="text-xs font-normal text-muted-foreground">{user?.email ?? "—"}</span>
              </DropdownMenuLabel>
            </DropdownMenuGroup>
            <DropdownMenuSeparator />
            <DropdownMenuItem
              render={
                <Link href="/settings">
                  <Settings className="size-4" />
                  설정
                </Link>
              }
            />
            <DropdownMenuSeparator />
            <DropdownMenuItem onClick={handleLogout} className="text-destructive focus:text-destructive">
              <LogOut className="size-4" />
              로그아웃
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </div>
    </header>
  )
}
