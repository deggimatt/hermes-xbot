package com.uzairansar.hermex.data.repository

import com.uzairansar.hermex.data.db.CacheDao
import com.uzairansar.hermex.data.db.ServerCacheOwnership

class CacheMaintenanceRepository(
    private val cacheDao: CacheDao,
    private val cacheOwnership: ServerCacheOwnership,
) {
    suspend fun clearServer(serverUrl: String) {
        cacheOwnership.invalidateAndClear(serverUrl) {
            cacheDao.clearServer(serverUrl)
        }
    }

    suspend fun maintenance(now: Long = System.currentTimeMillis()) {
        cacheDao.maintenance(now)
    }
}
