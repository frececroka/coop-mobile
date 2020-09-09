package de.lorenzgorse.coopmobile.coopclient

import de.lorenzgorse.coopmobile.coopclient.CoopException.PlanUnsupported
import kotlinx.coroutines.runBlocking
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.*
import org.junit.Test

class CoopClientTest {

    private val username = Config.read("username")
    private val password = Config.read("password")

    private val wrongUsername = "0781234567"
    private val wrongPassword = "supersecret"

    private val client by lazy {
        val sessionId = runBlocking { RealCoopLogin().login(username, password) }
        RealCoopClient(sessionId!!)
    }

    private val expiredClient = RealCoopClient("23847329847324")

    @Test
    fun testLogin() = runBlocking {
        val sessionId = RealCoopLogin().login(username, password)
        assertThat(sessionId, not(nullValue()))
    }

    @Test
    fun testLoginWrongUsername() = runBlocking {
        val sessionId = RealCoopLogin().login(wrongUsername, password)
        assertThat(sessionId, nullValue())
    }

    @Test
    fun testLoginWrongPassword() = runBlocking {
        val sessionId = RealCoopLogin().login(username, wrongPassword)
        assertThat(sessionId, nullValue())
    }

    @Test
    fun testLoadData() = runBlocking {
        val data = client.getData()
        assertThat(data.items, hasSize(3))
        assertThat(data.items.map { it.description }, everyItem(not(emptyString())))
        assertThat(data.items.map { it.unit }, everyItem(not(emptyString())))
    }

    @Test
    fun testLoadProducts() = runBlocking {
        val products = client.getProducts()
        assertThat(products, not(empty()))
        assertThat(products.map { it.name }, everyItem(not(emptyString())))
        assertThat(products.map { it.description }, everyItem(not(emptyString())))
        assertThat(products.map { it.price }, everyItem(not(emptyString())))
    }

    @Test
    fun testLoadCorrespondences() = runBlocking {
        val correspondences = client.getCorrespondeces()
        assertThat(correspondences, not(empty()))
        assertThat(correspondences.map { it.subject }, everyItem(not(emptyString())))
        val correspondenceHeader = correspondences[0]
        val correspondence = client.augmentCorrespondence(correspondenceHeader)
        assertThat(correspondence.message, not(emptyString()))
    }

    @Test(expected = CoopException.Unauthorized::class)
    fun testLoadDataInvalidSession() { runBlocking {
        expiredClient.getData()
    } }

    @Test(expected = CoopException.Unauthorized::class)
    fun testLoadProductsInvalidSession() { runBlocking {
        expiredClient.getProducts()
    } }

    @Test(expected = CoopException.Unauthorized::class)
    fun testLoadCorrespondencesInvalidSession() { runBlocking {
        expiredClient.getCorrespondeces()
    } }

    @Test(expected = PlanUnsupported::class)
    fun testAssertResponseSuccessfulWirelessPlanUnsupported() {
        val response = makeResponse(
            "https://myaccount.coopmobile.ch/eCare/unsupported/de"
        )
        RealCoopClient.assertResponseSuccessful(response)
    }

    @Test(expected = PlanUnsupported::class)
    fun testAssertResponseSuccessfulWirelessAddProductPlanUnsupported() {
        val response = makeResponse(
            "https://myaccount.coopmobile.ch/eCare/unsupported/de/add_product"
        )
        RealCoopClient.assertResponseSuccessful(response)
    }

    @Test(expected = PlanUnsupported::class)
    fun testAssertResponseSuccessfulWirelessCorrespondencesPlanUnsupported() {
        val response = makeResponse(
            "https://myaccount.coopmobile.ch/eCare/unsupported/de/my_correspondence/index"
        )
        RealCoopClient.assertResponseSuccessful(response)
    }

    @Test(expected = CoopException.Unauthorized::class)
    fun testAssertResponseSuccessfulUnauthorized() {
        val response = makeResponse(
            "https://myaccount.coopmobile.ch/eCare/de/users/sign_in"
        )
        RealCoopClient.assertResponseSuccessful(response)
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
