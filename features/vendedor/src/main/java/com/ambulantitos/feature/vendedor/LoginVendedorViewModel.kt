package com.ambulantitos.feature.vendedor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ambulantitos.core.data.RepositorioFirestore
import com.ambulantitos.core.data.ResultadoAmbulantitos
import com.ambulantitos.core.domain.RolUsuario
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LoginVendedorViewModel : ViewModel() {

    private val repositorio = RepositorioFirestore.obtener()

    private val estadoInterno =
        MutableStateFlow<EstadoLoginVendedor>(
            EstadoLoginVendedor.Inicial
        )

    val estado = estadoInterno.asStateFlow()
    fun iniciarSesion(
        usuario: String,
        contrasena: String
    ) {
        val estadoActual = estadoInterno.value

        if (
            estadoActual is EstadoLoginVendedor.Cargando ||
            estadoActual is EstadoLoginVendedor.Completado
        ) {
            return
        }

        if (usuario.isBlank() || contrasena.isEmpty()) {
            estadoInterno.value = EstadoLoginVendedor.Error(
                mensaje = "Escribe tu usuario y contraseña."
            )
            return
        }

        estadoInterno.value = EstadoLoginVendedor.Cargando

        viewModelScope.launch {
            val resultado = repositorio.iniciarSesion(
                usuario = usuario.trim(),
                contrasena = contrasena,
                rolEsperado = RolUsuario.VENDEDOR
            )

            estadoInterno.value = when (resultado) {
                is ResultadoAmbulantitos.Exito ->
                    EstadoLoginVendedor.Completado(
                        usuario = resultado.dato
                    )

                is ResultadoAmbulantitos.Error ->
                    EstadoLoginVendedor.Error(
                        mensaje = resultado.mensaje
                    )
            }
        }
    }
    fun limpiarError() {
        if (estadoInterno.value is EstadoLoginVendedor.Error) {
            estadoInterno.value = EstadoLoginVendedor.Inicial
        }
    }
}