"use client"

import { useEffect, useState } from "react"
import { useRouter } from "next/navigation"
import { User, Mail, Lock, LogOut, ShieldCheck, ShieldAlert, Trash2, AlertCircle } from "lucide-react"
import { toast } from "sonner"
import { AppShell } from "@/components/app/app-shell"
import { Card } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Separator } from "@/components/ui/separator"
import { Avatar, AvatarFallback } from "@/components/ui/avatar"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { useStore } from "@/lib/store"
import { apiRequest } from "@/lib/api"

export default function SettingsPage() {
  const router = useRouter()
  const { user, logout } = useStore()

  const [name, setName] = useState(user?.name ?? "")
  const [email, setEmail] = useState(user?.email ?? "")

  useEffect(() => {
    if (user) {
      setName(user.name)
      setEmail(user.email)
    }
  }, [user])

  const [pwOpen, setPwOpen] = useState(false)
  const [current, setCurrent] = useState("")
  const [next, setNext] = useState("")
  const [confirm, setConfirm] = useState("")

  const [deleteOpen, setDeleteOpen] = useState(false)
  const [deletePassword, setDeletePassword] = useState("")
  const [deleteError, setDeleteError] = useState("")
  const [deleting, setDeleting] = useState(false)

  function saveProfile() {
    if (!name.trim()) return toast.error("이름을 입력하세요.")
    toast.success("프로필이 저장되었습니다.")
  }

  function changePassword() {
    if (!current || !next) return toast.error("비밀번호를 입력하세요.")
    if (next.length < 8) return toast.error("새 비밀번호는 8자 이상이어야 합니다.")
    if (next !== confirm) return toast.error("새 비밀번호가 일치하지 않습니다.")
    setPwOpen(false)
    setCurrent("")
    setNext("")
    setConfirm("")
    toast.success("비밀번호가 변경되었습니다.")
  }

  async function handleLogout() {
    try {
      await apiRequest("POST", "/api/v1/auth/logout")
    } catch (err) {
      console.error("로그아웃 API 호출 실패", err)
    } finally {
      if (typeof window !== "undefined") {
        localStorage.removeItem("fxflow-userId")
      }
      logout()
      router.push("/login")
    }
  }

  function openDeleteDialog() {
    setDeletePassword("")
    setDeleteError("")
    setDeleteOpen(true)
  }

  async function handleDelete() {
    if (!deletePassword) {
      setDeleteError("비밀번호를 입력하세요.")
      return
    }

    setDeleting(true)
    setDeleteError("")
    try {
      await apiRequest("DELETE", "/api/v1/auth/me", { password: deletePassword })

      // 백엔드가 탈퇴 처리 시 JWT 쿠키를 이미 무효화하므로,
      // 프론트는 로컬 상태(store, localStorage)만 정리하면 된다.
      if (typeof window !== "undefined") {
        localStorage.removeItem("fxflow-userId")
      }
      logout()
      setDeleteOpen(false)
      toast.success("회원 탈퇴가 완료되었습니다.")
      router.push("/")
    } catch (err: any) {
      console.error(err)
      setDeleteError(err.message || "회원 탈퇴에 실패했습니다.")
    } finally {
      setDeleting(false)
    }
  }

  const initial = user?.name?.charAt(0) ?? "U"

  return (
    <AppShell title="설정">
      <div className="mx-auto max-w-2xl space-y-6">
        {/* Profile */}
        <Card className="p-5">
          <h2 className="text-base font-bold">프로필</h2>
          <div className="mt-4 flex items-center gap-4">
            <Avatar className="size-16">
              <AvatarFallback className="bg-primary/10 text-xl font-bold text-primary">{initial}</AvatarFallback>
            </Avatar>
            <div>
              <p className="text-lg font-semibold">{user?.name ?? "사용자"}</p>
              <span
                className={`mt-1 inline-flex items-center gap-1 rounded-full px-2.5 py-0.5 text-xs font-medium ${
                  user?.verified ? "bg-accent/15 text-accent" : "bg-chart-3/15 text-chart-3"
                }`}
              >
                {user?.verified ? <ShieldCheck className="size-3" /> : <ShieldAlert className="size-3" />}
                {user?.verified ? "KYC 인증 완료" : "KYC 미인증"}
              </span>
            </div>
          </div>

          <div className="mt-5 space-y-4">
            <div className="space-y-2">
              <Label htmlFor="name">이름</Label>
              <div className="relative">
                <User className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
                <Input id="name" value={name} onChange={(e) => setName(e.target.value)} className="pl-9" />
              </div>
            </div>
            <div className="space-y-2">
              <Label htmlFor="email">이메일</Label>
              <div className="relative">
                <Mail className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
                <Input id="email" type="email" value={email} onChange={(e) => setEmail(e.target.value)} className="pl-9" />
              </div>
            </div>
            <Button onClick={saveProfile}>변경사항 저장</Button>
          </div>
        </Card>

        {/* Security */}
        <Card className="p-5">
          <h2 className="text-base font-bold">보안</h2>
          <div className="mt-4 flex items-center justify-between rounded-2xl border border-border p-4">
            <div className="flex items-center gap-3">
              <span className="flex size-10 items-center justify-center rounded-full bg-secondary text-muted-foreground">
                <Lock className="size-[18px]" />
              </span>
              <div>
                <p className="font-medium">비밀번호</p>
                <p className="text-sm text-muted-foreground">정기적으로 비밀번호를 변경하세요.</p>
              </div>
            </div>
            <Button variant="outline" size="sm" onClick={() => setPwOpen(true)}>
              비밀번호 변경
            </Button>
          </div>
        </Card>

        {/* Account actions */}
        <Card className="p-5">
          <h2 className="text-base font-bold">계정</h2>
          <div className="mt-4 space-y-2">
            <Button variant="outline" className="w-full justify-start" onClick={handleLogout}>
              <LogOut className="size-4" /> 로그아웃
            </Button>
            <Button
              variant="outline"
              className="w-full justify-start border-destructive/30 text-destructive hover:bg-destructive/5 hover:text-destructive"
              onClick={openDeleteDialog}
            >
              <Trash2 className="size-4" /> 회원 탈퇴
            </Button>
          </div>
        </Card>

        <p className="text-center text-xs text-muted-foreground">
          본 서비스는 시뮬레이션 전용 데모입니다. 실제 자금이 이동하지 않습니다.
        </p>
      </div>

      {/* Change password dialog */}
      <Dialog open={pwOpen} onOpenChange={setPwOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>비밀번호 변경</DialogTitle>
            <DialogDescription>새 비밀번호는 8자 이상이어야 합니다.</DialogDescription>
          </DialogHeader>
          <div className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="current">현재 비밀번호</Label>
              <Input id="current" type="password" value={current} onChange={(e) => setCurrent(e.target.value)} />
            </div>
            <div className="space-y-2">
              <Label htmlFor="next">새 비밀번호</Label>
              <Input id="next" type="password" value={next} onChange={(e) => setNext(e.target.value)} />
            </div>
            <div className="space-y-2">
              <Label htmlFor="confirm-pw">새 비밀번호 확인</Label>
              <Input id="confirm-pw" type="password" value={confirm} onChange={(e) => setConfirm(e.target.value)} />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setPwOpen(false)}>
              취소
            </Button>
            <Button onClick={changePassword}>변경하기</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Delete account dialog */}
      <Dialog open={deleteOpen} onOpenChange={(o) => !deleting && setDeleteOpen(o)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>회원 탈퇴</DialogTitle>
            <DialogDescription>
              탈퇴 시 모든 데이터가 마스킹 처리되며 복구할 수 없습니다. 잔액이 남아있거나 진행 중인 거래가
              있으면 탈퇴할 수 없습니다. 계속하려면 비밀번호를 입력하세요.
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-3">
            <div className="space-y-2">
              <Label htmlFor="delete-password">비밀번호</Label>
              <Input
                id="delete-password"
                type="password"
                value={deletePassword}
                onChange={(e) => {
                  setDeletePassword(e.target.value)
                  setDeleteError("")
                }}
                placeholder="현재 비밀번호 입력"
                disabled={deleting}
              />
            </div>
            {deleteError && (
              <div className="flex items-center gap-2 rounded-xl bg-destructive/10 px-3 py-2 text-sm text-destructive">
                <AlertCircle className="size-4 shrink-0" />
                {deleteError}
              </div>
            )}
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDeleteOpen(false)} disabled={deleting}>
              취소
            </Button>
            <Button variant="destructive" onClick={handleDelete} disabled={deleting}>
              {deleting ? "처리 중..." : "탈퇴하기"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </AppShell>
  )
}
