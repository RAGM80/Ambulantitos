package com.ambulantitos.core.data

import android.content.Context
import android.net.Uri
import com.ambulantitos.core.domain.ImagenProducto
import com.ambulantitos.core.network.SubidorImagenesCloudinary
import kotlinx.coroutines.CancellationException

class RepositorioImagenes(contexto: Context) {

    private val subidor = SubidorImagenesCloudinary(
        contexto.applicationContext
    )

    suspend fun subirImagen(
        direccionLocal: String
    ): ResultadoAmbulantitos<ImagenProducto> {
        return try {
            require(direccionLocal.isNotBlank()) {
                "Selecciona una fotografía del producto."
            }

            val imagenSubida = subidor.subirImagen(
                Uri.parse(direccionLocal)
            )

            ResultadoAmbulantitos.Exito(
                ImagenProducto(
                    url = imagenSubida.url,
                    idPublico = imagenSubida.idPublico
                )
            )
        } catch (cancelacion: CancellationException) {
            throw cancelacion
        } catch (error: Exception) {
            ResultadoAmbulantitos.Error(
                mensaje = error.message
                    ?.takeIf { it.isNotBlank() }
                    ?: "No se pudo subir la fotografía.",
                causa = error
            )
        }
    }
}