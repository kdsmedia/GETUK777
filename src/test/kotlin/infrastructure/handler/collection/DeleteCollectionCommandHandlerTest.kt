package infrastructure.handler.collection

import application.command.collection.DeleteCollectionCommand
import domain.exception.notfound.CollectionNotFoundException
import domain.model.Collection
import domain.repository.ICollectionRepository
import domain.vo.Identity
import domain.vo.Page
import domain.vo.Pageable
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Fake-based tests for [DeleteCollectionCommandHandler] — same pattern as
 * `CollectionGameMembershipHandlerTest` (no mockk: value-class parameters trip
 * its signature generator).
 *
 * The fake models what the Exposed repository guarantees: deleting a rail drops
 * its memberships and never touches the games on the other side of them.
 */
class DeleteCollectionCommandHandlerTest : FunSpec({

    class FakeCollectionRepo(
        private val collections: MutableSet<String>,
        private val membership: MutableSet<Pair<String, String>> = mutableSetOf(),
        private val games: MutableSet<String> = mutableSetOf(),
    ) : ICollectionRepository {
        val deleteCalls = mutableListOf<Identity>()

        fun memberships() = membership.toSet()
        fun games() = games.toSet()

        override suspend fun save(collection: Collection): Collection = collection
        override suspend fun findByIdentity(identity: Identity): Collection? = null
        override suspend fun findAll(pageable: Pageable): Page<Collection> = Page(emptyList(), 0, 0, 0)
        override suspend fun addImage(identity: Identity, key: String, url: String) = Unit
        override suspend fun addGame(identity: Identity, gameIdentity: Identity) = Unit
        override suspend fun removeGame(identity: Identity, gameIdentity: Identity) = Unit
        override suspend fun updateGameOrder(identity: Identity, gameIdentity: Identity, order: Int) = Unit

        override suspend fun deleteByIdentity(identity: Identity) {
            if (identity.value !in collections) throw CollectionNotFoundException()
            deleteCalls += identity
            collections -= identity.value
            membership.removeAll { (collection, _) -> collection == identity.value }
        }
    }

    test("deletes the collection and forwards the identity to the repository") {
        val repo = FakeCollectionRepo(collections = mutableSetOf("tournaments", "popular_now"))
        val handler = DeleteCollectionCommandHandler(repo)

        val result = handler.handle(DeleteCollectionCommand(identity = Identity("tournaments")))

        result.isSuccess shouldBe true
        repo.deleteCalls.single() shouldBe Identity("tournaments")
    }

    test("drops the rail's memberships but keeps the games and other rails") {
        val repo = FakeCollectionRepo(
            collections = mutableSetOf("tournaments", "popular_now"),
            membership = mutableSetOf(
                "tournaments" to "gates_of_olympus",
                "tournaments" to "sweet_bonanza",
                "popular_now" to "gates_of_olympus",
            ),
            games = mutableSetOf("gates_of_olympus", "sweet_bonanza"),
        )
        val handler = DeleteCollectionCommandHandler(repo)

        handler.handle(DeleteCollectionCommand(identity = Identity("tournaments"))).isSuccess shouldBe true

        repo.memberships() shouldContainExactly setOf("popular_now" to "gates_of_olympus")
        repo.games() shouldContainExactly setOf("gates_of_olympus", "sweet_bonanza")
    }

    test("unknown collection raises CollectionNotFoundException") {
        val repo = FakeCollectionRepo(collections = mutableSetOf("popular_now"))
        val handler = DeleteCollectionCommandHandler(repo)

        val result = handler.handle(DeleteCollectionCommand(identity = Identity("does_not_exist")))

        result.isFailure shouldBe true
        result.exceptionOrNull().shouldBeInstanceOf<CollectionNotFoundException>()
        repo.deleteCalls.shouldContainExactly(emptyList())
    }

    test("deleting twice fails the second time — the rail is really gone") {
        val repo = FakeCollectionRepo(collections = mutableSetOf("tournaments"))
        val handler = DeleteCollectionCommandHandler(repo)

        handler.handle(DeleteCollectionCommand(identity = Identity("tournaments"))).isSuccess shouldBe true
        val second = handler.handle(DeleteCollectionCommand(identity = Identity("tournaments")))

        second.isFailure shouldBe true
        second.exceptionOrNull().shouldBeInstanceOf<CollectionNotFoundException>()
    }
})
