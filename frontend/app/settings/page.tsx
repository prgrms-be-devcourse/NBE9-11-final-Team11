"use client"

import { useEffect, useState } from "react"
import { useRouter } from "next/navigation"
import { User, Mail, Lock, LogOut, ShieldCheck, ShieldAlert, Trash2 } from "lucide-react"
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
  const [changingPassword, setChangingPassword] = useState(false)

  const [deleteOpen, setDeleteOpen] = useState(false)

  async function changePassword() {
    if (!current || !next) return toast.error("비밀번호를 입력하세요.")
    if (next.length < 8) return toast.error("새 비밀번호는 8자 이상이어야 합니다.")
    if (next !== confirm) return toast.error("새 비밀번호가 일치하지 않습니다.")

    setChangingPassword(true)
    try {
      await apiRequest("PATCH", "/api/v1/auth/me/password", {
        currentPassword: current,
        newPassword: next,
      })

      setPwOpen(false)
      setCurrent("")
      setNext("")
      setConfirm("")
      toast.success("비밀번호가 변경되었습니다. 다시 로그인해주세요.")

      // 비밀번호 변경 성공 시 서버 측 세션(쿠키)이 이미 무효화되므로
      // 클라이언트 상태도 함께 정리하고 로그인 화면으로 이동한다.
      logout()
      router.push("/login")
    } catch (err: any) {
      console.error(err)
      toast.error(err.message || "비밀번호 변경에 실패했습니다.")
    } finally {
      setChangingPassword(false)
    }
  }

  function handleLogout() {
    logout()
    router.push("/login")
  }

  function handleDelete() {
    logout()
    toast.success("회원 탈퇴가 완료되었습니다.")
    router.push("/")
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
                <Input id="name" value={name} disabled className="pl-9" />
              </div>
            </div>
            <div className="space-y-2">
              <Label htmlFor="email">이메일</Label>
              <div className="relative">
                <Mail className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
                <Input id="email" type="email" value={email} disabled className="pl-9" />
              </div>
            </div>
            <p className="text-xs text-muted-foreground">
              이름과 이메일은 본인확인(KYC) 정보와 연결되어 있어 직접 변경할 수 없습니다.
            </p>
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
              onClick={() => setDeleteOpen(true)}
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
            <DialogDescription>
              새 비밀번호는 8자 이상, 대소문자·숫자·특수문자를 포함해야 합니다. 변경 후 다시 로그인해야 합니다.
            </DialogDescription>
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
            <Button variant="outline" onClick={() => setPwOpen(false)} disabled={changingPassword}>
              취소
            </Button>
            <Button onClick={changePassword} disabled={changingPassword}>
              {changingPassword ? "변경 중..." : "변경하기"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Delete account dialog */}
      <Dialog open={deleteOpen} onOpenChange={setDeleteOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>회원 탈퇴</DialogTitle>
            <DialogDescription>
              탈퇴 시 모든 시뮬레이션 데이터가 삭제되며 복구할 수 없습니다. 계속하시겠습니까?
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDeleteOpen(false)}>
              취소
            </Button>
            <Button variant="destructive" onClick={handleDelete}>
              탈퇴하기
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </AppShell>
  )
}
