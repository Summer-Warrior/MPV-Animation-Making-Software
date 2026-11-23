package com.example.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.model.AnimationMotion
import com.example.model.CameraMove
import com.example.model.Model3DType
import com.example.model.ParticleFxType
import com.example.model.ShowProject
import com.example.model.ShowScene
import com.example.model.SpriteActorType
import com.example.model.TexturePreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

@Entity(tableName = "show_projects")
data class ShowProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val subtitle: String,
    val sourceDocumentation: String,
    val scenesJson: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface ShowDao {
    @Query("SELECT * FROM show_projects ORDER BY createdAt DESC")
    fun observeAllShows(): Flow<List<ShowProjectEntity>>

    @Query("SELECT COUNT(*) FROM show_projects")
    suspend fun countShows(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShow(entity: ShowProjectEntity): Long

    @Query("DELETE FROM show_projects WHERE id = :id")
    suspend fun deleteShowById(id: Long)
}

@Database(entities = [ShowProjectEntity::class], version = 1, exportSchema = false)
abstract class ShowDatabase : RoomDatabase() {
    abstract fun showDao(): ShowDao

    companion object {
        @Volatile
        private var INSTANCE: ShowDatabase? = null

        fun getInstance(context: Context): ShowDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    ShowDatabase::class.java,
                    "chronostage_shows.db"
                ).fallbackToDestructiveMigration(dropAllTables = true).build().also { INSTANCE = it }
            }
        }
    }
}

class ShowRepository(private val showDao: ShowDao) {

    val allShows: Flow<List<ShowProject>> = showDao.observeAllShows().map { list ->
        list.map { it.toDomain() }
    }

    suspend fun ensureSeeded() {
        if (showDao.countShows() == 0) {
            StarterShowPresets.defaultShowProjects.forEach { project ->
                showDao.insertShow(project.toEntity())
            }
        }
    }

    suspend fun saveShow(project: ShowProject): Long {
        return showDao.insertShow(project.toEntity())
    }

    suspend fun deleteShow(id: Long) {
        showDao.deleteShowById(id)
    }

    private fun ShowProject.toEntity(): ShowProjectEntity {
        val arr = JSONArray()
        scenes.forEach { scene ->
            val obj = JSONObject().apply {
                put("id", scene.id)
                put("index", scene.index)
                put("title", scene.title)
                put("documentationExcerpt", scene.documentationExcerpt)
                put("narratorScript", scene.narratorScript)
                put("consoleId", scene.consoleId)
                put("model3D", scene.model3D.id)
                put("modelMotion", scene.modelMotion.id)
                put("texturePreset", scene.texturePreset.id)
                put("primarySprite", scene.primarySprite.id)
                put("secondarySprite", scene.secondarySprite.id)
                put("cameraMove", scene.cameraMove.id)
                put("particleFx", scene.particleFx.id)
                put("durationSeconds", scene.durationSeconds)
                put("modelScale", scene.modelScale.toDouble())
                put("lightAzimuth", scene.lightAzimuth.toDouble())
            }
            arr.put(obj)
        }
        return ShowProjectEntity(
            id = id,
            title = title,
            subtitle = subtitle,
            sourceDocumentation = sourceDocumentation,
            scenesJson = arr.toString(),
            createdAt = createdAt
        )
    }

    private fun ShowProjectEntity.toDomain(): ShowProject {
        val scenes = mutableListOf<ShowScene>()
        try {
            val arr = JSONArray(scenesJson)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                scenes += ShowScene(
                    id = obj.optString("id", "scene_$i"),
                    index = obj.optInt("index", i),
                    title = obj.optString("title", "Scene ${i + 1}"),
                    documentationExcerpt = obj.optString("documentationExcerpt", ""),
                    narratorScript = obj.optString("narratorScript", ""),
                    consoleId = obj.optString("consoleId", "switch_deck_hd2d"),
                    model3D = Model3DType.fromId(obj.optString("model3D", "MECHA_VANGUARD")),
                    modelMotion = AnimationMotion.fromId(obj.optString("modelMotion", "ORBIT_SHOWCASE")),
                    texturePreset = TexturePreset.fromId(obj.optString("texturePreset", "NEON_GRID")),
                    primarySprite = SpriteActorType.fromId(obj.optString("primarySprite", "CYBER_KNIGHT")),
                    secondarySprite = SpriteActorType.fromId(obj.optString("secondarySprite", "DOCU_BOT")),
                    cameraMove = CameraMove.fromId(obj.optString("cameraMove", "ORBIT_360")),
                    particleFx = ParticleFxType.fromId(obj.optString("particleFx", "STARFIELD_WARP")),
                    durationSeconds = obj.optInt("durationSeconds", 7),
                    modelScale = obj.optDouble("modelScale", 1.0).toFloat(),
                    lightAzimuth = obj.optDouble("lightAzimuth", 45.0).toFloat()
                )
            }
        } catch (_: Exception) {
            // Fallback if corrupted
        }
        return ShowProject(
            id = id,
            title = title,
            subtitle = subtitle,
            sourceDocumentation = sourceDocumentation,
            scenes = if (scenes.isNotEmpty()) scenes else StarterShowPresets.defaultShowProjects.first().scenes,
            createdAt = createdAt
        )
    }
}
