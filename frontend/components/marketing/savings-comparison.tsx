import Link from "next/link"
import { Check, X, ArrowRight } from "lucide-react"
import { Button } from "@/components/ui/button"
import { formatKRW, bankFee, remittanceFee } from "@/lib/fx-data"

export function SavingsComparison() {
  const sampleKRW = 1387500 // ~$1,000
  const bank = bankFee(sampleKRW)
  const fxflow = remittanceFee(sampleKRW)
  const savings = bank - fxflow

  return (
    <section id="savings" className="mx-auto max-w-6xl px-4 py-16 sm:px-6">
      <div className="mx-auto mb-12 max-w-2xl text-center">
        <h2 className="text-balance text-3xl font-bold tracking-tight sm:text-4xl">은행보다 투명한 수수료</h2>
        <p className="mt-3 text-pretty text-muted-foreground">
          $1,000 (약 {formatKRW(sampleKRW)}) 송금 시 예상 수수료를 비교했습니다.
        </p>
      </div>

      <div className="mx-auto grid max-w-3xl gap-4 sm:grid-cols-2">
        <div className="rounded-3xl border border-border bg-card p-6">
          <p className="text-sm font-semibold text-muted-foreground">일반 은행 송금</p>
          <p className="mt-2 text-3xl font-bold tabular-nums">{formatKRW(bank)}</p>
          <ul className="mt-5 flex flex-col gap-3 text-sm">
            <li className="flex items-center gap-2 text-muted-foreground">
              <X className="size-4 text-destructive" /> 환율 우대 제한적
            </li>
            <li className="flex items-center gap-2 text-muted-foreground">
              <X className="size-4 text-destructive" /> 높은 송금 수수료 + 전신료
            </li>
            <li className="flex items-center gap-2 text-muted-foreground">
              <X className="size-4 text-destructive" /> 불투명한 중간 수수료
            </li>
          </ul>
        </div>

        <div className="rounded-3xl border-2 border-primary bg-primary/5 p-6">
          <p className="text-sm font-semibold text-primary">FXFlow</p>
          <p className="mt-2 text-3xl font-bold tabular-nums text-primary">{formatKRW(fxflow)}</p>
          <ul className="mt-5 flex flex-col gap-3 text-sm">
            <li className="flex items-center gap-2">
              <Check className="size-4 text-accent" /> 실시간 시장 환율 적용
            </li>
            <li className="flex items-center gap-2">
              <Check className="size-4 text-accent" /> 낮고 투명한 수수료
            </li>
            <li className="flex items-center gap-2">
              <Check className="size-4 text-accent" /> 숨은 비용 없음
            </li>
          </ul>
        </div>
      </div>

      <div className="mx-auto mt-8 max-w-3xl rounded-3xl bg-accent/10 p-6 text-center">
        <p className="text-sm text-muted-foreground">예상 절약 금액</p>
        <p className="mt-1 text-4xl font-bold tabular-nums text-accent">{formatKRW(savings)}</p>
        <Button render={<Link href="/signup" />} className="mt-5">
          지금 시작하기
          <ArrowRight className="size-4" />
        </Button>
      </div>
    </section>
  )
}
