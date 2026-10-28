package com.felixbrucker.jules.di

import android.content.Context
import androidx.room.Room
import com.felixbrucker.jules.data.database.ActivityBasedStateDao
import com.felixbrucker.jules.data.database.AppDatabase
import com.felixbrucker.jules.data.database.ChatElementDao
import com.felixbrucker.jules.data.database.ChatStateDao
import com.felixbrucker.jules.data.database.DispatchableWorkflowDao
import com.felixbrucker.jules.data.database.DispatchedWorkflowRunDao
import com.felixbrucker.jules.data.database.LogDao
import com.felixbrucker.jules.data.database.PullRequestDao
import com.felixbrucker.jules.data.database.SessionDao
import com.felixbrucker.jules.data.database.SessionDraftDao
import com.felixbrucker.jules.data.database.SourceDao
import com.felixbrucker.jules.data.database.SourceSyncMetadataDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "jules_db"
        )
            .addMigrations(AppDatabase.MIGRATION_4_5)
            .build()
    }

    @Provides
    @Singleton
    fun provideSessionDao(database: AppDatabase): SessionDao {
        return database.sessionDao()
    }

    @Provides
    @Singleton
    fun providePullRequestDao(database: AppDatabase): PullRequestDao {
        return database.pullRequestDao()
    }

    @Provides
    @Singleton
    fun provideSourceDao(database: AppDatabase): SourceDao {
        return database.sourceDao()
    }

    @Provides
    fun provideLogDao(database: AppDatabase): LogDao {
        return database.logDao()
    }

    @Provides
    @Singleton
    fun provideSessionDraftDao(database: AppDatabase): SessionDraftDao {
        return database.sessionDraftDao()
    }

    @Provides
    @Singleton
    fun provideDispatchableWorkflowDao(database: AppDatabase): DispatchableWorkflowDao {
        return database.dispatchableWorkflowDao()
    }

    @Provides
    @Singleton
    fun provideSourceSyncMetadataDao(database: AppDatabase): SourceSyncMetadataDao {
        return database.sourceSyncMetadataDao()
    }

    @Provides
    @Singleton
    fun provideChatElementDao(database: AppDatabase): ChatElementDao {
        return database.chatElementDao()
    }

    @Provides
    @Singleton
    fun provideChatStateDao(database: AppDatabase): ChatStateDao {
        return database.chatStateDao()
    }

    @Provides
    @Singleton
    fun provideActivityBasedStateDao(database: AppDatabase): ActivityBasedStateDao {
        return database.activityBasedStateDao()
    }

    @Provides
    @Singleton
    fun provideDispatchedWorkflowRunDao(database: AppDatabase): DispatchedWorkflowRunDao {
        return database.dispatchedWorkflowRunDao()
    }
}
