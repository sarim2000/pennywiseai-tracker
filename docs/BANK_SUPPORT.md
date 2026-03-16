# Bank SMS Support Matrix

## Supported Banks & Transaction Patterns

| Bank | Credit | Debit | UPI Send | UPI Receive | ATM | Card Payment | Balance | Reference |
|------|--------|-------|----------|-------------|-----|--------------|---------|-----------|
| **HDFC Bank** | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅* | ✅ |
| **SBI** | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ | ✅ | ✅ |
| **ICICI Bank** | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅* | ✅ |
| **Axis Bank** | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ | ✅* | ✅ |
| **Indian Bank** | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ | ✅ | ✅ |
| **Federal Bank** | ✅ | ✅ | ✅ | ✅ | ❌ | ❌ | ✅* | ❌ |
| **PNB** | ✅ | ✅ | ❌ | ❌ | ✅ | ❌ | ✅ | ❌ |
| **IDBI Bank** | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ | ✅ | ✅ |
| **Karnataka Bank** | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ | ✅ | ✅ |
| **Canara Bank** | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ | ✅ | ✅ |
| **Bank of Baroda** | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ | ✅ | ✅ |
| **Jio Payments** | ✅ | ✅ | ✅ | ✅ | ❌ | ❌ | ✅ | ✅ |
| **Jupiter** | ✅ | ✅ | ✅ | ✅ | ❌ | ❌ | ✅* | ✅ |
| **Amazon Pay** | ✅ | ✅ | ✅ | ✅ | ❌ | ❌ | ✅* | ✅ |
| **IDFC First** | ✅ | ✅ | ✅ | ✅ | ❌ | ❌ | ✅ | ✅ |

*Uses default balance patterns (may have limited support)

---

## SMS Template Patterns by Bank

### HDFC Bank
| Type | Template Pattern | Example |
|------|-----------------|---------|
| **Credit** | `Rs.XXX credited to a/c **XXXX` | Rs.5000 credited to a/c **3456 on 15-01 |
| **Debit** | `Rs.XXX debited from a/c **XXXX` | Rs.2000 debited from a/c **3456 on 15-01 |
| **UPI Send** | `Rs.XXX debited for UPI/` | Rs.500 debited for UPI/PhonePe/merchant@ybl |
| **ATM** | `debited from a/c at ATM` | Rs.3000 debited from a/c **3456 at ATM |
| **Card** | `Rs.XXX spent on Credit Card` | Rs.1500 spent on HDFC Credit Card XX1234 |

### State Bank of India (SBI)
| Type | Template Pattern | Example |
|------|-----------------|---------|
| **Credit** | `Rs.XXX credited to A/c` | Rs.5000 credited to A/c XX1234 |
| **Credit** | `A/c XXXX-credited by Rs.XXX` | A/c X9338-credited by Rs.500 |
| **Credit** | `has credit for MERCHANT of Rs XXX` | has credit for SHOP of Rs 10,000.00 |
| **Credit** | `has a credit by Cheque of Rs XXX` | has a credit by Cheque of Rs 12,07,000.00 |
| **Credit** | `Credited INR XXX` | Credited INR 9,000.00 |
| **Debit** | `Rs.XXX debited from A/c` | Rs.2000 debited from A/c XX1234 |
| **Debit** | `A/C XXXX debited by XXX` | A/C X9474 debited by 370.0 |
| **UPI Send** | `trf to MERCHANT` | debited by 100 trf to SHOPKEEPER |
| **UPI Receive** | `transfer from SENDER` | credited by Rs.500 transfer from PERSON |
| **ATM** | `ATM withdrawal of Rs.XXX` | ATM withdrawal of Rs.2000 |
| **YONO Cash** | `Yono Cash Rs.XXX w/d@` | Yono Cash Rs.3000 w/d@SBI ATM |

### ICICI Bank
| Type | Template Pattern | Example |
|------|-----------------|---------|
| **Credit** | `INR XXX credited to A/c` | INR 5000 credited to A/c XX456 |
| **Debit** | `Acct XXXX debited with INR` | Acct XX456 debited with INR 2000 |
| **UPI** | `linked to VPA` | Rs 500 debited from A/c linked to VPA merchant@icici |
| **Card** | `Transaction of INR XXX on Card` | Transaction of INR 1500 on Credit Card XX1234 |

### Axis Bank
| Type | Template Pattern | Example |
|------|-----------------|---------|
| **Credit** | `A/c no. XXXX credited with Rs` | A/c no. XX789 credited with Rs 5000 |
| **Debit** | `A/c no. XXXX debited for Rs` | A/c no. XX789 debited for Rs 2000 |
| **UPI** | `transferred from A/c to UPI:` | Rs.500 transferred from A/c to UPI: merchant@axl |
| **ATM** | `withdrawn using Debit Card` | Rs.3000 withdrawn using Debit Card XX1234 |

### Indian Bank
| Type | Template Pattern | Example |
|------|-----------------|---------|
| **Credit** | `Rs.XXX credited to a/c` | Rs.589.00 credited to a/c *3829 |
| **Credit** | `credited Rs. XXX` | Your a/c credited Rs. 5000.00 |
| **Debit** | `debited Rs. XXX` | Your a/c debited Rs. 2000.00 |
| **UPI** | `linked to VPA XXX@XXX` | by a/c linked to VPA 7970282159-2@axl |
| **ATM** | `withdrawn Rs. XXX` | Your a/c withdrawn Rs. 2000 at ATM |

### Federal Bank
| Type | Template Pattern | Example |
|------|-----------------|---------|
| **Credit** | `credited by Rs.XXX` | Federal Bank A/c credited by Rs.5000 |
| **Debit** | `debited by Rs.XXX` | Federal Bank A/c debited by Rs.2000 |
| **UPI** | `transferred via UPI` | Rs.500 transferred via UPI to merchant@federal |

### Punjab National Bank (PNB)
| Type | Template Pattern | Example |
|------|-----------------|---------|
| **Credit** | `credited by Rs XXX` | PNB A/c XX5678 credited by Rs 3000 |
| **Debit** | `Ac XX1234 Debited with Rs.XXX` | Ac XX1234 Debited with Rs.5000.00 |
| **Debit** | `initial amount of Rs. 2.00 has been debited` | auto pay facility... An initial amount of Rs. 2.00 has been debited |
| **Mandate** | `UPI-Mandate is successfully created` | Your UPI-Mandate is successfully created towards Google |
| **Debit** | `debited with Rs.XXX thru card XX1234` | debited with Rs.5000.00 thru card XX9239 |
| **ATM** | `ATM withdrawal Rs XXX` | ATM withdrawal Rs 2000 from A/c XX5678 |

### IDFC First Bank
| Type | Template Pattern | Example |
|------|-----------------|---------|
| **Credit** | `Credited Rs XXX to A/c` | Credited Rs 5000 to A/c XX9876 |
| **Debit** | `Debited Rs XXX from A/c` | Debited Rs 2500 from A/c XX9876 |
| **UPI** | `UPI payment of Rs XXX` | UPI payment of Rs 500 from A/c XX9876 |

---

## Sender ID Patterns

| Bank | Common Sender IDs |
|------|-------------------|
| **HDFC** | `HDFC`, `HDFCBK`, `XX-HDFC-S`, `XX-HDFCBK-S` |
| **SBI** | `SBI`, `SBIBK`, `SBIUPI`, `XX-SBIBK-S`, `XX-SBI-S` |
| **ICICI** | `ICICI`, `ICICIB`, `XX-ICICI-S`, `XX-ICICIB-S` |
| **Axis** | `AXIS`, `AXISBK`, `XX-AXIS-S`, `XX-AXISBK-S` |
| **Indian Bank** | `INDBNK`, `INDIAN`, `XX-INDBNK-S` (e.g., `AD-INDBNK-S`) |
| **Federal** | `FEDERA`, `FEDERALBNK`, `XX-FEDERA-S` |
| **PNB** | `PNB`, `PNBSMS`, `XX-PNB-S`, `XX-PNBSMS-S` |
| **IDBI** | `IDBI`, `IDBIBNK`, `XX-IDBI-S` |
| **Karnataka** | `KARNBK`, `KARNATAKA`, `XX-KARNBK-S` |
| **Canara** | `CANBNK`, `CANARA`, `XX-CANBNK-S` |
| **BOB** | `BOB`, `BOBBNK`, `XX-BOB-S` |
| **Jio** | `JIOBK`, `JIOPAY`, `XX-JIOBK-S` |
| **Jupiter** | `JUSPAY`, `JUPITER`, `XX-JUSPAY-S` |
| **Amazon Pay** | `AMZNPY`, `AMAZONPAY`, `XX-AMZNPY-S` |
| **IDFC First** | `IDFCFB`, `IDFCFBANK`, `XX-IDFCFB-S` |

---

## Balance Extraction Patterns

### Banks with Custom Balance Patterns

| Bank | Balance Pattern | Example |
|------|----------------|---------|
| **SBI** | `Avl Bal Rs XXX` | Avl Bal Rs 10000.00 |
| **SBI** | `Available Balance: Rs XXX` | Available Balance: Rs 25000 |
| **SBI** | `Bal: Rs XXX` | Bal: Rs 5000 |
| **Indian Bank** | `Bal Rs. XXX` | Bal Rs. 50000.00 |
| **Indian Bank** | `Available Balance: Rs. XXX` | Available Balance: Rs. 25000 |
| **PNB** | `Aval Bal Rs.XX.XX CR` or `Bal XX.XX CR` | Aval Bal Rs.27000.00 CR |
| **IDBI** | `Bal Rs XXX` | Bal Rs 3694.38 |
| **IDFC First** | `New Bal :INR XXX` | New Bal :INR 15000.00 |
| **Karnataka Bank** | `Balance is Rs.XXX` | Balance is Rs.705.92 |
| **Canara Bank** | `Total Avail.bal INR XXX` | Total Avail.bal INR 1,092.62 |
| **Bank of Baroda** | `AvlBal:RsXXX` | AvlBal:Rs10500.50 |
| **Jio Payments** | `Avl. Bal: Rs. XXX` | Avl. Bal: Rs. 9095.5 |

### Banks Using Default Patterns
These banks rely on the base parser patterns which look for:
- `Bal:`, `Balance:`, `Avl Bal`, `Available Balance` followed by amount

| Bank | Status |
|------|--------|
| **HDFC Bank** | Uses default patterns |
| **ICICI Bank** | Uses default patterns |
| **Axis Bank** | Uses default patterns |
| **Federal Bank** | Uses default patterns |
| **Jupiter** | Uses default patterns |
| **Amazon Pay** | Uses default patterns |

---

## Data Extraction Capabilities

| Field | Description | Banks Supporting |
|-------|-------------|------------------|
| **Amount** | Transaction amount | All banks ✅ |
| **Type** | Credit/Debit/Income/Expense | All banks ✅ |
| **Account** | Last 4 digits of account | All banks ✅ |
| **Merchant** | Merchant/Sender name | All banks ✅ |
| **Balance** | Available balance after transaction | Most banks (except ICICI) |
| **Reference** | Transaction reference/ID | Most banks (except Federal) |
| **UPI ID** | VPA for UPI transactions | HDFC, SBI, ICICI, Axis, Indian |
| **Card Number** | Last 4 digits of card | HDFC, ICICI, PNB |
| **ATM Location** | ATM withdrawal location | HDFC, SBI, Axis, Indian, PNB |
| **Date/Time** | Transaction timestamp | Parsed from SMS timestamp |

---

## Quick Implementation Guide

To add a new bank:

1. **Create Parser Class**
```kotlin
class NewBankParser : BankParser() {
    override fun getBankName() = "New Bank"
    override fun canHandle(sender: String): Boolean
    override fun parse(smsBody: String, sender: String, timestamp: Long): ParsedTransaction?
}
```

2. **Register in Factory**
```kotlin
// In BankParserFactory.kt
val parsers = listOf(
    // ... existing parsers
    NewBankParser()
)
```

3. **Test with Real SMS**
- Test credit transactions
- Test debit transactions
- Test UPI transactions
- Test edge cases

---

*Last Updated: January 2025*