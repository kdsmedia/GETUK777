package infrastructure.aggregator.gambutsoft.client.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `POST /auth/login`. `refreshToken` is deliberately absent: the access token lives an hour, a
 * catalogue sync finishes in seconds, and every new token the vendor mints invalidates the
 * previous one — so nothing here ever refreshes.
 */
@Serializable
data class LoginResponseDto(
    val accessToken: String,

    val user: UserDto = UserDto(),
)

@Serializable
data class UserDto(
    val id: String = "",

    val login: String = "",

    /** Currencies our account holds. Each one has its own game list. */
    val currencies: List<UserCurrencyDto> = emptyList(),
)

@Serializable
data class UserCurrencyDto(
    val currency: CurrencyDto = CurrencyDto(),
)

@Serializable
data class CurrencyDto(
    val code: String = "",
)

/**
 * One entry of `GET /users/{user_id}/getUserGames/{currencyISO}`.
 *
 * `id` is the vendor's compound `<integration>:<provider>:<game>` key and round-trips verbatim
 * into `openGame.gameId`. `category` is undocumented but present on every row of the live feed.
 */
@Serializable
data class GameCatalogItemDto(
    val id: String,

    val isEnabled: Boolean = false,

    val title: String = "",

    val imageUrl: String = "",

    val provider: String = "",

    val category: String = "",
)

/**
 * `POST /games/openGame`. Serialized once and signed byte-for-byte, so absent optionals must stay
 * absent rather than serialize as `null` — the client's Json is configured accordingly.
 */
@Serializable
data class OpenGameRequestDto(
    val currency: String,

    val demo: String,

    val exitUrl: String,

    val gameId: String,

    val language: String,

    @SerialName("player_login") val playerLogin: String,

    @SerialName("user_id") val userId: String,

    val callbackUrl: String? = null,
)

@Serializable
data class OpenGameResponseDto(
    val status: String = "",

    val error: String = "",

    val content: OpenGameContentDto = OpenGameContentDto(),
)

@Serializable
data class OpenGameContentDto(
    val game: OpenGameUrlDto = OpenGameUrlDto(),

    val gameRes: OpenGameSessionDto = OpenGameSessionDto(),
)

@Serializable
data class OpenGameUrlDto(
    val url: String = "",
)

@Serializable
data class OpenGameSessionDto(
    val sessionId: String = "",
)
