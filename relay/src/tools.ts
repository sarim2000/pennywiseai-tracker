// The tools the phone answers. The Worker advertises them; only the phone runs them.
const date = { type: "string", description: "Date as YYYY-MM-DD" };

const readOnly = { readOnlyHint: true, destructiveHint: false, idempotentHint: true, openWorldHint: false };
// Writes: MCP clients ask the user before calling these. The phone also refuses them unless
// the user turned on "Let AI apps make changes".
const write = { readOnlyHint: false, destructiveHint: false, idempotentHint: true, openWorldHint: false };
const create = { ...write, idempotentHint: false };
const destroy = { ...write, destructiveHint: true };
const merchant = { type: "string", description: "Merchant exactly as shown in search_transactions results (the merchant field, not the alias)" };
// Names match ignoring case; a name that matches nothing leaves nothing out.
const tag = {
	anyOf: [{ type: "string" }, { type: "array", items: { type: "string" } }],
	description: "Only transactions with this tag: a single tag name, or a list of names to match any of them",
};
const excludeCategories = { type: "array", items: { type: "string" }, description: "Leave out transactions in these categories" };
const excludeTags = { type: "array", items: { type: "string" }, description: "Leave out transactions with any of these tags" };
const field = {
	type: "string",
	enum: ["AMOUNT", "TYPE", "CATEGORY", "MERCHANT", "NARRATION", "SMS_TEXT", "BANK_NAME", "TRANSACTION_HOUR", "TRANSACTION_TIME", "TRANSACTION_DAY_OF_WEEK", "TRANSACTION_DAY_OF_MONTH", "TRANSACTION_DATE", "ACCOUNT", "TAGS"],
};
const ruleId = { type: "string", description: "The rule's id, from list_rules" };
const applyToPast = {
	type: "string",
	enum: ["none", "uncategorized", "all"],
	default: "none",
	description: "Also run the rule now on past transactions: none, only those still in Others (uncategorized), or all. The result says how many changed.",
};
const dryRun = { type: "boolean", default: false, description: "Change nothing: report how many past transactions apply_to_past would change, so the user can confirm first" };
const conditions = {
	type: "array",
	minItems: 1,
	items: {
		type: "object",
		properties: {
			field,
			operator: { type: "string", enum: ["EQUALS", "NOT_EQUALS", "CONTAINS", "NOT_CONTAINS", "STARTS_WITH", "LESS_THAN", "GREATER_THAN", "LESS_THAN_OR_EQUAL", "GREATER_THAN_OR_EQUAL", "IN", "NOT_IN"] },
			value: { type: "string", description: "Comma-separated for IN and NOT_IN" },
			logicalOperator: { type: "string", enum: ["AND", "OR"], default: "AND", description: "How this condition joins the ones before it" },
		},
		required: ["field", "operator", "value"],
	},
};
const actions = {
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
};
const RULE_OPERATORS =
	" Operators by field: AMOUNT LESS_THAN/GREATER_THAN/EQUALS (number); TYPE EQUALS/NOT_EQUALS (EXPENSE, INCOME, CREDIT, TRANSFER, INVESTMENT); MERCHANT, NARRATION, SMS_TEXT, CATEGORY, BANK_NAME CONTAINS/NOT_CONTAINS/EQUALS/NOT_EQUALS/STARTS_WITH/IN/NOT_IN (IN and NOT_IN take a comma-separated list, e.g. MERCHANT IN \"Swiggy, Zomato\"; NOT_CONTAINS e.g. MERCHANT NOT_CONTAINS \"Refund\"); TRANSACTION_HOUR EQUALS/LESS_THAN/GREATER_THAN (0-23); TRANSACTION_DAY_OF_MONTH EQUALS/IN/NOT_IN/LESS_THAN/GREATER_THAN (1-31); TRANSACTION_DAY_OF_WEEK EQUALS/IN/NOT_IN/NOT_EQUALS (1=Monday..7=Sunday); TRANSACTION_DATE EQUALS/LESS_THAN/GREATER_THAN/LESS_THAN_OR_EQUAL/GREATER_THAN_OR_EQUAL (YYYY-MM-DD); ACCOUNT EQUALS/NOT_EQUALS (\"Bank||last4\"). There is no BETWEEN: between two dates is two TRANSACTION_DATE conditions, the second with logicalOperator AND, e.g. GREATER_THAN_OR_EQUAL 2026-04-01 then LESS_THAN_OR_EQUAL 2026-04-30. Actions: CATEGORY SET (a category name) or CLEAR; TYPE SET (a type name); MERCHANT or NARRATION SET/APPEND/PREPEND/CLEAR; TAGS ADD_TAG/REMOVE_TAG. SMS_TEXT is matched on the phone; you never see SMS text.";
const WRITE_NOTE = " Changes data: only works while the user has turned on Let AI apps make changes in PennyWise.";

export const TOOLS = [
	{
		name: "search_transactions",
		title: "Search transactions",
		description:
			"Find the user's transactions in a date range, optionally filtered by text, category, tag or type, or leaving out categories or tags. Amounts are returned with their currency; never add amounts in different currencies together.",
		inputSchema: {
			type: "object",
			properties: {
				from: date,
				to: date,
				query: { type: "string", description: "Words to match in the merchant or its alias" },
				category: { type: "string", description: "Category name, e.g. Food & Dining" },
				tag,
				exclude_categories: excludeCategories,
				exclude_tags: excludeTags,
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
			"Totals for a date range grouped by category, merchant, month, day, account, or month and category together, optionally for one tag or leaving out categories or tags. Merchant groups use the merchant's alias when it has one, so aliased merchants total together. EXPENSE means spending, including card purchases. Each total is per currency; the result never mixes currencies.",
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
				tag,
				exclude_categories: excludeCategories,
				exclude_tags: excludeTags,
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
		description: "The user's own smart rules (built-in templates excluded), in the same shape create_rule takes, each with an id for update_rule and delete_rule. Rules run on each new transaction as it arrives.",
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
			"Add a smart rule that runs on each new transaction: when its conditions match, its actions apply. Conditions join with their logicalOperator (AND by default)." +
			RULE_OPERATORS +
			WRITE_NOTE,
		inputSchema: {
			type: "object",
			properties: {
				name: { type: "string" },
				description: { type: "string" },
				priority: { type: "integer", default: 100, description: "Lower runs first" },
				conditions,
				actions,
				apply_to_past: applyToPast,
				dry_run: dryRun,
			},
			required: ["name", "conditions", "actions"],
		},
		annotations: create,
	},
	{
		name: "update_rule",
		title: "Update rule",
		description:
			"Change one of the user's own rules, found by its id from list_rules. Only the fields you send change; conditions or actions, if sent, replace that whole list. SMS text conditions are hidden in list_rules, so to keep one, leave conditions out." +
			RULE_OPERATORS +
			WRITE_NOTE,
		inputSchema: {
			type: "object",
			properties: {
				id: ruleId,
				name: { type: "string" },
				description: { type: "string", description: "Empty to remove" },
				priority: { type: "integer", description: "Lower runs first" },
				conditions,
				actions,
				apply_to_past: applyToPast,
				dry_run: dryRun,
			},
			required: ["id"],
		},
		annotations: write,
	},
	{
		name: "delete_rule",
		title: "Delete rule",
		description:
			"Delete one of the user's own rules, found by its id from list_rules. It stops running on new transactions; what it already changed on past transactions stays. Built-in rules can't be deleted." +
			WRITE_NOTE,
		inputSchema: { type: "object", properties: { id: ruleId }, required: ["id"] },
		annotations: destroy,
	},
] as const;

export const TOOL_NAMES: ReadonlySet<string> = new Set(TOOLS.map((t) => t.name));

export type ToolResult = { content: { type: "text"; text: string }[]; isError?: boolean; structuredContent?: unknown };

export const textResult = (text: string, isError = false): ToolResult => ({ content: [{ type: "text", text }], isError });

export const NOT_CONNECTED =
	"PennyWise isn't connected right now. On your phone, open PennyWise → Settings → Connect Claude / ChatGPT and start an AI session (or tap the PennyWise AI tile in Quick Settings), then ask again. If the session is on and the phone is online but this keeps happening, the connection may belong to an earlier install of PennyWise (for example after reinstalling): remove the PennyWise connector here and add it again.";

const FILTER_KEYS = ["tag", "exclude_categories", "exclude_tags"];
// The app reads a filter as a non-blank JSON string or a list of them; anything else is no filter there either.
// Has a visible character: the same set the app's isName uses (whitespace, control characters, BOM).
const isName = (v: unknown): boolean => typeof v === "string" && /[^\s\u0000-\u001f\u007f-\u009f\u180e\ufeff]/.test(v);
const nonEmpty = (v: unknown): boolean => (Array.isArray(v) ? v.some(isName) : isName(v));

/**
 * An older PennyWise app ignores the filter arguments and answers unfiltered. A newer one echoes
 * them back in a `filters` field, so a result for filtered arguments without it isn't passed on.
 */
export function requireFilterEcho(name: string, args: unknown, result: ToolResult): ToolResult {
	if ((name !== "search_transactions" && name !== "spending_summary") || result.isError) return result;
	const given = args as Record<string, unknown> | null;
	if (!FILTER_KEYS.some((k) => nonEmpty(given?.[k]))) return result;
	try {
		if ("filters" in JSON.parse(result.content[0].text)) return result;
	} catch {}
	return textResult("This filter needs a newer version of the PennyWise app. Update the app, or retry without tag, exclude_categories and exclude_tags.", true);
}
