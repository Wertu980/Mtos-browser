package com.mtos.web.browser.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mtos.web.browser.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.util.UUID

data class BrowserTab(
    val id: String = UUID.randomUUID().toString(),
    val url: String = "browser://home",
    val title: String = "Start Page",
    val progress: Int = 0,
    val isLoading: Boolean = false,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val jsEnabled: Boolean = true,
    val desktopMode: Boolean = false
)

class BrowserViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val repository = BrowserRepository(database)

    // Observe bookmarks and history
    val bookmarks: StateFlow<List<Bookmark>> = repository.allBookmarks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val history: StateFlow<List<HistoryItem>> = repository.allHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Tabs management
    private val _tabs = MutableStateFlow<List<BrowserTab>>(listOf(BrowserTab()))
    val tabs: StateFlow<List<BrowserTab>> = _tabs.asStateFlow()

    private val _activeTabId = MutableStateFlow<String>(_tabs.value.first().id)
    val activeTabId: StateFlow<String> = _activeTabId.asStateFlow()

    val activeTab: StateFlow<BrowserTab?> = combine(_tabs, _activeTabId) { tabs, activeId ->
        tabs.find { it.id == activeId }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), _tabs.value.first())

    // Toggle Bookmarks flow for UI highlights
    fun isBookmarked(url: String): Flow<Boolean> = repository.isBookmarked(url)

    // Actions
    fun createNewTab(url: String = "browser://home") {
        val title = when (url) {
            "browser://home" -> "Start Page"
            "https://www.google.com" -> "Google"
            else -> "New Tab"
        }
        val newTab = BrowserTab(url = url, title = title)
        _tabs.update { it + newTab }
        _activeTabId.value = newTab.id
    }

    fun closeTab(tabId: String) {
        val currentList = _tabs.value
        if (currentList.size <= 1) {
            // Re-initialize if the last tab is closed
            val replacement = BrowserTab()
            _tabs.value = listOf(replacement)
            _activeTabId.value = replacement.id
            return
        }

        val closingIndex = currentList.indexOfFirst { it.id == tabId }
        val newList = currentList.filter { it.id != tabId }
        _tabs.value = newList

        if (_activeTabId.value == tabId) {
            // Focus another tab
            val nextActiveIndex = if (closingIndex >= newList.size) newList.size - 1 else closingIndex
            _activeTabId.value = newList[nextActiveIndex].id
        }
    }

    fun selectTab(tabId: String) {
        _activeTabId.value = tabId
    }

    private fun updateTab(tabId: String, block: (BrowserTab) -> BrowserTab) {
        _tabs.update { list ->
            list.map { if (it.id == tabId) block(it) else it }
        }
    }

    fun updateUrl(tabId: String, url: String) {
        updateTab(tabId) { it.copy(url = url) }
    }

    fun updateProgress(tabId: String, progress: Int, isLoading: Boolean) {
        updateTab(tabId) { it.copy(progress = progress, isLoading = isLoading) }
    }

    fun updateNavigationState(tabId: String, canGoBack: Boolean, canGoForward: Boolean) {
        updateTab(tabId) { it.copy(canGoBack = canGoBack, canGoForward = canGoForward) }
    }

    fun toggleJs(tabId: String) {
        updateTab(tabId) { it.copy(jsEnabled = !it.jsEnabled) }
    }

    fun toggleDesktopMode(tabId: String) {
        updateTab(tabId) { it.copy(desktopMode = !it.desktopMode) }
    }

    fun onPageFinished(tabId: String, title: String, url: String) {
        val polishedTitle = if (title.isBlank()) {
            if (url.startsWith("https://www.google.com/search")) "Google Search" else url
        } else {
            title
        }
        updateTab(tabId) { it.copy(url = url, title = polishedTitle) }

        // Insert into history (ignore search engine queries if desired or load cleanly)
        if (url.isNotBlank() && !url.startsWith("about:") && !url.startsWith("chrome:")) {
            viewModelScope.launch {
                repository.insertHistory(
                    HistoryItem(
                        url = url,
                        title = polishedTitle
                    )
                )
            }
        }
    }

    fun toggleBookmark(url: String, title: String) {
        viewModelScope.launch {
            val isAlreadyBookmarked = repository.isBookmarkedSync(url)
            val currentTitle = if (title.isBlank()) url else title
            if (isAlreadyBookmarked) {
                repository.deleteBookmark(Bookmark(url = url, title = currentTitle))
            } else {
                repository.insertBookmark(Bookmark(url = url, title = currentTitle))
            }
        }
    }

    fun deleteHistoryItem(id: Int) {
        viewModelScope.launch {
            repository.deleteHistoryItem(id)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    fun resolveUrl(input: String): String {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return "https://www.google.com"

        // Recognizes domains like google.com, test.co, custom IP, etc.
        val urlPattern = "^(https?://)?([a-zA-Z0-9-]+\\.)+[a-zA-Z0-9-]{2,}(/.*)?$".toRegex()
        return if (trimmed.matches(urlPattern) || trimmed.startsWith("localhost") || trimmed.startsWith("10.0.2.2")) {
            if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
                "https://$trimmed"
            } else {
                trimmed
            }
        } else {
            // Google search query
            val queryEncoded = URLEncoder.encode(trimmed, "UTF-8")
            "https://www.google.com/search?q=$queryEncoded"
        }
    }
}
