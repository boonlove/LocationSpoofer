package com.suseoaa.locationspoofer.utils

import android.content.Context
import android.os.Build
import java.util.zip.ZipFile

/**
 * ABI 工具类。
 *
 * - [systemAbis]：系统支持的 ABI（按优先级，来自 [Build.SUPPORTED_ABIS]）
 * - [appAbis]：应用 APK 实际打包的 ABI（解析 APK 内 lib/ 目录）
 * - [bestMatchAbi]：系统与应用的最佳匹配 ABI
 *
 * [appAbis] 固定按 arm64-v8a → armeabi-v7a → x86 → x86_64 排序；
 * 包内无 lib（纯 Kotlin/Java 通用包）时为 null。
 */
class AbiHelper(private val context: Context) {

    /** 系统支持的 ABI（按优先级） */
    val systemAbis: Array<String>
        get() = Build.SUPPORTED_ABIS

    /** 系统首选 ABI */
    val systemPrimaryAbi: String
        get() = Build.SUPPORTED_ABIS[0]

    /** 应用打包的 ABI（来自 APK 解析），无 lib 目录时为 null */
    val appAbis: List<String>? by lazy { getAbisFromApk(context) }

    /** 系统与应用的最佳匹配 ABI；应用为通用包时为 null */
    val bestMatchAbi: String?
        get() = appAbis?.let { systemAbis.firstOrNull { abi -> abi in it } }

    /**
     * 解析 APK 内 lib/ 目录得到打包的 ABI 列表。
     * 无 lib 条目时返回 null。
     */
    private fun getAbisFromApk(context: Context): List<String>? {
        val sourceDir = context.applicationInfo.sourceDir ?: return null
        val abis = try {
            ZipFile(sourceDir).use { zip ->
                val entries = zip.entries()
                sequence {
                    while (entries.hasMoreElements()) {
                        // 条目形如 "lib/arm64-v8a/libfoo.so"，取第二段为 ABI 名
                        val name = entries.nextElement().name
                        if (name.startsWith("lib/")) {
                            val abi = name.removePrefix("lib/").substringBefore('/')
                            if (abi.isNotBlank()) yield(abi)
                        }
                    }
                }
                    .distinct()
                    .sortedWith(compareBy { abiOrder(it) })
                    .toList()
            }
        } catch (e: Exception) {
            emptyList()
        }
        // 包内无 lib 返回 null
        return abis.takeIf { it.isNotEmpty() }
    }

    private fun abiOrder(abi: String): Int = ABI_ORDER[abi] ?: Int.MAX_VALUE

    private companion object {
        // 排序优先级：arm64 → arm32 → x86 → x86_64
        private val ABI_ORDER = mapOf(
            "arm64-v8a" to 0,
            "armeabi-v7a" to 1,
            "x86" to 2,
            "x86_64" to 3,
        )
    }
}
