package com.felixbrucker.jules.data.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [SessionEntity::class, PullRequestEntity::class, SourceEntity::class, LogEntity::class, SessionDraftEntity::class],
    version = 6,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 5, to = 6)
    ]
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun pullRequestDao(): PullRequestDao
    abstract fun sourceDao(): SourceDao
    abstract fun logDao(): LogDao
    abstract fun sessionDraftDao(): SessionDraftDao

    companion object {
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `pull_requests_new` (
                        `sessionId` TEXT NOT NULL,
                        `url` TEXT NOT NULL,
                        `state` TEXT NOT NULL,
                        PRIMARY KEY(`sessionId`),
                        FOREIGN KEY(`sessionId`) REFERENCES `sessions`(`id`) ON UPDATE CASCADE ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `pull_requests_new` (`sessionId`, `url`, `state`)
                    SELECT s.`id`, pr.`url`, pr.`state`
                    FROM `pull_requests` pr
                    INNER JOIN `sessions` s ON pr.`url` = s.`pull_request_url`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `pull_requests`")
                db.execSQL("ALTER TABLE `pull_requests_new` RENAME TO `pull_requests`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_pull_requests_url` ON `pull_requests` (`url`)")
                db.execSQL("DROP INDEX IF EXISTS `index_sessions_pull_request_url`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sessions_pull_request_url` ON `sessions` (`pull_request_url`)")
            }
        }
    }
}
