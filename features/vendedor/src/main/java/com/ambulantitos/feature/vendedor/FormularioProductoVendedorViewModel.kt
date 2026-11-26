package com.ambulantitos.feature.vendedor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ambulantitos.core.data.RepositorioFirestore
import com.ambulantitos.core.data.ResultadoAmbulantitos
import com.ambulantitos.core.domain.EstadoProducto
import com.ambulantitos.core.domain.ImagenProducto
import com.ambulantitos.core.domain.Product
import com.ambulantitos.core.domain.User
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FormularioProductoVendedorViewModel internal constructor(
    private val datosGuardados: SavedStateHandle,
    private val obtenerPerfil: suspend () -> ResultadoAmbulantitos<User>,
    private val guardarEnFirestore: suspend (String, Product) -> ResultadoAmbulantitos<Product>,
    private val consultarProducto: suspend (String, String) -> ResultadoAmbulantitos<Product> =
        { negocio, id -> RepositorioFirestore.obtener().obtenerProducto(negocio, id) },
    private val actualizarEnFirestore: suspend (String, Product, Int) -> ResultadoAmbulantitos<Product> =
        { negocio, producto, existencias -> RepositorioFirestore.obtener().editarProducto(negocio, producto, existencias) }
) : ViewModel() {

    constructor(datosGuardados: SavedStateHandle) : this(
        datosGuardados,
        { RepositorioFirestore.obtener().obtenerUsuarioActual() },
        { negocio, producto -> RepositorioFirestore.obtener().crearProducto(negocio, producto) }
    )

    private val guardadoInterno = MutableStateFlow<EstadoGuardadoProductoVendedor>(
        EstadoGuardadoProductoVendedor.Inicial
    )
    val estadoGuardado = guardadoInterno.asStateFlow()

    val guardando: Boolean
        get() = guardadoInterno.value == EstadoGuardadoProductoVendedor.CargandoProducto ||
            guardadoInterno.value == EstadoGuardadoProductoVendedor.SubiendoImagen ||
            guardadoInterno.value == EstadoGuardadoProductoVendedor.GuardandoProducto

    private val bloqueado: Boolean
        get() = guardando || guardadoInterno.value is EstadoGuardadoProductoVendedor.Completado ||
            (esEdicion && productoOriginal == null)

    val estadoProducto = datosGuardados.getStateFlow("estado_producto", EstadoProducto.EN_EXISTENCIA)
    val ingredientes = datosGuardados.getStateFlow("ingredientes", arrayListOf<String>())
    val imagenSeleccionada = datosGuardados.getStateFlow<String?>("imagen_producto", null)

    val esEdicion = !datosGuardados.get<String>(DetalleProductoVendedorActivity.EXTRA_PRODUCTO_ID).isNullOrBlank()
    private var productoOriginal: Product? = null
    private val productoParaEditarInterno = MutableStateFlow<Product?>(null)
    val productoParaEditar = productoParaEditarInterno.asStateFlow()

    init { if (esEdicion) cargarProductoParaEditar() }

    private fun cargarProductoParaEditar() {
        guardadoInterno.value = EstadoGuardadoProductoVendedor.CargandoProducto
        viewModelScope.launch {
            try {
                val perfil = when (val resultado = obtenerPerfil()) {
                    is ResultadoAmbulantitos.Exito -> resultado.dato
                    is ResultadoAmbulantitos.Error -> { mostrarError(resultado.mensaje); return@launch }
                }
                val negocio = perfil.vendorId
                if (!perfil.esVendedor || negocio.isNullOrBlank()) {
                    mostrarError("Inicia sesión con una cuenta de vendedor.")
                    return@launch
                }
                val id = datosGuardados.get<String>(DetalleProductoVendedorActivity.EXTRA_PRODUCTO_ID).orEmpty()
                when (val resultado = consultarProducto(negocio, id)) {
                    is ResultadoAmbulantitos.Exito -> {
                        val producto = resultado.dato
                        if (!producto.activo) { mostrarError("El producto ya fue eliminado."); return@launch }
                        productoOriginal = producto
                        if (datosGuardados.get<Boolean>("edicion_preparada") != true) {
                            datosGuardados["estado_producto"] = if (datosGuardados.get<Boolean>(DetalleProductoVendedorActivity.EXTRA_REPONER) == true) EstadoProducto.EN_EXISTENCIA else producto.estado
                            datosGuardados["ingredientes"] = ArrayList(producto.ingredientes)
                            datosGuardados["imagen_producto"] = producto.imagenUrl
                            datosGuardados["existencias_originales_edicion"] = producto.existencias
                            datosGuardados["edicion_preparada"] = true
                        }
                        productoParaEditarInterno.value = producto
                        guardadoInterno.value = EstadoGuardadoProductoVendedor.Inicial
                    }
                    is ResultadoAmbulantitos.Error -> mostrarError(resultado.mensaje)
                }
            } catch (cancelacion: CancellationException) {
                throw cancelacion
            } catch (error: Exception) {
                mostrarError(error.message ?: "No se pudo cargar el producto para editar.")
            }
        }
    }

    fun seleccionarEstado(estado: String) {
        if (!bloqueado && estado in listOf(EstadoProducto.EN_EXISTENCIA, EstadoProducto.AGOTADO)) {
            datosGuardados["estado_producto"] = estado
        }
    }

    fun agregarIngrediente(texto: String): String? {
        if (bloqueado) return "Espera a que termine el registro."
        val ingrediente = texto.trim().replace(Regex("\\s+"), " ")
        if (ingrediente.isBlank()) return "Escribe un ingrediente."
        if (ingredientes.value.any { it.equals(ingrediente, ignoreCase = true) }) {
            return "Ese ingrediente ya está agregado."
        }
        if (ingredientes.value.size >= 6) return "Puedes agregar hasta 6 ingredientes."
        datosGuardados["ingredientes"] = ArrayList(ingredientes.value + ingrediente)
        return null
    }

    fun quitarIngrediente(ingrediente: String) {
        if (bloqueado) return
        datosGuardados["ingredientes"] = ArrayList(ingredientes.value.filterNot { it == ingrediente })
    }

    fun seleccionarImagen(uri: String) {
        if (!bloqueado) datosGuardados["imagen_producto"] = uri
    }

    private fun prepararProducto(
        nombre: String,
        unidadMedida: String,
        precioTexto: String,
        existenciasTexto: String,
        descripcion: String,
        marca: String
    ): Product? {
        fun rechazar(mensaje: String): Product? {
            guardadoInterno.value = EstadoGuardadoProductoVendedor.Error(mensaje)
            return null
        }
        val nombreLimpio = nombre.trim()
        if (nombreLimpio.isBlank()) return rechazar("Escribe el nombre del producto.")
        if (unidadMedida !in listOf("pieza", "litro", "kilogramo")) {
            return rechazar("Selecciona una unidad de medida válida.")
        }
        val precio = precioTexto.trim().replace(',', '.').toDoubleOrNull()
        if (precio == null || !precio.isFinite() || precio <= 0.0) {
            return rechazar("Escribe un precio válido mayor que cero.")
        }
        val existencias = existenciasTexto.trim().toIntOrNull()
        if (existencias == null || existencias < 0) {
            return rechazar("Las existencias deben ser un número entero de cero o más.")
        }
        return Product(
            nombre = nombreLimpio,
            unidadMedida = unidadMedida,
            precio = precio,
            existencias = existencias,
            estado = if (existencias == 0) EstadoProducto.AGOTADO else estadoProducto.value,
            descripcion = descripcion.trim(),
            marca = marca.trim().takeIf { it.isNotEmpty() },
            ingredientes = ingredientes.value.toList()
        )
    }

    fun guardarProducto(
        nombre: String,
        unidadMedida: String,
        precioTexto: String,
        existenciasTexto: String,
        descripcion: String,
        marca: String,
        subirImagen: suspend (String) -> ResultadoAmbulantitos<ImagenProducto>
    ) {
        if (bloqueado) return
        val producto = prepararProducto(
            nombre, unidadMedida, precioTexto, existenciasTexto, descripcion, marca
        ) ?: return
        val direccion = imagenSeleccionada.value
        if (direccion.isNullOrBlank()) {
            guardadoInterno.value = EstadoGuardadoProductoVendedor.Error(
                "Selecciona una fotografía del producto."
            )
            return
        }
        guardadoInterno.value = EstadoGuardadoProductoVendedor.SubiendoImagen
        viewModelScope.launch {
            try {
                val perfil = when (val resultado = obtenerPerfil()) {
                    is ResultadoAmbulantitos.Exito -> resultado.dato
                    is ResultadoAmbulantitos.Error -> {
                        mostrarError(resultado.mensaje)
                        return@launch
                    }
                }
                val negocio = perfil.vendorId
                if (!perfil.esVendedor || perfil.userId.isBlank() || negocio.isNullOrBlank()) {
                    mostrarError("Inicia sesión con una cuenta de vendedor que tenga un negocio.")
                    return@launch
                }
                val imagen = obtenerImagen(direccion, perfil.userId, subirImagen) ?: return@launch
                val listo = producto.copy(
                    productId = obtenerIdProducto(),
                    iconoClave = productoOriginal?.iconoClave ?: producto.iconoClave,
                    imagenUrl = imagen.url,
                    imagenIdPublico = imagen.idPublico.takeIf { it.isNotBlank() }
                )
                guardadoInterno.value = EstadoGuardadoProductoVendedor.GuardandoProducto
                val resultado = if (esEdicion) {
                    val esperadas = datosGuardados.get<Int>("existencias_originales_edicion")
                        ?: requireNotNull(productoOriginal).existencias
                    actualizarEnFirestore(negocio, listo, esperadas)
                } else guardarEnFirestore(negocio, listo)
                guardadoInterno.value = when (resultado) {
                    is ResultadoAmbulantitos.Exito ->
                        EstadoGuardadoProductoVendedor.Completado(resultado.dato)
                    is ResultadoAmbulantitos.Error ->
                        EstadoGuardadoProductoVendedor.Error(resultado.mensaje)
                }
            } catch (cancelacion: CancellationException) {
                throw cancelacion
            } catch (error: Exception) {
                mostrarError(error.message ?: "No se pudo registrar el producto.")
            }
        }
    }

    private suspend fun obtenerImagen(
        direccion: String,
        usuario: String,
        subirImagen: suspend (String) -> ResultadoAmbulantitos<ImagenProducto>
    ): ImagenProducto? {
        val original = productoOriginal
        if (esEdicion && original != null && direccion == original.imagenUrl) {
            return ImagenProducto(direccion, original.imagenIdPublico.orEmpty())
        }
        val url = datosGuardados.get<String>("imagen_subida_url")
        val id = datosGuardados.get<String>("imagen_subida_id")
        if (
            datosGuardados.get<String>("imagen_subida_uri") == direccion &&
            datosGuardados.get<String>("imagen_subida_usuario") == usuario &&
            !url.isNullOrBlank() && !id.isNullOrBlank()
        ) {
            return ImagenProducto(url, id)
        }
        return when (val resultado = subirImagen(direccion)) {
            is ResultadoAmbulantitos.Exito -> {
                val imagen = resultado.dato
                datosGuardados["imagen_subida_uri"] = direccion
                datosGuardados["imagen_subida_usuario"] = usuario
                datosGuardados["imagen_subida_url"] = imagen.url
                datosGuardados["imagen_subida_id"] = imagen.idPublico
                imagen
            }
            is ResultadoAmbulantitos.Error -> {
                mostrarError(resultado.mensaje)
                null
            }
        }
    }

    private fun obtenerIdProducto(): String {
        if (esEdicion) return requireNotNull(productoOriginal).productId
        val existente = datosGuardados.get<String>("producto_id")
        if (existente != null) return existente
        return UUID.randomUUID().toString().also { datosGuardados["producto_id"] = it }
    }

    private fun mostrarError(mensaje: String) {
        guardadoInterno.value = EstadoGuardadoProductoVendedor.Error(mensaje)
    }

    fun limpiarErrorGuardado() {
        if (guardadoInterno.value is EstadoGuardadoProductoVendedor.Error) {
            guardadoInterno.value = EstadoGuardadoProductoVendedor.Inicial
        }
    }
}
