import { Bar, BarChart, CartesianGrid, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import type { Group } from "@/lib/api";
import { amountIn, fmt } from "@/lib/money";

const axis = { stroke: "var(--muted-foreground)", fontSize: 12, tickLine: false, axisLine: false } as const;

function ChartTooltip({ active, payload, label, currency }: { active?: boolean; payload?: { name: string; value: number; color: string }[]; label?: string; currency: string }) {
	if (!active || !payload?.length) return null;
	return (
		<div className="rounded-lg border bg-popover px-3 py-2 text-xs shadow-md">
			<div className="mb-1 font-medium">{label}</div>
			{payload.map((p) => (
				<div key={p.name} className="flex items-center gap-2">
					<span className="size-2 rounded-full" style={{ background: p.color }} aria-hidden />
					<span className="text-muted-foreground">{p.name}</span>
					<span className="num ml-auto pl-3 font-medium">{fmt({ amount: String(p.value), currency })}</span>
				</div>
			))}
		</div>
	);
}

/** Vertical bars over time for one currency. */
export function TrendBars({ data, currency, name = "Spent", color = "var(--series-2)", height = 260 }: {
	data: { label: string; value: number }[]; currency: string; name?: string; color?: string; height?: number;
}) {
	return (
		<ResponsiveContainer width="100%" height={height}>
			<BarChart data={data} margin={{ top: 8, right: 8, left: 0, bottom: 0 }} barCategoryGap="22%">
				<CartesianGrid vertical={false} stroke="var(--grid)" />
				<XAxis dataKey="label" {...axis} interval="preserveStartEnd" minTickGap={16} />
				<YAxis {...axis} width={64} tickFormatter={(v) => fmt({ amount: String(v), currency }, true)} />
				<Tooltip cursor={{ fill: "var(--muted)", opacity: 0.5 }} content={<ChartTooltip currency={currency} />} />
				<Bar dataKey="value" name={name} fill={color} radius={[4, 4, 0, 0]} maxBarSize={36} />
			</BarChart>
		</ResponsiveContainer>
	);
}

/** Two series side by side per period (income and spending): one axis, one currency, legend + tooltip. */
export function PairedBars({ data, currency, height = 280 }: { data: { label: string; income: number; spent: number }[]; currency: string; height?: number }) {
	return (
		<ResponsiveContainer width="100%" height={height}>
			<BarChart data={data} margin={{ top: 8, right: 8, left: 0, bottom: 0 }} barGap={2} barCategoryGap="24%">
				<CartesianGrid vertical={false} stroke="var(--grid)" />
				<XAxis dataKey="label" {...axis} />
				<YAxis {...axis} width={64} tickFormatter={(v) => fmt({ amount: String(v), currency }, true)} />
				<Tooltip cursor={{ fill: "var(--muted)", opacity: 0.5 }} content={<ChartTooltip currency={currency} />} />
				<Legend iconType="circle" iconSize={8} wrapperStyle={{ fontSize: 12, color: "var(--muted-foreground)" }} />
				<Bar dataKey="income" name="Received" fill="var(--series-1)" radius={[4, 4, 0, 0]} maxBarSize={28} />
				<Bar dataKey="spent" name="Spent" fill="var(--series-2)" radius={[4, 4, 0, 0]} maxBarSize={28} />
			</BarChart>
		</ResponsiveContainer>
	);
}

/**
 * Ranked horizontal bars in one currency. Groups that also have amounts in other
 * currencies show them as text beside the bar; they are never folded into its length.
 */
export function BarList({ groups, currency, limit = 8, onSelect }: { groups: Group[]; currency: string; limit?: number; onSelect?: (g: Group) => void }) {
	const ranked = [...groups].filter((g) => amountIn(g.totals, currency) > 0).sort((a, b) => amountIn(b.totals, currency) - amountIn(a.totals, currency));
	const shown = ranked.slice(0, limit);
	const max = Math.max(1, ...shown.map((g) => amountIn(g.totals, currency)));
	if (!shown.length) return <p className="py-6 text-center text-sm text-muted-foreground">Nothing in {currency} for this period.</p>;
	return (
		<ul className="flex flex-col gap-3">
			{shown.map((g) => {
				const v = amountIn(g.totals, currency);
				const others = g.totals.filter((t) => t.currency !== currency);
				const Row = onSelect ? "button" : "div";
				return (
					<li key={g.group + (g.sub ?? "")}>
						<Row
							{...(onSelect ? { onClick: () => onSelect(g), type: "button" as const } : {})}
							className="group grid w-full grid-cols-[minmax(0,1fr)_auto] gap-x-3 gap-y-1.5 text-left text-sm"
							title={`${g.group}: ${g.totals.map((t) => fmt(t)).join(" · ")} · ${g.count} transactions`}
						>
							<span className={`truncate ${onSelect ? "group-hover:underline" : ""}`}>{g.group}</span>
							<span className="num text-right font-medium">
								{fmt({ amount: String(v), currency })}
								{others.length > 0 && <span className="ml-1.5 font-normal text-muted-foreground">+ {others.map((t) => fmt(t)).join(" · ")}</span>}
							</span>
							<span className="col-span-2 h-1.5 overflow-hidden rounded-full bg-muted">
								<span className="block h-full rounded-full" style={{ width: `${(v / max) * 100}%`, background: "var(--series-1)" }} />
							</span>
						</Row>
					</li>
				);
			})}
			{ranked.length > limit && <li className="text-xs text-muted-foreground">+ {ranked.length - limit} more</li>}
		</ul>
	);
}

// Sequential blue ramp, light → dark (reference palette, steps 100–700).
const RAMP = ["#cde2fb", "#9ec5f4", "#6da7ec", "#3987e5", "#256abf", "#184f95", "#0d366b"];

/** Category × month grid: one hue, darker = more spent. Click a row to see its transactions. */
export function Heatmap({ rows, cols, colLabel, value, currency, onRow }: {
	rows: string[]; cols: string[]; colLabel: (c: string) => string; value: (r: string, c: string) => number; currency: string; onRow?: (r: string) => void;
}) {
	const max = Math.max(1, ...rows.flatMap((r) => cols.map((c) => value(r, c))));
	const step = (v: number) => (v <= 0 ? null : RAMP[Math.min(RAMP.length - 1, Math.floor((v / max) * RAMP.length))]);
	return (
		<div className="overflow-x-auto">
			<table className="w-full border-separate border-spacing-0.5 text-xs">
				<thead>
					<tr>
						<th className="sticky left-0 bg-card text-left font-medium text-muted-foreground" scope="col">Category</th>
						{cols.map((c) => <th key={c} scope="col" className="px-1 pb-1 text-center font-medium text-muted-foreground">{colLabel(c)}</th>)}
						<th scope="col" className="pb-1 pl-2 text-right font-medium text-muted-foreground">Total</th>
					</tr>
				</thead>
				<tbody>
					{rows.map((r) => {
						const total = cols.reduce((s, c) => s + value(r, c), 0);
						return (
							<tr key={r}>
								<th scope="row" className="sticky left-0 max-w-44 truncate bg-card pr-2 text-left font-normal">
									{onRow ? <button className="truncate hover:underline" onClick={() => onRow(r)}>{r}</button> : r}
								</th>
								{cols.map((c) => {
									const v = value(r, c);
									const bg = step(v);
									return (
										<td key={c} title={`${r}, ${colLabel(c)}: ${fmt({ amount: String(v), currency })}`}
											className="h-8 min-w-14 rounded-[4px] text-center num"
											// Ink follows the cell shade, not the theme: dark text on light steps, white on dark ones.
											style={{ background: bg ?? "var(--muted)", color: !bg ? "var(--muted-foreground)" : RAMP.indexOf(bg) >= 3 ? "#ffffff" : "#0d2a4f" }}>
											{v > 0 ? fmt({ amount: String(v), currency }, true) : ""}
										</td>
									);
								})}
								<td className="num pl-2 text-right font-medium">{fmt({ amount: String(total), currency }, true)}</td>
							</tr>
						);
					})}
				</tbody>
			</table>
		</div>
	);
}
