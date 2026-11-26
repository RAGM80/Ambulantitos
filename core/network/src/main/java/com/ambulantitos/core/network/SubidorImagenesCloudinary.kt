package com.ambulantitos.core.network

import android.content.Context
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URL
import java.util.UUID
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class ImagenSubidaCloudinary(
    val url: String,
    val idPublico: String
)

class SubidorImagenesCloudinary(contexto: Context) {

    private val resolver = contexto.applicationContext.contentResolver

    suspend fun subirImagen(uri: Uri): ImagenSubidaCloudinary =
        withContext(Dispatchers.IO) {
            val (tipo, contenido) = leerImagen(uri)

            currentCoroutineContext().ensureActive()

            enviarImagen(tipo, contenido)
        }
    private suspend fun leerImagen(uri: Uri): Pair<String, ByteArray> {
        val tipo = resolver.getType(uri)

        require(
            tipo in setOf(
                "image/jpeg",
                "image/png",
                "image/webp"
            )
        ) {
            "Selecciona una imagen JPG, PNG o WebP."
        }

        val entrada = resolver.openInputStream(uri)
            ?: throw IOException("No se pudo abrir la imagen.")

        val contenido = entrada.use { archivo ->
            ByteArrayOutputStream().use { salida ->
                val buffer = ByteArray(8192)

                while (true) {
                    currentCoroutineContext().ensureActive()

                    val cantidad = archivo.read(buffer)
                    if (cantidad == -1) break

                    require(
                        salida.size().toLong() + cantidad <=
                                ConfiguracionCloudinary.MAXIMO_BYTES_IMAGEN
                    ) {
                        "La imagen no puede superar los 5 MB."
                    }

                    salida.write(buffer, 0, cantidad)
                }

                salida.toByteArray()
            }
        }

        require(contenido.isNotEmpty()) {
            "La imagen está vacía."
        }

        return requireNotNull(tipo) to contenido
    }
    private fun enviarImagen(
        tipo: String,
        contenido: ByteArray
    ): ImagenSubidaCloudinary {
        val extension = when (tipo) {
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            else -> "webp"
        }

        val limite = "Ambulantitos-${UUID.randomUUID()}"

        val encabezado = (
                "--$limite\r\n" +
                        "Content-Disposition: form-data; " +
                        "name=\"upload_preset\"\r\n\r\n" +
                        "${ConfiguracionCloudinary.UPLOAD_PRESET}\r\n" +
                        "--$limite\r\n" +
                        "Content-Disposition: form-data; name=\"file\"; " +
                        "filename=\"producto.$extension\"\r\n" +
                        "Content-Type: $tipo\r\n\r\n"
                ).toByteArray(Charsets.UTF_8)

        val cierre = "\r\n--$limite--\r\n"
            .toByteArray(Charsets.UTF_8)

        val direccion =
            "https://api.cloudinary.com/v1_1/" +
                    "${ConfiguracionCloudinary.CLOUD_NAME}/image/upload"

        val conexion = URL(direccion)
            .openConnection() as HttpsURLConnection

        try {
            conexion.requestMethod = "POST"
            conexion.doOutput = true
            conexion.instanceFollowRedirects = false
            conexion.connectTimeout = 15_000
            conexion.readTimeout = 30_000

            conexion.setRequestProperty(
                "Content-Type",
                "multipart/form-data; boundary=$limite"
            )

            conexion.setFixedLengthStreamingMode(
                encabezado.size + contenido.size + cierre.size
            )

            conexion.outputStream.use { salida ->
                salida.write(encabezado)
                salida.write(contenido)
                salida.write(cierre)
            }

            val codigo = conexion.responseCode

            if (codigo !in 200..299) {
                conexion.errorStream?.close()

                val motivo = conexion.getHeaderField("X-Cld-Error")
                    ?: "No se pudo subir la imagen."

                throw IOException("$motivo (HTTP $codigo)")
            }

            val respuesta = conexion.inputStream
                .bufferedReader(Charsets.UTF_8)
                .use { JSONObject(it.readText()) }

            val url = respuesta.getString("secure_url")
            val idPublico = respuesta.getString("public_id")

            if (!url.startsWith("https://") || idPublico.isBlank()) {
                throw IOException(
                    "Cloudinary devolvió una respuesta incompleta."
                )
            }

            return ImagenSubidaCloudinary(url, idPublico)
        } finally {
            conexion.disconnect()
        }
    }
}