package com.ambulantitos.core.data.firebase

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings

object FirebaseAmbulantitos {
    @Volatile
    private var persistenciaActivada = false

    fun inicializar(context: Context) {
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }
        activarCachePersistente()
    }

    fun activarCachePersistente() {
        if (persistenciaActivada) return
        synchronized(this) {
            if (persistenciaActivada) return
            val configuracion = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(
                    PersistentCacheSettings.newBuilder().build(),
                )
                .build()
            try {
                FirebaseFirestore.getInstance().firestoreSettings = configuracion
            } catch (_: IllegalStateException) {
                // Firestore ya se inicializó; en Android la caché persistente queda activa.
            }
            persistenciaActivada = true
        }
    }
}
