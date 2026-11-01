// The tools the phone answers. The Worker advertises them; only the phone runs them.
const date = { type: "string", description: "Date as YYYY-MM-DD" };

const readOnly = { readOnlyHint: true, destructiveHint: false, idempotentHint: true, openWorldHint: false };
// Writes: MCP clients ask the user before calling these. The phone also refuses them unless
// the user turned on "Let AI apps make changes".
const write = { readOnlyHint: false, destructiveHint: false, idempotentHint: true, openWorldHint: false };
const create = { ...write, idempotentHint: false };
const merchant = { type: "string", description: "Merchant exactly as shown in search_transactions results (the merchant field, not the alias)" };
const field = {
	type: "string",
	enum: ["AMOUNT", "TYPE", "CATEGORY", "MERCHANT", "NARRATION", "SMS_TEXT", "BANK_NAME", "TRANSACTION_HOUR", "TRANSACTION_TIME", "TRANSACTION_DAY_OF_WEEK", "TRANSACTION_DAY_OF_MONTH", "TRANSACTION_DATE", "ACCOUNT", "TAGS"],
};
const WRITE_NOTE = " Changes data: only works while the user has turned on Let AI apps make changes in PennyWise.";

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
				account: { type: "string", description: "Only this account, exactly as shown in results, e.g. HDFC ••1234" },
				sort: { type: "string", enum: ["date_desc", "date_asc", "amount_desc", "amount_asc"], default: "date_desc" },
				limit: { type: "integer", minimum: 1, maximum: 200, default: 50 },
				offset: { type: "integer", minimum: 0, default: 0, description: "Skip this many matches, to page through more than 200" },
			},
			required: ["from", "to"],
		},
		annotations: readOnly,
	},
	{
		name: "spending_summary",
		title: "Spending summary",
		description:
			"Totals for a date range grouped by category, merchant, month, day, account, or month and category together. EXPENSE means spending, including card purchases. Each total is per currency; the result never mixes currencies.",
		inputSchema: {
			type: "object",
			properties: {
				from: date,
				to: date,
				group_by: {
					type: "string",
					enum: ["category", "merchant", "month", "day", "account", "month_category"],
					default: "category",
					description: "month_category returns one group per month and category: group is YYYY-MM, sub is the category",
				},
				type: { type: "string", enum: ["EXPENSE", "INCOME"], default: "EXPENSE" },
				account: { type: "string", description: "Only this account, exactly as shown in results, e.g. HDFC ••1234" },
				limit: { type: "integer", minimum: 1, description: "Only the first this many groups, most transactions first. total_groups says how many there are." },
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
	{
		name: "list_categories",
		title: "List categories",
		description: "The user's categories and whether each is for expenses or income. Use these exact names when changing categories.",
		inputSchema: { type: "object", properties: {} },
		annotations: readOnly,
	},
	{
		name: "list_rules",
		title: "List rules",
		description: "The user's own smart rules (built-in templates excluded), in the same shape create_rule takes. Rules run on each new transaction as it arrives.",
		inputSchema: { type: "object", properties: {} },
		annotations: readOnly,
	},
	{
		name: "categorize_merchants",
		title: "Set merchant categories",
		description:
			"Remember a category for each merchant, so its future transactions are filed there, and move its past transactions out of from_category (default Others) into it. Rows the user filed in another category stay put; split and loan-linked rows never move. Only rows of the category's kind move: an income category files money received, an expense category files spending. Payment channels like \"UPI Payment\" can't be remembered." +
			WRITE_NOTE,
		inputSchema: {
			type: "object",
			properties: {
				mappings: {
					type: "array",
					maxItems: 100,
					items: {
						type: "object",
						properties: {
							merchant,
							category: { type: "string", description: "An existing category name from list_categories" },
							from_category: { type: "string", description: "Move past transactions out of this category. Default Others." },
						},
						required: ["merchant", "category"],
					},
				},
			},
			required: ["mappings"],
		},
		annotations: write,
	},
	{
		name: "set_merchant_aliases",
		title: "Set merchant display names",
		description:
			"Give merchants a friendlier display name (alias) in PennyWise, for example \"AMAZON PAY INDIA\" → \"Amazon\". An empty alias removes it. Affects every transaction from that merchant." +
			WRITE_NOTE,
		inputSchema: {
			type: "object",
			properties: {
				aliases: {
					type: "array",
					maxItems: 100,
					items: {
						type: "object",
						properties: { merchant, alias: { type: "string", description: "Display name; empty to remove" } },
						required: ["merchant", "alias"],
					},
				},
			},
			required: ["aliases"],
		},
		annotations: write,
	},
	{
		name: "create_category",
		title: "Create category",
		description: "Add a new category." + WRITE_NOTE,
		inputSchema: {
			type: "object",
			properties: {
				name: { type: "string" },
				is_income: { type: "boolean", default: false, description: "True for a category of money received" },
			},
			required: ["name"],
		},
		annotations: create,
	},
	{
		name: "create_rule",
		title: "Create rule",
		description:
			"Add a smart rule that runs on each new transaction: when its conditions match, its actions apply. Conditions join with their logicalOperator (AND by default). Operators by field: AMOUNT LESS_THAN/GREATER_THAN/EQUALS (number); TYPE EQUALS/NOT_EQUALS (EXPENSE, INCOME, CREDIT, TRANSFER, INVESTMENT); MERCHANT, NARRATION, SMS_TEXT, CATEGORY, BANK_NAME CONTAINS/EQUALS/STARTS_WITH; TRANSACTION_HOUR EQUALS/LESS_THAN/GREATER_THAN (0-23); TRANSACTION_DAY_OF_MONTH EQUALS/IN/LESS_THAN/GREATER_THAN (1-31); TRANSACTION_DAY_OF_WEEK EQUALS/IN/NOT_EQUALS (1=Monday..7=Sunday); TRANSACTION_DATE EQUALS/LESS_THAN/GREATER_THAN (YYYY-MM-DD); ACCOUNT EQUALS/NOT_EQUALS (\"Bank||last4\"). Actions: CATEGORY SET (a category name) or CLEAR; TYPE SET (a type name); MERCHANT or NARRATION SET/APPEND/PREPEND/CLEAR; TAGS ADD_TAG/REMOVE_TAG. SMS_TEXT is matched on the phone; you never see SMS text." +
			WRITE_NOTE,
		inputSchema: {
			type: "object",
			properties: {
				name: { type: "string" },
				description: { type: "string" },
				priority: { type: "integer", default: 100, description: "Lower runs first" },
				conditions: {
					type: "array",
					minItems: 1,
					items: {
						type: "object",
						properties: {
							field,
							operator: { type: "string", enum: ["EQUALS", "NOT_EQUALS", "CONTAINS", "STARTS_WITH", "LESS_THAN", "GREATER_THAN", "LESS_THAN_OR_EQUAL", "GREATER_THAN_OR_EQUAL", "IN"] },
							value: { type: "string", description: "Comma-separated for IN" },
							logicalOperator: { type: "string", enum: ["AND", "OR"], default: "AND" },
						},
						required: ["field", "operator", "value"],
					},
				},
				actions: {
					type: "array",
					minItems: 1,
					items: {
						type: "object",
						properties: {
							field,
							actionType: { type: "string", enum: ["SET", "APPEND", "PREPEND", "CLEAR", "ADD_TAG", "REMOVE_TAG"] },
							value: { type: "string", description: "Empty for CLEAR" },
						},
						required: ["field", "actionType", "value"],
					},
				},
				apply_to_past: { type: "boolean", default: false, description: "Also run it now on past transactions still in Others" },
			},
			required: ["name", "conditions", "actions"],
		},
		annotations: create,
	},
] as const;

export const TOOL_NAMES: ReadonlySet<string> = new Set(TOOLS.map((t) => t.name));

export type ToolResult = { content: { type: "text"; text: string }[]; isError?: boolean; structuredContent?: unknown };

export const textResult = (text: string, isError = false): ToolResult => ({ content: [{ type: "text", text }], isError });

export const NOT_CONNECTED =
	"PennyWise isn't connected right now. On your phone, open PennyWise → Settings → Connect Claude / ChatGPT and start an AI session (or tap the PennyWise AI tile in Quick Settings), then ask again. If the session is on and the phone is online but this keeps happening, the connection may belong to an earlier install of PennyWise (for example after reinstalling): remove the PennyWise connector here and add it again.";
