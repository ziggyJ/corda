package net.corda.dummy.basecontract

import net.corda.core.contracts.*
import net.corda.core.identity.AbstractParty
import net.corda.core.transactions.LedgerTransaction
import net.corda.finance.contracts.asset.OnLedgerAsset
import java.util.*

abstract class BaseDummyAssetContract : OnLedgerAsset<Currency, BaseDummyAssetContract.Commands, BaseDummyAssetContract.State>() {
    override fun extractCommands(commands: Collection<CommandWithParties<CommandData>>): Collection<CommandWithParties<Commands>> = commands.select()

    override fun generateExitCommand(amount: Amount<Issued<Currency>>): CommandData = Commands.Exit(amount)
    override fun generateMoveCommand(): MoveCommand = Commands.Move()
    override fun deriveState(txState: TransactionState<State>, amount: Amount<Issued<Currency>>, owner: AbstractParty): TransactionState<State> = TODO()
    override fun verify(tx: LedgerTransaction) {
    }

    abstract class State(
            override val amount: Amount<Issued<Currency>>,
            override val owner: AbstractParty
    ) : FungibleAsset<Currency>, ContractState {
        override fun withNewOwnerAndAmount(newAmount: Amount<Issued<Currency>>, newOwner: AbstractParty): FungibleAsset<Currency> = TODO()
        override fun withNewOwner(newOwner: AbstractParty): CommandAndState = TODO()
    }

    interface Commands : CommandData {
        data class Move(override val contract: Class<out Contract>? = null) : MoveCommand
        class Issue : TypeOnlyCommandData()
        data class Exit(val amount: Amount<Issued<Currency>>) : CommandData
    }
}

