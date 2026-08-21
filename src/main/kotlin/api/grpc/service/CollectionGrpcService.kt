package api.grpc.service

import api.grpc.config.handleGrpcCall
import api.grpc.mapper.CollectionProtoMapper.toProto
import api.grpc.mapper.CasinoGameFilterProtoMapper.toDomain
import api.grpc.mapper.CasinoGamePageProtoMapper.toCasinoGamePageDto
import application.Bus
import application.command.collection.SaveCollectionCommand
import application.command.collection.SetCollectionImageCommand
import application.query.game.FindAllCasinoGameCollectionQuery
import com.nekgamebling.game.v1.BatchCollectionQueryKt
import com.nekgamebling.game.v1.CollectionServiceGrpcKt
import com.nekgamebling.game.v1.Empty
import com.nekgamebling.game.v1.FindAllCollectionQueryKt
import com.nekgamebling.game.v1.FindCollectionQueryKt
import com.nekgamebling.game.v1.CasinoGamePageDto
import com.nekgamebling.game.v1.UpdateCollectionImageCommand
import domain.exception.notfound.CollectionNotFoundException
import domain.vo.Identity
import domain.vo.LocaleName
import domain.vo.Pageable
import com.nekgamebling.game.v1.AddCollectionCasinoGameCommand as AddCollectionCasinoGameProto
import com.nekgamebling.game.v1.BatchCollectionQuery as BatchCollectionProto
import com.nekgamebling.game.v1.DeleteCollectionCommand as DeleteCollectionProto
import com.nekgamebling.game.v1.FindAllCollectionQuery as FindAllCollectionProto
import com.nekgamebling.game.v1.FindAllCasinoGameCollectionQuery as FindAllCasinoGameCollectionProto
import com.nekgamebling.game.v1.FindCollectionQuery as FindCollectionProto
import com.nekgamebling.game.v1.RemoveCollectionCasinoGameCommand as RemoveCollectionCasinoGameProto
import com.nekgamebling.game.v1.SaveCollectionCommand as SaveCollectionProto
import com.nekgamebling.game.v1.UpdateCollectionCasinoGameOrderCommand as UpdateCollectionCasinoGameOrderProto
import application.command.collection.AddCollectionCasinoGameCommand as AddCollectionCasinoGameCqrs
import application.command.collection.DeleteCollectionCommand as DeleteCollectionCqrs
import application.command.collection.RemoveCollectionCasinoGameCommand as RemoveCollectionCasinoGameCqrs
import application.command.collection.UpdateCollectionCasinoGameOrderCommand as UpdateCollectionCasinoGameOrderCqrs
import application.query.collection.BatchCollectionQuery as BatchCollectionCqrs
import application.query.collection.FindAllCollectionQuery as FindAllCollectionCqrs
import application.query.collection.FindCollectionQuery as FindCollectionCqrs

class CollectionGrpcService(
    private val bus: Bus,
) : CollectionServiceGrpcKt.CollectionServiceCoroutineImplBase() {

    override suspend fun save(request: SaveCollectionProto): Empty = handleGrpcCall {
        bus(
            SaveCollectionCommand(
                identity = Identity(request.identity),
                name = LocaleName(request.nameMap),
                tags = request.tagsList,
                active = request.active,
                order = request.order,
            )
        )
        Empty.getDefaultInstance()
    }

    override suspend fun find(request: FindCollectionProto): FindCollectionProto.Result = handleGrpcCall {
        val collection = bus(FindCollectionCqrs(identity = Identity(request.identity)))
            .orElseThrow { CollectionNotFoundException() }

        FindCollectionQueryKt.result {
            item = collection.toProto()
        }
    }

    override suspend fun findAll(request: FindAllCollectionProto): FindAllCollectionProto.Result = handleGrpcCall {
        val filter = request.filter
        val page = bus(
            FindAllCollectionCqrs(
                query = filter.query,
                active = if (filter.hasActive()) filter.active else null,
                inTags = filter.inTagsList,
                inProviderIdentities = filter.inProviderIdentitiesList.map { Identity(it) },
                pageable = Pageable(request.pageNum, request.pageSize),
            )
        )

        FindAllCollectionQueryKt.result {
            items.addAll(page.items.map { it.toProto() })
            totalItems = page.totalItems.toInt()
        }
    }

    override suspend fun batch(request: BatchCollectionProto): BatchCollectionProto.Result = handleGrpcCall {
        val collections = bus(
            BatchCollectionCqrs(
                identities = request.identitiesList.map { Identity(it) },
            )
        )

        BatchCollectionQueryKt.result {
            items.addAll(collections.map { it.toProto() })
        }
    }

    override suspend fun findAllCasinoGame(request: FindAllCasinoGameCollectionProto): CasinoGamePageDto = handleGrpcCall {
        val page = bus(
            FindAllCasinoGameCollectionQuery(
                collection = Identity(request.collectionIdentity),
                filter = request.filter.toDomain(),
                pageable = Pageable(request.pageNum, request.pageSize),
            )
        )

        page.toCasinoGamePageDto()
    }

    override suspend fun addCasinoGame(request: AddCollectionCasinoGameProto): Empty = handleGrpcCall {
        bus(
            AddCollectionCasinoGameCqrs(
                identity = Identity(request.identity),
                gameIdentity = Identity(request.gameIdentity),
            )
        )
        Empty.getDefaultInstance()
    }

    override suspend fun removeCasinoGame(request: RemoveCollectionCasinoGameProto): Empty = handleGrpcCall {
        bus(
            RemoveCollectionCasinoGameCqrs(
                identity = Identity(request.identity),
                gameIdentity = Identity(request.gameIdentity),
            )
        )
        Empty.getDefaultInstance()
    }

    override suspend fun updateCasinoGameOrder(request: UpdateCollectionCasinoGameOrderProto): Empty = handleGrpcCall {
        bus(
            UpdateCollectionCasinoGameOrderCqrs(
                identity = Identity(request.identity),
                gameIdentity = Identity(request.gameIdentity),
                order = request.order,
            )
        )
        Empty.getDefaultInstance()
    }

    override suspend fun updateImage(request: UpdateCollectionImageCommand): Empty = handleGrpcCall {
        bus(
            SetCollectionImageCommand(
                identity = Identity(request.identity),
                key = request.key,
                url = request.url,
            )
        )
        Empty.getDefaultInstance()
    }

    override suspend fun delete(request: DeleteCollectionProto): Empty = handleGrpcCall {
        bus(DeleteCollectionCqrs(identity = Identity(request.identity)))
        Empty.getDefaultInstance()
    }
}
