package infrastructure.persistence.entity

import infrastructure.persistence.table.CasinoGameVariantTable
import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID

class CasinoGameVariantEntity(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<CasinoGameVariantEntity>(CasinoGameVariantTable)

    var symbol by CasinoGameVariantTable.symbol
    var name by CasinoGameVariantTable.name
    var integration by CasinoGameVariantTable.integration
    var game by CasinoGameEntity referencedOn CasinoGameVariantTable.game
    var providerName by CasinoGameVariantTable.providerName
    var freeSpinEnable by CasinoGameVariantTable.freeSpinEnable
    var freeChipEnable by CasinoGameVariantTable.freeChipEnable
    var jackpotEnable by CasinoGameVariantTable.jackpotEnable
    var demoEnable by CasinoGameVariantTable.demoEnable
    var bonusBuyEnable by CasinoGameVariantTable.bonusBuyEnable
    var locales by CasinoGameVariantTable.locales
    var platforms by CasinoGameVariantTable.platforms
    var playLines by CasinoGameVariantTable.playLines
}
