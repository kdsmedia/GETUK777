package infrastructure.persistence.mapper

import domain.model.CasinoSession
import domain.vo.Currency
import domain.vo.Locale
import domain.vo.PlayerId
import domain.vo.CasinoSessionToken
import infrastructure.persistence.entity.CasinoSessionEntity
import infrastructure.persistence.mapper.CasinoGameVariantMapper.toDomain

object CasinoSessionMapper {

    fun CasinoSessionEntity.toDomain(): CasinoSession = CasinoSession(
        id = id.value,
        gameVariant = gameVariant.toDomain(),
        playerId = PlayerId(playerId),
        token = CasinoSessionToken(token),
        externalToken = externalToken,
        currency = Currency(currency),
        locale = Locale(locale),
        platform = platform,
    )
}
