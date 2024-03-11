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
import java.util.*


data class CreateNewLoanArgs(val lender: String, val amount: Int)

//Creating the loan state in this flow
class CreateNewLoanFlow : ClientStartableFlow {
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
        log.info("creating new loan")

        try {
            val flowArgs = requestBody.getRequestBodyAs(jsonMarshallingService, CreateNewLoanArgs::class.java)

            val borrower = memberLookup.myInfo()
            val lender =
                memberLookup.lookup(MemberX500Name.parse(flowArgs.lender)) ?: throw CordaRuntimeException("err")

            //Output state
            val loanState = LoanState(
                lender = lender.name,
                borrower = borrower.name,
                amount = flowArgs.amount,
                paid = 0,
                id = UUID.randomUUID(),
                participants = listOf(borrower.ledgerKeys.first(), lender.ledgerKeys.first())
            )

            //Notary
            val notary =
                notaryLookup.lookup(MemberX500Name.parse("CN=NotaryService, OU=Test Dept, O=R3, L=London, C=GB"))
                    ?: throw CordaRuntimeException("Notary not found")
            //Build the transaction
            val txBuilder = ledgerService.createTransactionBuilder().setNotary(notary.name)
                .setTimeWindowBetween(Instant.now(), Instant.now().plusMillis(Duration.ofDays(1).toMillis()))
                .addOutputState(loanState)
                .addCommand(LoanContract.Create())
                .addSignatories(loanState.participants)

            //self sign tr
            val selfSignedTx = txBuilder.toSignedTransaction()
            //notary sig and counter-parties sig
            return flowEngine.subFlow(FinalizeLoanSubFlow(selfSignedTx, lender.name))
        } catch (e: Exception) {
            log.warn("failed to process request body")
            throw e
        }
    }
}