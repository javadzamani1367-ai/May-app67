package ir.roozban.app.billing

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ir.roozban.billing.BillingGateway
import ir.roozban.billing.Store
import javax.inject.Singleton

/** Billing binding for the «dev» flavor. */
@Module
@InstallIn(SingletonComponent::class)
object StoreBillingModule {
    @Provides
    @Singleton
    fun billingGateway(): BillingGateway = UnavailableBillingGateway(Store.DEV)
}
