package net.corda.node.services.transactions

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.TimeWindow
import net.corda.core.contracts.TransactionVerificationException
import net.corda.core.flows.FlowSession
import net.corda.core.flows.NotarisationPayload
import net.corda.core.flows.NotarisationRequest
import net.corda.core.flows.NotaryError
import net.corda.core.internal.ResolveTransactionsFlow
import net.corda.core.internal.notary.NotaryInternalException
import net.corda.core.internal.notary.NotaryServiceFlow
import net.corda.core.internal.notary.TrustedAuthorityNotaryService
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionWithSignatures
import net.corda.core.transactions.WireTransaction
import java.security.SignatureException

/**
 * A notary commit flow that makes sure a given transaction is valid before committing it. This does mean that the calling
 * party has to reveal the whole transaction history; however, we avoid complex conflict resolution logic where a party
 * has its input states "blocked" by a transaction from another party, and needs to establish whether that transaction was
 * indeed valid.
 */
class ValidatingNotaryFlow(otherSideSession: FlowSession, service: TrustedAuthorityNotaryService) : NotaryServiceFlow(otherSideSession, service) {
    /**
     * Fully resolves the received transaction and its dependencies, runs contract verification logic and checks that
     * the transaction in question has all required signatures apart from the notary's.
     */
    @Suspendable
    override fun validateRequest(requestPayload: NotarisationPayload): TransactionParts {
        try {
            val stx = requestPayload.signedTransaction
            checkInputs(stx.inputs)
            validateRequestSignature(NotarisationRequest(stx.inputs, stx.id), requestPayload.requestSignature)
            val notary = stx.notary
            checkNotary(notary)
            resolveAndContractVerify(stx)
            verifySignatures(stx)
            // Due to idempotency requirements, time-window validity is not checked here.
            // I.e., the time-window could be invalid, but the transaction might already have been committed.
            // This is helpful when one sends a transaction for notarisation, it gets committed but the response is lost.
            // Then the client can retry with a different replica to get the signature.
            val timeWindow: TimeWindow? = if (stx.coreTransaction is WireTransaction) stx.tx.timeWindow else null
            return TransactionParts(stx.id, stx.inputs, timeWindow, notary!!)
        } catch (e: Exception) {
            throw when (e) {
                is TransactionVerificationException,
                is SignatureException -> NotaryInternalException(NotaryError.TransactionInvalid(e))
                else -> e
            }
        }
    }

    @Suspendable
    private fun resolveAndContractVerify(stx: SignedTransaction) {
        subFlow(ResolveTransactionsFlow(stx, otherSideSession))
        stx.verify(serviceHub, false)
    }

    private fun verifySignatures(stx: SignedTransaction) {
        val transactionWithSignatures = stx.resolveTransactionWithSignatures(serviceHub)
        checkSignatures(transactionWithSignatures)
    }

    private fun checkSignatures(tx: TransactionWithSignatures) {
        try {
            tx.verifySignaturesExcept(service.notaryIdentityKey)
        } catch (e: SignatureException) {
            throw NotaryInternalException(NotaryError.TransactionInvalid(e))
        }
    }
}
