package net.corda.sdk.tokens

import net.corda.core.contracts.*
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.Vault
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.serialization.CordaSerializable

/**
 * A [StatePointer] contains a [pointer] to a [ContractState]. The [StatePointer] can be included in a [ContractState]
 * or included in an off-ledger data structure. [StatePointer]s can be resolved to a [StateAndRef] by performing a
 * vault query. There are two types of pointers; linear and static. [LinearPointer]s are for use with [LinearState]s.
 * [StaticPointer]s are for use with any type of [ContractState].
 */
@CordaSerializable
interface StatePointer {
    /**
     * An identifier for the [ContractState] that this [StatePointer] points to.
     */
    val pointer: Any
    /**
     * Resolves a [StatePointer] to a [StateAndRef] via a vault query. This method will either return a [StateAndRef]
     * or return an exception.
     *
     * @param services a [ServiceHub] implementation is required to resolve the pointer.
     */
    fun resolve(services: ServiceHub): StateAndRef<ContractState>
}
/**
 * A [StaticPointer] contains a [pointer] to a specific [StateRef] and can be resolved by looking up the [StateRef] via
 * [ServiceHub]. There are a number of things to keep in mind when using [StaticPointer]s:
 * - The [ContractState] being pointed to may be spent or unspent when the [pointer] is resolved
 * - The [ContractState] may not be known by the node performing the look-up in which case the [resolve] method will
 *   throw a [TransactionResolutionException]
 */
class StaticPointer(override val pointer: StateRef) : StatePointer {
    /**
     * Resolves a [StaticPointer] to a [StateAndRef] via a [StateRef] look-up.
     */
    @Throws(TransactionResolutionException::class)
    override fun resolve(services: ServiceHub): StateAndRef<ContractState> {
        val transactionState = services.loadState(pointer)
        return StateAndRef(transactionState, pointer)
    }
}
/**
 * [LinearPointer] allows a [ContractState] to "point" to another [LinearState] creating a "many-to-one" relationship
 * between all the states containing the pointer to a particular [LinearState] and the [LinearState] being pointed to.
 * Using the [LinearPointer] is useful when one state depends on the data contained within another state that evolves
 * independently. When using [LinearPointer] it is worth noting:
 * - The node performing the resolution may not have seen any [LinearState]s with the specified [linearId], as such the
 *   vault query in [resolve] will return null
 * - The node performing the resolution may not have the latest version of the [LinearState] and therefore will return
 *   an older version of the [LinearState]
 */
class LinearPointer(override val pointer: UniqueIdentifier) : StatePointer {
    /**
     * Resolves a [LinearPointer] using the [UniqueIdentifier] contained in the [pointer] property. Returns a
     * [StateAndRef] containing the latest version of the [LinearState] that the node calling [resolve] is aware of.
     *
     * @param services a [ServiceHub] implementation is required to perform a vault query.
     */
    override fun resolve(services: ServiceHub): StateAndRef<LinearState> {
        // Returns the latest version of the LinearState,
        // even if it is not "relevant" for the resolver node.
        val query = QueryCriteria.LinearStateQueryCriteria(
                linearId = listOf(pointer),
                status = Vault.StateStatus.UNCONSUMED,
                relevancyStatus = Vault.RelevancyStatus.ALL
        )
        val result = services.vaultService.queryBy<LinearState>(query).states
        check(result.isNotEmpty()) { "LinearPointer $pointer cannot be resolved." }
        return result.single()
    }
}