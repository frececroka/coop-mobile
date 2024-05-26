package bifrost

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase

@Database(entities = [MetricD::class, SendMetricRequestD::class], version = 3)
abstract class BifrostDatabase : RoomDatabase() {
    abstract fun metricDao(): MetricDao
    abstract fun sendMetricRequestDao(): SendMetricRequestDao
}


@Entity(tableName = "metric")
data class MetricD(
    @PrimaryKey
    val id: String,
    val spec: ByteArray,
    val value: Long,
)

@Dao
interface MetricDao {
    // TODO: this ignores all errors
    @Query("Insert Or Ignore Into metric(id, spec, value) Values (:id, :spec, 0)")
    fun create(id: String, spec: ByteArray)

    @Query("Update metric Set value = value + :delta where id = :id")
    fun increment(id: String, delta: Long)

    @Query("Select * From metric")
    fun dump(): List<MetricD>

    @Query("Delete From metric")
    fun reset()
}


@Entity(tableName = "send_metric_request")
data class SendMetricRequestD(
    @PrimaryKey(autoGenerate = true)
    val id: Long,
    val request: ByteArray,
)

@Dao
interface SendMetricRequestDao {
    @Query("Insert Into send_metric_request(request) Values (:request)")
    fun add(request: ByteArray)

    @Query("Select * From send_metric_request Order By id Asc Limit 1")
    fun next(): SendMetricRequestD?

    @Query("Delete From send_metric_request Where id = :id")
    fun delete(id: Long)
}
