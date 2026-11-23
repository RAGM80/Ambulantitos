package com.ambulantitos.core.data

sealed class ResultadoAmbulantitos<out T> {
    data class Exito<T>(val dato: T) : ResultadoAmbulantitos<T>()
    data class Error(val mensaje: String, val causa: Throwable? = null) : ResultadoAmbulantitos<Nothing>()

    val esExito: Boolean get() = this is Exito
}

inline fun <T> ResultadoAmbulantitos<T>.getOrNull(): T? =
    (this as? ResultadoAmbulantitos.Exito)?.dato
