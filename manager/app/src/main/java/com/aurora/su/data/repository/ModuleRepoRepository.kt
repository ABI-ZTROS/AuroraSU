package com.aurora.su.data.repository

import com.aurora.su.data.model.RepoModule

interface ModuleRepoRepository {
    suspend fun fetchModules(): Result<List<RepoModule>>
}
