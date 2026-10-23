package com.felixbrucker.jules.network.api.util

import com.felixbrucker.jules.network.api.model.FileChangeType
import com.felixbrucker.jules.network.api.model.ParsedFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UnidiffParserTest {

    @Test
    fun testParseModifiedFile() {
        val patch = """
            diff --git a/MainActivity.kt b/MainActivity.kt
            --- a/MainActivity.kt
            +++ b/MainActivity.kt
            @@ -1,3 +1,4 @@
             package com.example
            -val x = 1
            +val x = 2
            +val y = 3
        """.trimIndent()

        val parsed = UnidiffParser.parse(patch)
        assertEquals(1, parsed.size)
        val file = parsed.first()
        assertEquals("MainActivity.kt", file.path)
        assertEquals(FileChangeType.MODIFIED, file.changeType)
        assertEquals(2, file.additions)
        assertEquals(1, file.deletions)
    }

    @Test
    fun testParseCreatedFile() {
        val patch = """
            diff --git a/NewFile.kt b/NewFile.kt
            --- /dev/null
            +++ b/NewFile.kt
            @@ -0,0 +1,2 @@
            +class NewClass {
            +}
        """.trimIndent()

        val parsed = UnidiffParser.parse(patch)
        assertEquals(1, parsed.size)
        val file = parsed.first()
        assertEquals("NewFile.kt", file.path)
        assertEquals(FileChangeType.CREATED, file.changeType)
        assertEquals(2, file.additions)
        assertEquals(0, file.deletions)
    }

    @Test
    fun testParseDeletedFile() {
        val patch = """
            diff --git a/OldFile.kt b/OldFile.kt
            --- a/OldFile.kt
            +++ /dev/null
            @@ -1,2 +0,0 @@
            -class OldClass {
            -}
        """.trimIndent()

        val parsed = UnidiffParser.parse(patch)
        assertEquals(1, parsed.size)
        val file = parsed.first()
        assertEquals("OldFile.kt", file.path)
        assertEquals(FileChangeType.DELETED, file.changeType)
        assertEquals(0, file.additions)
        assertEquals(2, file.deletions)
    }

    @Test
    fun testMultipleFiles() {
        val patch = """
diff --git a/.gitignore b/.gitignore
index 6d27850..54c70dc 100644
--- a/.gitignore
+++ b/.gitignore
@@ -14,3 +14,4 @@
 .cxx
 local.properties
 /*.keystore
+.artifacts
diff --git a/.idea/misc.xml b/.idea/misc.xml
index 591fd9a..1891d7f 100644
--- a/.idea/misc.xml
+++ b/.idea/misc.xml
@@ -1,5 +1,10 @@
 <?xml version="1.0" encoding="UTF-8"?>
 <project version="4">
+  <component name="CidrRootsConfiguration">
+    <excludeRoots>
+      <file path="${'$'}PROJECT_DIR$/.idea/caches" />
+    </excludeRoots>
+  </component>
   <component name="ExternalStorageConfigurationManager" enabled="true" />
   <component name="ProjectInspectionProfilesVisibleTreeState">
     <entry key="Project Default">
diff --git a/app/build.gradle.kts b/app/build.gradle.kts
index 2a105dd..2d1229b 100644
--- a/app/build.gradle.kts
+++ b/app/build.gradle.kts
@@ -63,11 +63,25 @@ android {
     }
 }
 
+kotlin {
+    compilerOptions {
+        freeCompilerArgs.add("-Xannotation-default-target=param-property")
+    }
+}
+
 dependencies {
     implementation(libs.androidx.appcompat)
     implementation(libs.androidx.core.ktx)
     implementation(libs.material)
+    implementation(libs.retrofit)
+    implementation(libs.retrofit.converter.moshi)
+    implementation(libs.okhttp)
+    implementation(libs.okhttp.logging.interceptor)
+    implementation(libs.moshi)
+    implementation(libs.moshi.kotlin)
+    implementation(libs.moshi.adapters)
     testImplementation(libs.junit)
+    testImplementation(libs.okhttp.mockwebserver)
     androidTestImplementation(libs.androidx.espresso.core)
     androidTestImplementation(libs.androidx.junit)
 }
diff --git a/app/src/main/java/com/felixbrucker/jules/network/api/JulesApi.kt b/app/src/main/java/com/felixbrucker/jules/network/api/JulesApi.kt
index 214ce92..a6eb4f6 100644
--- a/app/src/main/java/com/felixbrucker/jules/network/api/JulesApi.kt
+++ b/app/src/main/java/com/felixbrucker/jules/network/api/JulesApi.kt
@@ -1,15 +1,27 @@
 package com.felixbrucker.jules.network.api
 
+import com.felixbrucker.jules.network.api.adapter.ActivityAdapter
+import com.felixbrucker.jules.network.api.adapter.InstantAdapter
+import com.felixbrucker.jules.network.api.filter.ActivityFilter
+import com.felixbrucker.jules.network.api.filter.Filter
+import com.felixbrucker.jules.network.api.filter.SessionFilter
+import com.felixbrucker.jules.network.api.filter.SourceFilter
 import com.felixbrucker.jules.network.api.model.ActivitiesListResponse
-import com.felixbrucker.jules.network.api.model.ActivityType
 import com.felixbrucker.jules.network.api.model.ApprovePlanRequest
+import com.felixbrucker.jules.network.api.model.ArchiveSessionRequest
+import com.felixbrucker.jules.network.api.model.ArtifactType
+import com.felixbrucker.jules.network.api.model.AutomationMode
 import com.felixbrucker.jules.network.api.model.CreateSessionRequest
+import com.felixbrucker.jules.network.api.model.FileChangeType
+import com.felixbrucker.jules.network.api.model.Originator
 import com.felixbrucker.jules.network.api.model.SendMessageRequest
 import com.felixbrucker.jules.network.api.model.Session
 import com.felixbrucker.jules.network.api.model.SessionsListResponse
 import com.felixbrucker.jules.network.api.model.SessionState
 import com.felixbrucker.jules.network.api.model.Source
 import com.felixbrucker.jules.network.api.model.SourcesListResponse
+import com.felixbrucker.jules.network.api.model.SourceType
+import com.felixbrucker.jules.network.api.model.UnarchiveSessionRequest
 import com.squareup.moshi.Moshi
 import com.squareup.moshi.adapters.EnumJsonAdapter
 import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
@@ -30,44 +42,23 @@ class JulesApi internal constructor(
     constructor(apiKey: String) : this(
         apiService = createApiService(
             apiKey = apiKey,
-            baseUrl = DEFAULT_BASE_URL,
-            customOkHttpClient = null,
-            customMoshi = null
-        )
-    )
-
-    constructor(
-        apiKey: String,
-        baseUrl: String
-    ) : this(
-        apiService = createApiService(
-            apiKey = apiKey,
-            baseUrl = baseUrl,
-            customOkHttpClient = null,
-            customMoshi = null
-        )
-    )
-
-    constructor(
-        apiKey: String,
-        baseUrl: String,
-        customOkHttpClient: OkHttpClient?,
-        customMoshi: Moshi?
-    ) : this(
-        apiService = createApiService(
-            apiKey = apiKey,
-            baseUrl = baseUrl,
-            customOkHttpClient = customOkHttpClient,
-            customMoshi = customMoshi
+            baseUrl = DEFAULT_BASE_URL
         )
     )
 
     suspend fun getSources(
+        filter: Filter<SourceFilter>? = null,
         pageSize: Int? = null,
-        pageToken: String? = null,
-        filter: String? = null
-    ): SourcesListResponse = executeApiCall {
-        apiService.getSources(pageSize = pageSize, pageToken = pageToken, filter = filter)
+        pageToken: String? = null
+    ): SourcesListResponse {
+        pageSize?.let { require(it in 1..100) { "pageSize must be between 1 and 100 inclusive" } }
+        return executeApiCall {
+            apiService.getSources(
+                pageSize = pageSize,
+                pageToken = pageToken,
+                filter = filter?.toAip160String()
+            )
+        }
     }
 
     suspend fun getSource(sourceId: String): Source = executeApiCall {
@@ -79,11 +70,30 @@ class JulesApi internal constructor(
     }
 
     suspend fun getSessions(
+        filter: Filter<SessionFilter>? = null,
         pageSize: Int? = null,
-        pageToken: String? = null,
-        filter: String? = null
-    ): SessionsListResponse = executeApiCall {
-        apiService.getSessions(pageSize = pageSize, pageToken = pageToken, filter = filter)
+        pageToken: String? = null
+    ): SessionsListResponse {
+        pageSize?.let { require(it in 1..100) { "pageSize must be between 1 and 100 inclusive" } }
+        return executeApiCall {
+            apiService.getSessions(
+                pageSize = pageSize,
+                pageToken = pageToken,
+                filter = filter?.toAip160String()
+            )
+        }
+    }
+
+    suspend fun getArchivedSessions(
+        pageSize: Int? = null,
+        pageToken: String? = null
+    ): SessionsListResponse {
+        pageSize?.let { require(it in 1..100) { "pageSize must be between 1 and 100 inclusive" } }
+        return getSessions(
+            filter = SessionFilter.ByArchived(isArchived = true),
+            pageSize = pageSize,
+            pageToken = pageToken
+        )
     }
 
     suspend fun getSession(sessionId: String): Session = executeApiCall {
@@ -94,20 +104,28 @@ class JulesApi internal constructor(
         apiService.createSession(request = request)
     }
 
-    suspend fun sendMessage(sessionId: String, prompt: String): Session = executeApiCall {
-        apiService.sendMessage(sessionId = sessionId, request = SendMessageRequest(prompt = prompt))
+    suspend fun sendMessage(sessionId: String, prompt: String) {
+        executeApiCall {
+            apiService.sendMessage(sessionId = sessionId, request = SendMessageRequest(prompt = prompt))
+        }
     }
 
-    suspend fun approvePlan(sessionId: String, planId: String? = null): Session = executeApiCall {
-        apiService.approvePlan(sessionId = sessionId, request = ApprovePlanRequest(planId = planId))
+    suspend fun approvePlan(sessionId: String) {
+        executeApiCall {
+            apiService.approvePlan(sessionId = sessionId, request = ApprovePlanRequest())
+        }
     }
 
-    suspend fun archiveSession(sessionId: String): Session = executeApiCall {
-        apiService.archiveSession(sessionId = sessionId)
+    suspend fun archiveSession(sessionId: String) {
+        executeApiCall {
+            apiService.archiveSession(sessionId = sessionId, request = ArchiveSessionRequest())
+        }
     }
 
-    suspend fun unarchiveSession(sessionId: String): Session = executeApiCall {
-        apiService.unarchiveSession(sessionId = sessionId)
+    suspend fun unarchiveSession(sessionId: String) {
+        executeApiCall {
+            apiService.unarchiveSession(sessionId = sessionId, request = UnarchiveSessionRequest())
+        }
     }
 
     suspend fun deleteSession(sessionId: String) {
@@ -118,10 +136,19 @@ class JulesApi internal constructor(
 
     suspend fun getActivities(
         sessionId: String,
+        filter: Filter<ActivityFilter>? = null,
         pageSize: Int? = null,
         pageToken: String? = null
-    ): ActivitiesListResponse = executeApiCall {
-        apiService.getActivities(sessionId = sessionId, pageSize = pageSize, pageToken = pageToken)
+    ): ActivitiesListResponse {
+        pageSize?.let { require(it in 1..100) { "pageSize must be between 1 and 100 inclusive" } }
+        return executeApiCall {
+            apiService.getActivities(
+                sessionId = sessionId,
+                pageSize = pageSize,
+                pageToken = pageToken,
+                filter = filter?.toAip160String()
+            )
+        }
     }
 
     private inline fun <T> executeApiCall(block: () -> T): T {
@@ -144,26 +171,9 @@ class JulesApi internal constructor(
         const val DEFAULT_BASE_URL = "https://jules.googleapis.com/v1alpha/"
         private const val API_KEY_HEADER = "X-Goog-Api-Key"
 
-        fun create(
-            apiKey: String,
-            baseUrl: String = DEFAULT_BASE_URL,
-            customOkHttpClient: OkHttpClient? = null,
-            customMoshi: Moshi? = null
-        ): JulesApi {
-            val apiService = createApiService(
-                apiKey = apiKey,
-                baseUrl = baseUrl,
-                customOkHttpClient = customOkHttpClient,
-                customMoshi = customMoshi
-            )
-            return JulesApi(apiService = apiService)
-        }
-
-        private fun createApiService(
+        internal fun createApiService(
             apiKey: String,
-            baseUrl: String,
-            customOkHttpClient: OkHttpClient?,
-            customMoshi: Moshi?
+            baseUrl: String
         ): JulesApiService {
             require(apiKey.isNotBlank()) { "API Key must not be blank" }
 
@@ -177,19 +187,19 @@ class JulesApi internal constructor(
                 chain.proceed(request)
             }
 
-            val okHttpClient = (customOkHttpClient?.newBuilder() ?: OkHttpClient.Builder())
+            val okHttpClient = OkHttpClient.Builder()
                 .addInterceptor(authInterceptor)
                 .build()
 
-            val moshi = customMoshi ?: Moshi.Builder()
-                .add(
-                    SessionState::class.java,
-                    EnumJsonAdapter.create(SessionState::class.java).withUnknownFallback(SessionState.UNSPECIFIED)
-                )
-                .add(
-                    ActivityType::class.java,
-                    EnumJsonAdapter.create(ActivityType::class.java).withUnknownFallback(ActivityType.UNSPECIFIED)
-                )
+            val moshi = Moshi.Builder()
+                .add(InstantAdapter())
+                .add(ActivityAdapter())
+                .add(SessionState::class.java, EnumJsonAdapter.create(SessionState::class.java).withUnknownFallback(SessionState.UNSPECIFIED))
+                .add(AutomationMode::class.java, EnumJsonAdapter.create(AutomationMode::class.java).withUnknownFallback(AutomationMode.AUTOMATION_MODE_UNSPECIFIED))
+                .add(Originator::class.java, EnumJsonAdapter.create(Originator::class.java).withUnknownFallback(Originator.SYSTEM))
+                .add(ArtifactType::class.java, EnumJsonAdapter.create(ArtifactType::class.java).withUnknownFallback(ArtifactType.UNSPECIFIED))
+                .add(SourceType::class.java, EnumJsonAdapter.create(SourceType::class.java).withUnknownFallback(SourceType.UNSPECIFIED))
+                .add(FileChangeType::class.java, EnumJsonAdapter.create(FileChangeType::class.java).withUnknownFallback(FileChangeType.UNSPECIFIED))
                 .addLast(KotlinJsonAdapterFactory())
                 .build()
 
        """.trimIndent()

        val parsed = UnidiffParser.parse(patch)

        val expectedFiles = listOf(
            ParsedFile(".gitignore", FileChangeType.MODIFIED, additions = 1, deletions = 0),
            ParsedFile(".idea/misc.xml", FileChangeType.MODIFIED, additions = 5, deletions = 0),
            ParsedFile("app/build.gradle.kts", FileChangeType.MODIFIED, additions = 14, deletions = 0),
            ParsedFile(
                "app/src/main/java/com/felixbrucker/jules/network/api/JulesApi.kt",
                FileChangeType.MODIFIED,
                additions = 87,
                deletions = 77
            ),
        )
        assertEquals(expectedFiles.size, parsed.size)
        expectedFiles.forEachIndexed { index, expectedFile ->
            assertEquals(expectedFile, parsed[index])
        }
    }

    @Test
    fun testParseNullOrBlankPatch() {
        assertTrue(UnidiffParser.parse(null).isEmpty())
        assertTrue(UnidiffParser.parse("").isEmpty())
        assertTrue(UnidiffParser.parse("   ").isEmpty())
    }
}
