package de.lorenzgorse.coopmobile.components

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializer
import com.google.gson.JsonSerializer
import java.lang.reflect.Type
import java.time.Instant

class KV(context: Context) : AutoCloseable {

    private val kvData = KVData(context)
    private val gson = GsonBuilder()
        .registerTypeAdapter(
            Instant::class.java,
            JsonDeserializer { json, _, context -> Instant.ofEpochMilli(context.deserialize(json, Long::class.java)) }
        )
        .registerTypeAdapter(
            Instant::class.java,
            JsonSerializer<Instant> { instant, _, context -> context.serialize(instant.toEpochMilli()) }
        )
        .create()

    fun <T> set(key: String, value: T) {
        val serializedValue = gson.toJson(value).encodeToByteArray()
        val bindings = arrayOf(key, serializedValue)
        kvData.writableDatabase.execSQL(
            "insert into kv(k, v) values(?, ?)",
            bindings
        )
    }

    fun <T> get(key: String, clazz: Type): T? {
        val bindings = arrayOf(key)
        val cursor = kvData.readableDatabase.rawQuery("select v from kv where k=?", bindings)
        val value = cursor.use {
            if (!cursor.moveToNext()) return@get null
            cursor.getBlob(0).decodeToString()
        }
        return gson.fromJson(value, clazz)
    }

    override fun close() {
        kvData.close()
    }

}

class KVData(context: Context) : SQLiteOpenHelper(context, "kv", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("create table kv (k string primary key on conflict replace, v blob)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        throw NotImplementedError()
    }
}
