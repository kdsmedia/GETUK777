package api.grpc.mapper

import api.grpc.mapper.AggregatorProtoMapper.toProto
import api.grpc.mapper.CollectionProtoMapper.toProto
import api.grpc.mapper.CasinoGameProtoMapper.toProto
import api.grpc.mapper.CasinoProviderProtoMapper.toProto
import application.query.game.CasinoGameView
import com.nekgamebling.game.v1.CasinoGamePageDto
import com.nekgamebling.game.v1.casinoGamePageDto
import domain.vo.Page

/**
 * Shared mapping from a page of [CasinoGameView]s to the wire-level [CasinoGamePageDto].
 * Used by every paged game-listing RPC (`CasinoGameService.FindAll`,
 * `CasinoGameService.FindAllPlayerFavourite`, `CollectionService.FindAllCasinoGame`) so
 * the denormalization logic lives in exactly one place.
 */
object CasinoGamePageProtoMapper {

    fun Page<CasinoGameView>.toCasinoGamePageDto(): CasinoGamePageDto {
        val uniqueProviders = items
            .map { it.game.provider }
            .distinctBy { it.identity.value }
        val uniqueAggregators = uniqueProviders
            .map { it.aggregator }
            .distinctBy { it.identity.value }
        val uniqueCollections = items
            .flatMap { it.game.collections }
            .distinctBy { it.identity.value }

        return casinoGamePageDto {
            items.addAll(this@toCasinoGamePageDto.items.map { it.game.toProto(it.variant) })
            providers.addAll(uniqueProviders.map { it.toProto() })
            aggregators.addAll(uniqueAggregators.map { it.toProto() })
            collections.addAll(uniqueCollections.map { it.toProto() })
            totalItems = this@toCasinoGamePageDto.totalItems.toInt()
        }
    }
}
