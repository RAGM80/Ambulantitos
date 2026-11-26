package com.ambulantitos.feature.vendedor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ambulantitos.core.data.RepositorioFirestore
import com.ambulantitos.core.data.ResultadoAmbulantitos
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class ProductosVendedorViewModel : ViewModel() {

    private val repositorio = RepositorioFirestore.obtener()

    private val estadoInterno =
        MutableStateFlow<EstadoProductosVendedor>(
            EstadoProductosVendedor.Cargando
        )

    val estado = estadoInterno.asStateFlow()

    init {
        cargarProductos()
    }
    private fun cargarProductos() {
        if (repositorio.uidActual() == null) {
            estadoInterno.value = EstadoProductosVendedor.SinSesion
            return
        }

        viewModelScope.launch {
            val resultadoPerfil = repositorio.obtenerUsuarioActual()

            val perfil = when (resultadoPerfil) {
                is ResultadoAmbulantitos.Exito ->
                    resultadoPerfil.dato

                is ResultadoAmbulantitos.Error -> {
                    estadoInterno.value = EstadoProductosVendedor.Error(
                        mensaje = resultadoPerfil.mensaje
                    )
                    return@launch
                }
            }

            if (!perfil.esVendedor) {
                repositorio.cerrarSesion()
                estadoInterno.value = EstadoProductosVendedor.SinSesion
                return@launch
            }

            val vendorId = perfil.vendorId

            if (vendorId.isNullOrBlank()) {
                estadoInterno.value = EstadoProductosVendedor.Error(
                    mensaje = "No se encontró el identificador de tu negocio."
                )
                return@launch
            }

            repositorio.escucharProductosVendedor(vendorId)
                .catch { error ->
                    estadoInterno.value = EstadoProductosVendedor.Error(
                        mensaje = error.message
                            ?: "No fue posible consultar tus productos."
                    )
                }
                .collect { productos ->
                    estadoInterno.value =
                        EstadoProductosVendedor.Completado(
                            productos = productos
                        )
                }
        }
    }
}