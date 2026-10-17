package com.par9uet.jm.di

import com.par9uet.jm.coil.createAsyncImageLoader
import com.par9uet.jm.store.ImageCacheManager
import com.par9uet.jm.task.AppInitTask
import org.koin.dsl.bind
import org.koin.dsl.module

val coilModule = module {
    single { createAsyncImageLoader(get()) }
    single { ImageCacheManager(get(), get()) } bind AppInitTask::class
}
