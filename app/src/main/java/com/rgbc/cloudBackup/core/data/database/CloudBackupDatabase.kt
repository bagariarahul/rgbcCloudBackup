package com.rgbc.cloudBackup.core.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.rgbc.cloudBackup.core.data.database.entity.FileIndexDao
import com.rgbc.cloudBackup.core.data.database.entity.FileIndex

@Database(
    entities = [FileIndex::class],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class CloudBackupDatabase : RoomDatabase() {
    abstract fun fileIndexDao(): FileIndexDao
}
