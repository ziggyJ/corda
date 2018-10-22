package net.corda.sdk.tokens

import com.nhaarman.mockito_kotlin.whenever
import net.corda.core.contracts.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.node.ServiceHub
import net.corda.core.node.StatesToRecord
import net.corda.core.node.services.Vault
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.TransactionBuilder
import net.corda.finance.GBP
import net.corda.node.services.api.IdentityServiceInternal
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.sdk.tokens.contracts.TokenContract
import net.corda.sdk.tokens.contracts.TokenTypeContract
import net.corda.sdk.tokens.states.Token
import net.corda.sdk.tokens.states.TokenType
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.core.TestIdentity
import net.corda.testing.internal.rigorousMock
import net.corda.testing.node.MockServices
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.doReturn
import java.math.BigDecimal
import java.util.*

//identityService = rigorousMock<IdentityServiceInternal>().also {
//    doReturn(net.corda.sdk.tokens.Test.ALICE_PARTY).whenever(it).partyFromKey(net.corda.sdk.tokens.Test.ALICE_PUBKEY)
//    doReturn(net.corda.sdk.tokens.Test.BOB_PARTY).whenever(it).partyFromKey(net.corda.sdk.tokens.Test.BOB_PUBKEY)
//    doReturn(net.corda.sdk.tokens.Test.ISSUER_PARTY).whenever(it).partyFromKey(net.corda.sdk.tokens.Test.ISSUER_PUBKEY)
//},

class Test {

    private data class FiatCurrency(override val of: Currency) : FungibleTokenDescription<Currency> {
        override val symbol: String get() = of.symbol
        override val name: String get() = of.displayName
        override val displayTokenSize: BigDecimal get() = BigDecimal.ONE.scaleByPowerOfTen(-of.defaultFractionDigits)
    }

    private val mockIdentityService = rigorousMock<IdentityServiceInternal>().also {
        doReturn(ALICE_PARTY).whenever(it).partyFromKey(ALICE_PUBKEY)
        doReturn(BOB_PARTY).whenever(it).partyFromKey(BOB_PUBKEY)
        doReturn(ISSUER_PARTY).whenever(it).partyFromKey(ISSUER_PUBKEY)
        doReturn(NOTARY_PARTY).whenever(it).partyFromKey(NOTARY_PUBKEY)
        doReturn(NOTARY_PARTY).whenever(it).wellKnownPartyFromAnonymous(NOTARY_PARTY)
        doReturn(ALICE_PARTY).whenever(it).wellKnownPartyFromAnonymous(ALICE_PARTY)
        doReturn(ISSUER_PARTY).whenever(it).wellKnownPartyFromAnonymous(ISSUER_PARTY)
        doReturn(ISSUER_PARTY).whenever(it).wellKnownPartyFromX500Name(ISSUER_PARTY.name)
        doReturn(NOTARY_PARTY).whenever(it).wellKnownPartyFromX500Name(NOTARY_PARTY.name)
        doReturn(ALICE_PARTY).whenever(it).wellKnownPartyFromX500Name(ALICE_PARTY.name)

    }

    private companion object {
        val NOTARY = TestIdentity(DUMMY_NOTARY_NAME, 20)
        val NOTARY_PARTY = NOTARY.party
        val NOTARY_PUBKEY = NOTARY.publicKey
        val ISSUER = TestIdentity(CordaX500Name("ISSUER", "London", "GB"))
        val ISSUER_PARTY = ISSUER.party
        val ISSUER_PUBKEY = ISSUER.publicKey
        val ALICE = TestIdentity(CordaX500Name("ALICE", "London", "GB"))
        val ALICE_PARTY get() = ALICE.party
        val ALICE_PUBKEY get() = ALICE.publicKey
        val BOB = TestIdentity(CordaX500Name("BOB", "London", "GB"))
        val BOB_PARTY get() = BOB.party
        val BOB_PUBKEY get() = BOB.publicKey
    }

    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()

    private val aliceDbAndServices = MockServices.makeTestDatabaseAndMockServices(
            cordappPackages = listOf("net.corda.sdk.tokens"),
            initialIdentity = ALICE,
            identityService = mockIdentityService,
            networkParameters = testNetworkParameters(minimumPlatformVersion = 4)
    )

    val aliceDB = aliceDbAndServices.first
    val aliceServices = aliceDbAndServices.second

    private val bobDbAndServices = MockServices.makeTestDatabaseAndMockServices(
            cordappPackages = listOf("net.corda.sdk.tokens"),
            initialIdentity = BOB,
            identityService = mockIdentityService,
            networkParameters = testNetworkParameters(minimumPlatformVersion = 4)
    )

    val bobDB = aliceDbAndServices.first
    val bobServices = aliceDbAndServices.second

    private val issuerDbAndServices = MockServices.makeTestDatabaseAndMockServices(
            cordappPackages = listOf("net.corda.sdk.tokens"),
            initialIdentity = ISSUER,
            identityService = mockIdentityService,
            networkParameters = testNetworkParameters(minimumPlatformVersion = 4)
    )

    val issuerDB = aliceDbAndServices.first
    val issuerServices = aliceDbAndServices.second

    private fun MockServices.getTokenType(db: CordaPersistence, linearId: UniqueIdentifier): StateAndRef<LinearState>? {
        return db.transaction {
            val query = QueryCriteria.LinearStateQueryCriteria(
                    linearId = listOf(linearId),
                    relevancyStatus = Vault.RelevancyStatus.NOT_RELEVANT
            )
            vaultService.queryBy<LinearState>(query).states.singleOrNull()
        }
    }

    @Test
    fun `create token type, issue some token pointing to that type, resolve pointer`() {
        // Create a token definition.
        val gbp = FiatCurrency(GBP)

        // Create a token type state and commit it to ledger.
        val fiatCurrency = TokenType(gbp, ISSUER.party)
        val tokenTypeTx = issuerServices.run {
            val tx = signInitialTransaction(TransactionBuilder(NOTARY_PARTY).apply {
                addOutputState(fiatCurrency, TokenTypeContract.contractId)
                addCommand(TokenType.Create(), listOf(ISSUER_PUBKEY))
            })
            recordTransactions(StatesToRecord.ALL_VISIBLE, listOf(tx))
            tx
        }

        // Share the token type state with the other parties.
        aliceServices.recordTransactions(StatesToRecord.ALL_VISIBLE, listOf(tokenTypeTx))
        bobServices.recordTransactions(StatesToRecord.ALL_VISIBLE, listOf(tokenTypeTx))

        // Check everyone has the token type state.
        val linearId = tokenTypeTx.tx.outputsOfType<LinearState>().single().linearId
        require(aliceServices.getTokenType(aliceDB, linearId) != null)
        require(bobServices.getTokenType(bobDB, linearId) != null)
        require(issuerServices.getTokenType(issuerDB, linearId) != null)

        // Issue a new token of the above token type and record tx for issuer and alice.
        val tokenTx = issuerServices.run {
            val amount = Amount(10L, fiatCurrency.toPointer())
            val token = Token(amount, ALICE.party)
            val tx = signInitialTransaction(TransactionBuilder(NOTARY_PARTY).apply {
                addOutputState(token, TokenContract.contractId)
                addCommand(Token.Issue(), listOf(ISSUER_PUBKEY))
            })
            recordTransactions(listOf(tx))
            tx
        }
        aliceServices.recordTransactions(StatesToRecord.ONLY_RELEVANT, listOf(tokenTx))

        // Alice resolves the token type.
        val token = tokenTx.tx.outputsOfType<Token<*>>().single()
        val resolvedTokenType = token.resolveTokenType(aliceServices)
        println(resolvedTokenType)
    }
}

object Testing {

    val NOTARY = TestIdentity(DUMMY_NOTARY_NAME, 20)
    val NOTARY_PARTY = NOTARY.party
    val REFERENCE_DATA_PARTY = NOTARY.party
    val NOTARY_PUBKEY = NOTARY.publicKey
    val ISSUER = TestIdentity(CordaX500Name("ISSUER", "London", "GB"))
    val BANK_OF_ENGLAND = ISSUER.party
    val ISSUER_PUBKEY = ISSUER.publicKey
    val ALICE = TestIdentity(CordaX500Name("ALICE", "London", "GB"))
    val ALICE_PARTY get() = ALICE.party
    val ALICE_PUBKEY get() = ALICE.publicKey
    val BOB = TestIdentity(CordaX500Name("BOB", "London", "GB"))
    val BOB_PARTY get() = BOB.party
    val BOB_PUBKEY get() = BOB.publicKey

    class IssueToken : CommandData
    class CreateTokenType : CommandData

    class Token(
            override val amount: Amount<LinearPointer>,
            override val owner: AbstractParty
    ) : OwnableState, FungibleState<LinearPointer> {
        override val participants: List<AbstractParty> get() = listOf(owner)
        override fun withNewOwner(newOwner: AbstractParty) = CommandAndState(IssueToken(), Token(amount, newOwner))
    }

    data class TokenType<T : Any>(
            val data: T,
            val creator: Party,
            override val linearId: UniqueIdentifier = UniqueIdentifier()
    ) : LinearState {
        override val participants: List<AbstractParty> get() = listOf(creator)
        fun toPointer() = LinearPointer(linearId)
    }

    interface TokenDescription<T : Any> {
        val data: T
    }

    data class FiatCurrency(val name: String, override val displayTokenSize: BigDecimal) : TokenizableAssetInfo

    data class CentralBankReserves(override val data: LinearPointer, val issuer: Party) : TokenDescription<LinearPointer>

    fun test() {
        // The base token type.
        val fiatCurrency: FiatCurrency = FiatCurrency("GBP", BigDecimal.ONE)
        // This is committed to ledger and can be pointed to.
        val fiatCurrencyTokenType: TokenType<FiatCurrency> = TokenType(fiatCurrency, REFERENCE_DATA_PARTY)
        // A pointer to the above.
        val fiatCurrencyTokenTypePointer: LinearPointer = fiatCurrencyTokenType.toPointer()

        // Another token description for central bank reserves. It points to the GBP definition.
        val reserves: CentralBankReserves = CentralBankReserves(fiatCurrencyTokenTypePointer, BANK_OF_ENGLAND)
        val reservesTokenType: TokenType<CentralBankReserves> = TokenType(reserves, BANK_OF_ENGLAND)
        val reservesTokenTypePointer: LinearPointer = reservesTokenType.toPointer()

        // An amount of some token where the token type is a pointer to the reserves definition.
        val amount: Amount<LinearPointer> = Amount(10L, reservesTokenTypePointer)
        val token: Token = Token(amount, ALICE_PARTY)
    }

}



