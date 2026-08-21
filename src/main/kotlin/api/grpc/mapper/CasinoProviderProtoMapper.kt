package api.grpc.mapper

import com.nekgamebling.game.v1.CasinoProviderDto
import com.nekgamebling.game.v1.casinoProviderDto
import domain.model.CasinoProvider

object CasinoProviderProtoMapper {

    fun CasinoProvider.toProto(): CasinoProviderDto = casinoProviderDto {
        identity = this@toProto.identity.value
        name = this@toProto.name
        images.putAll(this@toProto.images.data)
        order = this@toProto.order
        active = this@toProto.active
        aggregatorIdentity = this@toProto.aggregator.identity.value
        blockedCountry.addAll(this@toProto.blockedCountry.map { it.value })
        tags.addAll(this@toProto.tags)
    }
}
