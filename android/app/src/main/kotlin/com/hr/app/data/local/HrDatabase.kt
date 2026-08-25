package com.hr.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

@Database(
    entities = [OutboxEntry::class, SyncCursor::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class HrDatabase : RoomDatabase() {
    abstract fun outboxDao(): OutboxDao

    abstract fun syncCursorDao(): SyncCursorDao

    companion object {
        const val NAME = "hr.db"
    }
}

class Converters {
    @TypeConverter
    fun outboxStateToString(state: OutboxState): String = state.name

    @TypeConverter
    fun stringToOutboxState(value: String): OutboxState = OutboxState.valueOf(value)

    @TypeConverter
    fun syncStateToString(state: SyncState): String = state.name

    @TypeConverter
    fun stringToSyncState(value: String): SyncState = SyncState.valueOf(value)
}
