package de.lorenzgorse.coopmobile

object MockCoopData {

    val coopData1 = CoopData(
        UnitValue("Guthaben", 123.45F, "CHF"),
        listOf(
            UnitValue("Mobile Daten", 666, "GB"),
            UnitValue("Telefon Inland", 102, "Einheiten"),
            UnitValue("Telefon Ausland", 0, "Einheiten")
        )
    )

    val coopData2 = CoopData(
        UnitValue("Guthaben", 121.45F, "CHF"),
        listOf(
            UnitValue("Mobile Daten", 660, "GB"),
            UnitValue("Telefon Inland", 89, "Einheiten")
        )
    )

}
