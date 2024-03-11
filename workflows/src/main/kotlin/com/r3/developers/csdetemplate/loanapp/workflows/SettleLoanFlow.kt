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
import net.corda.v5.ledger.utxo.UtxoLedgerService
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import java.util.UUID


data class SettleLoanArgs(val amountToSettle: Int, val txId: UUID)

class SettleLoanFlow : ClientStartableFlow {
    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    // Injects the JsonMarshallingService to read and populate JSON parameters.
    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    // Injects the MemberLookup to look up the VNode identities.
    @CordaInject
    lateinit var memberLookup: MemberLookup

    // Injects the UtxoLedgerService to enable the flow to make use of the Ledger API.
    @CordaInject
    lateinit var ledgerService: UtxoLedgerService

    // FlowEngine service is required to run SubFlows.
    @CordaInject
    lateinit var flowEngine: FlowEngine

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        log.info("Settling loan")

        try {
            val flowArgs = requestBody.getRequestBodyAs(jsonMarshallingService, SettleLoanArgs::class.java)

            val id = flowArgs.txId
            val amountToSettle = flowArgs.amountToSettle

            val borrower = memberLookup.myInfo()

            val states = ledgerService.findUnconsumedStatesByType(LoanState::class.java)
            val tx = states.filter { it.state.contractState.id == id }

            if (tx.size != 1) throw CordaRuntimeException("Invalid transaction or transaction not found")
            val foundTx = tx.first()

            val loan = foundTx.state.contractState

            val lender = memberLookup.lookup(loan.lender)
                ?: throw CordaRuntimeException("Transaction not found")

            val newLoan = loan.payBack(amountToSettle)

            val notary = foundTx.state.notaryName

            val txBuilder = ledgerService.createTransactionBuilder()
                .setNotary(notary)
                .setTimeWindowBetween(Instant.now(), Instant.now().plusMillis(Duration.ofDays(1).toMillis()))
                .addInputState(foundTx.ref)
                .addOutputState(newLoan)
                .addCommand(LoanContract.PayBack())
                .addSignatories(newLoan.participants)

            val signedTx = txBuilder.toSignedTransaction()

            return flowEngine.subFlow(FinalizeLoanSubFlow(signedTx, lender.name))
        } catch (e: Exception) {
            log.warn("Settle loan failed ", e)
            throw e
        }
    }
}