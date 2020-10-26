package de.lorenzgorse.coopmobile

import android.content.Context
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import de.lorenzgorse.coopmobile.MockCoopData.coopData1
import de.lorenzgorse.coopmobile.coopclient.CoopClient
import de.lorenzgorse.coopmobile.coopclient.CoopException
import de.lorenzgorse.coopmobile.coopclient.UnitValue
import kotlinx.coroutines.runBlocking
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
	fun happyPathValidClient() = runBlocking {
		happyPath(mockCoopClient())
	}

	@Test
	fun happyPathExpiredClient() = runBlocking {
		happyPath(mockExpiredCoopClient())
	}

	private suspend fun happyPath(coopClient: CoopClient) {
		`when`(coopClient.getConsumption()).thenReturn(coopData1)
		doLoadDataTest(nullValue(), equalTo(coopData1))
	}

	@Test
	fun noNetworkValidClient() = runBlocking {
		noNetwork(mockCoopClient())
	}

	@Test
	fun noNetworkExpiredClient() = runBlocking {
		noNetwork(mockExpiredCoopClient())
	}

	private suspend fun noNetwork(coopClient: CoopClient) {
		`when`(coopClient.getConsumption()).thenThrow(UnknownHostException())
		doLoadDataTest(equalTo(LoadDataError.NoNetwork), nullValue())
	}

	@Test
	fun htmlChangedValidClient() = runBlocking {
		htmlChanged(mockCoopClient())
	}

	@Test
	fun htmlChangedExpiredClient() = runBlocking {
		htmlChanged(mockExpiredCoopClient())
	}

	private suspend fun htmlChanged(coopClient: CoopClient) {
		val htmlChanged = CoopException.HtmlChanged(Exception())
		`when`(coopClient.getConsumption()).thenThrow(htmlChanged)
		doLoadDataTest(equalTo(LoadDataError.HtmlChanged(htmlChanged)), nullValue())
	}

	@Test
	fun unauthorized() = runBlocking {
		val coopClient = mockExpiredCoopClient()
		`when`(coopClient.getConsumption()).thenThrow(CoopException.Unauthorized())
		doLoadDataTest(equalTo(LoadDataError.Unauthorized), nullValue())
	}

	@Test
	fun refreshFails() = runBlocking {
		val coopClientFactory = prepareExpiredCoopClient()
		`when`(coopClientFactory.refresh(anyObject(), eq(true))).thenReturn(null)
		doLoadDataTest(equalTo(LoadDataError.FailedLogin), nullValue())
	}

	private suspend fun doLoadDataTest(
		failureMatcher: Matcher<in LoadDataError>,
		successMatcher: Matcher<in List<UnitValue<Float>>>
	) {
		CoopModule.firebaseAnalytics = { mock(FirebaseAnalytics::class.java) }
		CoopModule.firebaseCrashlytics = { mock(FirebaseCrashlytics::class.java) }
		when (val result = loadData(mock(Context::class.java)) { it.getConsumption() }) {
			is Either.Left -> assertThat(result.value, failureMatcher)
			is Either.Right -> assertThat(result.value, successMatcher)
		}
	}

}
