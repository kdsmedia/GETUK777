package infrastructure.wallet

import application.port.external.ICurrencyPort
import com.nekgamebling.wallet.v1.ConvertFromSystemUnitRequest
import com.nekgamebling.wallet.v1.ConvertToSystemUnitRequest
import com.nekgamebling.wallet.v1.CurrencyServiceGrpc
import domain.vo.Currency
import io.grpc.ManagedChannel
import java.math.BigDecimal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Currency conversion backed by wallet-engine's gRPC CurrencyService. The
 * system unit is nano (1e9), currency-independent. Shares the same wallet-engine
 * channel as [WalletAdapter].
 */
class CurrencyAdapter(
    channel: ManagedChannel
) : ICurrencyPort {

    private val stub: CurrencyServiceGrpc.CurrencyServiceBlockingStub =
        CurrencyServiceGrpc.newBlockingStub(channel)

    override suspend fun convertToUnits(amount: Double, currency: Currency): Long {
        val request = ConvertToSystemUnitRequest.newBuilder()
            .setAmount(BigDecimal.valueOf(amount).toPlainString())
            .setCurrency(currency.value)
            .build()

        return withContext(Dispatchers.IO) { stub.convertToSystemUnit(request) }.amount
    }

    override suspend fun convertFromUnits(amount: Long, currency: Currency): Double {
        val request = ConvertFromSystemUnitRequest.newBuilder()
            .setAmount(amount)
            .setCurrency(currency.value)
            .build()

        return withContext(Dispatchers.IO) { stub.convertFromSystemUnit(request) }.amount.toDouble()
    }
}
