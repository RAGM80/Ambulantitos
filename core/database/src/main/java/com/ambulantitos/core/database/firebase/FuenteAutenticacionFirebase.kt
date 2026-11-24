package com.ambulantitos.core.database.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.tasks.await

class FuenteAutenticacionFirebase {
    private val auth by lazy { FirebaseAuth.getInstance() }

    fun uidActual(): String? = auth.currentUser?.uid

    suspend fun iniciarSesion(correo: String, contrasena: String): String =
        auth.signInWithEmailAndPassword(correo, contrasena).await().user?.uid
            ?: error("No se pudo iniciar sesión.")

    suspend fun crearCuenta(correo: String, contrasena: String): Cuenta {
        val usuario = auth.createUserWithEmailAndPassword(correo, contrasena).await().user
            ?: error("No se pudo crear la cuenta.")
        return Cuenta(usuario)
    }

    fun cerrarSesion() = auth.signOut()

    class Cuenta internal constructor(private val usuario: FirebaseUser) {
        val uid: String get() = usuario.uid

        suspend fun eliminar() {
            usuario.delete().await()
        }
    }
}
