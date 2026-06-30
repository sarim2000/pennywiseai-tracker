package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.ParsedTransaction

/**
 * Factory for creating bank-specific parsers based on SMS sender.
 */
object BankParserFactory {

    private val parsers = listOf(
        HDFCMutualFundParser(),  // HDFC Mutual Fund (must be before HDFCBankParser to avoid interception by HDFC's broad DLT pattern)
        NaviMutualFundParser(),  // Navi Mutual Fund (AMC SIP / unit-allotment SMS — NAVAMC sender)
        HDFCBankParser(),
        NepalSBIBankParser(),      // Nepal SBI Bank (Nepal) — NSBI_ALERT (must be before SBIBankParser to avoid SBI-match on NSBI sender)
        SBIBankParser(),
        SaraswatBankParser(),
        DBSBankParser(),
        IndianBankParser(),
        FederalBankParser(),
        JuspayParser(),
        CashfreeParser(),  // Cashfree payment gateway (India) — grouped with other aggregators
        SliceParser(),
        CredParser(),
        LazyPayParser(),
        UtkarshBankParser(),
        ICICIBankParser(),
        KarnatakaBankParser(),
        KeralaGraminBankParser(),
        KeralaBankParser(),  // Kerala State Co-operative Bank (India) — KELBNK; distinct from Kerala Gramin (KGBANK)
        IDBIBankParser(),
        JupiterBankParser(),
        AxisBankParser(),
        PNBBankParser(),
        PunjabSindBankParser(),  // Punjab & Sind Bank (India)
        CanaraBankParser(),
        BankOfBarodaParser(),
        BankOfIndiaParser(),
        JioPaymentsBankParser(),
        NSDLPaymentsBankParser(),  // NSDL Payments Bank (India) — legacy NSDLPB sender, rebranded to Jio Payments Bank
        JanaSmallFinanceBankParser(),  // Jana Small Finance Bank (India) — JANABK sender
        KotakBankParser(),
        IDFCFirstBankParser(),
        UnionBankParser(),
        HSBCBankParser(),
        CentralBankOfIndiaParser(),
        SouthIndianBankParser(),
        JKBankParser(),
        JioPayParser(),
        IPPBParser(),
        CityUnionBankParser(),
        IndianOverseasBankParser(),
        AirtelPaymentsBankParser(),
        IndusIndBankParser(),
        AMEXBankParser(),
        OneCardParser(),
        UCOBankParser(),
        AUBankParser(),
        YesBankParser(),
        BandhanBankParser(),
        ADCBParser(),  // Abu Dhabi Commercial Bank (UAE)
        FABParser(),  // First Abu Dhabi Bank (UAE)
        EmiratesNBDParser(),  // Emirates NBD Bank (UAE)
        EmiratesIslamicParser(),  // Emirates Islamic Bank (UAE)
        LivBankParser(),  // Liv Bank (UAE)
        CitiBankParser(),  // Citi Bank (USA)
        DiscoverCardParser(),  // Discover Card (USA)
        OldHickoryParser(),  // Old Hickory Credit Union (USA)
        LaxmiBankParser(),  // Laxmi Sunrise Bank (Nepal)
        CBEBankParser(),  // Commercial Bank of Ethiopia
        AltanaFCUParser(),  // Altana Federal Credit Union (USA) — must precede EverestBank, which greedily claims numeric senders
        StandardBankMozambiqueParser(),  // Standard Bank Mozambique — must precede EverestBank (shortcode 7832265 is a 7-digit numeric sender)
        EMolaParser(),  // eMola (Mozambique)
        MillenniumBimParser(),  // Millennium BIM (Mozambique)
        EverestBankParser(),  // Everest Bank (Nepal)
        BancolombiaParser(),  // Bancolombia (Colombia)
        MashreqBankParser(),  // Mashreq Bank (UAE)
        CharlesSchwabParser(),  // Charles Schwab (USA)
        NavyFederalParser(),  // Navy Federal Credit Union (USA)
        AdelFiParser(),  // AdelFi Credit Union (USA)
        AlecuBankParser(),  // ALECU Credit Union (USA)
        PriorbankParser(),  // Priorbank (Belarus)
        AlinmaBankParser(),  // Alinma Bank (Saudi Arabia)
        NabilBankParser(),  // Nabil Bank (Nepal) — must be before NMBBankParser (NMB handles NABIL senders broadly)
        NMBTanzaniaParser(),  // NMB Bank Plc (Tanzania) — shares NMB sender with Nepal NMB; must precede it so content-aware dispatch tries TZS/Swahili first
        NMBBankParser(),  // NMB Bank / Nabil Bank (Nepal)
        ManjushreeFinanceParser(), // Manjushree Finance (Nepal)
        CitizensBankParser(),      // Citizens Bank International (Nepal) — CTZN_ALERT
        MachchhapuchreBankParser(),  // Machchhapuchchhre Bank (Nepal) — MBL_ALERT
        StandardCharteredNepalParser(),  // Standard Chartered Bank Nepal (Nepal) — SC_ALERT
        LumbiniBikashBankParser(),  // Lumbini Bikash Bank (Nepal) — LBBL_SMART
        NepalBankParser(),  // Nepal Bank Limited (Nepal) — NBL_Alert
        SiddharthaBankParser(),  // Siddhartha Bank Limited (Nepal)
        PrimeCommercialBankParser(),  // Prime Commercial Bank (Nepal)
        MPesaMozambiqueParser(),  // M-Pesa Mozambique (must be before Tanzania & Kenya; gates on Portuguese "Confirmado" + "MT")
        MPesaTanzaniaParser(),  // M-Pesa Tanzania (must be before Kenya M-PESA)
        MPESAParser(),  // M-PESA (Kenya)
        SelcomPesaParser(),  // Selcom Pesa (Tanzania)
        CrdbBankParser(),  // CRDB Bank (Tanzania) — bilingual Swahili/English, multi-currency cards
        MixxByYasParser(),  // Mixx by Yas (Tanzania) — must precede TigoPesaParser (shares MIXXBYYAS sender; claims newer Mixx-specific formats)
        TigoPesaParser(),  // Tigo Pesa / Mixx by Yas (Tanzania)
        DiamondTrustBankParser(),  // Diamond Trust Bank (DTB) Tanzania
        CIBEgyptParser(),  // CIB - Commercial International Bank (Egypt)
        ArabBankParser(),  // Arab Bank (Egypt) — multi-currency card, English + Arabic SMS
        DhanlaxmiBankParser(),  // Dhanlaxmi Bank (India)
        DOPBankParser(),  // Department of Post (India)
        HuntingtonBankParser(),  // Huntington Bank (USA)
        StandardCharteredBankParser(),  // Standard Chartered Bank (India and Pakistan)
        EquitasBankParser(),  // Equitas Small Finance Bank (India)
        TelebirrParser(),  // Telebirr (Ethiopia)
        ZemenBankParser(),  // Zemen Bank (Ethiopia)
        DashenBankParser(),  // Dashen Bank (Ethiopia)
        FaysalBankParser(),  // Faysal Bank (Pakistan)
        MelliBankParser(),  // Melli Bank (Iran)
        MellatBankParser(), // Mellat Bank (Iran)
        ParsianBankParser(),  // Parsian Bank (Iran)
        BankinoBankParser(),  // Bankino / Middle East Bank (Iran)
        BluBankParser(),  // blu Bank (Iran)
        BangkokBankParser(),  // Bangkok Bank (Thailand)
        KasikornBankParser(),  // Kasikorn Bank (Thailand)
        SiamCommercialBankParser(),  // Siam Commercial Bank (Thailand)
        KrungThaiBankParser(),  // Krungthai Bank (Thailand)
        KrungsriBankParser(),  // Krungsri / Bank of Ayudhya (Thailand)
        TTBBankParser(),  // TMBThanachart Bank (Thailand)
        GSBBankParser(),  // Government Savings Bank (Thailand)
        BAACBankParser(),  // BAAC (Thailand)
        UOBThailandParser(),  // UOB Thailand
        CIMBThaiParser(),  // CIMB Thai (Thailand)
        KTCCreditCardParser(),  // KTC Credit Card (Thailand)
        TBankParser(),  // T-Bank / Tinkoff (Russia)
        ChaseBankParser(),  // Chase Bank (USA)
        AlRajhiBankParser(),  // Al Rajhi Bank (Saudi Arabia)
        SNBAlAhliBankParser(),  // Saudi National Bank / Al Ahli Bank (Saudi Arabia)
        STCBankParser(),  // STC Bank (Saudi Arabia)
        SabbBankParser(),  // SABB - Saudi Awwal Bank (Saudi Arabia)
        MBankCZParser(),  // mBank CZ (Czech Republic)
        SparkasseRheinMaasParser(),  // Sparkasse Rhein-Maas (Germany)
        EnparaBankParser(),  // Enpara (Turkey) — push notifications
        BankMuscatParser(),  // Bank Muscat (Oman)
        GreaterBankParser(),  // Greater Bank (India)
        BPCEParser(),  // BPCE (France)
        SampathBankParser(),  // Sampath Bank (Sri Lanka)
        AccessBankParser(),  // Access Bank (Nigeria)
        ZenithBankParser(),  // Zenith Bank (Nigeria)
        KeystoneBankParser(),  // Keystone Bank (Nigeria)
        JaizBankParser(),  // Jaiz Bank (Nigeria)
        OpayBankParser()  // Opay (Nigeria)
        // Add more bank parsers here as we implement them
    )

    /**
     * Returns the appropriate bank parser for the given sender.
     * Returns null if no specific parser is found.
     */
    fun getParser(sender: String): BankParser? {
        return parsers.firstOrNull { it.canHandle(sender) }
    }

    /**
     * Returns every parser whose canHandle matches the sender.
     * Multiple parsers can share a sender (e.g. M-Pesa Kenya/Tanzania/Mozambique);
     * content-aware dispatch in [parse] picks the right one.
     */
    fun getParsers(sender: String): List<BankParser> = parsers.filter { it.canHandle(sender) }

    /**
     * Content-aware dispatch: tries every canHandle-matching parser and returns the
     * first non-null parse(). This un-shadows parsers that share a sender ID — each
     * parser gates its own parse() by message content and returns null otherwise.
     */
    fun parse(smsBody: String, sender: String, timestamp: Long): ParsedTransaction? =
        getParsers(sender).firstNotNullOfOrNull { it.parse(smsBody, sender, timestamp) }

    /**
     * Returns the bank parser for the given bank name.
     * Returns null if no specific parser is found.
     */
    fun getParserByName(bankName: String): BankParser? {
        return parsers.firstOrNull { it.getBankName() == bankName }
    }

    /**
     * Returns all available bank parsers.
     */
    fun getAllParsers(): List<BankParser> = parsers

    /**
     * Checks if the sender belongs to any known bank.
     */
    fun isKnownBankSender(sender: String): Boolean {
        return parsers.any { it.canHandle(sender) }
    }
}
