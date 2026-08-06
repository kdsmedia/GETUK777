package infrastructure.handler.common

import application.command.collection.SetCollectionImageCommand
import application.command.game.SetGameImageCommand
import application.command.provider.SetProviderImageCommand
import domain.exception.badrequest.BlankImageUrlException
import domain.model.Collection
import domain.model.Game
import domain.model.Provider
import domain.repository.ICollectionRepository
import domain.repository.IGameRepository
import domain.repository.IProviderRepository
import domain.vo.Identity
import domain.vo.Page
import domain.vo.Pageable
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * Verifies the polymorphic [SetImageCommandHandler] dispatches each sealed sub-command
 * to the correct repository. Uses hand-rolled fake repositories instead of mockk because
 * mockk's argument matchers trip over `@JvmInline value class Identity` (signature
 * generator instantiates the value class with a bogus default, triggering the init
 * validation).
 */
class SetImageCommandHandlerTest : FunSpec({

    val sampleUrl = "https://cdn.example.com/casino/game/game_a/main.webp"

    class FakeGameRepo : IGameRepository {
        val calls = mutableListOf<Triple<Identity, String, String>>()
        override suspend fun save(game: Game): Game = game
        override suspend fun saveAll(gameList: List<Game>): List<Game> = gameList
        override suspend fun findByIdentity(identity: Identity): Game? = null
        override suspend fun findAll(pageable: Pageable): Page<Game> = Page(emptyList(), 0, 0, 0)
        override suspend fun findAll(): List<Game> = emptyList()
        override suspend fun addImage(identity: Identity, key: String, url: String) {
            calls += Triple(identity, key, url)
        }
    }

    class FakeProviderRepo : IProviderRepository {
        val calls = mutableListOf<Triple<Identity, String, String>>()
        override suspend fun save(provider: Provider): Provider = provider
        override suspend fun saveAll(providers: List<Provider>): List<Provider> = providers
        override suspend fun findByIdentity(identity: Identity): Provider? = null
        override suspend fun findAll(pageable: Pageable): Page<Provider> = Page(emptyList(), 0, 0, 0)
        override suspend fun findAll(): List<Provider> = emptyList()
        override suspend fun addImage(identity: Identity, key: String, url: String) {
            calls += Triple(identity, key, url)
        }
    }

    class FakeCollectionRepo : ICollectionRepository {
        val calls = mutableListOf<Triple<Identity, String, String>>()
        override suspend fun save(collection: Collection): Collection = collection
        override suspend fun findByIdentity(identity: Identity): Collection? = null
        override suspend fun findAll(pageable: Pageable): Page<Collection> = Page(emptyList(), 0, 0, 0)
        override suspend fun addImage(identity: Identity, key: String, url: String) {
            calls += Triple(identity, key, url)
        }
        override suspend fun addGame(identity: Identity, gameIdentity: Identity) = Unit
        override suspend fun removeGame(identity: Identity, gameIdentity: Identity) = Unit
        override suspend fun updateGameOrder(identity: Identity, gameIdentity: Identity, order: Int) = Unit
        override suspend fun deleteByIdentity(identity: Identity) = Unit
    }

    fun handler(
        gameRepo: FakeGameRepo = FakeGameRepo(),
        providerRepo: FakeProviderRepo = FakeProviderRepo(),
        collectionRepo: FakeCollectionRepo = FakeCollectionRepo(),
    ) = SetImageCommandHandler(
        gameRepository = gameRepo,
        providerRepository = providerRepo,
        collectionRepository = collectionRepo,
    )

    test("SetGameImageCommand stores the URL via the game repository") {
        val gameRepo = FakeGameRepo()
        val providerRepo = FakeProviderRepo()
        val collectionRepo = FakeCollectionRepo()

        val result = handler(gameRepo, providerRepo, collectionRepo)
            .handle(SetGameImageCommand(Identity("game_a"), "main", sampleUrl))

        result.isSuccess shouldBe true
        gameRepo.calls.single() shouldBe Triple(Identity("game_a"), "main", sampleUrl)
        providerRepo.calls.size shouldBe 0
        collectionRepo.calls.size shouldBe 0
    }

    test("SetProviderImageCommand stores the URL via the provider repository") {
        val gameRepo = FakeGameRepo()
        val providerRepo = FakeProviderRepo()
        val collectionRepo = FakeCollectionRepo()

        val result = handler(gameRepo, providerRepo, collectionRepo)
            .handle(SetProviderImageCommand(Identity("prov_a"), "logo", sampleUrl))

        result.isSuccess shouldBe true
        providerRepo.calls.single() shouldBe Triple(Identity("prov_a"), "logo", sampleUrl)
        gameRepo.calls.size shouldBe 0
        collectionRepo.calls.size shouldBe 0
    }

    test("SetCollectionImageCommand stores the URL via the collection repository") {
        val gameRepo = FakeGameRepo()
        val providerRepo = FakeProviderRepo()
        val collectionRepo = FakeCollectionRepo()

        val result = handler(gameRepo, providerRepo, collectionRepo)
            .handle(SetCollectionImageCommand(Identity("coll_a"), "cover", sampleUrl))

        result.isSuccess shouldBe true
        collectionRepo.calls.single() shouldBe Triple(Identity("coll_a"), "cover", sampleUrl)
        gameRepo.calls.size shouldBe 0
        providerRepo.calls.size shouldBe 0
    }

    test("blank URL is rejected with BlankImageUrlException and nothing is stored") {
        val gameRepo = FakeGameRepo()

        val result = handler(gameRepo = gameRepo)
            .handle(SetGameImageCommand(Identity("game_a"), "main", "  "))

        result.isFailure shouldBe true
        shouldThrow<BlankImageUrlException> { result.getOrThrow() }
        gameRepo.calls.size shouldBe 0
    }
})
