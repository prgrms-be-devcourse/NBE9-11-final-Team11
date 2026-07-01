import Link from "next/link"
import { ArrowRight, ShieldCheck } from "lucide-react"
import { Button } from "@/components/ui/button"
import { LiveRateWidget } from "./live-rate-widget"

export function Hero() {
  return (
    <section className="relative overflow-hidden">
      <div className="mx-auto grid max-w-6xl items-center gap-12 px-4 py-16 sm:px-6 lg:grid-cols-2 lg:py-24">
        <div className="flex flex-col items-start gap-6">
          <span className="inline-flex items-center gap-2 rounded-full border border-border bg-secondary px-3 py-1 text-xs font-medium text-secondary-foreground">
            <ShieldCheck className="size-3.5 text-accent" />
            시뮬레이션 전용 · 실제 자금 이동 없음
          </span>
          <h1 className="text-balance text-4xl font-bold leading-tight tracking-tight sm:text-5xl lg:text-6xl">
            실시간 환율 기반 <br />
            <span className="inline-block text-primary">해외송금</span> 시뮬레이션
          </h1>
          <p className="text-pretty text-lg leading-relaxed text-muted-foreground">
            은행보다 투명한 환율과 수수료를 경험해보세요. 가상 계좌로 환전과 해외송금의 모든 과정을 안전하게
            시뮬레이션할 수 있습니다.
          </p>
          <div className="flex flex-wrap items-center gap-3">
            <Button render={<Link href="/signup" />} size="lg">
              시작하기
              <ArrowRight className="size-4" />
            </Button>
            <Button render={<Link href="/rates" />} size="lg" variant="outline">
              환율 확인하기
            </Button>
          </div>
        </div>

        <div className="relative">
          <LiveRateWidget />
        </div>
      </div>
    </section>
  )
}
