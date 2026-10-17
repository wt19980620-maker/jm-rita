package com.par9uet.jm.utils

import android.util.Log
import com.par9uet.jm.BuildConfig

inline fun <reified T> T.log(msg: String) {
    if (BuildConfig.DEBUG) Log.d("[JM-MOBILE] ${T::class.java.simpleName}", msg)
}

fun log(tag: String, msg: String) {
    if (BuildConfig.DEBUG) Log.d("[JM-MOBILE] $tag", msg)
}
