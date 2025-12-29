package com.docs.scanner.data.local.database

import androidx.room.TypeConverter

/**
 * Room Type Converters.
 * 
 * ⚠️ CRITICAL: This class MUST be registered in AppDatabase with @TypeConverters
 * 
 * Room can only store primitive types by default (String, Int, Long, Boolean, etc.).
 * For any custom types, you need to provide converters to tell Room how to store them.
 * 
 * Current status:
 * - All entities use primitive types only (String, Long, Int, Boolean)
 * - This file is kept for future use when custom types are needed
 * 
 * Examples of when you'd need converters:
 * - Date objects → convert to/from Long (timestamp)
 * - List<String> → convert to/from JSON string
 * - Custom objects → convert to/from JSON
 * - Enums → convert to/from String/Int
 */
class Converters {
    
    // ============================================
    // EXAMPLE: Date conversions (commented out, not needed yet)
    // ============================================
    
    /*
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }
    
    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }
    */
    
    // ============================================
    // EXAMPLE: List<String> conversions (commented out, not needed yet)
    // ============================================
    
    /*
    @TypeConverter
    fun fromStringList(value: List<String>?): String? {
        return value?.joinToString(",")
    }
    
    @TypeConverter
    fun toStringList(value: String?): List<String>? {
        return value?.split(",")?.map { it.trim() }
    }
    */
    
    // ============================================
    // EXAMPLE: JSON conversions (commented out, not needed yet)
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