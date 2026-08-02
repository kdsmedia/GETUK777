package domain.model

import domain.util.Activatable
import domain.util.Imageable
import domain.vo.Identity
import domain.vo.ImageMap
import domain.vo.LocaleName
import kotlinx.serialization.Serializable

@Serializable
data class Collection(
    val identity: Identity,

    val name: LocaleName,

    val tags: List<String> = emptyList(),

    override var images: ImageMap = ImageMap.EMPTY,

    override var active: Boolean = true,

    val order: Int = 100
) : Activatable, Imageable
