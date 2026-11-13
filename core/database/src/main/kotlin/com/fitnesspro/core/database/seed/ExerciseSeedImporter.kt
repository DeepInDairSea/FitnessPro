package com.fitnesspro.core.database.seed

import android.content.Context
import androidx.room.withTransaction
import com.fitnesspro.core.database.FitnessDatabase
import com.fitnesspro.core.database.entity.AppFlagEntity
import com.fitnesspro.core.database.entity.ExerciseDefinitionEntity
import com.fitnesspro.core.database.entity.ExerciseMuscleEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 内置动作库种子数据的序列化格式。
 *
 * 之所以不直接反序列化成 Entity：种子文件是**内容资产**，字段可能随版本演进，
 * 与持久化结构解耦可以避免改 schema 就要改种子文件。
 */
@Serializable
data class SeedExercise(
    val name: String,
    val aliases: List<String> = emptyList(),
    @SerialName("type") val type: String,
    @SerialName("primaryMuscle") val primaryMuscle: String,
    @SerialName("secondaryMuscles") val secondaryMuscles: List<String> = emptyList(),
    val equipment: String,
    val isUnilateral: Boolean = false,
    val defaultRestSeconds: Int = 90,
    val defaultSets: Int = 3,
    val defaultReps: String? = "10",
    val instructions: String? = null,
    /** 大肌群（MajorGroup 枚举名）—— v2 分类体系 */
    val majorGroup: String? = null,
    /** 细分肌群（中文名即 ID，须属于 majorGroup）—— v2 分类体系 */
    val subGroup: String? = null,
    // ===== 能耗维度（v3 标定，fit-carculate.md / kcal-develp.md）=====
    val metValue: Double = 3.5,
    val isBodyweight: Boolean = false,
    val selfWeightCoefficient: Double = 0.5,
    val equipmentEfficiency: Double = 1.0,
    val isCompound: Boolean = true,
    val tempoFactor: Double = 1.0,
    val intensityZone: String = "HYPERTROPHY",
    val movementPattern: String = "OTHER",
    val metabolicType: String = "MIXED",
    val averageRepDurationSeconds: Int = 3,
    val primaryMuscleVolume: String = "LARGE",
    val isBeginnerFriendly: Boolean = true,
    val isWarmupSuitable: Boolean = false,
)

@Serializable
private data class SeedFile(
    val version: Int = 2,
    val exercises: List<SeedExercise> = emptyList(),
)

/**
 * 把 assets/exercise_seed.json 导入动作库。
 *
 * v2 语义（分类体系升级：MajorGroup → SubGroup → 动作）：
 *  - **按名更新**：同名内置动作保留 id / 收藏 / 使用次数 / 媒体附件，内容字段刷新为种子值；
 *    这样被计划、训练记录、个人纪录引用的动作不会断链，用户收藏也不丢
 *  - **插入新增**：库里没有的动作直接插入
 *  - **清理废弃**：新版种子中已不存在的内置动作，仅在**未被计划/训练/个人纪录引用**时删除
 *    （被引用的保留为"遗留条目"，分类由 v10 迁移按旧 primaryMuscle 回填）
 *
 * 幂等：以 app_flag 表的 exercise_seed_v2 标记为准。整个导入在**单个 Room 事务**内完成。
 */
@Singleton
class ExerciseSeedImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: FitnessDatabase,
    private val json: Json,
) {

    suspend fun importIfNeeded(force: Boolean = false): Int = withContext(Dispatchers.IO) {
        val flagDao = database.appFlagDao()
        if (!force && flagDao.exists(FLAG_KEY) > 0) return@withContext 0

        val seeds = runCatching { loadSeeds() }
            .onFailure { Timber.e(it, "解析动作库种子文件失败") }
            .getOrDefault(emptyList())

        if (seeds.isEmpty()) {
            Timber.w("动作库种子文件为空，跳过导入")
            flagDao.put(AppFlagEntity(FLAG_KEY, "empty"))
            return@withContext 0
        }

        val now = System.currentTimeMillis()
        val dao = database.exerciseDao()

        database.withTransaction {
            val existingByName = dao.getAllNonCustom().associateBy { it.name }
            val keepNames = ArrayList<String>(seeds.size)
            var inserted = 0
            var updated = 0

            for (seed in seeds) {
                keepNames.add(seed.name)
                val existing = existingByName[seed.name]
                if (existing == null) {
                    val rowId = dao.insert(seed.toEntity(now))
                    if (rowId > 0) {
                        insertMuscles(dao, rowId, seed)
                        inserted++
                    }
                } else {
                    // 按名更新：保留 id / 收藏 / 用量 / 媒体 / 创建时间，其余刷新为种子内容
                    dao.update(
                        seed.toEntity(now).copy(
                            id = existing.id,
                            isFavorite = existing.isFavorite,
                            usageCount = existing.usageCount,
                            lastUsedAt = existing.lastUsedAt,
                            mediaType = existing.mediaType,
                            mediaPath = existing.mediaPath,
                            thumbnailPath = existing.thumbnailPath,
                            colorKey = existing.colorKey,
                            createdAt = existing.createdAt,
                        ),
                    )
                    dao.deleteMusclesOf(existing.id)
                    insertMuscles(dao, existing.id, seed)
                    updated++
                }
            }

            val pruned = dao.deleteObsoleteNonCustom(keepNames)
            database.appFlagDao()
                .put(AppFlagEntity(FLAG_KEY, "total=${seeds.size} inserted=$inserted updated=$updated pruned=$pruned"))
            Timber.i("动作库种子 v2 导入完成：%d 条（新增 %d，更新 %d，清理 %d）", seeds.size, inserted, updated, pruned)
        }

        seeds.size
    }

    private suspend fun insertMuscles(dao: com.fitnesspro.core.database.dao.ExerciseDao, exerciseId: Long, seed: SeedExercise) {
        if (seed.secondaryMuscles.isEmpty()) return
        dao.insertMuscles(seed.secondaryMuscles.map { ExerciseMuscleEntity(exerciseId = exerciseId, muscle = it) })
    }

    private fun loadSeeds(): List<SeedExercise> {
        val text = context.assets.open(SEED_FILE).bufferedReader().use { it.readText() }
        return json.decodeFromString<SeedFile>(text).exercises
    }

    private fun SeedExercise.toEntity(now: Long) = ExerciseDefinitionEntity(
        name = name,
        aliases = aliases.takeIf { it.isNotEmpty() }?.joinToString(","),
        type = type,
        primaryMuscle = primaryMuscle,
        equipment = equipment,
        majorGroup = majorGroup,
        subGroup = subGroup,
        isUnilateral = isUnilateral,
        defaultRestSeconds = defaultRestSeconds,
        defaultSets = defaultSets,
        defaultReps = defaultReps,
        instructions = instructions,
        // 能耗维度标定值：逐动作写入，卡路里多因素估算的输入
        metValue = metValue,
        isBodyweight = isBodyweight,
        selfWeightCoefficient = selfWeightCoefficient,
        equipmentEfficiency = equipmentEfficiency,
        isCompound = isCompound,
        tempoFactor = tempoFactor,
        intensityZone = intensityZone,
        movementPattern = movementPattern,
        metabolicType = metabolicType,
        averageRepDurationSeconds = averageRepDurationSeconds,
        primaryMuscleVolume = primaryMuscleVolume,
        isBeginnerFriendly = isBeginnerFriendly,
        isWarmupSuitable = isWarmupSuitable,
        isCustom = false,
        createdAt = now,
        updatedAt = now,
    )

    companion object {
        const val SEED_FILE = "exercise_seed.json"

        /**
         * v3：能耗维度标定。升版让老安装也重导一次——
         * 导入按名更新且保留收藏/媒体/用量，只会回填标定字段，对用户无感。
         */
        const val FLAG_KEY = "exercise_seed_v3"
    }
}
