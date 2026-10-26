package com.felixbrucker.jules.util

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppState @Inject constructor() {
    @Volatile
    var isForeground: Boolean = false
}
