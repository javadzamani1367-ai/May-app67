package ir.roozban.app.billing

import ir.roozban.billing.BillingGateway
import ir.roozban.billing.ProductId
import ir.roozban.billing.PurchaseResult
import ir.roozban.billing.Store

/** Placeholder until the store SDKs are integrated in phase 7. */
class UnavailableBillingGateway(override val store: Store) : BillingGateway {
    override val isAvailable: Boolean = false

    override suspend fun purchase(product: ProductId): PurchaseResult =
        PurchaseResult.Failed("In-app billing is not available yet")
}
