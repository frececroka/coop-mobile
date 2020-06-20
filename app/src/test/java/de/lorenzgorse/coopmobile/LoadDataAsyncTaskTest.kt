package de.lorenzgorse.coopmobile

import android.content.Context
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import de.lorenzgorse.coopmobile.CoopClient.CoopException.*
import de.lorenzgorse.coopmobile.MockCoopData.coopData1
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
		`when`(coopClient.getData()).thenReturn(coopData1)
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
		`when`(coopClient.getData()).thenThrow(UnknownHostException())
		doLoadDataTest(equalTo(LoadDataError.NO_NETWORK), nullValue())
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
		`when`(coopClient.getData()).thenThrow(HtmlChangedException(Exception()))
		doLoadDataTest(equalTo(LoadDataError.HTML_CHANGED), nullValue())
	}

	@Test
	fun unauthorized() = runBlocking {
		val coopClient = mockExpiredCoopClient()
		`when`(coopClient.getData()).thenThrow(UnauthorizedException(null))
		doLoadDataTest(equalTo(LoadDataError.UNAUTHORIZED), nullValue())
	}

	@Test
	fun refreshFails() = runBlocking {
		val coopClientFactory = prepareExpiredCoopClient()
		`when`(coopClientFactory.refresh(anyObject(), eq(true))).thenReturn(null)
		doLoadDataTest(equalTo(LoadDataError.FAILED_LOGIN), nullValue())
	}

	private suspend fun doLoadDataTest(
		failureMatcher: Matcher<in LoadDataError>,
		successMatcher: Matcher<in CoopData>
	) {
		CoopModule.firebaseAnalytics = { mock(FirebaseAnalytics::class.java) }
		CoopModule.firebaseCrashlytics = { mock(FirebaseCrashlytics::class.java) }
		when (val result = loadData(mock(Context::class.java)) { it.getData() }) {
			is Either.Left -> assertThat(result.value, failureMatcher)
			is Either.Right -> assertThat(result.value, successMatcher)
		}
	}

}
