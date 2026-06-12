import { MarketingHeader } from "@/components/marketing/marketing-header"
import { Hero } from "@/components/marketing/hero"
import { Features } from "@/components/marketing/features"
import { HowItWorks } from "@/components/marketing/how-it-works"
import { SavingsComparison } from "@/components/marketing/savings-comparison"

export default function Page() {
  return (
    <div className="flex min-h-screen flex-col">
      <MarketingHeader />
      <main className="flex-1">
        <Hero />
        <Features />
        <HowItWorks />
        <SavingsComparison />
      </main>
      <footer className="border-t border-border py-8">
        <div className="mx-auto max-w-6xl px-4 text-center text-sm text-muted-foreground sm:px-6">
          <p className="font-semibold text-foreground">FXFlow</p>
          <p className="mt-2">
            본 서비스는 시뮬레이션 전용 데모입니다. 실제 은행 API와 연결되지 않으며 실제 자금이 이동하지 않습니다.
          </p>
          <p className="mt-2">© 2026 FXFlow. All rights reserved.</p>
        </div>
      </footer>
    </div>
  )
}
