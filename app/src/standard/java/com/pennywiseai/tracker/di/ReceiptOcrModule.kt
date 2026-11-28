package com.pennywiseai.tracker.di

import com.pennywiseai.tracker.data.receipt.MlKitReceiptTextRecognizer
import com.pennywiseai.tracker.data.receipt.ReceiptTextRecognizer
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Standard: receipt OCR by ML Kit's bundled model; F-Droid has a sibling module binding a stub. */
@Module
@InstallIn(SingletonComponent::class)
abstract class ReceiptOcrModule {

    @Binds
    abstract fun bindReceiptTextRecognizer(impl: MlKitReceiptTextRecognizer): ReceiptTextRecognizer
}
