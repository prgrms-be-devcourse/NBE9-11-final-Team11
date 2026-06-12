"use client"

import { useMemo, useState } from "react"
import { useRouter } from "next/navigation"
import { Check, ChevronRight, UserPlus } from "lucide-react"
import { toast } from "sonner"
import { AppShell } from "@/components/app/app-shell"
import { Card } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Separator } from "@/components/ui/separator"
import { useStore, type Recipient } from "@/lib/store"
import {
  COUNTRIES,
  CURRENCY_META,
  RATES,
  REMITTANCE_REASONS,
  remittanceFee,
  bankFee,
  formatKRW,
  formatCurrency,
  krwPerUnit,
  type CurrencyCode,
} from "@/lib/fx-data"
import { cn } from "@/lib/utils"

const STEPS = ["수취인", "금액", "확인"]

export default function RemittancePage() {
  const router = useRouter()
  const { krwBalance, recipients, addRecipient, remit } = useStore()
  const [step, setStep] = useState(0)

  const [recipientId, setRecipientId] = useState(recipients[0]?.id ?? "")
  const [adding, setAdding] = useState(recipients.length === 0)
  const [form, setForm] = useState({ name: "", country: COUNTRIES[0].name, bank: "", account: "" })

  const [krwInput, setKrwInput] = useState("1000000")
  const [reason, setReason] = useState(REMITTANCE_REASONS[0])

  const recipient = recipients.find((r) => r.id === recipientId)
  const country = COUNTRIES.find((c) => c.name === (recipient?.country ?? form.country)) ?? COUNTRIES[0]
  const currency: CurrencyCode = recipient?.currency ?? country.currency
  const rate = currency === "KRW" ? { rate: 1, unit: 1 } : RATES[currency as Exclude<CurrencyCode, "KRW">]

  const krwAmount = Number(krwInput.replace(/[^\d]/g, "")) || 0
  const { fee, received, bankCost, savings } = useMemo(() => {
    const fee = remittanceFee(krwAmount)
    const received = krwAmount / krwPerUnit(currency)
    const bankCost = bankFee(krwAmount) + Math.round(krwAmount * 0.0175)
    const savings = Math.max(0, bankCost - fee)
    return { fee, received, bankCost, savings }
  }, [krwAmount, currency])

  const total = krwAmount + fee

  function saveRecipient() {
    if (!form.name.trim() || !form.bank.trim() || !form.account.trim())
      return toast.error("수취인 정보를 모두 입력하세요.")
    const c = COUNTRIES.find((x) => x.name === form.country) ?? COUNTRIES[0]
    const rec = addRecipient({
      name: form.name,
      country: form.country,
      currency: c.currency,
      bank: form.bank,
      account: form.account,
    })
    setRecipientId(rec.id)
    setAdding(false)
    toast.success("수취인이 추가되었습니다.")
  }

  function next() {
    if (step === 0 && !recipient) return toast.error("수취인을 선택하거나 추가하세요.")
    if (step === 1) {
      if (krwAmount <= 0) return toast.error("송금 금액을 입력하세요.")
      if (total > krwBalance) return toast.error("KRW 잔액이 부족합니다.")
    }
    setStep((s) => Math.min(2, s + 1))
  }

  function submit() {
    if (!recipient) return
    const id = remit(recipient, krwAmount, received, rate.rate, fee, reason)
    toast.success("송금이 신청되었습니다.")
    router.push(`/remittance/${id}`)
  }

  return (
    <AppShell title="해외송금">
      <div className="mx-auto max-w-2xl">
        {/* Stepper */}
        <div className="mb-6 flex items-center justify-center gap-2">
          {STEPS.map((label, i) => (
            <div key={label} className="flex items-center gap-2">
              <div className="flex items-center gap-2">
                <span
                  className={cn(
                    "flex size-7 items-center justify-center rounded-full text-xs font-semibold transition-colors",
                    i < step
                      ? "bg-accent text-accent-foreground"
                      : i === step
                        ? "bg-primary text-primary-foreground"
                        : "bg-muted text-muted-foreground",
                  )}
                >
                  {i < step ? <Check className="size-3.5" /> : i + 1}
                </span>
                <span className={cn("text-sm font-medium", i === step ? "text-foreground" : "text-muted-foreground")}>
                  {label}
                </span>
              </div>
              {i < STEPS.length - 1 && <ChevronRight className="size-4 text-muted-foreground" />}
            </div>
          ))}
        </div>

        {/* Step 1: recipient */}
        {step === 0 && (
          <Card className="p-5">
            <h2 className="text-base font-bold">수취인 선택</h2>
            {!adding && recipients.length > 0 && (
              <div className="mt-4 space-y-2">
                {recipients.map((r) => (
                  <button
                    key={r.id}
                    onClick={() => setRecipientId(r.id)}
                    className={cn(
                      "flex w-full items-center justify-between rounded-2xl border p-4 text-left transition-all",
                      recipientId === r.id ? "border-primary ring-2 ring-primary/20" : "border-border hover:border-primary/40",
                    )}
                  >
                    <div>
                      <p className="font-semibold">{r.name}</p>
                      <p className="text-sm text-muted-foreground">
                        {r.country} · {r.bank} · {r.account}
                      </p>
                    </div>
                    <span className="text-lg" aria-hidden>
                      {CURRENCY_META[r.currency].flag}
                    </span>
                  </button>
                ))}
                <Button variant="outline" className="w-full" onClick={() => setAdding(true)}>
                  <UserPlus className="size-4" /> 새 수취인 추가
                </Button>
              </div>
            )}

            {adding && (
              <div className="mt-4 space-y-4">
                <div className="space-y-2">
                  <Label htmlFor="r-name">수취인 이름</Label>
                  <Input
                    id="r-name"
                    placeholder="John Smith"
                    value={form.name}
                    onChange={(e) => setForm({ ...form, name: e.target.value })}
                  />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="r-country">국가</Label>
                  <Select value={form.country} onValueChange={(v) => setForm({ ...form, country: v ?? COUNTRIES[0].name })}>
                    <SelectTrigger id="r-country">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      {COUNTRIES.map((c) => (
                        <SelectItem key={c.code} value={c.name}>
                          {c.flag} {c.name} ({c.currency})
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
                <div className="space-y-2">
                  <Label htmlFor="r-bank">은행명</Label>
                  <Input
                    id="r-bank"
                    placeholder="Chase Bank"
                    value={form.bank}
                    onChange={(e) => setForm({ ...form, bank: e.target.value })}
                  />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="r-account">계좌번호 / IBAN</Label>
                  <Input
                    id="r-account"
                    placeholder="****6789"
                    value={form.account}
                    onChange={(e) => setForm({ ...form, account: e.target.value })}
                  />
                </div>
                <div className="flex gap-2">
                  <Button className="flex-1" onClick={saveRecipient}>
                    저장
                  </Button>
                  {recipients.length > 0 && (
                    <Button variant="outline" onClick={() => setAdding(false)}>
                      취소
                    </Button>
                  )}
                </div>
              </div>
            )}
          </Card>
        )}

        {/* Step 2: amount */}
        {step === 1 && (
          <Card className="p-5">
            <h2 className="text-base font-bold">송금 금액</h2>
            <p className="mt-1 text-sm text-muted-foreground">
              {recipient?.name} · {recipient?.country} · {currency}
            </p>
            <div className="mt-4 space-y-4">
              <div className="rounded-2xl border border-border bg-secondary/40 p-4">
                <div className="flex items-center justify-between">
                  <Label className="text-xs text-muted-foreground">보내는 금액 (KRW)</Label>
                  <span className="text-xs text-muted-foreground tabular-nums">잔액 {formatKRW(krwBalance)}</span>
                </div>
                <Input
                  inputMode="numeric"
                  value={krwInput ? Number(krwInput).toLocaleString("ko-KR") : ""}
                  onChange={(e) => setKrwInput(e.target.value.replace(/[^\d]/g, ""))}
                  className="mt-2 border-0 bg-transparent text-right text-2xl font-bold tabular-nums shadow-none focus-visible:ring-0"
                  placeholder="0"
                />
                <p className="mt-1 text-right text-sm text-muted-foreground">
                  수취 예상 {formatCurrency(received, currency)}
                </p>
              </div>
              <div className="space-y-2">
                <Label htmlFor="reason">송금 목적</Label>
                <Select value={reason} onValueChange={(v) => setReason(v ?? REMITTANCE_REASONS[0])}>
                  <SelectTrigger id="reason">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {REMITTANCE_REASONS.map((r) => (
                      <SelectItem key={r} value={r}>
                        {r}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <div className="rounded-2xl bg-accent/10 p-4">
                <div className="flex items-center justify-between text-sm">
                  <span className="text-muted-foreground">은행 대비 절약</span>
                  <span className="font-bold text-accent tabular-nums">{formatKRW(savings)}</span>
                </div>
              </div>
            </div>
          </Card>
        )}

        {/* Step 3: confirm */}
        {step === 2 && recipient && (
          <Card className="p-5">
            <h2 className="text-base font-bold">송금 확인</h2>
            <dl className="mt-4 space-y-2.5 text-sm">
              <Row label="수취인" value={recipient.name} />
              <Row label="국가 / 통화" value={`${recipient.country} · ${currency}`} />
              <Row label="은행 / 계좌" value={`${recipient.bank} · ${recipient.account}`} />
              <Row label="송금 목적" value={reason} />
              <Separator />
              <Row label="송금 금액" value={formatKRW(krwAmount)} />
              <Row label="적용 환율" value={`₩${rate.rate.toLocaleString("ko-KR")} / ${rate.unit > 1 ? rate.unit + " " : ""}${currency}`} />
              <Row label="수수료" value={formatKRW(fee)} />
              <Separator />
              <Row label="총 출금액" value={formatKRW(total)} bold />
              <Row label="수취 금액" value={formatCurrency(received, currency)} bold accent />
            </dl>
            <p className="mt-4 rounded-xl bg-secondary px-3 py-2 text-xs text-muted-foreground">
              실제 송금이 아닌 시뮬레이션입니다. 신청 후 약 1~2일 내 처리되는 과정을 추적할 수 있습니다.
            </p>
          </Card>
        )}

        {/* Nav buttons */}
        <div className="mt-5 flex gap-2">
          {step > 0 && (
            <Button variant="outline" onClick={() => setStep((s) => s - 1)}>
              이전
            </Button>
          )}
          {step < 2 ? (
            <Button className="flex-1" onClick={next}>
              다음
            </Button>
          ) : (
            <Button className="flex-1" size="lg" onClick={submit}>
              송금 신청하기
            </Button>
          )}
        </div>
      </div>
    </AppShell>
  )
}

function Row({ label, value, bold, accent }: { label: string; value: string; bold?: boolean; accent?: boolean }) {
  return (
    <div className="flex items-center justify-between">
      <dt className="text-muted-foreground">{label}</dt>
      <dd className={cn("tabular-nums", bold ? "font-bold" : "font-medium", accent && "text-primary")}>{value}</dd>
    </div>
  )
}
