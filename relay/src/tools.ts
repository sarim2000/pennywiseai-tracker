// The tools the phone answers. The Worker advertises them; only the phone runs them.
const date = { type: "string", description: "Date as YYYY-MM-DD" };

const readOnly = { readOnlyHint: true, destructiveHint: false, idempotentHint: true, openWorldHint: false };

export const TOOLS = [
	{
		name: "search_transactions",
		title: "Search transactions",
		description:
			"Find the user's transactions in a date range, optionally filtered by text, category or type. Amounts are returned with their currency; never add amounts in different currencies together.",
		inputSchema: {
			type: "object",
			properties: {
				from: date,
				to: date,
				query: { type: "string", description: "Words to match in the merchant or description" },
				category: { type: "string", description: "Category name, e.g. Food & Dining" },
				type: { type: "string", enum: ["EXPENSE", "INCOME", "TRANSFER", "CREDIT", "INVESTMENT"] },
				limit: { type: "integer", minimum: 1, maximum: 200, default: 50 },
			},
			required: ["from", "to"],
		},
		annotations: readOnly,
	},
	{
		name: "spending_summary",
		title: "Spending summary",
		description:
			"Totals for a date range grouped by category, merchant or month. Each total is per currency; the result never mixes currencies.",
		inputSchema: {
			type: "object",
			properties: {
				from: date,
				to: date,
				group_by: { type: "string", enum: ["category", "merchant", "month"], default: "category" },
				type: { type: "string", enum: ["EXPENSE", "INCOME"], default: "EXPENSE" },
			},
			required: ["from", "to"],
		},
		annotations: readOnly,
	},
	{
		name: "list_accounts",
		title: "List accounts",
		description: "The user's bank accounts and cards with their latest known balance. Account numbers are masked.",
		inputSchema: { type: "object", properties: {} },
		annotations: readOnly,
	},
	{
		name: "list_subscriptions",
		title: "List subscriptions",
		description: "The user's recurring payments and subscriptions, with amount, currency and next payment date.",
		inputSchema: { type: "object", properties: {} },
		annotations: readOnly,
	},
] as const;

export const TOOL_NAMES: ReadonlySet<string> = new Set(TOOLS.map((t) => t.name));

export type ToolResult = { content: { type: "text"; text: string }[]; isError?: boolean; structuredContent?: unknown };

export const textResult = (text: string, isError = false): ToolResult => ({ content: [{ type: "text", text }], isError });

export const NOT_CONNECTED =
	"PennyWise isn't connected right now. On your phone, open PennyWise and tap “Let AI read my data” (or use the PennyWise AI tile), then ask again.";
