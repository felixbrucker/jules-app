package com.felixbrucker.jules.ui.logging

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.felixbrucker.jules.data.logging.LogEntry
import com.felixbrucker.jules.data.repository.LogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
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

    private val _totalCount = MutableStateFlow(0)
    val totalCount: StateFlow<Int> = _totalCount.asStateFlow()

    private val _filteredCount = MutableStateFlow(0)
    val filteredCount: StateFlow<Int> = _filteredCount.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val logsPaged: kotlinx.coroutines.flow.Flow<PagingData<LogEntry>> = combine(
        selectedPriority,
        searchQuery
    ) { priority, query ->
        refreshCounts(priority, query)
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
                refreshCounts(selectedPriority.value, searchQuery.value)
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to clear logs")
            }
        }
    }

    private fun refreshCounts(priority: Int, query: String) {
        viewModelScope.launch {
            try {
                val priorities = if (priority == -1) emptySet() else setOf(priority)
                val (filtered, total) = logRepository.getCounts(priorities, query)
                _filteredCount.value = filtered
                _totalCount.value = total
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to refresh log counts")
            }
        }
    }

    companion object {
        private const val TAG = "LogViewerViewModel"
    }
}
