package com.ambulantitos.app

import android.app.Application
import com.ambulantitos.core.data.firebase.FirebaseAmbulantitos

class AmbulantitosApp : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseAmbulantitos.inicializar(this)
    }
}
