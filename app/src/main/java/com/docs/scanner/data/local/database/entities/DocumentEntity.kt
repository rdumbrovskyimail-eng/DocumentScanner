package com.docs.scanner.data.local.database.entities

import androidx.room.Fts5

/**
 * FTS5 Virtual Table для полнотекстового поиска.
 * 
 * ✅ FTS5 преимущества:
 * - Лучшая производительность
 * - Поддержка BM25 ранжирования
 * - Более эффективная индексация
 * - contentEntity связывает FTS с основной таблицей documents
 * - Room автоматически создаст триггеры для синхронизации
 * 
 * ⚠️ ВАЖНО: Используем ТОЛЬКО @Fts5, без @Entity!
 * 
 * Связь с DocumentEntity:
 * - originalText синхронизируется с DocumentEntity.originalText
 * - translatedText синхронизируется с DocumentEntity.translatedText
 */
@Fts5(contentEntity = DocumentEntity::class)
data class DocumentsFtsEntity(
    val originalText: String?,
    val translatedText: String?
)