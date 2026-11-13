package com.fitnesspro.core.data.repository

import androidx.room.withTransaction
import com.fitnesspro.core.model.result.DomainError
import com.fitnesspro.core.model.result.Outcome
import com.fitnesspro.core.model.result.runCatchingOutcomeSuspend
import com.fitnesspro.core.data.mapper.groupIntoMuscles
import com.fitnesspro.core.data.mapper.mediaRefOf
import com.fitnesspro.core.data.mapper.toDomain
import com.fitnesspro.core.data.media.MediaRegistrar
import com.fitnesspro.core.database.FitnessDatabase
import com.fitnesspro.core.database.dao.ExerciseDao
import com.fitnesspro.core.database.entity.ExerciseDefinitionEntity
import com.fitnesspro.core.database.entity.ExerciseMuscleEntity
import com.fitnesspro.core.database.seed.ExerciseSeedImporter
import com.fitnesspro.core.domain.calculator.FtsQueryBuilder
import com.fitnesspro.core.domain.repository.ExerciseRepository
import com.fitnesspro.core.model.Equipment
import com.fitnesspro.core.model.Exercise
import com.fitnesspro.core.model.ExerciseFilter
import com.fitnesspro.core.model.ExerciseTaxonomy
import com.fitnesspro.core.model.ExerciseType
import com.fitnesspro.core.model.MajorGroup
import com.fitnesspro.core.model.MediaRef
import com.fitnesspro.core.model.MediaType
import com.fitnesspro.core.model.MuscleGroup
import com.fitnesspro.core.model.time.TimeProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExerciseRepositoryImpl @Inject constructor(
    private val database: FitnessDatabase,
    private val exerciseDao: ExerciseDao,
    private val seedImporter: ExerciseSeedImporter,
    private val mediaRegistrar: MediaRegistrar,
    private val time: TimeProvider,
) : ExerciseRepository {

    override fun observeExercise(id: Long): Flow<Exercise?> =
        exerciseDao.observeById(id).map { entity ->
            entity?.let { withMuscles(listOf(it)).firstOrNull() }
        }

    override fun observeRecentlyUsed(limit: Int): Flow<List<Exercise>> =
        exerciseDao.observeRecentlyUsed(limit).map { withMuscles(it) }

    override fun observeFiltered(filter: ExerciseFilter): Flow<List<Exercise>> {
        val query = filter.query.trim()

        // 中文场景下 FTS 前缀匹配会漏（FTS4 无中文分词），因此把 FTS 与 LIKE 结果做并集。
        // 这是保证"卧推能搜到平板卧推、也能搜到上斜卧推"的关键兜底。
        val hasQuery = query.isNotEmpty()
        val ftsQuery = FtsQueryBuilder.build(query)
        val useFallback = FtsQueryBuilder.needsLikeFallback(query)

        val source: Flow<List<ExerciseDefinitionEntity>> = if (!hasQuery) {
            exerciseDao.filter(
                muscle = filter.muscle?.name,
                majorGroup = filter.majorGroup?.name,
                subGroup = filter.subGroup,
                equipment = filter.equipment?.name,
                type = filter.type?.name,
                favoritesOnly = if (filter.favoritesOnly) 1 else 0,
                customOnly = if (filter.customOnly) 1 else 0,
            )
        } else {
            val ftsFlow = if (ftsQuery != null) exerciseDao.searchFts(ftsQuery) else flowOf(emptyList())
            if (useFallback) {
                combine(ftsFlow, exerciseDao.searchLike(query)) { fts, like ->
                    (fts + like).distinctBy { it.id }
                }
            } else {
                ftsFlow
            }
        }

        return source.map { entities ->
            var result = withMuscles(entities)
            // 内存内再做一次筛选，保证 FTS 路径下肌群/器械筛选同样生效
            filter.muscle?.let { m -> result = result.filter { it.primaryMuscle == m } }
            filter.majorGroup?.let { g -> result = result.filter { it.majorGroup == g } }
            filter.subGroup?.let { s -> result = result.filter { it.subGroup == s } }
            filter.equipment?.let { e -> result = result.filter { it.equipment == e } }
            filter.type?.let { t -> result = result.filter { it.type == t } }
            if (filter.favoritesOnly) result = result.filter { it.isFavorite }
            if (filter.customOnly) result = result.filter { it.isCustom }
            result
        }
    }

    private suspend fun withMuscles(entities: List<ExerciseDefinitionEntity>): List<Exercise> {
        if (entities.isEmpty()) return emptyList()
        val muscles = exerciseDao.getMusclesFor(entities.map { it.id }).groupIntoMuscles()
        return entities.map { it.toDomain(muscles[it.id].orEmpty()) }
    }

    override suspend fun getExercise(id: Long): Exercise? = runCatching {
        exerciseDao.getById(id)?.toDomain(
            exerciseDao.getMusclesFor(listOf(id)).groupIntoMuscles()[id].orEmpty()
        )
    }.getOrNull()

    override suspend fun createCustomExercise(
        name: String,
        type: ExerciseType,
        primaryMuscle: MuscleGroup,
        equipment: Equipment,
        isUnilateral: Boolean,
        defaultSets: Int,
        defaultReps: String?,
        defaultRestSeconds: Int,
        instructions: String?,
        colorKey: String?,
        majorGroup: MajorGroup?,
        subGroup: String?,
    ): Outcome<Long> {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "动作名称不能为空" }

        // 重名检查必须显式做：name 上是普通索引而非唯一索引，
        // OnConflictStrategy.IGNORE 对它不生效，重名会被直接插入。
        // 放在 runCatchingOutcomeSuspend 之外 —— 它用 crossinline 接收代码块，
        // 里面禁止非局部返回。单用户场景下查重与写入之间的间隙可以忽略。
        exerciseDao.findByName(trimmed)?.let {
            return Outcome.Failure(DomainError.DuplicateName(trimmed))
        }

        return runCatchingOutcomeSuspend {
        val now = time.nowEpochMs()
        database.withTransaction {
            exerciseDao.insert(
                ExerciseDefinitionEntity(
                    name = trimmed,
                    type = type.name,
                    primaryMuscle = primaryMuscle.name,
                    equipment = equipment.name,
                    majorGroup = (majorGroup ?: ExerciseTaxonomy.legacyMajorOf(primaryMuscle)).name,
                    subGroup = subGroup,
                    isUnilateral = isUnilateral,
                    defaultSets = defaultSets.coerceIn(1, 30),
                    defaultReps = defaultReps,
                    defaultRestSeconds = defaultRestSeconds.coerceIn(0, 900),
                    instructions = instructions?.trim()?.takeIf { it.isNotEmpty() },
                    colorKey = colorKey,
                    isCustom = true,
                    createdAt = now,
                    updatedAt = now,
                )
            )
        }
        }
    }

    override suspend fun updateExercise(exercise: Exercise): Outcome<Unit> {
        // 改名同样要查重，但排除自己（名字没改时查到的就是本动作）。
        // 与 createCustomExercise 相同，查重放在结果包装之外
        exerciseDao.findByName(exercise.name.trim())?.let { duplicate ->
            if (duplicate.id != exercise.id) {
                return Outcome.Failure(DomainError.DuplicateName(exercise.name.trim()))
            }
        }

        return runCatchingOutcomeSuspend {
            val now = time.nowEpochMs()
            database.withTransaction {
                exerciseDao.update(
                    ExerciseDefinitionEntity(
                        id = exercise.id,
                        name = exercise.name.trim(),
                        aliases = exercise.aliases.takeIf { it.isNotEmpty() }?.joinToString(","),
                        type = exercise.type.name,
                        primaryMuscle = exercise.primaryMuscle.name,
                        equipment = exercise.equipment.name,
                        majorGroup = exercise.majorGroup.name,
                        subGroup = exercise.subGroup,
                        isUnilateral = exercise.isUnilateral,
                        defaultRestSeconds = exercise.defaultRestSeconds,
                        defaultSets = exercise.defaultSets,
                        defaultReps = exercise.defaultReps,
                        instructions = exercise.instructions,
                        // 能耗标定字段必须透传：编辑动作时不能用实体默认值
                        // 覆盖种子数据的 MET/自重系数等标定值，否则估算精度退化
                        metValue = exercise.metValue,
                        isBodyweight = exercise.isBodyweight,
                        selfWeightCoefficient = exercise.selfWeightCoefficient,
                        equipmentEfficiency = exercise.equipmentEfficiency,
                        isCompound = exercise.isCompound,
                        tempoFactor = exercise.tempoFactor,
                        intensityZone = exercise.intensityZone.name,
                        movementPattern = exercise.movementPattern.name,
                        metabolicType = exercise.metabolicType.name,
                        averageRepDurationSeconds = exercise.averageRepDurationSeconds,
                        primaryMuscleVolume = exercise.primaryMuscleVolume.name,
                        isBeginnerFriendly = exercise.isBeginnerFriendly,
                        isWarmupSuitable = exercise.isWarmupSuitable,
                        mediaType = exercise.media.mediaTypeName(),
                        mediaPath = exercise.media?.path(),
                        thumbnailPath = exercise.media?.thumbnail(),
                        colorKey = exercise.colorKey,
                        isCustom = exercise.isCustom,
                        isFavorite = exercise.isFavorite,
                        createdAt = now,
                        updatedAt = now,
                    )
                )
                // 次要肌群整体重写
                exerciseDao.deleteMusclesOf(exercise.id)
                if (exercise.secondaryMuscles.isNotEmpty()) {
                    exerciseDao.insertMuscles(
                        exercise.secondaryMuscles.distinct().map {
                            ExerciseMuscleEntity(exerciseId = exercise.id, muscle = it.name)
                        }
                    )
                }
            }
        }
    }

    override suspend fun setFavorite(id: Long, favorite: Boolean): Outcome<Unit> =
        runCatchingOutcomeSuspend { exerciseDao.setFavorite(id, favorite, time.nowEpochMs()) }

    override suspend fun archive(id: Long): Outcome<Unit> =
        runCatchingOutcomeSuspend { exerciseDao.archive(id, time.nowEpochMs()) }

    override suspend fun markUsed(id: Long): Outcome<Unit> =
        runCatchingOutcomeSuspend { exerciseDao.bumpUsage(id, time.nowEpochMs()) }

    override suspend fun setExerciseMedia(id: Long, media: MediaRef?): Outcome<Unit> =
        runCatchingOutcomeSuspend {
            val entity = exerciseDao.getById(id) ?: error("动作不存在")
            exerciseDao.update(
                entity.copy(
                    mediaType = media.mediaTypeName(),
                    mediaPath = media?.path(),
                    thumbnailPath = media?.thumbnail(),
                    updatedAt = time.nowEpochMs(),
                )
            )
        }

    override suspend fun importSeedIfNeeded(force: Boolean): Outcome<Int> =
        runCatchingOutcomeSuspend { seedImporter.importIfNeeded(force) }

    // ------------------------------------------------------------------
    // 回收站
    // ------------------------------------------------------------------

    override fun observeTrash(): Flow<List<Exercise>> =
        exerciseDao.observeTrash().map { withMuscles(it) }

    override suspend fun moveToTrash(id: Long): Outcome<Unit> =
        runCatchingOutcomeSuspend { exerciseDao.moveToTrash(id, time.nowEpochMs()) }

    override suspend fun restoreFromTrash(id: Long): Outcome<Unit> =
        runCatchingOutcomeSuspend { exerciseDao.restoreFromTrash(id, time.nowEpochMs()) }

    override suspend fun deleteForever(id: Long): Outcome<Unit> = runCatchingOutcomeSuspend {
        // 内置动作被计划处方与训练历史的名称快照间接引用，物理删除会造成悬空；
        // 因此只有自定义动作允许彻底删除，内置动作只能恢复
        val entity = exerciseDao.getById(id)
        require(exerciseDao.deleteCustom(id) > 0) { "内置动作不能彻底删除，只能恢复" }
        // 行删了，媒体文件不能在这里删（用户可能刚误删）——
        // 把登记行打回 PENDING，让 GC 用 24h 短宽限期回收
        releaseMedia(entity)
    }

    override suspend fun purgeExpiredTrash(retentionDays: Int): Outcome<Int> = runCatchingOutcomeSuspend {
        val threshold = time.nowEpochMs() - retentionDays * 24L * 60 * 60 * 1000
        var purged = 0
        exerciseDao.getTrashOlderThan(threshold).forEach { entity ->
            if (entity.isCustom) {
                purged += exerciseDao.deleteCustom(entity.id)
                releaseMedia(entity)
            } else {
                // 内置动作不能物理删除（见 deleteForever），过期后直接移出回收站
                exerciseDao.restoreFromTrash(entity.id, time.nowEpochMs())
            }
        }
        purged
    }

    /** 动作被物理删除后，其演示文件不再有引用方：标记待回收，由 GC 统一处理 */
    private suspend fun releaseMedia(entity: ExerciseDefinitionEntity?) {
        entity?.mediaPath?.let { mediaRegistrar.markPending(it) }
        entity?.thumbnailPath?.let { mediaRegistrar.markPending(it) }
    }

    private fun MediaRef?.mediaTypeName(): String = when (this) {
        null -> MediaType.NONE.name
        is MediaRef.Local -> type.name
        is MediaRef.Remote -> MediaType.VIDEO_LINK.name
    }

    private fun MediaRef.path(): String = when (this) {
        is MediaRef.Local -> fileName
        is MediaRef.Remote -> url
    }

    private fun MediaRef.thumbnail(): String? = (this as? MediaRef.Local)?.thumbnailName

    @Suppress("unused")
    private fun unusedMediaRefHelper() = mediaRefOf(null, null, null)
}
