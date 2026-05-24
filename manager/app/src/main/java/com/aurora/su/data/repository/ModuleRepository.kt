package com.aurora.su.data.repository

import com.aurora.su.data.model.Module
import com.aurora.su.data.model.ModuleUpdateInfo

interface ModuleRepository {
    suspend fun getModules(): Result<List<Module>>
    suspend fun checkUpdate(module: Module): Result<ModuleUpdateInfo>
}
