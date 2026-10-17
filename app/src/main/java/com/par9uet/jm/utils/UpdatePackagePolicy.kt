package com.par9uet.jm.utils

data class UpdatePackageIdentity(
    val packageName: String,
    val versionCode: Long,
    val signerDigests: Set<String>,
)

object UpdatePackagePolicy {
    fun validate(installed: UpdatePackageIdentity, candidate: UpdatePackageIdentity) {
        require(candidate.packageName == installed.packageName) { "安装包不属于当前应用" }
        require(candidate.versionCode > installed.versionCode) { "安装包版本必须高于当前版本" }
        require(installed.signerDigests.isNotEmpty() && candidate.signerDigests == installed.signerDigests) {
            "安装包签名与当前应用不一致"
        }
    }
}
