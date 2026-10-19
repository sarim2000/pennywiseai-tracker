# PennyWise

A privacy-first expense tracker that reads transactions from bank SMS on the device and helps people plan what they spend and save.

## Planning

**Budget**:
A spending cap for a period that repeats (weekly, monthly) or runs once, over chosen categories.
_Avoid_: Limit (as a noun for the whole thing), plan

**Goal**:
An amount someone wants to have set aside by a chosen date, such as a yearly insurance bill or a trip.
_Avoid_: Savings target, sinking fund, budget target

**Target amount**:
How much a Goal needs in total.
_Avoid_: Goal amount, limit

**Due date**:
The date by which a Goal's target amount should be set aside.
_Avoid_: Deadline, end date, save-by date

**Already saved**:
The amount someone has put aside for a Goal before creating it, counted from day one.
_Avoid_: Opening balance, initial deposit

**Contribution**:
An amount the user adds to a Goal, either typed in or taken from a transaction; it is never added automatically.
_Avoid_: Deposit, allocation, saving

**Withdrawal**:
An amount the user takes out of a Goal, lowering how much is saved toward it.
_Avoid_: Debit, refund

**Saved**:
A Goal's Already saved amount plus its Contributions minus its Withdrawals, in the Goal's one currency.
_Avoid_: Balance, progress amount

**Behind**:
How far a Goal's Saved amount trails an even pace from the day it was created to its Due date.
_Avoid_: Shortfall, deficit, overdue

**Active goal**:
A Goal that is neither Reached nor Archived.
_Avoid_: Open goal, running goal

**Reached**:
A Goal whose money set aside meets its target amount.
_Avoid_: Completed, done, achieved

**Missed**:
A Goal whose due date has passed before it was Reached.
_Avoid_: Failed, expired, overdue

**Archived**:
A Goal the user has put away; it no longer shows with the others.
_Avoid_: Deleted, closed

## AI access

**AI connection**:
The lasting link between one PennyWise install and one AI app (for example Claude or ChatGPT), created once by pairing and removed by revoking it in PennyWise.
_Avoid_: token, MCP, integration

**AI session**:
A time-limited window, started by the user on the phone, during which connected AI apps can read PennyWise data. Outside an AI session no AI app can reach the data.
_Avoid_: web access, server on, live mode

**Pairing code**:
A short-lived 6-digit code shown in PennyWise that the user enters in an AI app's sign-in step to create an AI connection.
_Avoid_: token, password, API key

**Relay**:
The PennyWise-run service that passes an AI app's questions to the phone and the phone's answers back. It stores and logs none of them.
_Avoid_: cloud, backend, server
