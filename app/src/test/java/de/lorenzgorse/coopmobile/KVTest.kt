package de.lorenzgorse.coopmobile

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.gson.reflect.TypeToken
import de.lorenzgorse.coopmobile.components.KV
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = TestApplication::class)
class KVTest {

    private lateinit var kv: KV

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        kv = KV(context)
    }

    @After
    fun tearDown() {
        kv.close()
    }

    @Test
    fun testString() {
        val expectedValue = "hello world"
        kv.set("message", expectedValue)
        val actualValue = kv.get<String>("message", TypeToken.get(String::class.java).type)
        assertThat(actualValue, equalTo(expectedValue))
    }

    @Test
    fun testCompositeObject() {
        val expectedValue = Point(123, 456)
        kv.set("point", expectedValue)
        val actualValue = kv.get<Point>("point", TypeToken.get(Point::class.java).type)
        assertThat(actualValue, equalTo(expectedValue))
    }

}

data class Point(val x: Int, val y: Int)
