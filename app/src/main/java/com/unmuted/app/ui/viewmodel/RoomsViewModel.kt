package com.unmuted.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.unmuted.app.data.CommunityRoom
import com.unmuted.app.data.JoinedRoom
import com.unmuted.app.data.repository.RoomsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RoomsViewModel(
    private val roomsRepository: RoomsRepository,
    private val messageBus: AppMessageBus
) : ViewModel() {

    val pagedRooms: Flow<PagingData<CommunityRoom>> = roomsRepository.pagedRoomsFlow(pageSize = 15)
        .cachedIn(viewModelScope)

    // Bounded snapshot window for quick Room picker in Composer & Room detail lookup
    val roomsState: StateFlow<DataUiState<List<CommunityRoom>>> = roomsRepository.observeRoomsWindow(limit = 35L)
        .map<List<CommunityRoom>, DataUiState<List<CommunityRoom>>> { DataUiState.Success(it) }
        .catch { error -> emit(unwrapFlowError(error, "Failed to load rooms")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), DataUiState.Loading)

    val joinedRoomsState: StateFlow<Map<String, JoinedRoom>> = roomsRepository.observeJoinedRooms()
        .catch { error ->
            if (unwrapIsPureCancellation(error)) throw error
            emit(emptyMap())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyMap())

    fun refreshRooms() {
        roomsRepository.invalidateRoomsPaging()
    }

    fun createRoom(
        name: String,
        slug: String,
        description: String,
        category: String,
        anonymousRoom: Boolean,
        accentKey: String,
        guidelines: String,
        onCreated: () -> Unit
    ) {
        viewModelScope.launch {
            roomsRepository.createRoom(name, slug, description, category, anonymousRoom, accentKey, guidelines)
                .fold(
                    onSuccess = {
                        messageBus.emit("Room created!")
                        onCreated()
                    },
                    onFailure = { err ->
                        messageBus.emit(err.localizedMessage ?: "Could not create room.")
                    }
                )
        }
    }

    fun toggleJoinRoom(room: CommunityRoom) {
        val joined = joinedRoomsState.value.containsKey(room.id)
        viewModelScope.launch {
            roomsRepository.toggleJoinRoom(room, joined).onSuccess {
                messageBus.emit(if (joined) "Left r/${room.slug}" else "Joined r/${room.slug}")
            }
        }
    }

    class Factory(private val container: UnmutedContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return RoomsViewModel(
                roomsRepository = container.roomsRepository,
                messageBus = container.messageBus
            ) as T
        }
    }
}
