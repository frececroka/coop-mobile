package de.lorenzgorse.coopmobile

import android.content.Context
import androidx.room.*
import de.lorenzgorse.coopmobile.coopclient.ConsumptionLogEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.util.*

class ConsumptionLogCache(context: Context) {

    private val log = LoggerFactory.getLogger(ConsumptionLogCache::class.java)

    private val db = Room.databaseBuilder(
        context, EntryDatabase::class.java, "consumption-log"
    ).build()

    private val userDao = db.userDao()

    suspend fun insert(consumptionLog: List<ConsumptionLogEntry>) {
        log.info("Updating consumption log with ${consumptionLog.size} entries.")
        val entries = consumptionLog.map {
            Entry(it.date.time, it.type, it.amount)
        }
        withContext(Dispatchers.IO) {
            userDao.insertAll(entries)
        }
    }

    suspend fun load(): List<ConsumptionLogEntry> {
        val entries = withContext(Dispatchers.IO) { userDao.load() }
        return entries.map {
            val date = Date(it.timestamp)
            ConsumptionLogEntry(date, it.type, it.amount)
        }
    }

}

@Entity(primaryKeys = ["timestamp", "type", "amount"])
private data class Entry(
    val timestamp: Long,
    val type: String,
    val amount: Double
)

@Dao
private interface EntryDao {
    @Query("select * from entry")
    fun load(): List<Entry>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertAll(entries: List<Entry>)
}

@Database(entities = [Entry::class], version = 1)
private abstract class EntryDatabase : RoomDatabase() {
    abstract fun userDao(): EntryDao
}
