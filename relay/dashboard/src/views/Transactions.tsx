import { ChevronLeft, ChevronRight, Download, X } from "lucide-react";
import { useEffect, useState } from "react";
import { tool, type Search, type Txn } from "@/lib/api";
import { useFilters } from "@/lib/filters";
import { fmt } from "@/lib/money";
import { useTool } from "@/lib/useTool";
import { Empty, ErrorNote, Loading } from "@/components/states";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";

const PAGE = 100;
const ALL = "__all";
const TYPES = [["EXPENSE", "Expense"], ["CREDIT", "Card spend"], ["INCOME", "Income"], ["TRANSFER", "Transfer"], ["INVESTMENT", "Investment"]] as const;
const SORTS = [["date_desc", "Newest first"], ["date_asc", "Oldest first"], ["amount_desc", "Largest first"], ["amount_asc", "Smallest first"]] as const;

/** RFC 4180 CSV, built in the browser; nothing is uploaded. */
function toCsv(rows: Txn[]) {
	const esc = (s: string) => (/[",\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s);
	return ["Date,Merchant,Category,Type,Amount,Currency,Account", ...rows.map((t) =>
		[t.date, t.merchant, t.category, t.type, t.amount.amount, t.amount.currency, t.account ?? ""].map(esc).join(","))].join("\n");
}

/** Picker options from the phone, keeping the current choice listed so the select never goes blank. */
const withSelected = (options: string[] | undefined, selected: string) =>
	selected === ALL || options?.includes(selected) ? options ?? [] : [selected, ...(options ?? [])];

export function Transactions() {
	const { range, version, drilled } = useFilters();
	const [query, setQuery] = useState(drilled.query ?? "");
	const [debounced, setDebounced] = useState(query);
	const [category, setCategory] = useState(drilled.category ?? ALL);
	const [type, setType] = useState(ALL);
	const [account, setAccount] = useState(ALL);
	const [sort, setSort] = useState("date_desc");
	const [page, setPage] = useState(0);
	const [exporting, setExporting] = useState(false);
	const [exportError, setExportError] = useState<Error>();

	useEffect(() => { const t = setTimeout(() => setDebounced(query.trim()), 300); return () => clearTimeout(t); }, [query]);
	useEffect(() => setPage(0), [debounced, category, type, account, sort, range]);

	const args = {
		...range, limit: PAGE, offset: page * PAGE, sort,
		...(debounced && { query: debounced }), ...(category !== ALL && { category }), ...(type !== ALL && { type }), ...(account !== ALL && { account }),
	};
	const res = useTool<Search>("search_transactions", args, version);

	async function exportCsv() {
		setExporting(true);
		setExportError(undefined);
		try {
			const all: Txn[] = [];
			for (let offset = 0; ; offset += 200) {
				const r = await tool("search_transactions", { ...args, limit: 200, offset });
				all.push(...r.transactions);
				if (r.shown === 0 || offset + 200 >= r.total_matches) break;
			}
			const url = URL.createObjectURL(new Blob([toCsv(all)], { type: "text/csv" }));
			const a = Object.assign(document.createElement("a"), { href: url, download: `pennywise-${range.from}-to-${range.to}.csv` });
			a.click();
			URL.revokeObjectURL(url);
		} catch (e) {
			setExportError(e as Error);
		} finally {
			setExporting(false);
		}
	}

	const total = res.data?.total_matches ?? 0;
	const clear = () => { setQuery(""); setCategory(ALL); setType(ALL); setAccount(ALL); };
	const filtered = query || category !== ALL || type !== ALL || account !== ALL;

	return (
		<Card>
			<CardContent className="flex flex-col gap-4">
				<div className="flex flex-wrap items-center gap-2">
					<Input aria-label="Search merchant" placeholder="Search merchant" value={query} onChange={(e) => setQuery(e.target.value)} className="w-56" />
					<Select value={category} onValueChange={setCategory}>
						<SelectTrigger aria-label="Category" className="w-48"><SelectValue placeholder="Category" /></SelectTrigger>
						<SelectContent>
							<SelectItem value={ALL}>All categories</SelectItem>
							{withSelected(res.data?.categories, category).map((c) => <SelectItem key={c} value={c}>{c}</SelectItem>)}
						</SelectContent>
					</Select>
					<Select value={type} onValueChange={setType}>
						<SelectTrigger aria-label="Type" className="w-36"><SelectValue /></SelectTrigger>
						<SelectContent>
							<SelectItem value={ALL}>All types</SelectItem>
							{TYPES.map(([v, l]) => <SelectItem key={v} value={v}>{l}</SelectItem>)}
						</SelectContent>
					</Select>
					<Select value={account} onValueChange={setAccount}>
						<SelectTrigger aria-label="Account" className="w-48"><SelectValue /></SelectTrigger>
						<SelectContent>
							<SelectItem value={ALL}>All accounts</SelectItem>
							{withSelected(res.data?.accounts, account).map((a) => <SelectItem key={a} value={a}>{a}</SelectItem>)}
						</SelectContent>
					</Select>
					<Select value={sort} onValueChange={setSort}>
						<SelectTrigger aria-label="Sort" className="w-40"><SelectValue /></SelectTrigger>
						<SelectContent>{SORTS.map(([v, l]) => <SelectItem key={v} value={v}>{l}</SelectItem>)}</SelectContent>
					</Select>
					{filtered && <Button variant="ghost" size="sm" onClick={clear}><X className="size-4" />Clear</Button>}
					<Button variant="outline" size="sm" className="ml-auto" onClick={exportCsv} disabled={exporting || total === 0}>
						<Download className="size-4" />{exporting ? "Preparing…" : "Download CSV"}
					</Button>
				</div>

				{exportError && <ErrorNote error={exportError} onRetry={exportCsv} />}
				{res.error ? <ErrorNote error={res.error} /> : !res.data ? <Loading rows={10} /> : res.data.transactions.length === 0 ? <Empty>No transactions match.</Empty> : (
					<>
						<div className="overflow-x-auto">
							<Table>
								<TableHeader>
									<TableRow><TableHead>Date</TableHead><TableHead>Merchant</TableHead><TableHead>Category</TableHead><TableHead>Type</TableHead><TableHead>Account</TableHead><TableHead className="text-right">Amount</TableHead></TableRow>
								</TableHeader>
								<TableBody className={res.loading ? "opacity-60" : undefined}>
									{res.data.transactions.map((t, i) => (
										<TableRow key={`${t.date}-${i}`}>
											<TableCell className="num whitespace-nowrap text-muted-foreground">{new Date(t.date).toLocaleString(undefined, { day: "numeric", month: "short", year: "numeric", hour: "2-digit", minute: "2-digit" })}</TableCell>
											<TableCell className="max-w-64 truncate font-medium">{t.merchant}</TableCell>
											<TableCell className="max-w-48 truncate">{t.category}</TableCell>
											<TableCell><Badge variant="secondary">{TYPES.find(([v]) => v === t.type)?.[1] ?? t.type}</Badge></TableCell>
											<TableCell className="whitespace-nowrap text-muted-foreground">{t.account ?? "—"}</TableCell>
											<TableCell className="num whitespace-nowrap text-right font-medium" style={t.type === "INCOME" ? { color: "var(--good)" } : undefined}>
												{t.type === "INCOME" ? "+" : ""}{fmt(t.amount)}
											</TableCell>
										</TableRow>
									))}
								</TableBody>
							</Table>
						</div>
						<div className="flex items-center justify-between text-sm text-muted-foreground">
							<span className="num">{page * PAGE + 1}–{page * PAGE + res.data.shown} of {total}</span>
							<div className="flex gap-2">
								<Button variant="outline" size="sm" disabled={page === 0} onClick={() => setPage(page - 1)}><ChevronLeft className="size-4" />Previous</Button>
								<Button variant="outline" size="sm" disabled={(page + 1) * PAGE >= total} onClick={() => setPage(page + 1)}>Next<ChevronRight className="size-4" /></Button>
							</div>
						</div>
					</>
				)}
			</CardContent>
		</Card>
	);
}
