package com.felixbrucker.jules.ui.logging

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.felixbrucker.jules.data.database.LogEntity
import com.felixbrucker.jules.data.repository.LogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class LogViewerViewModel @Inject constructor(
    private val logRepository: LogRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedPriority = MutableStateFlow(-1)
    val selectedPriority: StateFlow<Int> = _selectedPriority.asStateFlow()

    val totalCount: StateFlow<Int> = logRepository.getTotalCountFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val filteredCount: StateFlow<Int> = combine(
        selectedPriority,
        searchQuery
    ) { priority, query ->
        val priorities = if (priority == -1) emptySet() else setOf(priority)
        priorities to query
    }.flatMapLatest { (priorities, query) ->
        logRepository.getFilteredCountFlow(priorities = priorities, searchQuery = query)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val logsPaged: Flow<PagingData<LogEntity>> = combine(
        selectedPriority,
        searchQuery
    ) { priority, query ->
        val priorities = if (priority == -1) emptySet() else setOf(priority)
        priorities to query
    }.flatMapLatest { (priorities, query) ->
        logRepository.getLogsPaged(priorities = priorities, searchQuery = query)
    }.cachedIn(viewModelScope)

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSelectedPriority(priority: Int) {
        _selectedPriority.value = priority
    }

    fun clearLogs() {
        viewModelScope.launch {
            try {
                logRepository.clearLogs()
            } catch (_: Exception) {
            }
        }
    }
}
