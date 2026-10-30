// [GLASS] 液态玻璃渲染内核（独立模块）。
//
// 为什么单独成模块而不是塞进 :app：
//  1. 强制单向依赖 —— 玻璃层不许引用任何业务代码，否则以后没法复用/替换；
//  2. 可以单独编译、单独写单测，不用把 29 万行的 :app 拖下水；
//  3. 将来上游升级 Kotlin/Compose 后，如果切回 Maven 依赖 io.github.kyant0:backdrop，
//     直接把本模块删掉即可。
//
// 代码来源：Kyant0/AndroidLiquidGlass（Apache-2.0）的 backdrop 模块，已做如下改动：
//  - Compose Multiplatform → Jetpack Compose（只保留 Android 目标，删掉 skiko/desktop/js/ios）
//  - expect/actual 合并为单一 Android 实现
//  - 依赖 com.kyant.shapes 的部分换成自研 Shape（见 shape/ 包）
// 详见 NOTICE 与 LICENSE-APACHE-2.0。
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "app.minis.glass"
    compileSdk = 36

    defaultConfig {
        // 与 :app 一致。AGSL（RuntimeShader）要 API 33，RenderEffect 要 31；
        // 更低的版本由 capability 层降级为半透明表面。
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.runtime:runtime")
    implementation("androidx.annotation:annotation:1.9.1")
    // [GLASS] 渐进模糊用 Haze（SaltUI / 椒盐音乐 用的就是它）。
    // 必须 1.6.x：1.7+ 用 Kotlin 2.3 编译，我们的 Kotlin 2.1 读不了它的元数据。
    api("dev.chrisbanes.haze:haze:1.6.10")  // api：HazeState 类型要暴露给 :app
}
