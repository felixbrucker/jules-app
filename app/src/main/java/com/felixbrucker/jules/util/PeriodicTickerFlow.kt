package com.felixbrucker.jules.util

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.time.Duration

fun periodicTickerFlow(period: Duration): Flow<Unit> = flow {
    while (true) {
        emit(Unit)
        delay(period)
    }
}
