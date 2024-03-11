package com.r3.developers.csdetemplate.loanapp.workflows

import com.r3.developers.csdetemplate.utxoexample.workflows.FinalizeChatResponderFlow
import com.r3.developers.csdetemplate.utxoexample.workflows.FinalizeChatSubFlow
import net.corda.v5.application.flows.*
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.ledger.utxo.UtxoLedgerService
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import org.slf4j.LoggerFactory

@InitiatingFlow(protocol = "finalize-loan-protocol")
class FinalizeLoanSubFlow(private val signedTx: UtxoSignedTransaction, private val lender: MemberX500Name) :
    SubFlow<String> {
    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    // Injects the UtxoLedgerService to enable the flow to make use of the Ledger API.
    @CordaInject
    lateinit var ledgerService: UtxoLedgerService

    @CordaInject
    lateinit var flowMessaging: FlowMessaging

    @Suspendable
    override fun call(): String {
        log.info("Finalize loan")
        val session = flowMessaging.initiateFlow(lender)
        return try {
            //notarisation and get sigs
            val finalizedSignedTransaction = ledgerService.finalize(signedTx, listOf(session))
            finalizedSignedTransaction.transaction.id.toString().also{
                log.info("Success! Response: $it")
            }
        } catch (e: Exception) {
            log.warn("Finality failed", e)
            "Finality failed, ${e.message}"
        }
    }
}

@InitiatedBy(protocol = "finalize-loan-protocol")
class FinalizeLoanResponderFlow : ResponderFlow {
    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    // Injects the UtxoLedgerService to enable the flow to make use of the Ledger API.
    @CordaInject
    lateinit var ledgerService: UtxoLedgerService

    @Suspendable
    override fun call(session: FlowSession) {
        log.info("Finalize loan responder called")

        try {
            val finalizedSignedTransaction = ledgerService.receiveFinality(session){}
            log.info("Finished responder flow - ${finalizedSignedTransaction.transaction.id}")
        }catch(e: Exception){
            log.warn("Exceptionally finished responder flow", e)
        }
    }
}
