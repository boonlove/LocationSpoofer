package com.suseoaa.locationspoofer.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface EnvironmentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: EnvironmentRecord)

    @Query("SELECT * FROM environment_records")
    suspend fun getAllRecords(): List<EnvironmentRecord>

    @Query("SELECT COUNT(*) FROM environment_records")
    suspend fun getRecordCount(): Int

    @Query("DELETE FROM environment_records")
    suspend fun clearAll()

    // 粗略查找距离最近的记录 (SQLite 无法使用复杂的地理计算，这里使用欧氏距离近似)
    @Query("""
        SELECT * FROM environment_records 
        ORDER BY ((lat - :targetLat)*(lat - :targetLat) + (lng - :targetLng)*(lng - :targetLng)) ASC 
        LIMIT 1
    """)
    suspend fun getNearestRecord(targetLat: Double, targetLng: Double): EnvironmentRecord?
}
