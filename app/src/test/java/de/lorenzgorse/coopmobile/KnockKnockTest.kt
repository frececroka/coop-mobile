package de.lorenzgorse.coopmobile

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Before
import org.junit.Test

class KnockKnockTest {

    private val time = FakeTime()
    private val knockKnock = KnockKnock(time::time, 10, 100)

    @Before
    fun before() {
        time.time = 0
        knockKnock.reset()
    }

    @Test
    fun testPatterns() {
        test(listOf(), listOf())
        test(listOf(0), listOf(1))
        test(listOf(5), listOf(1))
        test(listOf(15), listOf(1))
        test(listOf(0, 0), listOf(2))
        test(listOf(0, 5), listOf(2))
        test(listOf(0, 15), listOf(1, 1))
        test(listOf(5, 0), listOf(2))
        test(listOf(5, 5), listOf(2))
        test(listOf(5, 15), listOf(1, 1))
        test(listOf(5, 15, 5), listOf(1, 2))
        test(listOf(5, 15, 5, 5), listOf(1, 3))
        test(listOf(5, 5, 15, 15, 5, 5), listOf(2, 1, 3))
        test(listOf(5, 15, 5, 5, 5, 15, 5, 5, 5, 5, 5, 5, 5, 15, 5, 5, 5, 5, 5), listOf(1, 4, 8, 6))
        test(listOf(200), listOf(1))
        test(listOf(200, 5), listOf(2))
        test(listOf(200, 15), listOf(1, 1))
        test(listOf(5, 15, 5, 200), listOf(1))
        test(listOf(5, 15, 5, 200, 5), listOf(2))
        test(listOf(5, 15, 5, 200, 15), listOf(1, 1))
        test(listOf(5, 15, 5, 200, 15, 5, 5, 5, 15, 5, 5, 5, 5, 5, 5, 5, 15, 5, 5, 5, 5, 5), listOf(1, 4, 8, 6))
    }

    fun test(delays: List<Long>, knocks: List<Int>) {
        before()
        for (delay in delays) {
            time.time += delay
            knockKnock.knock()
        }
        assertThat("delays $delays produce the right knocks",
            knockKnock.getKnocks(), equalTo(knocks))
    }

}

class FakeTime {
    var time: Long = 0
}
