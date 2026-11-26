package com.ambulantitos.feature.vendedor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ambulantitos.core.data.RepositorioFirestore
import com.ambulantitos.core.data.ResultadoAmbulantitos
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PanelVendedorViewModel : ViewModel() {

    private val repositorio = RepositorioFirestore.obtener()

    private val estadoInterno =
        MutableStateFlow<EstadoPanelVendedor>(
            EstadoPanelVendedor.Cargando
        )

    val estado = estadoInterno.asStateFlow()

    init {
        cargarPerfil()
    }
    private fun cargarPerfil() {
        if (repositorio.uidActual() == null) {
            estadoInterno.value = EstadoPanelVendedor.SinSesion
            return
        }

        viewModelScope.launch {
            val resultado = repositorio.obtenerUsuarioActual()

            estadoInterno.value = when (resultado) {
                is ResultadoAmbulantitos.Exito -> {
                    val usuario = resultado.dato

                    if (usuario.esVendedor) {
                        EstadoPanelVendedor.Completado(
                            usuario = usuario
                        )
                    } else {
                        repositorio.cerrarSesion()
                        EstadoPanelVendedor.SinSesion
                    }
                }

                is ResultadoAmbulantitos.Error ->
                    EstadoPanelVendedor.Error(
                        mensaje = resultado.mensaje
                    )
            }
        }
    }
    fun reintentarPerfil() {
        if (estadoInterno.value is EstadoPanelVendedor.Error) {
            estadoInterno.value = EstadoPanelVendedor.Cargando
            cargarPerfil()
        }
    }
    fun cerrarSesion() {
        repositorio.cerrarSesion()
    }
}