package com.r3.developers.csdetemplate.loanapp.workflows

import com.r3.developers.csdetemplate.utxoexample.contracts.LoanContract
import com.r3.developers.csdetemplate.utxoexample.states.LoanState
import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.ledger.common.NotaryLookup
import net.corda.v5.ledger.utxo.UtxoLedgerService
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import java.util.UUID


data class TransferLoanArgs(val transferTo: String, val txId: UUID)

class TransferLoanFlow : ClientStartableFlow {
    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @CordaInject
    lateinit var memberLookup: MemberLookup

    // Injects the UtxoLedgerService to enable the flow to make use of the Ledger API.
    @CordaInject
    lateinit var ledgerService: UtxoLedgerService

    @CordaInject
    lateinit var notaryLookup: NotaryLookup

    // FlowEngine service is required to run SubFlows.
    @CordaInject
    lateinit var flowEngine: FlowEngine

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        log.info("TransferLoanFlow.call() called")
        try {
            val flowArgs = requestBody.getRequestBodyAs(jsonMarshallingService, TransferLoanArgs::class.java)

            val transferTo = flowArgs.transferTo
            val txId = flowArgs.txId

            val states = ledgerService.findUnconsumedStatesByType(LoanState::class.java)
            val tx = states.filter { it.state.contractState.id == txId }

            if (tx.size != 1) throw CordaRuntimeException("Invalid transaction or transaction not found")
            val foundTx = tx.first()

            val loan = foundTx.state.contractState

            val borrower = memberLookup.myInfo()
            val newLender = memberLookup.lookup(MemberX500Name.parse(transferTo))
                ?: throw CordaRuntimeException("MemberLookup can't find transferTo specified in flow arguments.")

            val newParticipants = listOf(borrower.ledgerKeys.first(), newLender.ledgerKeys.first())

            val newLoan = loan.transferLoan(newLender.name, newParticipants)

            val notary = foundTx.state.notaryName

            val txBuilder = ledgerService.createTransactionBuilder()
                .setNotary(notary)
                .setTimeWindowBetween(Instant.now(), Instant.now().plusMillis(Duration.ofDays(1).toMillis()))
                .addInputState(foundTx.ref)
                .addOutputState(newLoan)
                .addCommand(LoanContract.Transfer())
                .addSignatories(newLoan.participants)

            val signedTx = txBuilder.toSignedTransaction()

            return flowEngine.subFlow(FinalizeLoanSubFlow(signedTx, newLender.name))
        } catch (e: Exception) {
            log.warn("failed to process request body")
            throw e
        }
    }
}