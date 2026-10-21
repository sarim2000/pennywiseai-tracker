import type { Summary } from "@/lib/api";
import { useFilters } from "@/lib/filters";
import { amountIn, change, fmt, monthLabel } from "@/lib/money";
import { useTool } from "@/lib/useTool";
import { BarList, Heatmap, PairedBars, TrendBars } from "@/components/charts";
import { Empty, ErrorNote, Loading } from "@/components/states";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";

const WEEKDAYS = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"];

export function Analytics() {
	const { range, previous, currency, version, drill } = useFilters();
	const grid = useTool<Summary>("spending_summary", { ...range, group_by: "month_category", type: "EXPENSE" }, version);
	const spentByMonth = useTool<Summary>("spending_summary", { ...range, group_by: "month", type: "EXPENSE" }, version);
	const receivedByMonth = useTool<Summary>("spending_summary", { ...range, group_by: "month", type: "INCOME" }, version);
	const now = useTool<Summary>("spending_summary", { ...range, group_by: "category", type: "EXPENSE" }, version);
	const before = useTool<Summary>("spending_summary", { ...previous, group_by: "category", type: "EXPENSE" }, version);
	const byAccount = useTool<Summary>("spending_summary", { ...range, group_by: "account", type: "EXPENSE" }, version);
	const byDay = useTool<Summary>("spending_summary", { ...range, group_by: "day", type: "EXPENSE" }, version);

	// Any failed read fails the page, so a missing answer never looks like "no data".
	const error = [grid, spentByMonth, receivedByMonth, now, before, byAccount, byDay].find((r) => r.error)?.error;
	if (error) return <ErrorNote error={error} />;

	// Category × month, in the chart currency.
	const months = [...new Set((grid.data?.groups ?? []).map((g) => g.group))].sort();
	const cell = new Map((grid.data?.groups ?? []).map((g) => [`${g.sub}|${g.group}`, amountIn(g.totals, currency)]));
	const categories = [...new Set((grid.data?.groups ?? []).map((g) => g.sub!))]
		.map((c) => ({ c, total: months.reduce((s, m) => s + (cell.get(`${c}|${m}`) ?? 0), 0) }))
		.filter((x) => x.total > 0)
		.sort((a, b) => b.total - a.total)
		.map((x) => x.c);

	const paired = [...new Set([...(spentByMonth.data?.groups ?? []), ...(receivedByMonth.data?.groups ?? [])].map((g) => g.group))].sort().map((m) => ({
		label: monthLabel(m),
		spent: amountIn(spentByMonth.data?.groups.find((g) => g.group === m)?.totals ?? [], currency),
		income: amountIn(receivedByMonth.data?.groups.find((g) => g.group === m)?.totals ?? [], currency),
	}));

	const comparison = [...new Set([...(now.data?.groups ?? []), ...(before.data?.groups ?? [])].map((g) => g.group))]
		.map((cat) => {
			const a = amountIn(now.data?.groups.find((g) => g.group === cat)?.totals ?? [], currency);
			const b = amountIn(before.data?.groups.find((g) => g.group === cat)?.totals ?? [], currency);
			return { cat, a, b, pct: change(a, b) };
		})
		.filter((r) => r.a > 0 || r.b > 0)
		.sort((x, y) => Math.abs(y.a - y.b) - Math.abs(x.a - x.b));

	const weekday = WEEKDAYS.map((label) => ({ label, value: 0 }));
	for (const g of byDay.data?.groups ?? []) {
		const [y, m, d] = g.group.split("-").map(Number);
		weekday[(new Date(y, m - 1, d).getDay() + 6) % 7].value += amountIn(g.totals, currency);
	}

	return (
		<div className="flex flex-col gap-4">
			<Card>
				<CardHeader>
					<CardTitle>Category by month</CardTitle>
					<CardDescription>Darker means more spent, in {currency}. Click a category to see its transactions.</CardDescription>
				</CardHeader>
				<CardContent>
					{!grid.data ? <Loading rows={6} /> : categories.length === 0 ? <Empty>No spending in {currency} for this period.</Empty> : (
						<Heatmap rows={categories} cols={months} colLabel={monthLabel} value={(r, c) => cell.get(`${r}|${c}`) ?? 0} currency={currency} onRow={(r) => drill({ category: r })} />
					)}
				</CardContent>
			</Card>

			<div className="grid gap-4 lg:grid-cols-2">
				<Card>
					<CardHeader><CardTitle>Received vs spent</CardTitle><CardDescription>Per month, in {currency}</CardDescription></CardHeader>
					<CardContent>{!spentByMonth.data || !receivedByMonth.data ? <Loading rows={6} /> : paired.length ? <PairedBars data={paired} currency={currency} /> : <Empty>Nothing in this period.</Empty>}</CardContent>
				</Card>
				<Card>
					<CardHeader><CardTitle>Spending by day of week</CardTitle><CardDescription>Total for each weekday across the period, in {currency}</CardDescription></CardHeader>
					<CardContent>{!byDay.data ? <Loading rows={6} /> : <TrendBars data={weekday} currency={currency} color="var(--series-1)" height={280} />}</CardContent>
				</Card>
			</div>

			<div className="grid gap-4 lg:grid-cols-3">
				<Card className="lg:col-span-2">
					<CardHeader><CardTitle>Compared with the previous period</CardTitle><CardDescription>Biggest changes first, in {currency}</CardDescription></CardHeader>
					<CardContent>
						{!now.data || !before.data ? <Loading rows={6} /> : comparison.length === 0 ? <Empty>Nothing to compare.</Empty> : (
							<Table>
								<TableHeader>
									<TableRow><TableHead>Category</TableHead><TableHead className="text-right">This period</TableHead><TableHead className="text-right">Previous</TableHead><TableHead className="text-right">Change</TableHead></TableRow>
								</TableHeader>
								<TableBody>
									{comparison.slice(0, 12).map((r) => (
										<TableRow key={r.cat} className="cursor-pointer" onClick={() => drill({ category: r.cat })}>
											<TableCell className="max-w-56 truncate">{r.cat}</TableCell>
											<TableCell className="num text-right">{fmt({ amount: String(r.a), currency })}</TableCell>
											<TableCell className="num text-right text-muted-foreground">{fmt({ amount: String(r.b), currency })}</TableCell>
											<TableCell className="num text-right" style={{ color: r.pct === null ? undefined : r.pct > 0 ? "var(--destructive)" : "var(--good)" }}>
												{r.pct === null ? "new" : `${r.pct > 0 ? "+" : ""}${r.pct.toFixed(0)}%`}
											</TableCell>
										</TableRow>
									))}
								</TableBody>
							</Table>
						)}
					</CardContent>
				</Card>
				<Card>
					<CardHeader><CardTitle>By account or card</CardTitle><CardDescription>Spending, in {currency}</CardDescription></CardHeader>
					<CardContent>{byAccount.data ? <BarList groups={byAccount.data.groups} currency={currency} /> : <Loading />}</CardContent>
				</Card>
			</div>
		</div>
	);
}
