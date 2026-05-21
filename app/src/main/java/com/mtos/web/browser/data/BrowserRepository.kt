package com.mtos.web.browser.data

import kotlinx.coroutines.flow.Flow

class BrowserRepository(database: AppDatabase) {
    private val bookmarkDao = database.bookmarkDao()
    private val historyDao = database.historyDao()

    val allBookmarks: Flow<List<Bookmark>> = bookmarkDao.getAllBookmarks()
    val allHistory: Flow<List<HistoryItem>> = historyDao.getAllHistory()

    suspend fun insertBookmark(bookmark: Bookmark) {
        bookmarkDao.insertBookmark(bookmark)
    }

    suspend fun deleteBookmark(bookmark: Bookmark) {
        bookmarkDao.deleteBookmark(bookmark)
    }

    fun isBookmarked(url: String): Flow<Boolean> {
        return bookmarkDao.isBookmarkedFlow(url)
    }

    suspend fun isBookmarkedSync(url: String): Boolean {
        return bookmarkDao.isBookmarkedSync(url)
    }

    suspend fun insertHistory(historyItem: HistoryItem) {
        historyDao.insertHistory(historyItem)
    }

    suspend fun deleteHistoryItem(id: Int) {
        historyDao.deleteHistoryItem(id)
    }

    suspend fun clearHistory() {
        historyDao.clearAllHistory()
    }
}
