package com.pennywiseai.parser.core.bank

/**
 * Factory for creating bank-specific parsers based on SMS sender.
 */
object BankParserFactory {

    private val parsers = listOf(
        HDFCBankParser(),
        SBIBankParser(),
        SaraswatBankParser(),
        DBSBankParser(),
        IndianBankParser(),
        FederalBankParser(),
        JuspayParser(),
        SliceParser(),
        LazyPayParser(),
        UtkarshBankParser(),
        ICICIBankParser(),
        KarnatakaBankParser(),
        KeralaGraminBankParser(),
        IDBIBankParser(),
        JupiterBankParser(),
        AxisBankParser(),
        PNBBankParser(),
        CanaraBankParser(),
        BankOfBarodaParser(),
        BankOfIndiaParser(),
        JioPaymentsBankParser(),
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
        LivBankParser(),  // Liv Bank (UAE)
        CitiBankParser(),  // Citi Bank (USA)
        DiscoverCardParser(),  // Discover Card (USA)
        OldHickoryParser(),  // Old Hickory Credit Union (USA)
        LaxmiBankParser(),  // Laxmi Sunrise Bank (Nepal)
        CBEBankParser(),  // Commercial Bank of Ethiopia
        EverestBankParser(),  // Everest Bank (Nepal)
        BancolombiaParser(),  // Bancolombia (Colombia)
        MashreqBankParser(),  // Mashreq Bank (UAE)
        CharlesSchwabParser(),  // Charles Schwab (USA)
        NavyFederalParser(),  // Navy Federal Credit Union (USA)
        AdelFiParser(),  // AdelFi Credit Union (USA)
        PriorbankParser(),  // Priorbank (Belarus)
        AlinmaBankParser(),  // Alinma Bank (Saudi Arabia)
        NMBBankParser(),  // NMB Bank / Nabil Bank (Nepal)
        SiddharthaBankParser(),  // Siddhartha Bank Limited (Nepal)
        MPesaTanzaniaParser(),  // M-Pesa Tanzania (must be before Kenya M-PESA)
        MPESAParser(),  // M-PESA (Kenya)
        SelcomPesaParser(),  // Selcom Pesa (Tanzania)
        TigoPesaParser(),  // Tigo Pesa / Mixx by Yas (Tanzania)
        CIBEgyptParser(),  // CIB - Commercial International Bank (Egypt)
        DhanlaxmiBankParser(),  // Dhanlaxmi Bank (India)
        HuntingtonBankParser(),  // Huntington Bank (USA)
        StandardCharteredBankParser(),  // Standard Chartered Bank (India and Pakistan)
        EquitasBankParser(),  // Equitas Small Finance Bank (India)
        TelebirrParser(),  // Telebirr (Ethiopia)
        ZemenBankParser(),  // Zemen Bank (Ethiopia)
        DashenBankParser(),  // Dashen Bank (Ethiopia)
        FaysalBankParser(),  // Faysal Bank (Pakistan)
        MelliBankParser(),  // Melli Bank (Iran)
        ParsianBankParser(),  // Parsian Bank (Iran)
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
        KTCCreditCardParser()  // KTC Credit Card (Thailand)
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
