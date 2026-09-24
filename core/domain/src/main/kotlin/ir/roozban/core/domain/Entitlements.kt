package ir.roozban.core.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** What the user is entitled to. Features only ever look at this, never at stores or servers. */
sealed interface Entitlement {
    data object Free : Entitlement

    data class Trial(val endsAt: Instant) : Entitlement

    /** [until] null = lifetime purchase. */
    data class Pro(val until: Instant?) : Entitlement

    /** Pro whose periodic check could not reach the server; valid until [until]. */
    data class GraceOffline(val until: Instant) : Entitlement

    fun allowsPro(now: Instant): Boolean = when (this) {
        Free -> false
        is Trial -> now.isBefore(endsAt)
        is Pro -> until == null || now.isBefore(until)
        is GraceOffline -> now.isBefore(until)
    }
}

enum class ProFeature { ASSISTANT, AUTO_PLANNING, FOCUS, HABITS, REPORTS, LEARNING }

/**
 * Pro locks *features*, never data: without Pro, habits and past reports stay readable
 * ([Access.READ_ONLY]); only creating and running Pro features is locked.
 */
enum class Access { FULL, READ_ONLY }

interface Entitlements {
    val entitlement: StateFlow<Entitlement>

    fun access(feature: ProFeature): Access
}

/**
 * Everything open until licensing arrives (phase 7). Kept as a real implementation so features
 * are already written against [Entitlements].
 */
@Singleton
class ProvisionalEntitlements @Inject constructor(private val clock: Clock) : Entitlements {
    private val state = MutableStateFlow<Entitlement>(Entitlement.Pro(until = null))
    override val entitlement: StateFlow<Entitlement> = state.asStateFlow()

    override fun access(feature: ProFeature): Access =
        if (state.value.allowsPro(Instant.now(clock))) Access.FULL else Access.READ_ONLY
}
