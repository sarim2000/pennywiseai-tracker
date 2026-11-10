package com.pennywiseai.tracker.di

import com.pennywiseai.tracker.data.currency.CurrencyConversionService
import com.pennywiseai.tracker.data.database.entity.ExchangeRateEntity
import com.pennywiseai.tracker.data.repository.TransactionRepository
import com.pennywiseai.tracker.presentation.exchangerates.ExchangeRateSnapshot
import com.pennywiseai.tracker.presentation.exchangerates.ExchangeRatesData
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import java.math.BigDecimal
import javax.inject.Inject

class AppExchangeRatesData @Inject constructor(
    private val currencyConversionService: CurrencyConversionService,
    private val transactionRepository: TransactionRepository
) : ExchangeRatesData {

    override suspend fun getUserCurrencies(): List<String> =
        transactionRepository.getAllCurrencies().first()

    override suspend fun getActiveRates(currencies: List<String>): List<ExchangeRateSnapshot> =
        currencyConversionService.getActiveRates(currencies).map { it.toSnapshot() }

    override suspend fun refreshExchangeRates(currencies: List<String>) =
        currencyConversionService.refreshExchangeRates(currencies)

    override suspend fun setCustomRate(fromCurrency: String, toCurrency: String, rate: BigDecimal) =
        currencyConversionService.setCustomRate(fromCurrency, toCurrency, rate)

    override suspend fun clearCustomRate(fromCurrency: String, toCurrency: String) =
        currencyConversionService.clearCustomRate(fromCurrency, toCurrency)

    override suspend fun clearAllCustomRates() =
        currencyConversionService.clearAllCustomRates()
}

internal fun ExchangeRateEntity.toSnapshot() = ExchangeRateSnapshot(
    fromCurrency = fromCurrency,
    toCurrency = toCurrency,
    rate = rate,
    updatedAt = updatedAt,
    isCustomRate = isCustomRate
)

@Module
@InstallIn(SingletonComponent::class)
abstract class ExchangeRatesModule {

    @Binds
    abstract fun bindExchangeRatesData(impl: AppExchangeRatesData): ExchangeRatesData
}
