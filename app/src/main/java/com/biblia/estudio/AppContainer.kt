package com.biblia.estudio

import android.app.Application
import android.content.Context
import com.biblia.estudio.data.content.ContentRepository
import com.biblia.estudio.data.prefs.SettingsRepository
import com.biblia.estudio.data.user.UserDatabase
import com.biblia.estudio.data.user.UserRepository
import com.biblia.estudio.domain.RefParser
import com.biblia.estudio.domain.Speaker
import com.biblia.estudio.work.Notifications

class AppContainer(private val context: Context) {
    val content = ContentRepository(context)
    val user = UserRepository(UserDatabase.create(context))
    val settings = SettingsRepository(context)
    @Volatile var refParser: RefParser? = null
    val speaker by lazy { Speaker(context) }

    suspend fun init(onProgress: (Float) -> Unit = {}) {
        content.ensureReady(onProgress)
        if (refParser == null) refParser = RefParser(content.abbreviations())
    }
}

class BibliaApp : Application() {
    lateinit var container: AppContainer
    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifications.createChannel(this)
    }
}

val Context.app: AppContainer get() = (applicationContext as BibliaApp).container
