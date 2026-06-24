"use client"

import { useEffect, useMemo, useState } from "react"
import { useRouter } from "next/navigation"
import { ArrowLeftRight, Check, ChevronRight, Trash2, UserPlus } from "lucide-react"
import { toast } from "sonner"
import { AppShell } from "@/components/app/app-shell"
import { Card } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Separator } from "@/components/ui/separator"
import { Textarea } from "@/components/ui/textarea"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import {
  COUNTRIES,
  CURRENCY_META,
  RATES,
  REMITTANCE_REASONS,
  formatKRW,
  formatCurrency,
  krwPerUnit,
  type CurrencyCode,
} from "@/lib/fx-data"
import { cn } from "@/lib/utils"
import { apiRequest } from "@/lib/api"

const STEPS = ["수취인", "금액", "확인"]
const MIN_SEND_AMOUNT_KRW = 10000
const PER_TRANSFER_LIMIT_USD = 5000
const REASON_TO_API: Record<string, string> = {
  가족생활비: "FAMILY_SUPPORT",
  유학경비: "TUITION",
  여행경비: "LIVING_EXPENSES",
  투자: "ETC",
  기타: "ETC",
}

function createIdempotencyKey() {
  if (typeof crypto !== "undefined" && crypto.randomUUID) {
    return crypto.randomUUID()
  }

  return Math.random().toString(36).substring(2) + Date.now().toString(36)
}

interface Recipient {
  id: string
  name: string
  country: string
  countryCode: string
  currency: CurrencyCode
  bank: string
  account: string
}

interface QuoteResponse {
  sendAmountKrw: number
  receiveAmountUsd: number
  exchangeRate: number
  fixedFee: number
  percentFee: number
  totalFee: number
  quoteId: string
  expiredAt: string
}

interface CreateTransferResponse {
  transferId: number
  status: string
}

export default function RemittancePage() {
  const router = useRouter()
  const [step, setStep] = useState(0)
  const [recipients, setRecipients] = useState<Recipient[]>([])
  const [loadingRecipients, setLoadingRecipients] = useState(true)
  const [loadingQuote, setLoadingQuote] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [deletingRecipient, setDeletingRecipient] = useState(false)
  const [quote, setQuote] = useState<QuoteResponse | null>(null)

  const [recipientId, setRecipientId] = useState("")
  const [deleteTarget, setDeleteTarget] = useState<Recipient | null>(null)
  const [adding, setAdding] = useState(false)
  const [form, setForm] = useState({ name: "", country: COUNTRIES[0].name, bank: "", account: "" })

  const [krwInput, setKrwInput] = useState("1000000")
  const [receiveInput, setReceiveInput] = useState("")
  const [amountMode, setAmountMode] = useState<"send" | "receive">("send")
  const [reason, setReason] = useState(REMITTANCE_REASONS[0])
  const [reasonDetail, setReasonDetail] = useState("")

  const recipient = recipients.find((r) => r.id === recipientId)
  const country = COUNTRIES.find((c) => c.name === (recipient?.country ?? form.country)) ?? COUNTRIES[0]
  const currency: CurrencyCode = recipient?.currency ?? country.currency
  const rate = currency === "KRW" ? { rate: 1, unit: 1 } : RATES[currency as Exclude<CurrencyCode, "KRW">]

  const krwAmount = Number(krwInput.replace(/[^\d]/g, "")) || 0
  const { fee, received } = useMemo(() => {
    const fee = quote?.totalFee ?? 0
    const received = quote?.receiveAmountUsd ?? krwAmount / krwPerUnit(currency)
    return { fee, received }
  }, [krwAmount, currency, quote])

  const total = krwAmount + fee
  const estimatedAmountUsd = krwAmount / krwPerUnit(currency)
  const estimatedMaxKrw = Math.floor(PER_TRANSFER_LIMIT_USD * krwPerUnit(currency))

  useEffect(() => {
    async function loadInitialData() {
      try {
        const recipientData = await apiRequest<any[]>("GET", "/api/v1/recipients")

        const mappedRecipients = (recipientData || []).map(mapRecipient)
        setRecipients(mappedRecipients)
        setRecipientId(mappedRecipients[0]?.id ?? "")
        setAdding(mappedRecipients.length === 0)
      } catch (err: any) {
        console.error(err)
        toast.error(err.message || "수취인 정보를 불러오지 못했습니다.")
        setAdding(true)
      } finally {
        setLoadingRecipients(false)
      }
    }

    loadInitialData()
  }, [])

  useEffect(() => {
    setQuote(null)
  }, [recipientId, krwInput, reason])

  async function fetchQuote() {
    if (!recipient) return null
    setLoadingQuote(true)
    try {
      const data = await apiRequest<QuoteResponse>("POST", "/api/v1/transfers/quote", {
        recipientId: Number(recipient.id),
        sendAmountKrw: krwAmount,
        reason: REASON_TO_API[reason] ?? "ETC",
      })
      setQuote(data)
      return data
    } catch (err: any) {
      console.error(err)
      toast.error(err.message || "송금 견적을 불러오지 못했습니다.")
      return null
    } finally {
      setLoadingQuote(false)
    }
  }

  async function saveRecipient() {
    if (!form.name.trim() || !form.bank.trim() || !form.account.trim())
      return toast.error("수취인 정보를 모두 입력하세요.")
    if (!/^\d{8,11}$/.test(form.account))
      return toast.error("계좌번호를 다시 확인해주세요.")
    const c = COUNTRIES.find((x) => x.name === form.country) ?? COUNTRIES[0]
    if (c.code !== "US" || c.currency !== "USD") return toast.error("현재 MVP에서는 미국·USD 수취인만 등록할 수 있습니다.")

    try {
      const created = await apiRequest<any>("POST", "/api/v1/recipients", {
        name: form.name.trim(),
        countryCode: c.code,
        currencyCode: c.currency,
        bankName: form.bank.trim(),
        accountNumber: form.account,
      })
      const rec = mapRecipient(created)
      setRecipients((prev) => [...prev, rec])
      setRecipientId(rec.id)
      setAdding(false)
      setForm({ name: "", country: COUNTRIES[0].name, bank: "", account: "" })
      toast.success("수취인이 추가되었습니다.")
    } catch (err: any) {
      console.error(err)
      toast.error(err.message || "수취인 등록에 실패했습니다.")
    }
  }

  async function deleteRecipient() {
    if (!deleteTarget) return

    setDeletingRecipient(true)
    try {
      await apiRequest("DELETE", `/api/v1/recipients/${deleteTarget.id}`)
      setRecipients((prev) => {
        const nextRecipients = prev.filter((item) => item.id !== deleteTarget.id)
        if (recipientId === deleteTarget.id) {
          setRecipientId(nextRecipients[0]?.id ?? "")
          setQuote(null)
          setAdding(nextRecipients.length === 0)
        }
        return nextRecipients
      })
      setDeleteTarget(null)
      toast.success("수취인이 삭제되었습니다.")
    } catch (err: any) {
      console.error(err)
      toast.error(err.message || "수취인 삭제에 실패했습니다.")
    } finally {
      setDeletingRecipient(false)
    }
  }

  function handleKrwInputChange(value: string) {
    setKrwInput(value.replace(/[^\d]/g, ""))
  }

  function handleReceiveInputChange(value: string) {
    const cleaned = value
      .replace(/[^0-9.]/g, "")
      .replace(/(\..*)\./g, "$1")

    setReceiveInput(cleaned)

    const receiveAmount = Number(cleaned) || 0
    const estimatedKrw = Math.ceil(receiveAmount * krwPerUnit(currency))
    setKrwInput(estimatedKrw > 0 ? String(estimatedKrw) : "")
  }

  function toggleAmountMode() {
    if (amountMode === "send") {
      setReceiveInput(received > 0 ? received.toFixed(2) : "")
      setAmountMode("receive")
      return
    }

    setAmountMode("send")
  }

  async function next() {
    if (step === 0 && !recipient) return toast.error("수취인을 선택하거나 추가하세요.")
    if (step === 1) {
      if (krwAmount <= 0) return toast.error("송금 금액을 입력하세요.")
      if (krwAmount < MIN_SEND_AMOUNT_KRW) {
        return toast.error(`최소 송금 금액은 ${formatKRW(MIN_SEND_AMOUNT_KRW)}입니다.`)
      }
      if (estimatedAmountUsd > PER_TRANSFER_LIMIT_USD) {
        return toast.error(`건당 최대 송금 한도는 ${formatCurrency(PER_TRANSFER_LIMIT_USD, "USD")}입니다.`)
      }
      const nextQuote = quote ?? await fetchQuote()
      if (!nextQuote) return
    }
    setStep((s) => Math.min(2, s + 1))
  }

  async function submit() {
    if (!recipient) return
    const currentQuote = quote ?? await fetchQuote()
    if (!currentQuote) return

    setSubmitting(true)
    try {
      const idempotencyKey = createIdempotencyKey()
      const response = await apiRequest<CreateTransferResponse>(
        "POST",
        "/api/v1/transfers",
        {
          quoteId: currentQuote.quoteId,
          reason: REASON_TO_API[reason] ?? "ETC",
          reasonDetail: reasonDetail.trim() || reason,
        },
        { "Idempotency-Key": idempotencyKey }
      )
      toast.success("송금이 신청되었습니다.")
      router.push(`/remittance/${response.transferId}`)
    } catch (err: any) {
      console.error(err)
      toast.error(err.message || "송금 신청에 실패했습니다.")
    } finally {
      setSubmitting(false)
    }
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
            {loadingRecipients && <p className="mt-4 text-sm text-muted-foreground">수취인 정보를 불러오는 중입니다.</p>}
            {!loadingRecipients && !adding && recipients.length > 0 && (
              <div className="mt-4 space-y-2">
                {recipients.map((r) => (
                  <div
                    key={r.id}
                    className={cn(
                      "flex w-full items-center gap-2 rounded-2xl border p-2 transition-all",
                      recipientId === r.id ? "border-primary ring-2 ring-primary/20" : "border-border hover:border-primary/40",
                    )}
                  >
                    <button
                      type="button"
                      onClick={() => setRecipientId(r.id)}
                      className="flex min-w-0 flex-1 items-center justify-between rounded-xl px-2 py-2 text-left"
                    >
                      <div className="min-w-0">
                        <p className="truncate font-semibold">{r.name}</p>
                        <p className="truncate text-sm text-muted-foreground">
                          {r.country} · {r.bank} · {r.account}
                        </p>
                      </div>
                      <span className="ml-3 shrink-0 text-lg" aria-hidden>
                        {CURRENCY_META[r.currency].flag}
                      </span>
                    </button>
                    <Button
                      type="button"
                      variant="ghost"
                      size="icon"
                      className="shrink-0 text-muted-foreground hover:text-destructive"
                      onClick={() => setDeleteTarget(r)}
                      aria-label={`${r.name} 수취인 삭제`}
                    >
                      <Trash2 className="size-4" />
                    </Button>
                  </div>
                ))}
                <Button variant="outline" className="w-full" onClick={() => setAdding(true)}>
                  <UserPlus className="size-4" /> 새 수취인 추가
                </Button>
              </div>
            )}

            {!loadingRecipients && adding && (
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
                    inputMode="numeric"
                    pattern="[0-9]*"
                    maxLength={11}
                    placeholder="1234567890"
                    value={form.account}
                    onChange={(e) => setForm({ ...form, account: e.target.value.replace(/\D/g, "") })}
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
                  <Label className="text-xs text-muted-foreground">
                    {amountMode === "send" ? "보내는 금액 (KRW)" : `수취 금액 (${currency})`}
                  </Label>
                  <Button
                    type="button"
                    variant="ghost"
                    size="sm"
                    onClick={toggleAmountMode}
                    className="h-7 gap-1.5 px-2 text-xs text-muted-foreground"
                  >
                    <ArrowLeftRight className="size-3.5" />
                    {amountMode === "send" ? "수취 금액 입력" : "보내는 금액 입력"}
                  </Button>
                </div>
                {amountMode === "send" ? (
                  <Input
                    inputMode="numeric"
                    value={krwInput ? Number(krwInput).toLocaleString("ko-KR") : ""}
                    onChange={(e) => handleKrwInputChange(e.target.value)}
                    className="mt-2 border-0 bg-transparent text-right text-2xl font-bold tabular-nums shadow-none focus-visible:ring-0"
                    placeholder="0"
                  />
                ) : (
                  <Input
                    inputMode="decimal"
                    value={receiveInput}
                    onChange={(e) => handleReceiveInputChange(e.target.value)}
                    className="mt-2 border-0 bg-transparent text-right text-2xl font-bold tabular-nums shadow-none focus-visible:ring-0"
                    placeholder="0.00"
                  />
                )}
                <p className="mt-1 text-right text-sm text-muted-foreground">
                  {loadingQuote
                    ? "견적 계산 중..."
                    : amountMode === "send"
                      ? `수취 예상 ${formatCurrency(received, currency)}`
                      : `예상 지출 비용 ${formatKRW(krwAmount)}`}
                </p>
                <div className="mt-3 flex flex-wrap items-center justify-end gap-x-3 gap-y-1 text-xs text-muted-foreground">
                  <span>최소 {formatKRW(MIN_SEND_AMOUNT_KRW)}</span>
                  <span>최대 {formatCurrency(PER_TRANSFER_LIMIT_USD, "USD")} · 약 {formatKRW(estimatedMaxKrw)}</span>
                </div>
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
              <div className="space-y-2">
                <Label htmlFor="reasonDetail">상세 사유</Label>
                <Textarea
                  id="reasonDetail"
                  value={reasonDetail}
                  onChange={(e) => setReasonDetail(e.target.value)}
                  maxLength={100}
                  placeholder="예: 유학 생활비, 가족 생활비 지원"
                  className="min-h-20 resize-none"
                />
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
              {reasonDetail.trim() && <Row label="상세 사유" value={reasonDetail.trim()} />}
              <Separator />
              <Row label="송금 금액" value={formatKRW(krwAmount)} />
              <Row label="적용 환율" value={`${formatRate(quote?.exchangeRate ?? rate.rate)} / ${currency}`} />
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
            <Button className="flex-1" onClick={next} disabled={loadingQuote}>
              {loadingQuote ? "견적 계산 중..." : "다음"}
            </Button>
          ) : (
            <Button className="flex-1" size="lg" onClick={submit} disabled={submitting}>
              {submitting ? "신청 중..." : "송금 신청하기"}
            </Button>
          )}
        </div>
      </div>
      <Dialog open={!!deleteTarget} onOpenChange={(open) => !deletingRecipient && !open && setDeleteTarget(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>수취인 삭제</DialogTitle>
            <DialogDescription>
              {deleteTarget?.name} 수취인을 삭제합니다. 삭제 후에도 기존 송금 내역에서는 해당 수취인 정보가 유지됩니다.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDeleteTarget(null)} disabled={deletingRecipient}>
              취소
            </Button>
            <Button variant="destructive" onClick={deleteRecipient} disabled={deletingRecipient}>
              {deletingRecipient ? "삭제 중..." : "삭제"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </AppShell>
  )
}

function mapRecipient(recipient: any): Recipient {
  const country = COUNTRIES.find((item) => item.code === recipient.countryCode) ?? COUNTRIES[0]
  return {
    id: String(recipient.recipientId),
    name: recipient.name,
    country: country.name,
    countryCode: recipient.countryCode,
    currency: recipient.currencyCode as CurrencyCode,
    bank: recipient.bankName,
    account: recipient.accountNumber,
  }
}

function formatRate(rate: number) {
  return `₩${Number(rate).toLocaleString("ko-KR", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
}

function Row({ label, value, bold, accent }: { label: string; value: string; bold?: boolean; accent?: boolean }) {
  return (
    <div className="flex items-center justify-between">
      <dt className="text-muted-foreground">{label}</dt>
      <dd className={cn("tabular-nums", bold ? "font-bold" : "font-medium", accent && "text-primary")}>{value}</dd>
    </div>
  )
}
