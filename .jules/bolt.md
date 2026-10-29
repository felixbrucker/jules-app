## 2026-03-30 - Reusing Moshi instance with KotlinJsonAdapterFactory in Converters
**Learning:** Reusing a single Moshi instance configured with `KotlinJsonAdapterFactory` across all Room TypeConverters is dangerous because it enables fallback reflection parsing globally for all models. This masks missing `@JsonClass(generateAdapter = true)` annotations that should use compile-time generated adapters instead of reflection.
**Action:** Do not share a Moshi instance containing `KotlinJsonAdapterFactory` with models that require generated adapters.
