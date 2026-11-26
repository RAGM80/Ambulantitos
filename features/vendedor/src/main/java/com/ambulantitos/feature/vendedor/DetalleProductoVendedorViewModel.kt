package com.ambulantitos.feature.vendedor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ambulantitos.core.data.RepositorioFirestore
import com.ambulantitos.core.data.ResultadoAmbulantitos
import com.ambulantitos.core.domain.Product
import com.ambulantitos.core.domain.User
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class DetalleProductoVendedorViewModel internal constructor(
    datosGuardados: SavedStateHandle,
    private val obtenerPerfil: suspend () -> ResultadoAmbulantitos<User>,
    private val escuchar: (String, String) -> Flow<Product?>,
    private val desactivar: suspend (String, String) -> ResultadoAmbulantitos<Unit>,
    private val agotar: suspend (String, String) -> ResultadoAmbulantitos<Unit>
) : ViewModel() {
    constructor(datosGuardados: SavedStateHandle) : this(
        datosGuardados,
        { RepositorioFirestore.obtener().obtenerUsuarioActual() },
        { negocio, producto -> RepositorioFirestore.obtener().escucharProducto(negocio, producto) },
        { negocio, producto -> RepositorioFirestore.obtener().desactivarProducto(negocio, producto) },
        { negocio, producto -> RepositorioFirestore.obtener().marcarProductoAgotado(negocio, producto) }
    )

    private val productoId = datosGuardados.get<String>(DetalleProductoVendedorActivity.EXTRA_PRODUCTO_ID).orEmpty()
    private var negocioId: String? = null
    private var procesando = false
    private var eliminado = false
    private val estadoInterno = MutableStateFlow<EstadoDetalleProductoVendedor>(EstadoDetalleProductoVendedor.Cargando)
    val estado = estadoInterno.asStateFlow()
    private val avisoInterno = MutableStateFlow<String?>(null)
    val aviso = avisoInterno.asStateFlow()

    init { consultarProducto() }

    private fun consultarProducto() {
        if (productoId.isBlank()) {
            estadoInterno.value = EstadoDetalleProductoVendedor.Error("Falta el identificador del producto.")
            return
        }
        viewModelScope.launch {
            try {
                val perfil = when (val resultado = obtenerPerfil()) {
                    is ResultadoAmbulantitos.Exito -> resultado.dato
                    is ResultadoAmbulantitos.Error -> {
                        estadoInterno.value = EstadoDetalleProductoVendedor.Error(resultado.mensaje)
                        return@launch
                    }
                }
                val negocio = perfil.vendorId
                if (!perfil.esVendedor || negocio.isNullOrBlank()) {
                    estadoInterno.value = EstadoDetalleProductoVendedor.SinSesion
                    return@launch
                }
                negocioId = negocio
                escuchar(negocio, productoId)
                    .catch { error -> estadoInterno.value = EstadoDetalleProductoVendedor.Error(
                        error.message ?: "No se pudo consultar el producto."
                    ) }
                    .collect { producto ->
                        if (eliminado) return@collect
                        if (producto == null || !producto.activo) {
                            eliminado = true
                            estadoInterno.value = EstadoDetalleProductoVendedor.Eliminado
                        } else {
                            estadoInterno.value = EstadoDetalleProductoVendedor.Disponible(producto, procesando)
                        }
                    }
            } catch (cancelacion: CancellationException) {
                throw cancelacion
            } catch (error: Exception) {
                estadoInterno.value = EstadoDetalleProductoVendedor.Error(error.message ?: "No se pudo abrir el detalle.")
            }
        }
    }

    fun eliminarProducto() = ejecutarAccion(eliminar = true)
    fun marcarAgotado() = ejecutarAccion(eliminar = false)

    private fun ejecutarAccion(eliminar: Boolean) {
        val disponible = estadoInterno.value as? EstadoDetalleProductoVendedor.Disponible ?: return
        val negocio = negocioId ?: return
        if (procesando || eliminado) return
        procesando = true
        estadoInterno.value = disponible.copy(procesando = true)
        viewModelScope.launch {
            try {
                val resultado = if (eliminar) desactivar(negocio, productoId) else agotar(negocio, productoId)
                when (resultado) {
                    is ResultadoAmbulantitos.Exito -> {
                        if (eliminar) {
                            eliminado = true
                            estadoInterno.value = EstadoDetalleProductoVendedor.Eliminado
                        }
                    }
                    is ResultadoAmbulantitos.Error -> avisoInterno.value = resultado.mensaje
                }
            } catch (cancelacion: CancellationException) {
                throw cancelacion
            } catch (error: Exception) {
                avisoInterno.value = error.message ?: "No se pudo actualizar el producto."
            } finally {
                procesando = false
                val actual = estadoInterno.value
                if (actual is EstadoDetalleProductoVendedor.Disponible) {
                    estadoInterno.value = actual.copy(procesando = false)
                }
            }
        }
    }

    fun consumirAviso() { avisoInterno.value = null }
}
