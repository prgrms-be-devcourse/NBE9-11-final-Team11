"use client"

import { useState } from "react"
import { useRouter } from "next/navigation"
import Link from "next/link"
import { AlertCircle } from "lucide-react"
import { AuthShell } from "@/components/auth/auth-shell"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Card } from "@/components/ui/card"
import { useStore } from "@/lib/store"
import { apiRequest } from "@/lib/api"

export default function LoginPage() {
  const router = useRouter()
  const { login } = useStore()
  const [email, setEmail] = useState("demo@fxflow.app")
  const [password, setPassword] = useState("demo1234")
  const [error, setError] = useState("")

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    setError("")
    try {
      const data = await apiRequest<{ userId: number; name: string; email: string }>(
        "POST",
        "/api/v1/auth/login",
        { email, password }
      )
      login(data.email, data.name)
      if (typeof window !== "undefined") {
        localStorage.setItem("fxflow-userId", String(data.userId))
      }
      router.push("/dashboard")
    } catch (err: any) {
      console.error(err)
      setError(err.message || "이메일 또는 비밀번호가 올바르지 않습니다.")
    }
  }

  return (
    <AuthShell title="로그인" subtitle="FXFlow 계정으로 로그인하세요">
      <Card className="p-6">
        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
          {error && (
            <div className="flex items-center gap-2 rounded-2xl bg-destructive/10 px-4 py-3 text-sm text-destructive">
              <AlertCircle className="size-4 shrink-0" />
              {error}
            </div>
          )}
          <div className="flex flex-col gap-2">
            <Label htmlFor="email">이메일</Label>
            <Input
              id="email"
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="you@example.com"
              required
            />
          </div>
          <div className="flex flex-col gap-2">
            <div className="flex items-center justify-between">
              <Label htmlFor="password">비밀번호</Label>
              <Link href="#" className="text-xs font-medium text-primary hover:underline">
                비밀번호 찾기
              </Link>
            </div>
            <Input
              id="password"
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
            />
          </div>
          <Button type="submit" className="mt-2 w-full">
            로그인
          </Button>
          <Button render={<Link href="/signup" />} variant="outline" className="w-full">
            회원가입
          </Button>
          <p className="rounded-xl bg-secondary px-3 py-2 text-center text-xs text-muted-foreground">
            데모 계정: demo@fxflow.app / demo1234
          </p>
        </form>
      </Card>
    </AuthShell>
  )
}
