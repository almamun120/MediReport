package com.medireport.portal

import android.app.Application
import com.medireport.portal.data.AppDatabase
import com.medireport.portal.data.Prefs
import com.medireport.portal.data.Repository
import com.medireport.portal.util.FileStore

class App : Application() {
    lateinit var repository: Repository
        private set

    override fun onCreate() {
        super.onCreate()
        val store = FileStore(this)
        repository = Repository(this, AppDatabase.build(this), store, Prefs(this))
    }
}
