package com.r3.developers.csdetemplate.utxoexample.contracts

import com.r3.developers.csdetemplate.utxoexample.states.LoanState
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.ledger.utxo.Command
import net.corda.v5.ledger.utxo.Contract
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction

class LoanContract : Contract {

    internal companion object {

        const val REQUIRE_SINGLE_COMMAND = "Requires a single command."
        const val UNKNOWN_COMMAND = "Command not allowed."
        const val LOAN_AMOUNT_INVALID = "Loan amount must be under 200 and more than 0"
        const val OUTPUT_STATE_SHOULD_ONLY_HAVE_TWO_PARTICIPANTS =
            "The output state should have two and only two participants."
        const val TRANSACTION_SHOULD_BE_SIGNED_BY_ALL_PARTICIPANTS =
            "The transaction should have been signed by both participants."
        const val PAYBACK_AMOUNT_OVER_THAN_REQUIRED_AMOUNT = "The payback amount needs to be under than the loan amount"

        const val CREATE_COMMAND_SHOULD_HAVE_NO_INPUT_STATES = "When command is Create there should be no input states."
        const val CREATE_COMMAND_SHOULD_HAVE_ONLY_ONE_OUTPUT_STATE =
            "When command is Create there should be one and only one output state."
        const val BORROWER_LENDER_INVALID = "Borrower and lender can't be changed"
        const val INVALID_OUTPUT_INPUT = "invalid output and input"
    }

    //Commands
    class Create : Command
    class PayBack : Command
    class Exit : Command
    class Transfer : Command

    //Verify Functions
    override fun verify(transaction: UtxoLedgerTransaction) {
        val command = transaction.commands.singleOrNull() ?: throw CordaRuntimeException(REQUIRE_SINGLE_COMMAND)

        when (command) {
            is Create -> {
                verifyCreate(transaction)
            }

            is PayBack -> {
                verifyPayback(transaction)
            }

            is Transfer -> {
                verifyTransfer(transaction)
            }
            else -> {
                throw CordaRuntimeException(UNKNOWN_COMMAND)
            }
        }
    }

    private fun verifyCreate(transaction: UtxoLedgerTransaction) {

        val outputs = transaction.outputContractStates.single() as LoanState

        CREATE_COMMAND_SHOULD_HAVE_NO_INPUT_STATES using (transaction.inputContractStates.isEmpty())
        CREATE_COMMAND_SHOULD_HAVE_ONLY_ONE_OUTPUT_STATE using (transaction.outputContractStates.size == 1)
        OUTPUT_STATE_SHOULD_ONLY_HAVE_TWO_PARTICIPANTS using (outputs.participants.size == 2)
        TRANSACTION_SHOULD_BE_SIGNED_BY_ALL_PARTICIPANTS using (transaction.signatories.containsAll(outputs.participants))

        LOAN_AMOUNT_INVALID using (outputs.amount in 1..199)
    }

    private fun verifyPayback(transaction: UtxoLedgerTransaction) {
        INVALID_OUTPUT_INPUT using (transaction.outputContractStates.size == 1 && transaction.inputContractStates.size == 1)

        val outputs = transaction.outputContractStates.single() as LoanState
        val inputs = transaction.inputContractStates.single() as LoanState

        BORROWER_LENDER_INVALID using {
            inputs.lender == outputs.lender &&
                    inputs.borrower == outputs.borrower
        }

        OUTPUT_STATE_SHOULD_ONLY_HAVE_TWO_PARTICIPANTS using (outputs.participants.size == 2)
        TRANSACTION_SHOULD_BE_SIGNED_BY_ALL_PARTICIPANTS using (transaction.signatories.containsAll(outputs.participants))

        PAYBACK_AMOUNT_OVER_THAN_REQUIRED_AMOUNT using (outputs.paid <= outputs.amount)
    }

    private fun verifyTransfer(transaction: UtxoLedgerTransaction) {
        INVALID_OUTPUT_INPUT using (transaction.outputContractStates.size == 1 && transaction.inputContractStates.size == 1)
    }

    //Helper methods

    // Helper function to allow writing constraints in the Corda 4 '"text" using (boolean)' style
    private infix fun String.using(expr: Boolean) {
        if (!expr) throw CordaRuntimeException("Failed requirement: $this")
    }

    // Helper function to allow writing constraints in '"text" using {lambda}' style where the last expression
    // in the lambda is a boolean.
    private infix fun String.using(expr: () -> Boolean) {
        if (!expr.invoke()) throw CordaRuntimeException("Failed requirement: $this")
    }
}