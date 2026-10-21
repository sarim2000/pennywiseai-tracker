import { Laptop, LogOut, Moon, RefreshCw, Smartphone, Sun } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { finishSignInIfReturning, isSignedIn, signIn, signOut, SignedOut, type Range, type Summary } from "@/lib/api";
import { FiltersContext, type Tab } from "@/lib/filters";
import { currenciesOf, PRESETS, presetRange, previousRange, rangeLabel, type Preset } from "@/lib/money";
import { useTheme, type ThemeChoice } from "@/lib/theme";
import { clearCache, useTool } from "@/lib/useTool";
import { ErrorNote } from "@/components/states";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { DropdownMenu, DropdownMenuContent, DropdownMenuRadioGroup, DropdownMenuRadioItem, DropdownMenuTrigger } from "@/components/ui/dropdown-menu";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Accounts } from "@/views/Accounts";
import { Analytics } from "@/views/Analytics";
import { Overview } from "@/views/Overview";
import { Transactions } from "@/views/Transactions";

const remembered = (k: string) => { try { return localStorage.getItem(k); } catch { return null; } };
const remember = (k: string, v: string) => { try { localStorage.setItem(k, v); } catch { /* private mode */ } };

function Logo() {
	return (
		<div className="flex items-center gap-2 font-semibold tracking-tight">
			<span className="grid size-7 place-items-center rounded-lg bg-primary text-sm text-primary-foreground" aria-hidden>₹</span>
			PennyWise
		</div>
	);
}

function ThemeToggle({ choice, set }: { choice: ThemeChoice; set: (t: ThemeChoice) => void }) {
	const Icon = choice === "dark" ? Moon : choice === "light" ? Sun : Laptop;
	return (
		<DropdownMenu>
			<DropdownMenuTrigger asChild><Button variant="ghost" size="icon" aria-label="Theme"><Icon className="size-4" /></Button></DropdownMenuTrigger>
			<DropdownMenuContent align="end">
				<DropdownMenuRadioGroup value={choice} onValueChange={(v) => set(v as ThemeChoice)}>
					<DropdownMenuRadioItem value="system">System</DropdownMenuRadioItem>
					<DropdownMenuRadioItem value="light">Light</DropdownMenuRadioItem>
					<DropdownMenuRadioItem value="dark">Dark</DropdownMenuRadioItem>
				</DropdownMenuRadioGroup>
			</DropdownMenuContent>
		</DropdownMenu>
	);
}

function SignIn({ error }: { error?: Error }) {
	const [busy, setBusy] = useState(false);
	const [ownErr, setErr] = useState<Error>();
	const err = ownErr ?? error;
	return (
		<main className="mx-auto flex min-h-[80vh] max-w-lg flex-col justify-center gap-6 px-4">
			<Logo />
			<div className="flex flex-col gap-2">
				<h1 className="text-3xl font-semibold tracking-tight text-balance">Your spending, on a bigger screen</h1>
				<p className="text-muted-foreground">Detailed analytics and your full transaction history, read live from your phone. Nothing is stored on PennyWise's servers.</p>
			</div>
			<Card>
				<CardContent className="flex flex-col gap-4">
					<ol className="flex flex-col gap-3 text-sm">
						{["On your phone, open PennyWise → Settings → Connect Claude / ChatGPT.", "Start an AI session and tap Get pairing code.", "Click Connect below and enter the code."].map((s, i) => (
							<li key={s} className="flex gap-3"><span className="grid size-6 shrink-0 place-items-center rounded-full bg-accent text-xs font-semibold text-accent-foreground">{i + 1}</span>{s}</li>
						))}
					</ol>
					<Button size="lg" disabled={busy} onClick={() => { setBusy(true); signIn().catch((e: Error) => { setErr(e); setBusy(false); }); }}>
						<Smartphone className="size-4" />Connect to my phone
					</Button>
					{err && <ErrorNote error={err} />}
				</CardContent>
			</Card>
			<p className="text-xs text-muted-foreground">This browser appears as “PennyWise dashboard” in your phone's connected apps, where you can disconnect it at any time. Read-only.</p>
		</main>
	);
}

export default function App() {
	const [theme, setTheme] = useTheme();
	const [ready, setReady] = useState(false);
	const [signedIn, setSignedIn] = useState(isSignedIn());
	const [signInError, setSignInError] = useState<Error>();

	useEffect(() => {
		finishSignInIfReturning().then(() => setSignedIn(isSignedIn()), (e: Error) => setSignInError(e)).finally(() => setReady(true));
	}, []);

	if (!ready) return null;
	if (!signedIn) return <SignIn error={signInError} />;
	return <Dashboard theme={theme} setTheme={setTheme} onSignOut={() => { signOut(); clearCache(); setSignedIn(false); }} />;
}

function Dashboard({ theme, setTheme, onSignOut }: { theme: ThemeChoice; setTheme: (t: ThemeChoice) => void; onSignOut: () => void }) {
	const [preset, setPreset] = useState<Preset>(() => (remembered("pw_preset") as Preset) ?? "3m");
	const [custom, setCustom] = useState<Range>(() => presetRange("3m"));
	const [version, setVersion] = useState(0);
	const [tab, setTab] = useState<Tab>("overview");
	const [drilled, setDrilled] = useState<{ category?: string; query?: string }>({});
	const range = useMemo(() => (preset === "custom" ? custom : presetRange(preset)), [preset, custom]);
	const previous = useMemo(() => previousRange(range), [range]);

	// Currencies present in this period, most-used first; the chart currency is remembered.
	const probe = useTool<Summary>("spending_summary", { ...range, group_by: "category", type: "EXPENSE" }, version);
	const currencies = probe.data ? currenciesOf(probe.data.groups) : [];
	const [picked, setPicked] = useState(() => remembered("pw_currency") ?? "");
	const currency = currencies.includes(picked) ? picked : currencies[0] ?? "INR";

	if (probe.error instanceof SignedOut) { onSignOut(); return null; }

	const filters = {
		range, previous, currency, currencies, version, drilled, setTab,
		drill: (f: { category?: string; query?: string }) => { setDrilled(f); setTab("transactions"); },
	};

	return (
		<FiltersContext.Provider value={filters}>
			<header className="sticky top-0 z-10 border-b bg-background/85 backdrop-blur">
				<div className="mx-auto flex max-w-7xl flex-wrap items-center gap-3 px-4 py-3">
					<Logo />
					<div className="ml-auto flex flex-wrap items-center gap-2">
						<Select value={preset} onValueChange={(v) => { setPreset(v as Preset); remember("pw_preset", v); }}>
							<SelectTrigger aria-label="Period" className="w-40"><SelectValue /></SelectTrigger>
							<SelectContent>
								{PRESETS.map((p) => <SelectItem key={p.id} value={p.id}>{p.label}</SelectItem>)}
								<SelectItem value="custom">Custom range…</SelectItem>
							</SelectContent>
						</Select>
						{preset === "custom" && (
							<div className="flex items-center gap-1">
								<Input type="date" aria-label="From" value={custom.from} max={custom.to} onChange={(e) => e.target.value && setCustom({ ...custom, from: e.target.value })} className="w-38" />
								<span className="text-muted-foreground">–</span>
								<Input type="date" aria-label="To" value={custom.to} min={custom.from} onChange={(e) => e.target.value && setCustom({ ...custom, to: e.target.value })} className="w-38" />
							</div>
						)}
						{currencies.length > 1 && (
							<Select value={currency} onValueChange={(v) => { setPicked(v); remember("pw_currency", v); }}>
								<SelectTrigger aria-label="Chart currency" className="w-24"><SelectValue /></SelectTrigger>
								<SelectContent>{currencies.map((c) => <SelectItem key={c} value={c}>{c}</SelectItem>)}</SelectContent>
							</Select>
						)}
						<Button variant="ghost" size="icon" aria-label="Refresh from phone" onClick={() => { clearCache(); setVersion((v) => v + 1); }}>
							<RefreshCw className={`size-4 ${probe.loading ? "animate-spin" : ""}`} />
						</Button>
						<ThemeToggle choice={theme} set={setTheme} />
						<Button variant="ghost" size="icon" aria-label="Sign out" onClick={onSignOut}><LogOut className="size-4" /></Button>
					</div>
				</div>
			</header>

			<main className="mx-auto flex max-w-7xl flex-col gap-4 px-4 py-6">
				<Tabs value={tab} onValueChange={(v) => { setTab(v as Tab); if (v !== "transactions") setDrilled({}); }}>
					<div className="flex flex-wrap items-center justify-between gap-3">
						<TabsList>
							<TabsTrigger value="overview">Overview</TabsTrigger>
							<TabsTrigger value="analytics">Analytics</TabsTrigger>
							<TabsTrigger value="transactions">Transactions</TabsTrigger>
							<TabsTrigger value="accounts">Accounts</TabsTrigger>
						</TabsList>
						<span className="text-sm text-muted-foreground">{rangeLabel(range)}</span>
					</div>
					{probe.error && !(probe.error instanceof SignedOut) ? (
						<div className="mt-4"><ErrorNote error={probe.error} onRetry={() => { clearCache(); setVersion((v) => v + 1); }} /></div>
					) : (
						<>
							<TabsContent value="overview" className="mt-4"><Overview /></TabsContent>
							<TabsContent value="analytics" className="mt-4"><Analytics /></TabsContent>
							<TabsContent value="transactions" className="mt-4"><Transactions key={JSON.stringify(drilled)} /></TabsContent>
							<TabsContent value="accounts" className="mt-4"><Accounts /></TabsContent>
						</>
					)}
				</Tabs>
			</main>
			<footer className="mx-auto max-w-7xl px-4 pb-10 text-center text-xs text-muted-foreground">
				Read live from your phone through PennyWise's relay, which never stores or logs your data. Amounts in different currencies are never added together.
			</footer>
		</FiltersContext.Provider>
	);
}
