package de.lorenzgorse.coopmobile

import de.lorenzgorse.coopmobile.CoopClient.CoopException.UnauthorizedException
import de.lorenzgorse.coopmobile.CoopModule.coopClientFactory
import de.lorenzgorse.coopmobile.CoopModule.coopLogin
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import org.mockito.Matchers.eq
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

class RestoreCoopModule: TestRule {

    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            base.evaluate()
            coopLogin = RealCoopLogin()
            coopClientFactory = RealCoopClientFactory()
        }
    }

}

fun mockCoopLogin(): CoopLogin {
    coopLogin = mock(CoopLogin::class.java)
    return coopLogin
}

fun mockCoopClientFactory(): CoopClientFactory {
    coopClientFactory = mock(CoopClientFactory::class.java)
    return coopClientFactory
}

fun mockCoopClient(): CoopClient {
    val coopClientFactory = mockCoopClientFactory()
    val coopClient = mock(CoopClient::class.java)
    `when`(coopClientFactory.get(anyObject())).thenReturn(coopClient)
    return coopClient
}

fun mockPreparedCoopClient(): CoopClient {
    val coopClient = mockCoopClient()
    `when`(coopClient.getData()).thenReturn(MockCoopData.coopData1)
    return coopClient
}

fun mockExpiredCoopClient(): CoopClient {
    val coopClientFactory = prepareExpiredCoopClient()
    val coopClient2 = mock(CoopClient::class.java)
    `when`(coopClientFactory.refresh(anyObject(), eq(true))).thenReturn(coopClient2)
    return coopClient2
}

fun prepareExpiredCoopClient(): CoopClientFactory {
    val coopClientFactory = mockCoopClientFactory()
    val coopClient1 = mock(CoopClient::class.java)
    `when`(coopClient1.getData()).thenThrow(UnauthorizedException(null))
    `when`(coopClient1.getProducts()).thenThrow(UnauthorizedException(null))
    `when`(coopClient1.getCorrespondeces()).thenThrow(UnauthorizedException(null))
    `when`(coopClient1.augmentCorrespondence(anyObject())).thenThrow(UnauthorizedException(null))
    `when`(coopClientFactory.get(anyObject())).thenReturn(coopClient1)
    return coopClientFactory
}

fun <T> anyObject(): T {
    Mockito.any<T>()
    return uninitialized()
}

@Suppress("UNCHECKED_CAST")
private fun <T> uninitialized(): T = null as T
