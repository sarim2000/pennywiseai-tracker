import { ArrowDownRight, ArrowUpRight } from "lucide-react";
import type { Money, Summary, Subscription } from "@/lib/api";
import { useFilters } from "@/lib/filters";
import { amountIn, change, dayLabel, fmt, monthLabel } from "@/lib/money";
import { useTool } from "@/lib/useTool";
import { BarList, TrendBars } from "@/components/charts";
import { Empty, ErrorNote, Loading } from "@/components/states";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";

function Delta({ now, before, invert = false }: { now: number; before: number; invert?: boolean }) {
	const pct = change(now, before);
	if (pct === null) return <span className="text-xs text-muted-foreground">No earlier data to compare</span>;
	const up = pct >= 0;
	const good = invert ? !up : up;
	const Icon = up ? ArrowUpRight : ArrowDownRight;
	return (
		<span className="inline-flex items-center gap-1 text-xs" style={{ color: good ? "var(--good)" : "var(--destructive)" }}>
			<Icon className="size-3.5" aria-hidden />
			{Math.abs(pct).toFixed(0)}% <span className="text-muted-foreground">vs previous period</span>
		</span>
	);
}

/** Every currency on its own line; the chart currency's line also shows the change. */
function Kpi({ title, totals, previous, currency, invert }: { title: string; totals?: Money[]; previous?: Money[]; currency: string; invert?: boolean }) {
	return (
		<Card className="gap-2">
			<CardHeader><CardDescription>{title}</CardDescription></CardHeader>
			<CardContent className="flex flex-col gap-1">
				{!totals ? <Loading rows={1} /> : totals.length === 0 ? <span className="text-2xl font-semibold">—</span> : (
					<>
						{[...totals].sort((a) => (a.currency === currency ? -1 : 1)).map((t, i) => (
							<span key={t.currency} className={`num font-semibold tracking-tight ${i === 0 ? "text-2xl" : "text-base text-muted-foreground"}`}>{fmt(t)}</span>
						))}
						{previous && <Delta now={amountIn(totals, currency)} before={amountIn(previous, currency)} invert={invert} />}
					</>
				)}
			</CardContent>
		</Card>
	);
}

export function Overview() {
	const { range, previous, currency, version, drill } = useFilters();
	const spent = useTool<Summary>("spending_summary", { ...range, group_by: "category", type: "EXPENSE" }, version);
	const received = useTool<Summary>("spending_summary", { ...range, group_by: "category", type: "INCOME" }, version);
	const prevSpent = useTool<Summary>("spending_summary", { ...previous, group_by: "category", type: "EXPENSE" }, version);
	const prevReceived = useTool<Summary>("spending_summary", { ...previous, group_by: "category", type: "INCOME" }, version);
	const days = Math.round((Date.parse(range.to) - Date.parse(range.from)) / 86_400_000) + 1;
	const trend = useTool<Summary>("spending_summary", { ...range, group_by: days <= 62 ? "day" : "month", type: "EXPENSE" }, version);
	const merchants = useTool<Summary>("spending_summary", { ...range, group_by: "merchant", type: "EXPENSE" }, version);
	const subs = useTool<{ subscriptions: Subscription[] }>("list_subscriptions", {}, version);

	// Any failed read fails the page, so a missing answer never looks like "no data".
	const error = [spent, received, prevSpent, prevReceived, trend, merchants, subs].find((r) => r.error)?.error;
	if (error) return <ErrorNote error={error} />;

	const net = spent.data && received.data
		? [...new Set([...spent.data.grand_total, ...received.data.grand_total].map((m) => m.currency))].map((c) => ({
			currency: c, amount: String(amountIn(received.data!.grand_total, c) - amountIn(spent.data!.grand_total, c)),
		}))
		: undefined;
	const trendData = (trend.data?.groups ?? [])
		.map((g) => ({ key: g.group, label: days <= 62 ? dayLabel(g.group) : monthLabel(g.group), value: amountIn(g.totals, currency) }))
		.sort((a, b) => a.key.localeCompare(b.key));
	const upcoming = (subs.data?.subscriptions ?? []).filter((s) => s.next_payment).sort((a, b) => a.next_payment!.localeCompare(b.next_payment!)).slice(0, 6);

	return (
		<div className="flex flex-col gap-4">
			<div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
				<Kpi title="Spent" totals={spent.data?.grand_total} previous={prevSpent.data?.grand_total} currency={currency} invert />
				<Kpi title="Received" totals={received.data?.grand_total} previous={prevReceived.data?.grand_total} currency={currency} />
				<Kpi title="Net (received − spent)" totals={net} currency={currency} />
				<Card className="gap-2">
					<CardHeader><CardDescription>Spending transactions</CardDescription></CardHeader>
					<CardContent>
						{spent.data ? <span className="num text-2xl font-semibold">{spent.data.groups.reduce((s, g) => s + g.count, 0)}</span> : <Loading rows={1} />}
					</CardContent>
				</Card>
			</div>

			<Card>
				<CardHeader>
					<CardTitle>Spending over time</CardTitle>
					<CardDescription>{days <= 62 ? "Per day" : "Per month"}, in {currency}</CardDescription>
				</CardHeader>
				<CardContent>{trend.loading && !trend.data ? <Loading rows={6} /> : trendData.length ? <TrendBars data={trendData} currency={currency} /> : <Empty>No spending in this period.</Empty>}</CardContent>
			</Card>

			<div className="grid gap-4 lg:grid-cols-3">
				<Card>
					<CardHeader><CardTitle>By category</CardTitle><CardDescription>Click one to see its transactions</CardDescription></CardHeader>
					<CardContent>{spent.data ? <BarList groups={spent.data.groups} currency={currency} onSelect={(g) => drill({ category: g.group })} /> : <Loading />}</CardContent>
				</Card>
				<Card>
					<CardHeader><CardTitle>Top merchants</CardTitle><CardDescription>Where the money went</CardDescription></CardHeader>
					<CardContent>{merchants.data ? <BarList groups={merchants.data.groups} currency={currency} onSelect={(g) => drill({ query: g.group })} /> : <Loading />}</CardContent>
				</Card>
				<Card>
					<CardHeader><CardTitle>Upcoming payments</CardTitle><CardDescription>Active subscriptions</CardDescription></CardHeader>
					<CardContent>
						{!subs.data ? <Loading /> : upcoming.length === 0 ? <Empty>No upcoming subscription payments.</Empty> : (
							<ul className="flex flex-col divide-y">
								{upcoming.map((s) => (
									<li key={s.merchant + s.next_payment} className="flex items-center justify-between gap-3 py-2 text-sm">
										<div className="min-w-0">
											<div className="truncate">{s.merchant}</div>
											<div className="text-xs text-muted-foreground">{dayLabel(s.next_payment!)} · {s.billing_cycle}</div>
										</div>
										<span className="num font-medium">{fmt(s.amount)}</span>
									</li>
								))}
							</ul>
						)}
					</CardContent>
				</Card>
			</div>
		</div>
	);
}
