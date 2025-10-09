package com.example.medicineshield.data.database

import androidx.room.TypeConverter
import com.example.medicineshield.data.model.CycleType

class Converters {
    @TypeConverter
    fun fromCycleType(value: CycleType): String {
        return value.name
    }

    @TypeConverter
    fun toCycleType(value: String): CycleType {
        return CycleType.valueOf(value)
    }
}
