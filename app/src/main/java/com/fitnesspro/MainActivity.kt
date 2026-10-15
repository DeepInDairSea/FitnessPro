package com.fitnesspro

import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitnesspro.core.designsystem.theme.FitnessProTheme
import com.fitnesspro.core.designsystem.theme.customThemeFrom
import com.fitnesspro.core.domain.repository.SettingsRepository
import com.fitnesspro.core.model.ThemeMode
import com.fitnesspro.core.model.ThemePreset
import com.fitnesspro.navigation.FitnessProNavHost
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settings: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        // splash 与 Bento 画布同渐变，保持到首帧 ready 再移除（跳过退出动画）
        var contentDrawn = false
        installSplashScreen().apply {
            setKeepOnScreenCondition { !contentDrawn }
            setOnExitAnimationListener { it.remove() }
        }
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            // 品牌页先于主题内容：与系统启动画面（同色底+logo）无缝衔接，短暂停留后进主界面
            var showSplash by remember { mutableStateOf(true) }
            val versionName = remember {
                runCatching {
                    packageManager.getPackageInfo(packageName, 0).versionName
                }.getOrNull().orEmpty().ifBlank { "1.0" }
            }
            // 首帧（品牌页）绘制完成后释放系统启动画面：必须放在 return@setContent 之前，
            // 否则显示品牌页期间不会组合，系统画面会一直盖住品牌页直到主界面出现
            LaunchedEffect(Unit) { contentDrawn = true }
            if (showSplash) {
                SplashScreen(versionName = versionName, onFinished = { showSplash = false })
                return@setContent
            }

            val userSettings by settings.settings
                .collectAsStateWithLifecycle(initialValue = null)

            val wallpaper = userSettings?.wallpaperUri?.let { uri ->
                // 壁纸已复制到 filesDir（选图时完成），这里降采样解码避免大图 OOM
                val file = File(filesDir, uri)
                remember(uri) {
                    if (file.exists()) {
                        val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                        BitmapFactory.decodeFile(file.absolutePath, opts)?.asImageBitmap()
                    } else {
                        null
                    }
                }.also {
                    android.util.Log.w("FPWP", "uri=$uri file=${file.absolutePath} exists=${file.exists()} bitmap=${it != null}")
                }
            }

            FitnessProTheme(
                themeMode = userSettings?.themeMode ?: ThemeMode.SYSTEM,
                hasWallpaper = wallpaper != null,
                themePreset = userSettings?.themePreset ?: ThemePreset.BENTO_SOFT,
                customTheme = userSettings?.let(::customThemeFrom),
            ) {
                Box(Modifier.fillMaxSize()) {
                    wallpaper?.let { bitmap ->
                        Image(
                            bitmap = bitmap,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            alpha = userSettings?.wallpaperOpacity ?: 0.35f,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    // onboardingCompleted 为 false（首次启动）时进入引导；
                    // 设置未加载完（null）时不引导，避免误判
                    FitnessProNavHost(
                        showOnboarding = userSettings?.onboardingCompleted == false,
                    )
                }
            }
        }
    }
}
