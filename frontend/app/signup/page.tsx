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
import { apiRequest } from "@/lib/api"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { KOREAN_BANKS } from "@/lib/fx-data"

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
  const [error, setError] = useState("")
  const [bankName, setBankName] = useState("국민은행")
  const [accountNumber, setAccountNumber] = useState("123456789012")

  const takenEmails = ["test@fxflow.app", "user@fxflow.app"]
  // Align validation with backend requirements (at least 8 chars, uppercase, lowercase, number, special char)
  const pwValid = password.length >= 8 && /[A-Z]/.test(password) && /[a-z]/.test(password) && /[0-9]/.test(password) && /[!@#$%^&*]/.test(password)

  function validate() {
    const e: Record<string, string> = {}
    if (!name.trim()) e.name = "이름을 입력해주세요."
    if (!/^[^@]+@[^@]+\.[^@]+$/.test(email)) e.email = "올바른 이메일 형식이 아닙니다."
    if (!pwValid) e.password = "8자 이상, 영문 대/소문자, 숫자, 특수문자를 모두 포함해야 합니다."
    if (password !== confirm) e.confirm = "비밀번호가 일치하지 않습니다."
    setErrors(e)
    return Object.keys(e).length === 0
  }

  async function handleFormSubmit(ev: React.FormEvent) {
    ev.preventDefault()
    setError("")
    if (validate()) {
      try {
        const data = await apiRequest<{ userId: number; email: string; name: string }>(
          "POST",
          "/api/v1/auth/signup",
          { name, email, password }
        )
        if (typeof window !== "undefined") {
          localStorage.setItem("fxflow-userId", String(data.userId))
        }
        setStep("kyc")
      } catch (err: any) {
        console.error(err)
        setError(err.message || "회원가입에 실패했습니다.")
      }
    }
  }

  // --- KYC step ---
  const [kyc, setKyc] = useState<KycState>("idle")
  const [code, setCode] = useState("")
  const correctCode = "1234"

  function startKyc() {
    setKyc("waiting")
  }

  async function verifyKyc(ev: React.FormEvent) {
    ev.preventDefault()
    setError("")
    
    const cleanAccount = accountNumber.replace(/[^\d]/g, "")
    if (cleanAccount.length !== 12) {
      setError("계좌번호는 정확히 12자리 숫자여야 합니다.")
      return
    }

    if (code === correctCode) {
      setKyc("waiting")
      try {
        const loginData = await apiRequest<{ userId: number; name: string; email: string }>(
          "POST",
          "/api/v1/auth/login",
          { email, password }
        )
        if (typeof window !== "undefined") {
          localStorage.setItem("fxflow-userId", String(loginData.userId))
        }
        login(loginData.email, loginData.name)
        
        try {
          await apiRequest("POST", "/api/v1/mockbank/link", {
            bankName: bankName,
            accountNumber: cleanAccount
          })
        } catch (linkErr) {
          console.warn("Failed to link mock bank account during signup:", linkErr)
        }

        setKyc("verified")
        setVerified()
        toast.success("인증이 완료되었습니다. 지갑이 생성되었어요.")
        setTimeout(() => router.push("/dashboard"), 1200)
      } catch (err: any) {
        console.error(err)
        setKyc("failed")
        setError(err.message || "인증 및 로그인 처리에 실패했습니다.")
      }
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
            {error && (
              <div className="mt-2 flex items-center gap-2 rounded-xl bg-destructive/10 px-3 py-2 text-xs text-destructive">
                <AlertCircle className="size-3.5 shrink-0" />
                {error}
              </div>
            )}
            <div className="mt-4 flex flex-col gap-3 text-sm">
              <div className="flex flex-col gap-1.5">
                <Label htmlFor="kycBank" className="text-muted-foreground text-xs">은행</Label>
                {kyc === "idle" ? (
                  <Select value={bankName} onValueChange={(v) => setBankName(v ?? KOREAN_BANKS[0])}>
                    <SelectTrigger id="kycBank" className="h-9">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      {KOREAN_BANKS.map((b) => (
                        <SelectItem key={b} value={b}>
                          {b}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                ) : (
                  <span className="font-medium">{bankName}</span>
                )}
              </div>
              <div className="flex flex-col gap-1.5">
                <Label htmlFor="kycAccount" className="text-muted-foreground text-xs">계좌번호</Label>
                {kyc === "idle" ? (
                  <Input
                    id="kycAccount"
                    placeholder="숫자 12자리"
                    value={accountNumber}
                    onChange={(e) => setAccountNumber(e.target.value.replace(/[^\d]/g, ""))}
                    maxLength={12}
                    className="h-9"
                  />
                ) : (
                  <span className="font-mono font-medium">
                    {accountNumber.replace(/(\d{3})(\d{3})(\d{6})/, "$1-$2-$3")}
                  </span>
                )}
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
          {error && (
            <div className="flex items-center gap-2 rounded-2xl bg-destructive/10 px-4 py-3 text-sm text-destructive">
              <AlertCircle className="size-4 shrink-0" />
              {error}
            </div>
          )}
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
              <li className={`flex items-center gap-1.5 ${/[A-Z]/.test(password) ? "text-accent" : ""}`}>
                <Check className="size-3" /> 대문자 포함
              </li>
              <li className={`flex items-center gap-1.5 ${/[a-z]/.test(password) ? "text-accent" : ""}`}>
                <Check className="size-3" /> 소문자 포함
              </li>
              <li className={`flex items-center gap-1.5 ${/[0-9]/.test(password) ? "text-accent" : ""}`}>
                <Check className="size-3" /> 숫자 포함
              </li>
              <li className={`flex items-center gap-1.5 ${/[!@#$%^&*]/.test(password) ? "text-accent" : ""}`}>
                <Check className="size-3" /> 특수문자 (!@#$%^&*) 포함
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
