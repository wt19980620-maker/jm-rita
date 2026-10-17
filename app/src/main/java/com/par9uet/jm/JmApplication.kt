package com.par9uet.jm

import android.app.Application
import coil.Coil
import coil.ImageLoader
import com.par9uet.jm.di.appModule
import com.par9uet.jm.di.coilModule
import com.par9uet.jm.di.comicModule
import com.par9uet.jm.di.databaseModule
import com.par9uet.jm.di.retrofitModule
import com.par9uet.jm.di.userModule
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.workmanager.koin.workManagerFactory
import org.koin.core.context.startKoin
import com.par9uet.jm.store.InitManager
import com.par9uet.jm.task.AppInitTask

private val moduleList = listOf(
    appModule,
    coilModule,
    comicModule,
    retrofitModule,
    userModule,
    databaseModule
)

class JmApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        val koin = startKoin {
            androidContext(this@JmApplication)
            workManagerFactory()
            modules(moduleList)
        }.koin
        Coil.setImageLoader(koin.get<ImageLoader>())
        koin.get<InitManager>().start(koin.getAll<AppInitTask>(), koin.get())
    }
}
