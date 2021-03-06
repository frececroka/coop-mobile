package de.lorenzgorse.coopmobile

import de.lorenzgorse.coopmobile.coopclient.UnitValue

object MockCoopData {

    val coopData1 = listOf(
        UnitValue("Guthaben", 123.45F, "CHF"),
        UnitValue("Mobile Daten", 666F, "GB"),
        UnitValue("Telefon Inland", 102F, "Einheiten"),
        UnitValue("Telefon Ausland", 0F, "Einheiten")
    )

    val coopData2 = listOf(
        UnitValue("Guthaben", 121.45F, "CHF"),
        UnitValue("Mobile Daten", 660F, "GB"),
        UnitValue("Telefon Inland", 89F, "Einheiten")
    )

}
