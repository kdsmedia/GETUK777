package api.grpc.service

import api.grpc.config.handleGrpcCall
import api.grpc.mapper.AggregatorProtoMapper.toProto
import api.grpc.mapper.CasinoProviderProtoMapper.toProto
import application.Bus
import application.command.provider.SaveCasinoProviderCommand
import application.command.provider.SetCasinoProviderImageCommand
import com.nekgamebling.game.v1.BatchCasinoProviderQueryKt
import com.nekgamebling.game.v1.Empty
import com.nekgamebling.game.v1.FindAllCasinoProviderQueryKt
import com.nekgamebling.game.v1.FindAllCasinoProviderTagQueryKt
import com.nekgamebling.game.v1.FindCasinoProviderQueryKt
import com.nekgamebling.game.v1.CasinoProviderServiceGrpcKt
import com.nekgamebling.game.v1.UpdateCasinoProviderImageCommand
import domain.exception.notfound.CasinoProviderNotFoundException
import domain.vo.Country
import domain.vo.Identity
import domain.vo.Pageable
import com.nekgamebling.game.v1.BatchCasinoProviderQuery as BatchCasinoProviderProto
import com.nekgamebling.game.v1.FindAllCasinoProviderQuery as FindAllCasinoProviderProto
import com.nekgamebling.game.v1.FindAllCasinoProviderTagQuery as FindAllCasinoProviderTagProto
import com.nekgamebling.game.v1.FindCasinoProviderQuery as FindCasinoProviderProto
import com.nekgamebling.game.v1.SaveCasinoProviderCommand as SaveCasinoProviderProto
import application.query.provider.BatchCasinoProviderQuery as BatchCasinoProviderCqrs
import application.query.provider.FindAllCasinoProviderQuery as FindAllCasinoProviderCqrs
import application.query.provider.FindAllCasinoProviderTagQuery as FindAllCasinoProviderTagCqrs
import application.query.provider.FindCasinoProviderQuery as FindCasinoProviderCqrs

class CasinoProviderGrpcService(
    private val bus: Bus,
) : CasinoProviderServiceGrpcKt.CasinoProviderServiceCoroutineImplBase() {

    override suspend fun save(request: SaveCasinoProviderProto): Empty = handleGrpcCall {
        bus(
            SaveCasinoProviderCommand(
                identity = Identity(request.identity),
                name = request.name,
                order = request.order,
                active = request.active,
                aggregatorIdentity = Identity(request.aggregatorIdentity),
                blockedCountry = request.blockedCountryList.map { Country(it) },
                tags = request.tagsList,
                aliases = request.aliasesList,
            )
        )
        Empty.getDefaultInstance()
    }

    override suspend fun find(request: FindCasinoProviderProto): FindCasinoProviderProto.Result = handleGrpcCall {
        val provider = bus(FindCasinoProviderCqrs(identity = Identity(request.identity)))
            .orElseThrow { CasinoProviderNotFoundException() }

        FindCasinoProviderQueryKt.result {
            item = provider.toProto()
            aggregator = provider.aggregator.toProto()
        }
    }

    override suspend fun findAll(request: FindAllCasinoProviderProto): FindAllCasinoProviderProto.Result = handleGrpcCall {
        val filter = request.filter
        val page = bus(
            FindAllCasinoProviderCqrs(
                query = filter.query,
                active = if (filter.hasActive()) filter.active else null,
                aggregatorId = if (filter.hasAggregatorIdentity()) filter.aggregatorIdentity else null,
                inCollectionIdentities = filter.inCollectionIdentitiesList.map { Identity(it) },
                inTags = filter.tagsList,
                pageable = Pageable(request.pageNum, request.pageSize),
            )
        )

        val uniqueAggregators = page.items
            .map { it.aggregator }
            .distinctBy { it.identity.value }

        FindAllCasinoProviderQueryKt.result {
            items.addAll(page.items.map { it.toProto() })
            aggregators.addAll(uniqueAggregators.map { it.toProto() })
            totalItems = page.totalItems.toInt()
        }
    }

    override suspend fun findTagsAll(request: FindAllCasinoProviderTagProto): FindAllCasinoProviderTagProto.Result = handleGrpcCall {
        val page = bus(
            FindAllCasinoProviderTagCqrs(
                pageable = Pageable(request.pageNum, request.pageSize),
            )
        )

        FindAllCasinoProviderTagQueryKt.result {
            items.addAll(page.items)
            totalItems = page.totalItems.toInt()
        }
    }

    override suspend fun batch(request: BatchCasinoProviderProto): BatchCasinoProviderProto.Result = handleGrpcCall {
        val providers = bus(
            BatchCasinoProviderCqrs(
                identities = request.identitiesList.map { Identity(it) },
            )
        )

        val uniqueAggregators = providers.map { it.aggregator }.distinctBy { it.identity.value }

        BatchCasinoProviderQueryKt.result {
            items.addAll(providers.map { it.toProto() })
            aggregators.addAll(uniqueAggregators.map { it.toProto() })
        }
    }

    override suspend fun updateImage(request: UpdateCasinoProviderImageCommand): Empty = handleGrpcCall {
        bus(
            SetCasinoProviderImageCommand(
                identity = Identity(request.identity),
                key = request.key,
                url = request.url,
            )
        )
        Empty.getDefaultInstance()
    }
}
