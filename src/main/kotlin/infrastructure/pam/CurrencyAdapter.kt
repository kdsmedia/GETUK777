package infrastructure.pam

import application.port.external.ICurrencyPort
import com.nekgambling.pam.v1.CurrencyServiceGrpc
import com.nekgambling.pam.v1.FromNanoRequest
import com.nekgambling.pam.v1.ToNanoRequest
import domain.vo.Currency
import io.grpc.ManagedChannel
import java.math.BigDecimal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Currency conversion backed by pam-engine's CurrencyService. The system unit is nano (1e9) and is
 * currency-independent; decimal amounts travel as plain strings so no amount is ever a float on the
 * wire. Shares the one pam channel with [WalletAdapter] and [PamAdapter].
 */
class CurrencyAdapter(
    channel: ManagedChannel
) : ICurrencyPort {

    private val stub: CurrencyServiceGrpc.CurrencyServiceBlockingStub =
        CurrencyServiceGrpc.newBlockingStub(channel)

    override suspend fun convertToUnits(amount: Double, currency: Currency): Long {
        val request = ToNanoRequest.newBuilder()
            .setAmount(BigDecimal.valueOf(amount).toPlainString())
            .setCurrency(currency.value)
            .build()

        return withContext(Dispatchers.IO) { stub.toNano(request) }.nano
    }

    override suspend fun convertFromUnits(amount: Long, currency: Currency): Double {
        val request = FromNanoRequest.newBuilder()
            .setNano(amount)
            .setCurrency(currency.value)
            .build()

        return withContext(Dispatchers.IO) { stub.fromNano(request) }.amount.toDouble()
    }
}
