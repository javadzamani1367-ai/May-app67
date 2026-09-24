package ir.roozban.billing

/** Store the app was built for. Each build flavor provides exactly one [BillingGateway]. */
enum class Store { BAZAAR, MYKET, DEV }

enum class ProductId(val sku: String) {
    MONTHLY("roozban_pro_monthly"),
    YEARLY("roozban_pro_yearly"),
    LIFETIME("roozban_pro_lifetime"),
}

sealed interface PurchaseResult {
    data class Purchased(val product: ProductId, val purchaseToken: String) : PurchaseResult
    data object Cancelled : PurchaseResult
    data class Failed(val reason: String) : PurchaseResult
}

/**
 * Store-specific in-app billing (Poolakey for Cafe Bazaar, Myket IAB).
 * Phase 1 ships a no-op implementation per flavor; the real ones arrive in phase 7.
 */
interface BillingGateway {
    val store: Store
    val isAvailable: Boolean
    suspend fun purchase(product: ProductId): PurchaseResult
}
