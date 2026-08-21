package infrastructure.persistence.mapper

import domain.model.CasinoGameVariant
import domain.model.Platform
import domain.vo.CasinoGameSymbol
import domain.vo.Locale
import infrastructure.persistence.entity.CasinoGameVariantEntity
import infrastructure.persistence.mapper.CasinoGameMapper.toDomain

object CasinoGameVariantMapper {

    fun CasinoGameVariantEntity.toDomain(): CasinoGameVariant = CasinoGameVariant(
        id = id.value,
        symbol = CasinoGameSymbol(symbol),
        name = name,
        integration = integration,
        game = game.toDomain(),
        providerName = providerName,
        freeSpinEnable = freeSpinEnable,
        freeChipEnable = freeChipEnable,
        jackpotEnable = jackpotEnable,
        demoEnable = demoEnable,
        bonusBuyEnable = bonusBuyEnable,
        locales = locales.map { Locale(it) },
        platforms = platforms.map { Platform.valueOf(it) },
        playLines = playLines,
    )
}
