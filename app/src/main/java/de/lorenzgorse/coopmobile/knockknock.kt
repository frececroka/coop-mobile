package de.lorenzgorse.coopmobile

class KnockKnock(private val time: () -> Long, val separator: Long, val memoryTime: Long) {

    companion object {
        fun default(separator: Long, memoryTime: Long): KnockKnock {
            return KnockKnock(System::currentTimeMillis, separator, memoryTime)
        }
    }

    private var pastKnocks: List<Int> = emptyList()
    private var currentKnocks: Int = 0
    private var lastKnockTime: Long? = null

    init {
        reset()
    }

    fun reset() {
        pastKnocks = emptyList()
        currentKnocks = 0
        lastKnockTime = null
    }

    fun knock() {
        val currentTime = time()
        val elapsed = lastKnockTime?.let { currentTime - it }
        if (elapsed != null) {
            if (elapsed > memoryTime) reset()
            else if (elapsed > separator) {
                pastKnocks = getKnocks()
                currentKnocks = 0
            }
        }
        currentKnocks += 1
        lastKnockTime = currentTime
    }

    fun getKnocks() = if (currentKnocks > 0) pastKnocks + currentKnocks else pastKnocks

}
