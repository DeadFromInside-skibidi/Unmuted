package com.unmuted.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.unmuted.app.data.DiscoverFilter
import com.unmuted.app.data.DiscoverQuery
import com.unmuted.app.data.TrendingAudioTopic
import com.unmuted.app.data.VoicePost
import com.unmuted.app.data.repository.DiscoverRepository
import com.unmuted.app.data.repository.ProfileRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class DiscoverViewModel(
    private val discoverRepository: DiscoverRepository,
    private val profileRepository: ProfileRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _activeFilter = MutableStateFlow(DiscoverFilter.TRENDING)
    val activeFilter: StateFlow<DiscoverFilter> = _activeFilter.asStateFlow()

    private val _trendingTopics = MutableStateFlow<List<TrendingAudioTopic>>(emptyList())
    val trendingTopics: StateFlow<List<TrendingAudioTopic>> = _trendingTopics.asStateFlow()

    private val followingUids = profileRepository.observeFollowingUids()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptySet())

    val pagedDiscoverPosts: Flow<PagingData<VoicePost>> = combine(
        _searchQuery,
        _activeFilter,
        followingUids
    ) { query, filter, following ->
        DiscoverQuery(
            filter = filter,
            searchToken = query.trim().takeIf { it.isNotEmpty() },
            followingUids = following
        )
    }
        .debounce(300L)
        .distinctUntilChanged()
        .flatMapLatest { query ->
            discoverRepository.pagedDiscoverPosts(query, pageSize = 15)
        }
        .cachedIn(viewModelScope)

    init {
        refreshTrendingTopics()
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectFilter(filter: DiscoverFilter) {
        _activeFilter.value = filter
    }

    fun refreshDiscover() {
        discoverRepository.invalidatePaging()
        refreshTrendingTopics()
    }

    fun refreshTrendingTopics() {
        viewModelScope.launch {
            val topics = discoverRepository.fetchTrendingTopics()
            _trendingTopics.value = topics
        }
    }

    class Factory(private val container: UnmutedContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DiscoverViewModel(
                discoverRepository = container.discoverRepository,
                profileRepository = container.profileRepository
            ) as T
        }
    }
}
