package ru.moycheckup

import android.app.Application
import ru.moycheckup.data.Repo
import ru.moycheckup.notify.Notifier

class CheckupApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Repo.init(this)
        Notifier.ensureChannel(this)
    }
}
