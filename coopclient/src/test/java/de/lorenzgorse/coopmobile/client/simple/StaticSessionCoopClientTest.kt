package de.lorenzgorse.coopmobile.client.simple

import arrow.core.Either
import de.lorenzgorse.coopmobile.CoopHtmlParser
import de.lorenzgorse.coopmobile.client.CoopError
import de.lorenzgorse.coopmobile.client.LocalizedConfig
import de.lorenzgorse.coopmobile.client.simple.CoopException.PlanUnsupported
import kotlinx.coroutines.runBlocking
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.*
import org.junit.Ignore
import org.junit.Test
import org.junit.experimental.runners.Enclosed
import org.junit.runner.RunWith

@RunWith(Enclosed::class)
@Ignore("I don't have a CoopMobile account at the moment")
class StaticSessionCoopClientTest {

    class Regular {

        private val backend = RealBackend()

        private val client by lazy {
            val sessionId =
                runBlocking {
                    RealCoopLogin(LocalizedConfig(), ::httpClientFactory).login(
                        backend.username,
                        backend.password,
                        CoopLogin.Origin.Manual
                    )
                }
            assertThat(sessionId, notNullValue())
            StaticSessionCoopClient(
                LocalizedConfig(),
                CoopHtmlParser.Experiments(),
                sessionId!!,
                ::httpClientFactory
            )
        }

        @Test
        fun testLoadData() = runBlocking {
            val data = assertRight(client.getConsumption())
            assertThat(data, hasSize(3))
            assertThat(data.map { it.description }, everyItem(not(emptyString())))
        }

        @Test
        @Ignore("Functionality is broken")
        fun testLoadProducts() = runBlocking {
            val products = assertRight(client.getProducts())
            assertThat(products, not(empty()))
            assertThat(products.map { it.name }, everyItem(not(emptyString())))
            assertThat(products.map { it.description }, everyItem(not(emptyString())))
            assertThat(products.map { it.price }, everyItem(not(emptyString())))
        }

        @Test
        fun testLoadCorrespondences() = runBlocking {
            val correspondences = assertRight(client.getCorrespondences())
            assertThat(correspondences, not(empty()))
            assertThat(correspondences.map { it.subject }, everyItem(not(emptyString())))
            val correspondenceHeader = correspondences[0]
            val correspondence = assertRight(client.augmentCorrespondence(correspondenceHeader))
            assertThat(correspondence.message, not(emptyString()))
        }

        @Test
        fun testLoadConsumptionLog() = runBlocking {
            val consumptionLog = assertRight(client.getConsumptionLog())
            assertThat(consumptionLog, not(empty()))
        }

    }

    class Expired {

        private val expiredClient = StaticSessionCoopClient(
            LocalizedConfig(),
            CoopHtmlParser.Experiments(),
            "23847329847324",
            ::httpClientFactory
        )

        @Test
        fun testLoadDataInvalidSession() {
            assertReturnsUnauthorized { expiredClient.getConsumption() }
        }

        @Test
        fun testLoadProductsInvalidSession() {
            assertReturnsUnauthorized { expiredClient.getProducts() }
        }

        @Test
        fun testLoadCorrespondencesInvalidSession() {
            assertReturnsUnauthorized { expiredClient.getCorrespondences() }
        }

        private fun <T> assertReturnsUnauthorized(block: suspend () -> Either<CoopError, T>) {
            assertThat(runBlocking { block() }, equalTo(Either.Left(CoopError.Unauthorized)))
        }

    }

    class AssertResponseSuccessful {

        private val client = StaticSessionCoopClient(
            LocalizedConfig(),
            CoopHtmlParser.Experiments(),
            "",
            ::httpClientFactory
        )

        @Test(expected = PlanUnsupported::class)
        fun testAssertResponseSuccessfulWirelessPlanUnsupported() {
            val response = makeResponse(
                "https://myaccount.coopmobile.ch/eCare/unsupported/de"
            )
            client.assertResponseSuccessful(response)
        }

        @Test(expected = PlanUnsupported::class)
        fun testAssertResponseSuccessfulWirelessAddProductPlanUnsupported() {
            val response = makeResponse(
                "https://myaccount.coopmobile.ch/eCare/unsupported/de/add_product"
            )
            client.assertResponseSuccessful(response)
        }

        @Test(expected = PlanUnsupported::class)
        fun testAssertResponseSuccessfulWirelessCorrespondencesPlanUnsupported() {
            val response = makeResponse(
                "https://myaccount.coopmobile.ch/eCare/unsupported/de/my_correspondence/index"
            )
            client.assertResponseSuccessful(response)
        }

        @Test(expected = CoopException.Unauthorized::class)
        fun testAssertResponseSuccessfulUnauthorized() {
            val response = makeResponse(
                "https://myaccount.coopmobile.ch/eCare/de/users/sign_in"
            )
            client.assertResponseSuccessful(response)
        }

        private fun makeResponse(responseUrl: String): Response {
            val request = Request.Builder()
                .url(responseUrl)
                .build()
            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200).message("OK")
                .build()
        }

    }

}

fun <L, R> assertRight(result: Either<L, R>): R = when (result) {
    is Either.Left -> throw AssertionError("expected Either.Right")
    is Either.Right -> result.value
}
