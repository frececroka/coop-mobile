package de.lorenzgorse.coopmobile

import android.content.Context
import de.lorenzgorse.coopmobile.client.*
import de.lorenzgorse.coopmobile.client.simple.CoopClient
import de.lorenzgorse.coopmobile.client.simple.CoopLogin
import de.lorenzgorse.coopmobile.components.Fuse
import java.net.URL
import java.time.Instant
import java.util.*
import java.util.concurrent.atomic.AtomicReference

class TestAccounts(context: Context) {

    // The list of test accounts; login information and data
    // are defined in TestModeCoopLogin and TestModeCoopClient
    private val testAccounts = setOf("prepaid@example.com", "wireless@example.com")

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

class TestModeCoopClient(private val sessionId: String) : CoopClient {

    // TODO: add data for more methods

    override suspend fun getProfile() = Either.Right(
        listOf(
            Pair("Name", "Peter Lustig"),
            Pair("Kundennummer", "12345678"),
            Pair("Status", "aktiv"),
        )
    )

    override suspend fun getConsumption() = Either.Right(
        listOf(
            UnitValueBlock(
                UnitValueBlock.Kind.Credit,
                "Guthaben",
                listOf(UnitValue("verbleibend", 1.23F, "CHF"))
            ),
            UnitValueBlock(
                UnitValueBlock.Kind.DataSwitzerland,
                "Daten in der Schweiz",
                listOf(
                    UnitValue("verbleibend", 4.56F, "GB"),
                    UnitValue("Diesen Monat genutzt", 7.89F, "GB"),
                )
            )
        )
    )

    override suspend fun getConsumptionLog() =
        Either.Right(listOf<ConsumptionLogEntry>())

    override suspend fun getProducts() =
        Either.Right(listOf<Product>())

    override suspend fun buyProduct(buySpec: ProductBuySpec) =
        Either.Right(true)

    override suspend fun getCorrespondences() =
        Either.Right(listOf<CorrespondenceHeader>())

    override suspend fun augmentCorrespondence(header: CorrespondenceHeader) =
        Either.Right(
            Correspondence(
                CorrespondenceHeader(
                    Date.from(Instant.now()),
                    "Betreff",
                    URL("https://example.com/")
                ),
                "Nachricht"
            )
        )

    override suspend fun sessionId() = sessionId

}
