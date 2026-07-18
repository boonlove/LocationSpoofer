// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.jetbrains.kotlin.android) apply false
    alias(libs.plugins.ksp) apply false
}

val androidMinSdkVersion by extra(26)
val androidTargetSdkVersion by extra(35)
val androidCompileSdkVersion by extra(35)
val androidApplicationId by extra("com.suseoaa.locationspoofer.fork")
val androidVersionName by extra(getVersionName())
val androidVersionCode by extra(getVersionCode())

fun getGitRef(): String {
    val process = Runtime.getRuntime().exec(
        arrayOf("git", "show-ref", "--verify", "--quiet", "refs/heads/main")
    )

    return if (process.waitFor() == 0) {
        "main"
    } else {
        "HEAD"
    }
}

fun getGitCommitCount(): Int {
    val ref = getGitRef()
    val process = Runtime.getRuntime().exec(arrayOf("git", "rev-list", "--count", ref))
    return process.inputStream.bufferedReader().use { it.readText().trim().toInt() }
}

fun getGitDescribe(): String {
    val ref = getGitRef()
    val process = Runtime.getRuntime().exec(arrayOf("git", "describe", "--tags", "--abbrev=0", ref))
    val result = process.inputStream.bufferedReader().use { it.readText().trim() }
    return result.ifEmpty { "v1.0.0" }
}

fun getVersionCode(): Int {
    val commitCount = getGitCommitCount()
    return commitCount
}

fun getVersionName(): String {
    return getGitDescribe().removePrefix("v")
}
