package com.runmaster.app.data.db

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "workouts")
data class WorkoutEntity(
    @PrimaryKey val id: String,
    val date: String,
    val startTime: Long,
    val endTime: Long,
    val totalDistanceMeters: Double,
    val totalDurationSeconds: Long,
    val avgSpeedMs: Float,
    val maxSpeedMs: Float,
    val totalLaps: Int,
    val calories: Int,
    val groundName: String?
)

@Entity(
    tableName = "laps",
    foreignKeys = [ForeignKey(
        entity = WorkoutEntity::class,
        parentColumns = ["id"],
        childColumns = ["workoutId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("workoutId")]
)
data class LapEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutId: String,
    val lapNumber: Int,
    val durationSeconds: Long,
    val distanceMeters: Double
)

@Dao
abstract class WorkoutDao {
    @Query("SELECT * FROM workouts ORDER BY startTime DESC")
    abstract fun observeWorkouts(): Flow<List<WorkoutEntity>>

    @Query("SELECT * FROM laps WHERE workoutId = :workoutId ORDER BY lapNumber")
    abstract suspend fun getLaps(workoutId: String): List<LapEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertWorkout(workout: WorkoutEntity)

    @Insert
    abstract suspend fun insertLaps(laps: List<LapEntity>)

    @Transaction
    open suspend fun saveWorkout(workout: WorkoutEntity, laps: List<LapEntity>) {
        insertWorkout(workout)
        if (laps.isNotEmpty()) insertLaps(laps)
    }

    @Delete
    abstract suspend fun deleteWorkout(workout: WorkoutEntity)
}

@Database(entities = [WorkoutEntity::class, LapEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun workoutDao(): WorkoutDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "run_master.db"
                ).fallbackToDestructiveMigration().build().also { instance = it }
            }
    }
}
