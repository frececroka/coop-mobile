package de.lorenzgorse.coopmobile

import android.content.Context
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import de.lorenzgorse.coopmobile.CoopClient.CoopException.*
import de.lorenzgorse.coopmobile.MockCoopData.coopData1
import org.hamcrest.Matcher
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.nullValue
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import java.net.UnknownHostException

class LoadDataAsyncTaskTest {

	@get:Rule
	val restoreCoopModule = RestoreCoopModule()

	@Test
	fun happyPathValidClient() {
		happyPath(mockCoopClient())
	}

	@Test
	fun happyPathExpiredClient() {
		happyPath(mockExpiredCoopClient())
	}

	private fun happyPath(coopClient: CoopClient) {
		`when`(coopClient.getData()).thenReturn(coopData1)
		doLoadDataTest(nullValue(), equalTo(coopData1))
	}

	@Test
	fun noNetworkValidClient() {
		noNetwork(mockCoopClient())
	}

	@Test
	fun noNetworkExpiredClient() {
		noNetwork(mockExpiredCoopClient())
	}

	private fun noNetwork(coopClient: CoopClient) {
		`when`(coopClient.getData()).thenThrow(UnknownHostException())
		doLoadDataTest(equalTo(LoadDataError.NO_NETWORK), nullValue())
	}

	@Test
	fun htmlChangedValidClient() {
		htmlChanged(mockCoopClient())
	}

	@Test
	fun htmlChangedExpiredClient() {
		htmlChanged(mockExpiredCoopClient())
	}

	private fun htmlChanged(coopClient: CoopClient) {
		`when`(coopClient.getData()).thenThrow(HtmlChangedException(Exception()))
		doLoadDataTest(equalTo(LoadDataError.HTML_CHANGED), nullValue())
	}

	@Test
	fun unauthorized() {
		val coopClient = mockExpiredCoopClient()
		`when`(coopClient.getData()).thenThrow(UnauthorizedException(null))
		doLoadDataTest(equalTo(LoadDataError.UNAUTHORIZED), nullValue())
	}

	@Test
	fun refreshFails() {
		val coopClientFactory = prepareExpiredCoopClient()
		`when`(coopClientFactory.refresh(anyObject(), eq(true))).thenReturn(null)
		doLoadDataTest(equalTo(LoadDataError.FAILED_LOGIN), nullValue())
	}

	private fun doLoadDataTest(
		failureMatcher: Matcher<in LoadDataError>,
		successMatcher: Matcher<in CoopData>
	) {
		CoopModule.firebaseAnalytics = { mock(FirebaseAnalytics::class.java) }
		CoopModule.firebaseCrashlytics = { mock(FirebaseCrashlytics::class.java) }
		val task = DummyLoadDataAsyncTask { it.getData() }.also { it.execute() }
		assertThat<LoadDataError>(task.failureValue, failureMatcher)
		assertThat<CoopData>(task.successValue, successMatcher)
	}

}

class DummyLoadDataAsyncTask<R>(val dataLoader: (CoopClient) -> R) : LoadDataAsyncTask<Void, R>(mock(Context::class.java)) {

	var failureValue: LoadDataError? = null; private set
	var successValue: R? = null; private set

	fun execute() {
		onPreExecute()
		val result = doInBackground()
		onPostExecute(result)
	}

	override fun loadData(client: CoopClient): R {
		return dataLoader(client)
	}

	override fun onFailure(error: LoadDataError) {
		failureValue = error
	}

	override fun onSuccess(result: R) {
		successValue = result
	}

}
