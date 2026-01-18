package com.pennywiseai.parser.core

object CompiledPatterns {
    object Amount {
        val RS_PATTERN = Regex("""Rs\.?\s*([0-9,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE)
        val INR_PATTERN = Regex("""INR\s*([0-9,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE)
        val RUPEE_SYMBOL_PATTERN = Regex("""₹\s*([0-9,]+(?:\.\d{2})?)""")
        val ALL_PATTERNS = listOf(RS_PATTERN, INR_PATTERN, RUPEE_SYMBOL_PATTERN)
    }

    object Reference {
        val GENERIC_REF = Regex(
            """(?:Ref|Reference|Txn|Transaction)(?:\s+No)?[:\s]+([A-Z0-9]+)""",
            RegexOption.IGNORE_CASE
        )
        val UPI_REF = Regex("""UPI[:\s]+([0-9]+)""", RegexOption.IGNORE_CASE)
        val REF_NUMBER = Regex("""Reference\s+Number[:\s]+([A-Z0-9]+)""", RegexOption.IGNORE_CASE)
        val ALL_PATTERNS = listOf(GENERIC_REF, UPI_REF, REF_NUMBER)
    }

    object Account {
        val AC_WITH_MASK = Regex(
            """(?:A/c|Account|Acct)(?:\s+No)?\.?\s+(?:XX+|\*+)?(\d{3,4})""",
            RegexOption.IGNORE_CASE
        )
        val CARD_WITH_MASK = Regex("""Card\s+(?:XX+|\*+)?(\d{4})""", RegexOption.IGNORE_CASE)
        // GENERIC_ACCOUNT removed - it was too loose and caused false positives
        // by capturing dates, amounts, and reference numbers as account numbers.
        // Bank-specific parsers should define their own patterns instead.
        // Only use specific masked patterns that require XX or * prefix
        val ALL_PATTERNS = listOf(AC_WITH_MASK, CARD_WITH_MASK)
    }

    object Balance {
        val AVL_BAL_RS = Regex("""(?:Bal|Balance|Avl Bal|Available Balance)[:\s]+Rs\.?\s*([0-9,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE)
        val AVL_BAL_INR = Regex("""(?:Bal|Balance|Avl Bal|Available Balance)[:\s]+INR\s*([0-9,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE)
        val AVL_BAL_RUPEE = Regex("""(?:Bal|Balance|Avl Bal|Available Balance)[:\s]+₹\s*([0-9,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE)
        val AVL_BAL_NO_CURRENCY = Regex("""(?:Bal|Balance|Avl Bal|Available Balance)[:\s]+([0-9,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE)
        val UPDATED_BAL_RS = Regex("""(?:Updated Balance|Remaining Balance)[:\s]+Rs\.?\s*([0-9,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE)
        val UPDATED_BAL_INR = Regex("""(?:Updated Balance|Remaining Balance)[:\s]+INR\s*([0-9,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE)
        val ALL_PATTERNS = listOf(AVL_BAL_RS, AVL_BAL_INR, AVL_BAL_RUPEE, AVL_BAL_NO_CURRENCY, UPDATED_BAL_RS, UPDATED_BAL_INR)
    }

    object Merchant {
        val TO_PATTERN =
            Regex("""to\s+([^\.\n]+?)(?:\s+on|\s+at|\s+Ref|\s+UPI)""", RegexOption.IGNORE_CASE)
        val FROM_PATTERN =
            Regex("""from\s+([^\.\n]+?)(?:\s+on|\s+at|\s+Ref|\s+UPI)""", RegexOption.IGNORE_CASE)
        val AT_PATTERN = Regex("""at\s+([^\.\n]+?)(?:\s+on|\s+Ref)""", RegexOption.IGNORE_CASE)
        val FOR_PATTERN =
            Regex("""for\s+([^\.\n]+?)(?:\s+on|\s+at|\s+Ref)""", RegexOption.IGNORE_CASE)
        val ALL_PATTERNS = listOf(TO_PATTERN, FROM_PATTERN, AT_PATTERN, FOR_PATTERN)
    }

    object HDFC {
        val DLT_PATTERNS = listOf(
            Regex("^[A-Z]{2}-HDFCBK.*$"),
            Regex("^[A-Z]{2}-HDFC.*$"),
            Regex("^HDFC-[A-Z]+$"),
            Regex("^[A-Z]{2}-HDFCB.*$")
        )

        val SALARY_PATTERN = Regex(
            """for\s+[^-]+-[^-]+-[^-]+\s+[A-Z]+\s+SALARY-([^\.\n]+)""",
            RegexOption.IGNORE_CASE
        )
        val SIMPLE_SALARY_PATTERN =
            Regex("""SALARY[- ]([^\.\n]+?)(?:\s+Info|$)""", RegexOption.IGNORE_CASE)
        val INFO_PATTERN =
            Regex("""Info:\s*(?:UPI/)?([^/\.\n]+?)(?:/|$)""", RegexOption.IGNORE_CASE)
        val VPA_WITH_NAME = Regex("""VPA\s+[^@\s]+@[^\s]+\s*\(([^)]+)\)""", RegexOption.IGNORE_CASE)
        val VPA_PATTERN = Regex("""VPA\s+([^@\s]+)@""", RegexOption.IGNORE_CASE)
        val SPENT_PATTERN = Regex("""at\s+([^\.\n]+?)\s+on\s+\d{2}""", RegexOption.IGNORE_CASE)
        val DEBIT_FOR_PATTERN =
            Regex("""debited\s+for\s+([^\.\n]+?)\s+on\s+\d{2}""", RegexOption.IGNORE_CASE)
        val MANDATE_PATTERN =
            Regex("""To\s+([^\n]+?)\s*(?:\n|\d{2}/\d{2})""", RegexOption.IGNORE_CASE)

        val REF_SIMPLE = Regex("""Ref\s+(\d{9,12})""", RegexOption.IGNORE_CASE)
        val UPI_REF_NO = Regex("""UPI\s+Ref\s+No\s+(\d{12})""", RegexOption.IGNORE_CASE)
        val REF_NO = Regex("""Ref\s+No\.?\s+([A-Z0-9]+)""", RegexOption.IGNORE_CASE)
        val REF_END = Regex(
            """(?:Ref|Reference)[:.\s]+([A-Z0-9]{6,})(?:\s*$|\s*Not\s+You)""",
            RegexOption.IGNORE_CASE
        )

        val ACCOUNT_DEPOSITED = Regex(
            """deposited\s+in\s+(?:HDFC\s+Bank\s+)?A/c\s+(?:XX+)?(\d+)""",
            RegexOption.IGNORE_CASE
        )
        val ACCOUNT_FROM =
            Regex("""from\s+(?:HDFC\s+Bank\s+)?A/c\s+(?:XX+)?(\d+)""", RegexOption.IGNORE_CASE)
        val ACCOUNT_SIMPLE = Regex("""HDFC\s+Bank\s+A/c\s+(\d+)""", RegexOption.IGNORE_CASE)
        val ACCOUNT_GENERIC = Regex("""A/c\s+(?:XX+)?(\d+)""", RegexOption.IGNORE_CASE)

        val AMOUNT_WILL_DEDUCT = Regex(
            """Rs\.?\s*([0-9,]+(?:\.\d{2})?)\s+will\s+be\s+deducted""",
            RegexOption.IGNORE_CASE
        )
        val DEDUCTION_DATE = Regex(
            """deducted\s+on\s+(\d{2}/\d{2}/\d{2}),?\s*\d{2}:\d{2}:\d{2}""",
            RegexOption.IGNORE_CASE
        )
        val MANDATE_MERCHANT = Regex("""For\s+([^\n]+?)\s+mandate""", RegexOption.IGNORE_CASE)
        val UMN_PATTERN = Regex("""UMN\s+([a-zA-Z0-9@]+)""", RegexOption.IGNORE_CASE)
    }

    object Cleaning {
        val TRAILING_PARENTHESES = Regex("""\s*\(.*?\)\s*$""")
        val REF_NUMBER_SUFFIX = Regex("""\s+Ref\s+No.*""", RegexOption.IGNORE_CASE)
        val DATE_SUFFIX = Regex("""\s+on\s+\d{2}.*""")
        val UPI_SUFFIX = Regex("""\s+UPI.*""", RegexOption.IGNORE_CASE)
        val TIME_SUFFIX = Regex("""\s+at\s+\d{2}:\d{2}.*""")
        val TRAILING_DASH = Regex("""\s*-\s*$""")
        val PVT_LTD =
            Regex("""(\s+PVT\.?\s*LTD\.?|\s+PRIVATE\s+LIMITED)$""", RegexOption.IGNORE_CASE)
        val LTD = Regex("""(\s+LTD\.?|\s+LIMITED)$""", RegexOption.IGNORE_CASE)
    }

    object Currency {
        val ISO_CODE = Regex("""[A-Z]{3}""")
        val SPECIFIC_ISO = { code: String -> Regex(code, RegexOption.IGNORE_CASE) }
        val COMMON_CURRENCIES = Regex("""(?:INR|Rs\.?|₹|USD|EUR|GBP|AED|SAR)""", RegexOption.IGNORE_CASE)
    }

    object Date {
        // dd/MM/yy e.g. 20/10/25
        val DD_MM_YY = Regex("""\d{1,2}/\d{1,2}/\d{2}""")

        // dd/MM/yyyy e.g. 20/10/2025
        val DD_MM_YYYY = Regex("""\d{1,2}/\d{1,2}/\d{4}""")

        // dd-MMM-yy e.g. 20-OCT-25
        val DD_MMM_YY = Regex("""\d{1,2}-[A-Za-z]{3}-\d{2}""", RegexOption.IGNORE_CASE)

        // dd-MM-yyyy e.g. 20-10-2025
        val DD_MM_YYYY_DASH = Regex("""\d{1,2}-\d{1,2}-\d{4}""")
    }

    object Time {
        // HH:mm:ss
        val HH_MM_SS = Regex("""\d{1,2}:\d{2}:\d{2}""")

        // HH:mm
        val HH_MM = Regex("""\d{1,2}:\d{2}""")
    }
}


