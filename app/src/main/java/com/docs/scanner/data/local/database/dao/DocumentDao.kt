package com.docs.scanner.data.local.database.dao

import androidx.room.*
import com.docs.scanner.data.local.database.entities.DocumentEntity
import com.docs.scanner.domain.model.DocumentWithNames
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {
    
    // ============================================
    // БАЗОВЫЕ ОПЕРАЦИИ
    // ============================================
    
    @Query("SELECT * FROM documents WHERE recordId = :recordId ORDER BY position ASC")
    fun getDocumentsByRecord(recordId: Long): Flow<List<DocumentEntity>>
    
    @Query("SELECT * FROM documents WHERE id = :documentId")
    suspend fun getDocumentById(documentId: Long): DocumentEntity?
    
    @Query("SELECT * FROM documents WHERE id = :documentId")
    fun getDocumentByIdFlow(documentId: Long): Flow<DocumentEntity?>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: DocumentEntity): Long
    
    @Update
    suspend fun updateDocument(document: DocumentEntity)
    
    @Delete
    suspend fun deleteDocument(document: DocumentEntity)
    
    @Query("DELETE FROM documents WHERE id = :documentId")
    suspend fun deleteDocumentById(documentId: Long)
    
    // ============================================
    // ✅ ПОИСК (LIKE - работает без FTS5)
    // ============================================
    
    @Query("""
        SELECT 
            d.id,
            d.recordId,
            d.imagePath,
            d.originalText,
            d.translatedText,
            d.position,
            d.processingStatus,
            d.createdAt,
            r.name as recordName,
            f.name as folderName
        FROM documents d
        INNER JOIN records r ON d.recordId = r.id
        INNER JOIN folders f ON r.folderId = f.id
        WHERE (d.originalText IS NOT NULL AND LOWER(d.originalText) LIKE LOWER('%' || :query || '%'))
           OR (d.translatedText IS NOT NULL AND LOWER(d.translatedText) LIKE LOWER('%' || :query || '%'))
        ORDER BY d.createdAt DESC
        LIMIT 50
    """)
    fun searchEverywhereWithNames(query: String): Flow<List<DocumentWithNames>>
    
    // ============================================
    // ОБНОВЛЕНИЕ ТЕКСТА
    // ============================================
    
    @Query("UPDATE documents SET originalText = :text, processingStatus = :status WHERE id = :documentId")
    suspend fun updateOriginalText(documentId: Long, text: String, status: Int = 2)
    
    @Query("UPDATE documents SET translatedText = :text, processingStatus = :status WHERE id = :documentId")
    suspend fun updateTranslatedText(documentId: Long, text: String, status: Int = 4)
    
    @Query("UPDATE documents SET processingStatus = :status WHERE id = :documentId")
    suspend fun updateProcessingStatus(documentId: Long, status: Int)
    
    // ============================================
    // СТАТИСТИКА
    // ============================================
    
    @Query("SELECT COUNT(*) FROM documents WHERE recordId = :recordId")
    suspend fun getDocumentCountInRecord(recordId: Long): Int
    
    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM documents WHERE recordId = :recordId")
    suspend fun getNextPosition(recordId: Long): Int
    
    @Query("SELECT COUNT(*) FROM documents")
    suspend fun getTotalDocumentCount(): Int
    
    @Query("SELECT imagePath FROM documents")
    suspend fun getAllImagePaths(): List<String>
    
    // ============================================
    // ПОЗИЦИОНИРОВАНИЕ
    // ============================================
    
    @Query("UPDATE documents SET position = :newPosition WHERE id = :documentId")
    suspend fun updateDocumentPosition(documentId: Long, newPosition: Int)
    
    // ============================================
    // ФИЛЬТРАЦИЯ ПО СТАТУСУ
    // ============================================
    
    @Query("SELECT * FROM documents WHERE processingStatus = :status ORDER BY createdAt DESC")
    fun getDocumentsByStatus(status: Int): Flow<List<DocumentEntity>>
}
