package com.fitnesspro.core.database.seed

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fitnesspro.core.database.FitnessDatabase
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * D19：ExerciseSeedImporter 幂等性测试（instrumented，使用内存数据库 + 合并进 apk 的 assets/exercise_seed.json）。
 */
@RunWith(AndroidJUnit4::class)
class ExerciseSeedImporterTest {

    private lateinit var db: FitnessDatabase
    private lateinit var importer: ExerciseSeedImporter

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, FitnessDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        importer = ExerciseSeedImporter(context, db, Json { ignoreUnknownKeys = true })
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun importIfNeeded_isIdempotent() = runTest {
        val first = importer.importIfNeeded()
        assertTrue("首次导入应灌入 >0 条动作", first > 0)
        // 第二次调用：app_flag 闸门已置位，应直接返回 0（不重复灌入）
        assertEquals(0, importer.importIfNeeded())
    }

    @Test
    fun importIfNeeded_forceReimportsWithoutCrash() = runTest {
        val first = importer.importIfNeeded()
        assertTrue(first > 0)
        // force=true 忽略 app_flag 闸门与已有数据，重新灌入；重名行被 IGNORE 跳过，整体仍幂等于首次规模
        val forced = importer.importIfNeeded(force = true)
        assertTrue(forced > 0)
        assertEquals(first, forced)
    }
}
