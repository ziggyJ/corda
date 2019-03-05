package net.corda.finance.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.confidential.SwapIdentitiesFlow
import net.corda.core.contracts.*
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.unwrap
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.flows.AbstractCashFlow.Companion.FINALISING_TX
import net.corda.finance.flows.AbstractCashFlow.Companion.GENERATING_ID
import net.corda.finance.flows.AbstractCashFlow.Companion.GENERATING_TX
import net.corda.finance.flows.AbstractCashFlow.Companion.SIGNING_TX
import net.corda.finance.workflows.asset.CashUtils
import java.util.*

/**
 * Initiates a flow that sends cash to a recipient.
 *
 * @param amount the amount of a currency to pay to the recipient.
 * @param recipient the party to pay the currency to.
 * @param issuerConstraint if specified, the payment will be made using only cash issued by the given parties.
 * @param anonymous whether to anonymous the recipient party. Should be true for normal usage, but may be false
 * @param notary if not specified, the first notary of the network map is selected
 * for testing purposes.
 */
@StartableByRPC
@InitiatingFlow
open class CashPaymentFlow(
        val amount: Amount<Currency>,
        val recipient: Party,
        val anonymous: Boolean,
        progressTracker: ProgressTracker,
        val issuerConstraint: Set<Party> = emptySet(),
        val notary: Party? = null
) : AbstractCashFlow<AbstractCashFlow.Result>(progressTracker) {
    /** A straightforward constructor that constructs spends using cash states of any issuer. */
    constructor(amount: Amount<Currency>, recipient: Party) : this(amount, recipient, true, tracker())

    /** A straightforward constructor that constructs spends using cash states of any issuer. */
    constructor(amount: Amount<Currency>, recipient: Party, anonymous: Boolean) : this(amount, recipient, anonymous, tracker())

    constructor(amount: Amount<Currency>, recipient: Party, anonymous: Boolean, notary: Party) : this(amount, recipient, anonymous, tracker(), notary = notary)

    constructor(request: PaymentRequest) : this(request.amount, request.recipient, request.anonymous, tracker(), request.issuerConstraint, request.notary)

    @Suspendable
    override fun call(): AbstractCashFlow.Result {
        progressTracker.currentStep = GENERATING_ID
        val recipientSession = initiateFlow(recipient)
        recipientSession.send(anonymous)
        val anonymousRecipient = if (anonymous) {
            subFlow(SwapIdentitiesFlow(recipientSession))[recipient]!!
        } else {
            recipient
        }
        progressTracker.currentStep = GENERATING_TX

        val builder = TransactionBuilder(notary = notary ?: serviceHub.networkMapCache.notaryIdentities.first())
        logger.info("Generating spend for: ${builder.lockId}")
        // TODO: Have some way of restricting this to states the caller controls
        val (spendTX, keysForSigning) = try {
            CashUtils.generateSpend(
                    serviceHub,
                    builder,
                    amount,
                    ourIdentityAndCert,
                    anonymousRecipient,
                    issuerConstraint
            )
        } catch (e: InsufficientBalanceException) {
            throw CashException("Insufficient cash for spend: ${e.message}", e)
        }

        /**
         * Constraint migration code (to retrieve pre Corda 4 hash/CZ-whitelisted constrained states:
         * need to attach public key of signed cordapp to output states
         */

        // This will read the signers for the deployed CorDapp.
        logger.info("SCM for contract: ${Cash.PROGRAM_ID}")
        val attachment = this.serviceHub.cordappProvider.getContractAttachmentID(Cash.PROGRAM_ID)
        logger.info("SCM, attachment hash : $attachment")
        val signers = this.serviceHub.attachments.openAttachment(attachment!!)!!.signerKeys
        logger.info("SCM, signer public keys : $signers")

        // Create the key that will have to pass for all future versions.
        val signatureConstraint = SignatureAttachmentConstraint(signers.first())
        logger.info("SCM, signatureConstraint : $signatureConstraint")

        val outputStatesWithSignatureConstraint =
                spendTX.outputStates().map {
                    TransactionState(it.data, it.contract, it.notary, constraint = signatureConstraint)
        }

        fun copy(outputs: List<TransactionState<ContractState>>): TransactionBuilder {
            return TransactionBuilder(
                    notary = spendTX.notary,
                    inputs = ArrayList(spendTX.inputStates()),
                    attachments = ArrayList(spendTX.attachments()),
                    outputs = ArrayList(outputs),
                    commands = ArrayList(spendTX.commands()),
                    serviceHub = serviceHub
            )
        }

        logger.info("SCM: copying transaction with signature constrained outputs")
        val spendTXWithSignatureConstraints = copy(outputStatesWithSignatureConstraint)

        progressTracker.currentStep = SIGNING_TX
        logger.info("Signing transaction for: ${spendTXWithSignatureConstraints.lockId}")
        val tx = serviceHub.signInitialTransaction(spendTXWithSignatureConstraints, keysForSigning)

        progressTracker.currentStep = FINALISING_TX
        logger.info("Finalising transaction for: ${tx.id}")
        val sessionsForFinality = if (serviceHub.myInfo.isLegalIdentity(recipient)) emptyList() else listOf(recipientSession)
        val notarised = finaliseTx(tx, sessionsForFinality, "Unable to notarise spend")
        logger.info("Finalised transaction for: ${notarised.id}")
        return Result(notarised, anonymousRecipient)
    }

    @CordaSerializable
    class PaymentRequest(amount: Amount<Currency>,
                         val recipient: Party,
                         val anonymous: Boolean,
                         val issuerConstraint: Set<Party> = emptySet(),
                         val notary: Party? = null) : AbstractRequest(amount)
}

@InitiatedBy(CashPaymentFlow::class)
class CashPaymentReceiverFlow(private val otherSide: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val anonymous = otherSide.receive<Boolean>().unwrap { it }
        if (anonymous) {
            subFlow(SwapIdentitiesFlow(otherSide))
        }
        // Not ideal that we have to do this check, but we must as FinalityFlow does not send locally
        if (!serviceHub.myInfo.isLegalIdentity(otherSide.counterparty)) {
            subFlow(ReceiveFinalityFlow(otherSide))
        }
    }
}
