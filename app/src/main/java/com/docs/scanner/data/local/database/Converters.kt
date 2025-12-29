package com.docs.scanner.data.local.database

import androidx.room.TypeConverter
import java.util.Date

/**
 * Room Type Converters.
 * 
 * ⚠️ CRITICAL: This class MUST be registered in AppDatabase with @TypeConverters
 * 
 * Room can only store primitive types by default (String, Int, Long, Boolean, etc.).
 * For any custom types, you need to provide converters to tell Room how to store them.
 * 
 * Current status:
 * - Date converters: ✅ Enabled
 * - List<String> converters: ✅ Enabled
 * - JSON converters: ❌ Disabled (requires Gson dependency)
 */
class Converters {
    
    // ============================================
    // Date conversions
    // ============================================
    
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }
    
    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }
    
    // ============================================
    // List<String> conversions
    // ============================================
    
    @TypeConverter
    fun fromStringList(value: List<String>?): String? {
        return value?.joinToString(",")
    }
    
    @TypeConverter
    fun toStringList(value: String?): List<String>? {
        return value?.split(",")?.map { it.trim() }
    }
    
    // ============================================
    // JSON conversions (requires Gson - add to build.gradle if needed)
    // ============================================
    
    /*
    private val gson = Gson()
    
    @TypeConverter
    fun fromJsonToObject(value: String?): MyCustomObject? {
        return value?.let { gson.fromJson(it, MyCustomObject::class.java) }
    }
    
    @TypeConverter
    fun objectToJson(obj: MyCustomObject?): String? {
        return obj?.let { gson.toJson(it) }
    }
    */
}