package com.chronoquest.app

import android.app.Application

class ChronoApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
