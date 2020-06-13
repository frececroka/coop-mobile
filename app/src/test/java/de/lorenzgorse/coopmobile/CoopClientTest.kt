package de.lorenzgorse.coopmobile

import de.lorenzgorse.coopmobile.CoopClient.CoopException.PlanUnsupported
import de.lorenzgorse.coopmobile.CoopClient.CoopException.UnauthorizedException
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.hamcrest.Matchers.*
import org.junit.Assert.*
import org.junit.Test

class CoopClientTest {

    private val username = Config.read("username")
    private val password = Config.read("password")

    private val wrongUsername = "0781234567"
    private val wrongPassword = "supersecret"

    private val client by lazy {
        val sessionId = RealCoopLogin().login(username, password)
        RealCoopClient(sessionId!!)
    }

    private val expiredClient = RealCoopClient("23847329847324")

    @Test
    fun testLogin() {
        val sessionId = RealCoopLogin().login(username, password)
        assertNotNull(sessionId)
    }

    @Test
    fun testLoginWrongUsername() {
        val sessionId = RealCoopLogin().login(wrongUsername, password)
        assertNull(sessionId)
    }

    @Test
    fun testLoginWrongPassword() {
        val sessionId = RealCoopLogin().login(username, wrongPassword)
        assertNull(sessionId)
    }

    @Test
    fun testLoadData() {
        val data = client.getData()
        assertNotNull(data.credit)
        assertThat(data.consumptions, hasSize(2))
        assertThat(data.consumptions.map { it.description }, everyItem(not(emptyString())))
        assertThat(data.consumptions.map { it.unit }, everyItem(not(emptyString())))
    }

    @Test
    fun testLoadProducts() {
        val products = client.getProducts()
        assertThat(products, not(empty()))
        assertThat(products.map { it.name }, everyItem(not(emptyString())))
        assertThat(products.map { it.description }, everyItem(not(emptyString())))
        assertThat(products.map { it.price }, everyItem(not(emptyString())))
    }

    @Test
    fun testLoadCorrespondences() {
        val correspondences = client.getCorrespondeces()
        assertThat(correspondences, not(empty()))
        assertThat(correspondences.map { it.subject }, everyItem(not(emptyString())))
        val correspondenceHeader = correspondences[0]
        val correspondence = client.augmentCorrespondence(correspondenceHeader)
        assertThat(correspondence.message, not(emptyString()))
    }

    @Test(expected = UnauthorizedException::class)
    fun testLoadDataInvalidSession() {
        expiredClient.getData()
    }

    @Test(expected = UnauthorizedException::class)
    fun testLoadProductsInvalidSession() {
        expiredClient.getProducts()
    }

    @Test(expected = UnauthorizedException::class)
    fun testLoadCorrespondencesInvalidSession() {
        expiredClient.getCorrespondeces()
    }

    @Test(expected = UnauthorizedException::class)
    fun testAssertResponseSuccessfulSessionExpired() {
        val response = makeRedirectResponse(
            "https://myaccount.coopmobile.ch/eCare/prepaid/de",
            "https://myaccount.coopmobile.ch/eCare/prepaid/de"
        )
        RealCoopClient.assertResponseSuccessful(response)
    }

    @Test(expected = PlanUnsupported::class)
    fun testAssertResponseSuccessfulWirelessPlanUnsupported() {
        val response = makeRedirectResponse(
            "https://myaccount.coopmobile.ch/eCare/prepaid/de",
            "https://myaccount.coopmobile.ch/eCare/wireless/de"
        )
        RealCoopClient.assertResponseSuccessful(response)
    }

    @Test(expected = PlanUnsupported::class)
    fun testAssertResponseSuccessfulWirelessAddProductPlanUnsupported() {
        val response = makeRedirectResponse(
            "https://myaccount.coopmobile.ch/eCare/prepaid/de/add_product",
            "https://myaccount.coopmobile.ch/eCare/wireless/de/add_product"
        )
        RealCoopClient.assertResponseSuccessful(response)
    }

    @Test(expected = PlanUnsupported::class)
    fun testAssertResponseSuccessfulWirelessCorrespondencesPlanUnsupported() {
        val response = makeRedirectResponse(
            "https://myaccount.coopmobile.ch/eCare/prepaid/de/my_correspondence/index",
            "https://myaccount.coopmobile.ch/eCare/wireless/de/my_correspondence/index"
        )
        RealCoopClient.assertResponseSuccessful(response)
    }

    @Test(expected = UnauthorizedException::class)
    fun testAssertResponseSuccessfulUnauthorized() {
        val response = makeRedirectResponse(
            "https://myaccount.coopmobile.ch/eCare/prepaid/de",
            "https://myaccount.coopmobile.ch/eCare/de/users/sign_in"
        )
        RealCoopClient.assertResponseSuccessful(response)
    }

    private fun makeRedirectResponse(requestUrl: String, responseUrl: String): Response {
        val request = Request.Builder()
            .url(requestUrl)
            .build()
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(301).message("Moved Permanently")
            .addHeader("Location", responseUrl)
            .build()
    }

}
