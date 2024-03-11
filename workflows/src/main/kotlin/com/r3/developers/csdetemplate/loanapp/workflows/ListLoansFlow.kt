package com.r3.developers.csdetemplate.loanapp.workflows

import com.r3.developers.csdetemplate.utxoexample.states.LoanState
import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.utxo.UtxoLedgerService
import org.slf4j.LoggerFactory
import java.util.UUID

data class ListLoansResult(val lender: String, val borrower : String, val amount : Int, val id: UUID?, val paid: Int)

class ListLoansFlow: ClientStartableFlow {
    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    // Injects the UtxoLedgerService to enable the flow to make use of the Ledger API.
    @CordaInject
    lateinit var ledgerService: UtxoLedgerService

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        log.info("ListLoansFlow.call() called")

        val states = ledgerService.findUnconsumedStatesByType(LoanState::class.java)

        val loans = states.map {
            ListLoansResult(
                it.state.contractState.lender.toString(),
                it.state.contractState.borrower.toString(),
                it.state.contractState.amount,
                it.state.contractState.id ?: null,
                it.state.contractState.paid,
            )
        }

        return jsonMarshallingService.format(loans)
    }
}