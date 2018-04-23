package net.corda.finance.schemas

import net.corda.core.identity.AbstractParty
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.serialization.CordaSerializable
import net.corda.core.utilities.MAX_HASH_HEX_SIZE
import net.corda.core.contracts.MAX_ISSUER_REF_SIZE
import net.corda.core.schemas.CommonSchemaV1
import net.corda.core.utilities.OpaqueBytes
import org.hibernate.annotations.Type
import javax.persistence.*

/**
 * First version of a cash contract ORM schema that maps all fields of the [Cash] contract state as it stood
 * at the time of writing.
 */
@CordaSerializable
object CashSchemaV2 : MappedSchema(schemaFamily = CashSchema.javaClass, version = 1, mappedTypes = listOf(PersistentCashState::class.java)) {
    @Entity
    @Table(name = "contract_cash_states_v2",
            indexes = arrayOf(Index(name = "ccy_code_idx", columnList = "ccy_code")))
    class PersistentCashState(

        @ElementCollection
        @Column(name = "participants")
        @CollectionTable(name="cash_states_v2_participants", joinColumns = arrayOf(
                JoinColumn(name = "output_index", referencedColumnName = "output_index"),
                JoinColumn(name = "transaction_id", referencedColumnName = "transaction_id")))
        override var participants: MutableSet<AbstractParty>? = null,

        /** product type */
        @Column(name = "ccy_code", length = 3)
        var currency: String,

        /** parent attributes */
        @Transient
        val _participants: Set<AbstractParty>,
        @Transient
        val _owner: AbstractParty,
        @Transient
        val _quantity: Long,
        @Transient
        val _issuerParty: AbstractParty,
        @Transient
        val _issuerRef: OpaqueBytes
    ) : CommonSchemaV1.FungibleState(_participants.toMutableSet(), _owner, _quantity, _issuerParty, _issuerRef.bytes)

}
