package com.example

import android.app.Application
import com.example.data.local.ChatDatabase

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        database = ChatDatabase.getDatabase(this)
    }

    companion object {
        lateinit var instance: MyApplication
            private set
        
        lateinit var database: ChatDatabase
            private set
    }
}
