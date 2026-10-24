# R8 / ProGuard rules for Jules Android App

# ----------------------------------------------------------------------------------------
# Application Data Models & Network API
# ----------------------------------------------------------------------------------------
# Preserve Moshi JSON models and generated adapters
-keep @com.squareup.moshi.JsonClass class * { *; }
-keep class *JsonAdapter {
    public <init>(...);
}
-keep class **.*JsonAdapter {
    public <init>(...);
}
-keepclassmembers class * {
    @com.squareup.moshi.Json <fields>;
}
-keepclassmembers class * {
    synthetic kotlin.jvm.internal.DefaultConstructorMarker;
}
-keep class com.felixbrucker.jules.network.api.model.** { *; }
-keep class com.felixbrucker.jules.network.api.JulesApiService { *; }
-keep class com.felixbrucker.jules.network.api.GitHubApiService { *; }

# ----------------------------------------------------------------------------------------
# Retrofit & OkHttp
# ----------------------------------------------------------------------------------------
-keepattributes Signature, InnerClasses, EnclosingMethod, AnnotationDefault
-keepclassmembers enum * { *; }
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-dontwarn com.squareup.moshi.**

# ----------------------------------------------------------------------------------------
# Room Database & Entities
# ----------------------------------------------------------------------------------------
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keep class com.felixbrucker.jules.data.database.** { *; }
-dontwarn androidx.room.paging.**

# ----------------------------------------------------------------------------------------
# Jetpack DataStore & WorkManager
# ----------------------------------------------------------------------------------------
-keep class com.felixbrucker.jules.data.datastore.** { *; }
-keep class com.felixbrucker.jules.data.worker.** { *; }

# ----------------------------------------------------------------------------------------
# Application Utilities & UI Models
# ----------------------------------------------------------------------------------------
-keep class com.felixbrucker.jules.util.** { *; }
-keep class com.felixbrucker.jules.notification.** { *; }
-keep class com.felixbrucker.jules.ui.settings.JulesPlan { *; }
-keep class com.felixbrucker.jules.ui.sessions.SessionFilterChip { *; }

# ----------------------------------------------------------------------------------------
# Hilt / Dagger Dependency Injection
# ----------------------------------------------------------------------------------------
-keep class * extends androidx.hilt.work.HiltWorker
-keep class com.felixbrucker.jules.di.** { *; }
