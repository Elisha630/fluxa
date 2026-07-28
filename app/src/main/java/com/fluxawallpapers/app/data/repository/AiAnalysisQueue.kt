package com.fluxawallpapers.app.data.repository

import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class AiAnalysisQueue {
    private val semaphore = Semaphore(2)
    suspend fun <T> execute(block: suspend () -> T): T = semaphore.withPermit { block() }
}
