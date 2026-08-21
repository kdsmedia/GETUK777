package infrastructure.handler.collection

import application.command.collection.AddCollectionCasinoGameCommand
import application.command.collection.RemoveCollectionCasinoGameCommand
import application.command.collection.UpdateCollectionCasinoGameOrderCommand
import domain.exception.notfound.CollectionNotFoundException
import domain.exception.notfound.CasinoGameNotFoundException
import domain.model.Collection
import domain.repository.ICollectionRepository
import domain.vo.Identity
import domain.vo.Page
import domain.vo.Pageable
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Fake-based tests for the three single-game collection-membership handlers.
 * Mirrors the pattern in `SetImageCommandHandlerTest` — no mockk because
 * value-class parameters trip mockk's signature generator.
 */
class CollectionCasinoGameMembershipHandlerTest : FunSpec({

    class FakeCollectionRepo(
        private val knownCollections: Set<String>,
        private val knownGames: Set<String>,
        private val membership: MutableSet<Pair<String, String>> = mutableSetOf(),
    ) : ICollectionRepository {
        val addCalls = mutableListOf<Pair<Identity, Identity>>()
        val removeCalls = mutableListOf<Pair<Identity, Identity>>()
        val orderCalls = mutableListOf<Triple<Identity, Identity, Int>>()

        override suspend fun save(collection: Collection): Collection = collection
        override suspend fun findByIdentity(identity: Identity): Collection? = null
        override suspend fun findAll(pageable: Pageable): Page<Collection> = Page(emptyList(), 0, 0, 0)
        override suspend fun addImage(identity: Identity, key: String, url: String) = Unit

        override suspend fun addCasinoGame(identity: Identity, gameIdentity: Identity) {
            if (identity.value !in knownCollections) throw CollectionNotFoundException()
            if (gameIdentity.value !in knownGames) throw CasinoGameNotFoundException()
            addCalls += identity to gameIdentity
            membership += identity.value to gameIdentity.value
        }

        override suspend fun removeCasinoGame(identity: Identity, gameIdentity: Identity) {
            if (identity.value !in knownCollections) throw CollectionNotFoundException()
            if (gameIdentity.value !in knownGames) throw CasinoGameNotFoundException()
            removeCalls += identity to gameIdentity
            membership -= identity.value to gameIdentity.value
        }

        override suspend fun updateCasinoGameOrder(identity: Identity, gameIdentity: Identity, order: Int) {
            if (identity.value !in knownCollections) throw CollectionNotFoundException()
            if (gameIdentity.value !in knownGames) throw CasinoGameNotFoundException()
            // Simulate "game must be a member of this collection" check.
            if ((identity.value to gameIdentity.value) !in membership) throw CasinoGameNotFoundException()
            orderCalls += Triple(identity, gameIdentity, order)
        }

        override suspend fun deleteByIdentity(identity: Identity) {
            if (identity.value !in knownCollections) throw CollectionNotFoundException()
            membership.removeAll { (collection, _) -> collection == identity.value }
        }
    }

    // ---------------------------------------------------------------------
    // AddCollectionCasinoGameCommandHandler
    // ---------------------------------------------------------------------

    test("AddCollectionCasinoGame — happy path forwards identities to repository") {
        val repo = FakeCollectionRepo(
            knownCollections = setOf("popular"),
            knownGames = setOf("g1"),
        )
        val handler = AddCollectionCasinoGameCommandHandler(repo)

        val result = handler.handle(
            AddCollectionCasinoGameCommand(
                identity = Identity("popular"),
                gameIdentity = Identity("g1"),
            )
        )

        result.isSuccess shouldBe true
        repo.addCalls.single() shouldBe (Identity("popular") to Identity("g1"))
    }

    test("AddCollectionCasinoGame — missing collection raises CollectionNotFoundException") {
        val repo = FakeCollectionRepo(
            knownCollections = emptySet(),
            knownGames = setOf("g1"),
        )
        val handler = AddCollectionCasinoGameCommandHandler(repo)

        val result = handler.handle(
            AddCollectionCasinoGameCommand(Identity("ghost"), Identity("g1"))
        )

        result.isFailure shouldBe true
        result.exceptionOrNull().shouldBeInstanceOf<CollectionNotFoundException>()
        repo.addCalls.size shouldBe 0
    }

    test("AddCollectionCasinoGame — missing game raises CasinoGameNotFoundException") {
        val repo = FakeCollectionRepo(
            knownCollections = setOf("popular"),
            knownGames = emptySet(),
        )
        val handler = AddCollectionCasinoGameCommandHandler(repo)

        val result = handler.handle(
            AddCollectionCasinoGameCommand(Identity("popular"), Identity("missing"))
        )

        result.isFailure shouldBe true
        result.exceptionOrNull().shouldBeInstanceOf<CasinoGameNotFoundException>()
    }

    // ---------------------------------------------------------------------
    // RemoveCollectionCasinoGameCommandHandler
    // ---------------------------------------------------------------------

    test("RemoveCollectionCasinoGame — happy path forwards identities to repository") {
        val repo = FakeCollectionRepo(
            knownCollections = setOf("popular"),
            knownGames = setOf("g1"),
            membership = mutableSetOf("popular" to "g1"),
        )
        val handler = RemoveCollectionCasinoGameCommandHandler(repo)

        val result = handler.handle(
            RemoveCollectionCasinoGameCommand(Identity("popular"), Identity("g1"))
        )

        result.isSuccess shouldBe true
        repo.removeCalls.single() shouldBe (Identity("popular") to Identity("g1"))
    }

    test("RemoveCollectionCasinoGame — missing collection raises CollectionNotFoundException") {
        val repo = FakeCollectionRepo(
            knownCollections = emptySet(),
            knownGames = setOf("g1"),
        )
        val handler = RemoveCollectionCasinoGameCommandHandler(repo)

        val result = handler.handle(
            RemoveCollectionCasinoGameCommand(Identity("ghost"), Identity("g1"))
        )

        result.isFailure shouldBe true
        result.exceptionOrNull().shouldBeInstanceOf<CollectionNotFoundException>()
    }

    // ---------------------------------------------------------------------
    // UpdateCollectionCasinoGameOrderCommandHandler
    // ---------------------------------------------------------------------

    test("UpdateCollectionCasinoGameOrder — happy path forwards order to repository") {
        val repo = FakeCollectionRepo(
            knownCollections = setOf("popular"),
            knownGames = setOf("g1"),
            membership = mutableSetOf("popular" to "g1"),
        )
        val handler = UpdateCollectionCasinoGameOrderCommandHandler(repo)

        val result = handler.handle(
            UpdateCollectionCasinoGameOrderCommand(
                identity = Identity("popular"),
                gameIdentity = Identity("g1"),
                order = 7,
            )
        )

        result.isSuccess shouldBe true
        repo.orderCalls.single() shouldBe Triple(Identity("popular"), Identity("g1"), 7)
    }

    test("UpdateCollectionCasinoGameOrder — game not in collection raises CasinoGameNotFoundException") {
        val repo = FakeCollectionRepo(
            knownCollections = setOf("popular"),
            knownGames = setOf("g1"),
            membership = mutableSetOf(), // known game but NOT in this collection
        )
        val handler = UpdateCollectionCasinoGameOrderCommandHandler(repo)

        val result = handler.handle(
            UpdateCollectionCasinoGameOrderCommand(Identity("popular"), Identity("g1"), 0)
        )

        result.isFailure shouldBe true
        result.exceptionOrNull().shouldBeInstanceOf<CasinoGameNotFoundException>()
    }
})
