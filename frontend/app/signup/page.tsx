"use client"

import { useState } from "react"
import { useRouter } from "next/navigation"
import Link from "next/link"
import { AlertCircle, Check, Loader2, CircleCheck } from "lucide-react"
import { AuthShell } from "@/components/auth/auth-shell"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Card } from "@/components/ui/card"
import { useStore } from "@/lib/store"
import { toast } from "sonner"

type KycState = "idle" | "waiting" | "verified" | "failed"

export default function SignupPage() {
  const router = useRouter()
  const { login, setVerified } = useStore()
  const [step, setStep] = useState<"form" | "kyc">("form")

  const [name, setName] = useState("")
  const [email, setEmail] = useState("")
  const [password, setPassword] = useState("")
  const [confirm, setConfirm] = useState("")
  const [errors, setErrors] = useState<Record<string, string>>({})

  const takenEmails = ["test@fxflow.app", "user@fxflow.app"]
  const pwValid = password.length >= 8 && /[A-Za-z]/.test(password) && /[0-9]/.test(password)

  function validate() {
    const e: Record<string, string> = {}
    if (!name.trim()) e.name = "이름을 입력해주세요."
    if (!/^[^@]+@[^@]+\.[^@]+$/.test(email)) e.email = "올바른 이메일 형식이 아닙니다."
    else if (takenEmails.includes(email)) e.email = "이미 사용 중인 이메일입니다."
    if (!pwValid) e.password = "8자 이상, 영문과 숫자를 포함해야 합니다."
    if (password !== confirm) e.confirm = "비밀번호가 일치하지 않습니다."
    setErrors(e)
    return Object.keys(e).length === 0
  }

  function handleFormSubmit(ev: React.FormEvent) {
    ev.preventDefault()
    if (validate()) {
      login(email, name)
      setStep("kyc")
    }
  }

  // --- KYC step ---
  const [kyc, setKyc] = useState<KycState>("idle")
  const [code, setCode] = useState("")
  const correctCode = "1234"

  function startKyc() {
    setKyc("waiting")
  }

  function verifyKyc(ev: React.FormEvent) {
    ev.preventDefault()
    if (code === correctCode) {
      setKyc("verified")
      setVerified()
      toast.success("인증이 완료되었습니다. 지갑이 생성되었어요.")
      setTimeout(() => router.push("/dashboard"), 1200)
    } else {
      setKyc("failed")
    }
  }

  if (step === "kyc") {
    return (
      <AuthShell title="KYC 본인 인증" subtitle="가상계좌 1원 인증으로 본인을 확인합니다">
        <Card className="gap-5 p-6">
          <div className="rounded-2xl border border-border bg-secondary/50 p-4">
            <p className="text-sm font-semibold">가상계좌 1원 인증</p>
            <p className="mt-1 text-xs text-muted-foreground">
              아래 계좌로 1원을 입금했습니다. 입금자명에 표시된 4자리 인증코드를 입력하세요.
            </p>
            <div className="mt-4 flex flex-col gap-2 text-sm">
              <div className="flex items-center justify-between">
                <span className="text-muted-foreground">은행</span>
                <span className="font-medium">국민은행</span>
              </div>
              <div className="flex items-center justify-between">
                <span className="text-muted-foreground">계좌번호</span>
                <span className="font-mono font-medium">123-456-789012</span>
              </div>
            </div>
          </div>

          {kyc === "idle" && (
            <Button onClick={startKyc} className="w-full">
              1원 입금 요청
            </Button>
          )}

          {(kyc === "waiting" || kyc === "failed") && (
            <form onSubmit={verifyKyc} className="flex flex-col gap-3">
              <div className="flex items-center gap-2 rounded-xl bg-primary/10 px-3 py-2 text-xs font-medium text-primary">
                <Loader2 className="size-3.5 animate-spin" />
                인증 대기 중 · 입금자명 코드를 확인하세요 (데모 코드: 1234)
              </div>
              <div className="flex flex-col gap-2">
                <Label htmlFor="code">인증코드</Label>
                <Input
                  id="code"
                  inputMode="numeric"
                  maxLength={4}
                  value={code}
                  onChange={(e) => setCode(e.target.value.replace(/\D/g, ""))}
                  placeholder="4자리 숫자"
                  className="text-center text-lg tracking-[0.5em]"
                />
              </div>
              {kyc === "failed" && (
                <div className="flex items-center gap-2 rounded-xl bg-destructive/10 px-3 py-2 text-sm text-destructive">
                  <AlertCircle className="size-4" />
                  인증에 실패했습니다. 코드를 다시 확인해주세요.
                </div>
              )}
              <Button type="submit" className="w-full">
                인증 확인
              </Button>
            </form>
          )}

          {kyc === "verified" && (
            <div className="flex flex-col items-center gap-3 py-4 text-center">
              <span className="flex size-14 items-center justify-center rounded-full bg-accent/15 text-accent">
                <CircleCheck className="size-8" />
              </span>
              <p className="font-semibold">인증 완료</p>
              <p className="text-sm text-muted-foreground">KRW 지갑이 생성되었습니다. 대시보드로 이동합니다…</p>
            </div>
          )}
        </Card>
      </AuthShell>
    )
  }

  return (
    <AuthShell title="회원가입" subtitle="몇 가지 정보로 빠르게 시작하세요">
      <Card className="p-6">
        <form onSubmit={handleFormSubmit} className="flex flex-col gap-4">
          <Field id="name" label="이름" error={errors.name}>
            <Input id="name" value={name} onChange={(e) => setName(e.target.value)} placeholder="홍길동" />
          </Field>
          <Field id="email" label="이메일" error={errors.email}>
            <Input
              id="email"
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="you@example.com"
            />
          </Field>
          <Field id="password" label="비밀번호" error={errors.password}>
            <Input id="password" type="password" value={password} onChange={(e) => setPassword(e.target.value)} />
            <ul className="mt-1 flex flex-col gap-1 text-xs text-muted-foreground">
              <li className={`flex items-center gap-1.5 ${password.length >= 8 ? "text-accent" : ""}`}>
                <Check className="size-3" /> 8자 이상
              </li>
              <li
                className={`flex items-center gap-1.5 ${
                  /[A-Za-z]/.test(password) && /[0-9]/.test(password) ? "text-accent" : ""
                }`}
              >
                <Check className="size-3" /> 영문 + 숫자 포함
              </li>
            </ul>
          </Field>
          <Field id="confirm" label="비밀번호 확인" error={errors.confirm}>
            <Input id="confirm" type="password" value={confirm} onChange={(e) => setConfirm(e.target.value)} />
          </Field>
          <Button type="submit" className="mt-2 w-full">
            다음 · KYC 인증
          </Button>
          <p className="text-center text-sm text-muted-foreground">
            이미 계정이 있으신가요?{" "}
            <Link href="/login" className="font-medium text-primary hover:underline">
              로그인
            </Link>
          </p>
        </form>
      </Card>
    </AuthShell>
  )
}

function Field({
  id,
  label,
  error,
  children,
}: {
  id: string
  label: string
  error?: string
  children: React.ReactNode
}) {
  return (
    <div className="flex flex-col gap-2">
      <Label htmlFor={id}>{label}</Label>
      {children}
      {error && (
        <p className="flex items-center gap-1.5 text-xs text-destructive">
          <AlertCircle className="size-3.5" />
          {error}
        </p>
      )}
    </div>
  )
}
