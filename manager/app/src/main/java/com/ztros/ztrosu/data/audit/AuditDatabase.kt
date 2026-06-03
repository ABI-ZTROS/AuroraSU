package com.ztros.ztrosu.data.audit

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * 审计日志数据库
 */
@Database(
    entities = [AuditLogEntry::class],
    version = 1,
    exportSchema = false
)
abstract class AuditDatabase : RoomDatabase() {

    abstract fun auditLogDao(): AuditLogDao

    companion object {
        @Volatile
        private var INSTANCE: AuditDatabase? = null

        fun getInstance(context: Context): AuditDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AuditDatabase::class.java,
                    "audit_database.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
