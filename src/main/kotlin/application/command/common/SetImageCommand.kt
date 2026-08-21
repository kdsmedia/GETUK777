package application.command.common

import application.ICommand
import domain.vo.Identity

/**
 * Polymorphic command for setting an image on any `Imageable` aggregate.
 *
 * The engine does not accept file content — callers upload to object storage
 * themselves and pass the final public URL. Concrete subclasses
 * (`SetCasinoGameImageCommand`, `SetCasinoProviderImageCommand`, `SetCollectionImageCommand`)
 * only route to the right repository; a single `SetImageCommandHandler` persists
 * the URL without per-entity duplication.
 */
interface SetImageCommand : ICommand<Unit> {

    val identity: Identity

    val key: String

    val url: String
}
