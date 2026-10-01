package com.g1lg1l.stash

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.g1lg1l.stash.data.MetadataService
import com.g1lg1l.stash.data.Stash
import com.g1lg1l.stash.ui.Prefs

class StashApp : Application(), SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        Stash.open(this)
    }

    /** Thumbnails cross-fade in and are cached in memory and on disk. */
    override fun newImageLoader(context: PlatformContext) = ImageLoader.Builder(context)
        .components { add(OkHttpNetworkFetcherFactory(callFactory = { MetadataService.client })) }
        .crossfade(true)
        .build()
}
