package com.pennywiseai.tracker.di

import com.pennywiseai.tracker.data.receipt.NoReceiptTextRecognizer
import com.pennywiseai.tracker.data.receipt.ReceiptTextRecognizer
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** F-Droid: receipt OCR is off (see [NoReceiptTextRecognizer]); standard has a sibling module binding ML Kit. */
@Module
@InstallIn(SingletonComponent::class)
abstract class ReceiptOcrModule {

    @Binds
    abstract fun bindReceiptTextRecognizer(impl: NoReceiptTextRecognizer): ReceiptTextRecognizer
}
