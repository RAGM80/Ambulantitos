package com.ambulantitos.feature.vendedor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ambulantitos.core.data.RepositorioFirestore
import com.ambulantitos.core.data.ResultadoAmbulantitos
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RegistroVendedorViewModel : ViewModel() {

    private val repositorio = RepositorioFirestore.obtener()

    private val estadoInterno =
        MutableStateFlow<EstadoRegistroVendedor>(
            EstadoRegistroVendedor.Inicial
        )

    val estado = estadoInterno.asStateFlow()
    fun limpiarError() {
        if (estadoInterno.value is EstadoRegistroVendedor.Error) {
            estadoInterno.value = EstadoRegistroVendedor.Inicial
        }
    }
    fun registrar(
        nombres: String,
        apellidoPaterno: String,
        apellidoMaterno: String,
        fechaNacimiento: String,
        nombreNegocio: String,
        usuario: String,
        contrasena: String
    ) {
        val estadoActual = estadoInterno.value

        if (
            estadoActual == EstadoRegistroVendedor.Cargando ||
            estadoActual is EstadoRegistroVendedor.Completado
        ) {
            return
        }
        val nombresValidos = listOf(
            nombres,
            apellidoPaterno,
            apellidoMaterno
        ).all { valor ->
            ValidadorRegistroVendedor.validarNombre(valor) == null
        }

        if (!nombresValidos) {
            estadoInterno.value = EstadoRegistroVendedor.Error(
                mensaje = "Los nombres y apellidos solo admiten letras y espacios."
            )
            return
        }

        if (
            !ValidadorRegistroVendedor.validarCampoObligatorio(fechaNacimiento) ||
            !ValidadorRegistroVendedor.validarCampoObligatorio(nombreNegocio)
        ) {
            estadoInterno.value = EstadoRegistroVendedor.Error(
                mensaje = "Completa todos los campos obligatorios."
            )
            return
        }

        when (ValidadorRegistroVendedor.validarUsuario(usuario)) {
            ErrorUsuarioRegistro.OBLIGATORIO -> {
                estadoInterno.value = EstadoRegistroVendedor.Error(
                    mensaje = "Este campo es obligatorio."
                )
                return
            }

            ErrorUsuarioRegistro.CORTO -> {
                estadoInterno.value = EstadoRegistroVendedor.Error(
                    mensaje = "El usuario debe tener al menos 3 caracteres."
                )
                return
            }

            ErrorUsuarioRegistro.FORMATO_INVALIDO -> {
                estadoInterno.value = EstadoRegistroVendedor.Error(
                    mensaje = "Usa solo letras de la A a la Z, números o guion bajo."
                )
                return
            }

            null -> Unit
        }

        when (ValidadorRegistroVendedor.validarContrasena(contrasena)) {
            ErrorContrasenaRegistro.OBLIGATORIO -> {
                estadoInterno.value = EstadoRegistroVendedor.Error(
                    mensaje = "Este campo es obligatorio."
                )
                return
            }

            ErrorContrasenaRegistro.CORTA -> {
                estadoInterno.value = EstadoRegistroVendedor.Error(
                    mensaje = "La contraseña debe tener al menos 6 caracteres."
                )
                return
            }

            ErrorContrasenaRegistro.LARGA -> {
                estadoInterno.value = EstadoRegistroVendedor.Error(
                    mensaje = "La contraseña debe tener entre 6 y 12 caracteres."
                )
                return
            }

            null -> Unit
        }
        estadoInterno.value = EstadoRegistroVendedor.Cargando

        viewModelScope.launch {
            val resultado = repositorio.registrarVendedor(
                nombres = nombres,
                apellidoPaterno = apellidoPaterno,
                apellidoMaterno = apellidoMaterno,
                fechaNacimiento = fechaNacimiento,
                nombreNegocio = nombreNegocio,
                usuario = usuario,
                contrasena = contrasena
            )

            estadoInterno.value = when (resultado) {
                is ResultadoAmbulantitos.Exito ->
                    EstadoRegistroVendedor.Completado(
                        usuario = resultado.dato
                    )

                is ResultadoAmbulantitos.Error ->
                    EstadoRegistroVendedor.Error(
                        mensaje = resultado.mensaje
                    )
            }
        }
    }
}