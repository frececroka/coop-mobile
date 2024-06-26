package de.lorenzgorse.coopmobile

import android.content.Context
import arrow.core.Either
import de.lorenzgorse.coopmobile.client.*
import de.lorenzgorse.coopmobile.client.simple.CoopClient
import de.lorenzgorse.coopmobile.client.simple.CoopLogin
import de.lorenzgorse.coopmobile.components.Fuse
import java.net.URL
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicReference

class TestAccounts(context: Context) {

    // The list of test accounts; login information and data
    // are defined in TestModeCoopLogin and TestModeCoopClient
    private val testAccounts = setOf(
        "0780000000",
        "prepaid@example.com",
        "wireless@example.com"
    )

    fun isTestAccount(username: String): Boolean {
        return testAccounts.contains(username)
    }

    // This fuse represents the runtime condition "we are in test account mode"
    private val fuse = Fuse(context, "test_account_mode")
    fun modeActive() = fuse.isBurnt()
    fun activate() = fuse.burn()
    fun deactivate() = fuse.mend()

}

class TestModeCoopLogin : CoopLogin {

    // TODO: log analytics event every time this is
    //  constructed, just to be safe this doesn't reach users

    // The test accounts; map of username to password
    private val testAccounts = mapOf(
        "0780000000" to "prepaid",
        "prepaid@example.com" to "prepaid",
        "wireless@example.com" to "wireless",
    )

    override suspend fun login(
        username: String,
        password: String,
        origin: CoopLogin.Origin,
        plan: AtomicReference<String?>?
    ): String? {
        if (testAccounts[username] == password) {
            plan?.set(password)
            return "test-session-id $username ${Instant.now()}"
        } else {
            return null
        }
    }

}

class TestModeCoopClient : CoopClient {

    // TODO: add data for more methods

    // TODO: expire session after some
    //  time to test session refresh logic

    override suspend fun getProfile() = Either.Right(
        listOf(
            ProfileItem("Kundennummer", "12345678"),
            ProfileItem("Status", "aktiv"),
            ProfileItem("Inhaber", "Peter Lustig"),
            ProfileItem("E-Mail Adresse", "peter@lustig.de"),
            ProfileItem("Handynummer", "078 123 45 67"),
        )
    )

    override suspend fun getConsumption() = Either.Right(
        listOf(
            LabelledAmounts(
                LabelledAmounts.Kind.Credit,
                "Guthaben",
                listOf(LabelledAmount("verbleibend", Amount(1.23, AmountUnit("CHF"))))
            ),
            LabelledAmounts(
                LabelledAmounts.Kind.DataSwitzerland,
                "Daten in der Schweiz",
                listOf(
                    LabelledAmount("verbleibend", Amount(4.56, AmountUnit("GB"))),
                    LabelledAmount("Diesen Monat genutzt", Amount(7.89, AmountUnit("GB"))),
                )
            ),
            LabelledAmounts(
                LabelledAmounts.Kind.DataEurope,
                "Daten in der EU",
                listOf(
                    LabelledAmount(
                        "verbleibend",
                        Amount(Double.POSITIVE_INFINITY, AmountUnit("unbegrenzt"))
                    )
                )
            )
        )
    )

    override suspend fun getConsumptionLog(): Either.Right<List<ConsumptionLogEntry>> {
        fun mk(time: String, value: Double) =
            ConsumptionLogEntry(Instant.parse(time), "Daten in der Schweiz", value)
        return Either.Right(
            listOf(
                mk("2022-02-03T10:15:30.00Z", 1234.56),
                mk("2022-02-04T10:15:30.00Z", 1230.56),
                mk("2022-02-05T10:15:30.00Z", 1204.56),
                mk("2022-02-06T10:15:30.00Z", 1034.56),
                mk("2022-02-07T10:15:30.00Z", 234.56),
                mk("2022-02-08T10:15:30.00Z", 230.56),
                mk("2022-02-09T10:15:30.00Z", 204.56),
            )
        )
    }

    override suspend fun getProducts() =
        Either.Right(listOf<Product>())

    override suspend fun buyProduct(buySpec: ProductBuySpec) =
        Either.Right(true)

    override suspend fun getCorrespondences() =
        Either.Right(
            listOf(
                CorrespondenceHeader(LocalDate.now(), "Betreff", URL("https://example.com/"))
            )
        )

    override suspend fun augmentCorrespondence(header: CorrespondenceHeader) =
        Either.Right(
            Correspondence(
                CorrespondenceHeader(
                    LocalDate.now(),
                    "Betreff",
                    URL("https://example.com/")
                ),
                "Nachricht"
            )
        )

}
