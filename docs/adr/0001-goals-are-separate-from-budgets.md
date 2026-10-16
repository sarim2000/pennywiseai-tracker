# Goals are a separate concept from Budgets

A Budget caps spending inside repeating windows by category; a Goal accumulates money set aside toward one target amount by one due date. We considered storing Goals as Budget groups of the existing `TARGET` type with a custom period, which would have reused the table and backup format, but every Budget calculation is windowed and category-based, so Goals would have fought that logic everywhere. Goals therefore get their own table and backup section, and only share UI pieces (progress ring, cards) and the Budgets screen with Budgets.
