import type { Account, Subscription } from "@/lib/api";
import { useFilters } from "@/lib/filters";
import { dayLabel, fmt } from "@/lib/money";
import { useTool } from "@/lib/useTool";
import { Empty, ErrorNote, Loading } from "@/components/states";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";

export function Accounts() {
	const { version } = useFilters();
	const accounts = useTool<{ accounts: Account[] }>("list_accounts", {}, version);
	const subs = useTool<{ subscriptions: Subscription[] }>("list_subscriptions", {}, version);
	if (accounts.error) return <ErrorNote error={accounts.error} />;

	return (
		<div className="grid gap-4 lg:grid-cols-2">
			<Card>
				<CardHeader><CardTitle>Accounts and cards</CardTitle><CardDescription>Latest known balance from your bank messages</CardDescription></CardHeader>
				<CardContent>
					{!accounts.data ? <Loading /> : accounts.data.accounts.length === 0 ? <Empty>No accounts yet.</Empty> : (
						<Table>
							<TableHeader><TableRow><TableHead>Account</TableHead><TableHead>As of</TableHead><TableHead className="text-right">Balance</TableHead></TableRow></TableHeader>
							<TableBody>
								{accounts.data.accounts.map((a) => (
									<TableRow key={a.account + a.kind}>
										<TableCell>
											<div className="font-medium">{a.account}</div>
											<Badge variant="secondary" className="mt-1 capitalize">{a.kind}</Badge>
										</TableCell>
										<TableCell className="text-muted-foreground">{dayLabel(a.as_of.slice(0, 10))}</TableCell>
										<TableCell className="num text-right font-medium">
											{a.outstanding ? <>{fmt(a.outstanding)} <span className="block text-xs font-normal text-muted-foreground">due{a.credit_limit && ` of ${fmt(a.credit_limit)}`}</span></> : fmt(a.balance)}
										</TableCell>
									</TableRow>
								))}
							</TableBody>
						</Table>
					)}
				</CardContent>
			</Card>
			<Card>
				<CardHeader><CardTitle>Subscriptions</CardTitle><CardDescription>Active recurring payments</CardDescription></CardHeader>
				<CardContent>
					{subs.error ? <ErrorNote error={subs.error} /> : !subs.data ? <Loading /> : subs.data.subscriptions.length === 0 ? <Empty>No active subscriptions.</Empty> : (
						<Table>
							<TableHeader><TableRow><TableHead>Name</TableHead><TableHead>Every</TableHead><TableHead>Next</TableHead><TableHead className="text-right">Amount</TableHead></TableRow></TableHeader>
							<TableBody>
								{subs.data.subscriptions.map((s) => (
									<TableRow key={s.merchant + s.amount.amount}>
										<TableCell className="font-medium">{s.merchant}</TableCell>
										<TableCell className="text-muted-foreground">{s.billing_cycle}</TableCell>
										<TableCell className="text-muted-foreground">{s.next_payment ? dayLabel(s.next_payment) : "—"}</TableCell>
										<TableCell className="num text-right font-medium">{fmt(s.amount)}</TableCell>
									</TableRow>
								))}
							</TableBody>
						</Table>
					)}
				</CardContent>
			</Card>
		</div>
	);
}
