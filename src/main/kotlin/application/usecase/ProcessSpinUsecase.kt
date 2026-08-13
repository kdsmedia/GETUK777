package application.usecase

import application.port.external.IEventPublisherPort
import application.port.external.IPlayerLimitPort
import application.port.external.IWalletPort
import domain.event.SpinEvent
import domain.exception.DomainException
import domain.exception.domainRequire
import domain.exception.forbidden.MaxPlaceSpinException
import domain.model.PlayerBalance
import domain.model.Spin
import domain.repository.ISpinRepository
import domain.service.SpinBalanceCalculator
import domain.service.SpinResult
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.slf4j.LoggerFactory

class ProcessSpinUsecase(
    private val spinRepository: ISpinRepository,
    private val eventPublisher: IEventPublisherPort,
    private val walletPort: IWalletPort,
    private val playerLimitPort: IPlayerLimitPort,
) {

    private val logger = LoggerFactory.getLogger(ProcessSpinUsecase::class.java)

    suspend operator fun invoke(spin: Spin): Result<Response> = runCatching {
        logger.info(
            "Processing spin: type={} session={} round={} amount={} freespin={}",
            spin.type, spin.round.session.id, spin.round.id, spin.amount, spin.round.freespinId,
        )

        // A free round's BET costs nothing, so it neither checks nor moves the balance. Its
        // WINNINGS are ordinary money and go through the normal path — skipping those too would
        // mean the player never receives what the bonus paid out.
        val result = if (spin.round.freespinId != null && spin.isPlace) {
            freeBet(spin)
        } else {
            process(spin)
        }

        val updatedSpin = spinRepository.save(result.spin)

        // Spin persisted: the event reflects committed state, so publish it now. A failed
        // wallet move (see process()) does NOT roll back this spin or this event — the committed
        // spin is the source of truth and the move is reconciled out-of-band. Likewise a broker
        // failure must never 500 the webhook once the spin is committed.
        try {
            eventPublisher.publish(SpinEvent(updatedSpin))
        } catch (e: Exception) {
            logger.error(
                "EVENT PUBLISH FAILED (event lost): route={} spinId={} externalId={} type={}",
                SpinEvent.route, updatedSpin.id, updatedSpin.externalId.value, updatedSpin.type, e,
            )
        }

        logger.info("Spin processed: id={} type={}", updatedSpin.id, updatedSpin.type)

        Response(spin = updatedSpin, balance = result.balance)
    }.onFailure { e ->
        if (e !is DomainException) {
            logger.error(
                "Failed to process spin: type={} session={} round={}",
                spin.type, spin.round.session.id, spin.round.id, e,
            )
        }
    }

    private suspend fun freeBet(spin: Spin): SpinResult {
        val balance = walletPort.findBalance(
            playerId = spin.round.session.playerId,
            currency = spin.round.session.currency,
        )
        return SpinResult(spin = spin, balance = balance)
    }

    private suspend fun process(spin: Spin): SpinResult = coroutineScope {
        val resultAsync = async { calculateResult(spin) }

        checkLimits(spin)

        val result = resultAsync.await()

        // Debit/credit the wallet with the balance-split spin (real/bonus computed by
        // SpinBalanceCalculator). The raw `spin` has realAmount/bonusAmount = 0, so
        // using it would move nothing and leave the wallet balance unchanged.
        //
        // Awaited, not dispatched: the move has to land before the spin row commits, or a
        // concurrent redelivery that loses the unique-constraint race reads a wallet that has
        // not caught up and answers the provider a balance that is simply wrong. A failure is
        // still swallowed — the committed spin remains the source of truth and a broken wallet
        // move is reconciled out of band, exactly as before.
        runCatching { updateBalance(result.spin) }
            .onFailure { logger.error("Wallet move failed for spin {}", spin.externalId.value, it) }

        result
    }

    private suspend fun updateBalance(spin: Spin) {
        val session = spin.round.session
        // PLACE takes money, SETTLE gives it back. A ROLLBACK moves it opposite to the spin it
        // reverses, so rolling back a win is a withdrawal, not a deposit.
        val takesMoney = spin.isPlace || (spin.isRollback && spin.reference?.isSettle == true)
        if (takesMoney) {
            walletPort.withdraw(
                playerId = session.playerId,
                transactionId = session.id.toString(),
                currency = session.currency,
                realAmount = spin.realAmount,
                bonusAmount = spin.bonusAmount,
            )
        } else {
            walletPort.deposit(
                playerId = session.playerId,
                transactionId = session.id.toString(),
                currency = session.currency,
                realAmount = spin.realAmount,
                bonusAmount = spin.bonusAmount,
            )
        }
    }

    private suspend fun calculateResult(spin: Spin): SpinResult {
        val session = spin.round.session
        val playerBalance = walletPort.findBalance(playerId = session.playerId, currency = session.currency)
        return SpinBalanceCalculator.process(balance = playerBalance, spin = spin)
    }

    private suspend fun checkLimits(spin: Spin) {
        if (!spin.isPlace) return

        val session = spin.round.session
        val playerMaxPlaceAmount = playerLimitPort.getMaxPlaceAmount(playerId = session.playerId) ?: return

        domainRequire(playerMaxPlaceAmount > spin.amount) { MaxPlaceSpinException() }
    }

    data class Response(val spin: Spin, val balance: PlayerBalance)
}
