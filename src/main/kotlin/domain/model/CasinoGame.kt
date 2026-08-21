package domain.model

import domain.util.Activatable
import domain.util.Imageable
import domain.util.Orderable
import domain.vo.Identity
import domain.vo.ImageMap
import kotlinx.serialization.Serializable

@Serializable
data class CasinoGame(
    val identity: Identity,

    val name: String,

    val provider: CasinoProvider,

    val collections: List<Collection> = emptyList(),

    val bonusBetEnable: Boolean = true,

    val bonusWageringEnable: Boolean = true,

    val tags: List<String> = emptyList(),

    val rtp: Double = DEFAULT_RTP,

    override var active: Boolean = false,

    override var images: ImageMap = ImageMap.EMPTY,

    override var order: Int = 0,
) : Activatable, Imageable, Orderable {

    companion object {
        const val DEFAULT_RTP = 96.0
    }
}
