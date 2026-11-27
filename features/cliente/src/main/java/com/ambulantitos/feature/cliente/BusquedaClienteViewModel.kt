package com.ambulantitos.feature.cliente

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ambulantitos.core.data.RepositorioFirestore
import com.ambulantitos.core.data.getOrNull
import com.ambulantitos.core.domain.Ubicacion
import com.ambulantitos.core.domain.Vendor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class BusquedaClienteViewModel(
    private val obtenerPerfilVendedor: suspend (String) -> Vendor? = { vendorId ->
        RepositorioFirestore.obtener().obtenerVendedor(vendorId).getOrNull()
    },
) : ViewModel() {

    private val _estado = MutableStateFlow(EstadoBusquedaCliente())
    val estado: StateFlow<EstadoBusquedaCliente> = _estado.asStateFlow()

    fun actualizarUbicacionCliente(lat: Double, lng: Double) {
        _estado.update { it.copy(ubicacionCliente = Ubicacion(lat = lat, lng = lng)) }
    }

    fun actualizarTextoBusqueda(texto: String) {
        _estado.update { it.copy(textoBusqueda = texto) }
    }

    fun seleccionarFiltro(filtro: FiltroCategoriaCliente) {
        _estado.update { it.copy(filtroCategoria = filtro) }
    }

    fun alIniciarBusquedaBle() {
        _estado.update {
            it.copy(
                buscandoBle = true,
                mensajeEstado = null,
            )
        }
    }

    fun alEncontrarVendedorBle(direccionBle: String, nombreAnuncio: String, rssi: Int) {
        _estado.update { actual ->
            val existentes = actual.vendedoresDetectados.toMutableList()
            val indice = existentes.indexOfFirst { it.direccionBle == direccionBle }
            val actualizado = if (indice >= 0) {
                val previo = existentes[indice]
                previo.copy(
                    nombreAnuncio = nombreAnuncio.ifBlank { previo.nombreAnuncio },
                    rssi = rssi,
                )
            } else {
                VendedorEnMapa(
                    direccionBle = direccionBle,
                    nombreAnuncio = nombreAnuncio,
                    rssi = rssi,
                )
            }
            if (indice >= 0) {
                existentes[indice] = actualizado
            } else {
                existentes.add(actualizado)
            }
            val seleccionActualizada = if (actual.vendedorSeleccionado?.direccionBle == direccionBle) {
                actualizado
            } else {
                actual.vendedorSeleccionado
            }
            actual.copy(
                vendedoresDetectados = existentes,
                vendedorSeleccionado = seleccionActualizada,
            )
        }
    }

    fun alTerminarBusquedaBle(error: String?) {
        _estado.update { actual ->
            actual.copy(
                buscandoBle = false,
                mensajeEstado = error,
            )
        }
    }

    fun alIniciarIdentificacion() {
        _estado.update {
            it.copy(
                identificandoBle = true,
                mensajeEstado = null,
            )
        }
    }

    fun alFallarIdentificacion(mensaje: String) {
        _estado.update {
            it.copy(
                identificandoBle = false,
                mensajeEstado = mensaje,
            )
        }
    }

    fun alIdentificarVendedor(direccionBle: String, vendorId: String, nombreRespaldo: String) {
        _estado.update { actual ->
            val existentes = actual.vendedoresDetectados.toMutableList()
            val indice = existentes.indexOfFirst { it.direccionBle == direccionBle }
            val actualizado = if (indice >= 0) {
                existentes[indice].copy(
                    vendorId = vendorId,
                    nombreNegocio = existentes[indice].nombreNegocio.ifBlank { nombreRespaldo },
                )
            } else {
                VendedorEnMapa(
                    direccionBle = direccionBle,
                    nombreAnuncio = nombreRespaldo,
                    vendorId = vendorId,
                    nombreNegocio = nombreRespaldo,
                )
            }
            if (indice >= 0) existentes[indice] = actualizado else existentes.add(actualizado)
            actual.copy(
                identificandoBle = false,
                mensajeEstado = null,
                vendedoresDetectados = existentes,
                vendedorSeleccionado = actualizado,
            )
        }

        viewModelScope.launch {
            val perfil = obtenerPerfilVendedor(vendorId) ?: return@launch
            aplicarPerfilFirestore(vendorId, perfil)
        }
    }

    fun seleccionarVendedorYaIdentificado(vendedor: VendedorEnMapa) {
        _estado.update { it.copy(vendedorSeleccionado = vendedor, mensajeEstado = null) }
        val id = vendedor.vendorId ?: return
        viewModelScope.launch {
            val perfil = obtenerPerfilVendedor(id) ?: return@launch
            aplicarPerfilFirestore(id, perfil)
        }
    }

    fun restaurarSeleccion(vendorId: String, nombreNegocio: String) {
        val restaurado = VendedorEnMapa(
            direccionBle = vendorId,
            nombreAnuncio = nombreNegocio,
            vendorId = vendorId,
            nombreNegocio = nombreNegocio,
        )
        _estado.update { actual ->
            if (actual.vendedorSeleccionado?.vendorId == vendorId) actual
            else actual.copy(vendedorSeleccionado = restaurado)
        }
    }

    private fun aplicarPerfilFirestore(vendorId: String, perfil: Vendor) {
        val ubicacionValida = perfil.ubicacion.takeIf { it.lat != 0.0 || it.lng != 0.0 }
        _estado.update { actual ->
            val actualizados = actual.vendedoresDetectados.map { item ->
                if (item.vendorId == vendorId) {
                    item.copy(
                        nombreNegocio = perfil.nombreNegocio.ifBlank { item.nombreNegocio },
                        categoriaPrincipal = perfil.categoriaPrincipal.ifBlank { item.categoriaPrincipal },
                        ubicacionReal = ubicacionValida ?: item.ubicacionReal,
                    )
                } else {
                    item
                }
            }
            val seleccion = actual.vendedorSeleccionado?.let { sel ->
                if (sel.vendorId == vendorId) {
                    sel.copy(
                        nombreNegocio = perfil.nombreNegocio.ifBlank { sel.nombreNegocio },
                        categoriaPrincipal = perfil.categoriaPrincipal.ifBlank { sel.categoriaPrincipal },
                        ubicacionReal = ubicacionValida ?: sel.ubicacionReal,
                    )
                } else {
                    sel
                }
            }
            actual.copy(
                vendedoresDetectados = actualizados,
                vendedorSeleccionado = seleccion,
            )
        }
    }
}
