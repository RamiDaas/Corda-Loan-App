package com.r3.developers.csdetemplate.utxoexample.states

import com.r3.developers.csdetemplate.utxoexample.contracts.LoanContract
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.ledger.utxo.BelongsToContract
import net.corda.v5.ledger.utxo.ContractState
import java.security.PublicKey
import java.util.UUID

@BelongsToContract(LoanContract::class)
data class LoanState (
    val lender: MemberX500Name,
    val borrower : MemberX500Name,
    val amount : Int,
    val paid: Int,
    val id: UUID,
    private val participants: List<PublicKey>
) : ContractState {
    override fun getParticipants(): List<PublicKey> {
        return participants
    }

    fun payBack(amountPaid: Int): LoanState{
        return LoanState(this.lender, this.borrower, this.amount, this.paid + amountPaid, this.id, this.participants)
    }

    fun transferLoan(newLender : MemberX500Name, newParticipants: List<PublicKey>): LoanState{
        return LoanState(newLender, this.borrower, this.amount, this.paid, this.id, newParticipants)
    }
}