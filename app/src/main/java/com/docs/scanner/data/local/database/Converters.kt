package com.docs.scanner.data.local.database

import androidx.room.TypeConverter
import com.docs.scanner.domain.model.ProcessingStatus
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Room TypeConverters for complex types.
 * 
 * Converts between:
 * - ProcessingStatus enum <-> Int
 * - List<String> <-> JSON String
 * - Map<String, String> <-> JSON String
 */
class Converters {

    private val gson = Gson()

    // ══════════════════════════════════════════════════════════════
    // ProcessingStatus
    // ══════════════════════════════════════════════════════════════

    @TypeConverter
    fun fromProcessingStatus(status: ProcessingStatus): Int {
        return status.value
    }

    @TypeConverter
    fun toProcessingStatus(value: Int): ProcessingStatus {
        return ProcessingStatus.fromInt(value)
    }

    // ══════════════════════════════════════════════════════════════
    // List<String>
    // ══════════════════════════════════════════════════════════════

    @TypeConverter
    fun fromStringList(list: List<String>?): String? {
        return list?.let { gson.toJson(it) }
    }

    @TypeConverter
    fun toStringList(json: String?): List<String>? {
        return json?.let {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson(it, type)
        }
    }

    // ══════════════════════════════════════════════════════════════
    // Map<String, String>
    // ══════════════════════════════════════════════════════════════

    @TypeConverter
    fun fromStringMap(map: Map<String, String>?): String? {
        return map?.let { gson.toJson(it) }
    }

    @TypeConverter
    fun toStringMap(json: String?): Map<String, String>? {
        return json?.let {
            val type = object : TypeToken<Map<String, String>>() {}.type
            gson.fromJson(it, type)
        }
    }

    // ══════════════════════════════════════════════════════════════
    // Long List (for backup file IDs, etc.)
    // ══════════════════════════════════════════════════════════════

    @TypeConverter
    fun fromLongList(list: List<Long>?): String? {
        return list?.let { gson.toJson(it) }
    }

    @TypeConverter
    fun toLongList(json: String?): List<Long>? {
        return json?.let {
            val type = object : TypeToken<List<Long>>() {}.type
            gson.fromJson(it, type)
        }
    }
}