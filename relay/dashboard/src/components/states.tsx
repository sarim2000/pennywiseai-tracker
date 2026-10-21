import { AlertCircle, Smartphone } from "lucide-react";
import { PhoneError } from "@/lib/api";
import { Skeleton } from "@/components/ui/skeleton";

/** Shows why the phone didn't answer, in its own words, with a way to try again. */
export function ErrorNote({ error, onRetry }: { error: Error; onRetry?: () => void }) {
	const phone = error instanceof PhoneError;
	return (
		<div role="status" className="flex items-start gap-3 rounded-xl border border-amber-500/30 bg-amber-500/10 p-4 text-sm">
			{phone ? <Smartphone className="mt-0.5 size-4 shrink-0" aria-hidden /> : <AlertCircle className="mt-0.5 size-4 shrink-0" aria-hidden />}
			<div className="flex-1">{error.message}</div>
			{onRetry && <button onClick={onRetry} className="font-medium text-primary underline-offset-4 hover:underline">Try again</button>}
		</div>
	);
}

export function Loading({ rows = 4 }: { rows?: number }) {
	return (
		<div className="flex flex-col gap-3" aria-busy>
			{Array.from({ length: rows }, (_, i) => <Skeleton key={i} className="h-6 w-full" />)}
		</div>
	);
}

export function Empty({ children }: { children: React.ReactNode }) {
	return <p className="py-6 text-center text-sm text-muted-foreground">{children}</p>;
}
