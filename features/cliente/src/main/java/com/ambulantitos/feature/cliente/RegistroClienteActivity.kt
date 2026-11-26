package com.ambulantitos.feature.cliente

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.ambulantitos.core.data.RepositorioFirestore
import com.ambulantitos.core.data.ResultadoAmbulantitos
import com.ambulantitos.feature.cliente.R as ClienteR
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.textfield.TextInputLayout
import java.util.Calendar
import kotlinx.coroutines.launch

class RegistroClienteActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(ClienteR.layout.pantalla_registro_cliente)

        val barra = findViewById<MaterialToolbar>(ClienteR.id.barra_registro_cliente)
        barra.setNavigationOnClickListener {
            finish()
        }

        val campoNombres = findViewById<TextInputLayout>(ClienteR.id.contenedor_nombres)
        val campoApellidoPaterno = findViewById<TextInputLayout>(
            ClienteR.id.contenedor_apellido_paterno
        )
        val campoApellidoMaterno = findViewById<TextInputLayout>(
            ClienteR.id.contenedor_apellido_materno
        )
        val campoFecha = findViewById<TextInputLayout>(
            ClienteR.id.contenedor_fecha_nacimiento
        )
        val campoCelular = findViewById<TextInputLayout>(ClienteR.id.contenedor_celular)
        val campoUsuario = findViewById<TextInputLayout>(ClienteR.id.contenedor_usuario)
        val campoContrasena = findViewById<TextInputLayout>(
            ClienteR.id.contenedor_contrasena
        )
        val botonCrear = findViewById<View>(ClienteR.id.boton_crear_cuenta)

        //La fecha se llena desde el calendario
        val entradaFecha = campoFecha.editText
        entradaFecha?.keyListener = null
        entradaFecha?.setOnClickListener {
            abrirCalendario(campoFecha)
        }
        campoFecha.setEndIconOnClickListener {
            abrirCalendario(campoFecha)
        }

        botonCrear.setOnClickListener {
            validarNombre(campoNombres)
            validarNombre(campoApellidoPaterno)
            validarNombre(campoApellidoMaterno)

            val nombres = campoNombres.editText?.text?.toString().orEmpty()
            val apellidoPaterno = campoApellidoPaterno.editText?.text?.toString().orEmpty()
            val apellidoMaterno = campoApellidoMaterno.editText?.text?.toString().orEmpty()
            val fecha = campoFecha.editText?.text?.toString().orEmpty()
            val celular = campoCelular.editText?.text?.toString().orEmpty()
            val usuario = campoUsuario.editText?.text?.toString().orEmpty().trim()
            val contrasena = campoContrasena.editText?.text?.toString().orEmpty()

            campoFecha.error = if (fecha.isBlank()) {
                getString(ClienteR.string.cliente_registro_campo_obligatorio)
            } else {
                null
            }

            campoCelular.error = if (celular.isBlank()) {
                getString(ClienteR.string.cliente_registro_campo_obligatorio)
            } else if (!celular.matches(Regex("[0-9]+"))) {
                getString(ClienteR.string.cliente_registro_celular_solo_numeros)
            } else {
                null
            }

            campoUsuario.error = if (usuario.isEmpty()) {
                getString(ClienteR.string.cliente_registro_campo_obligatorio)
            } else if (usuario.length < 3) {
                getString(ClienteR.string.cliente_registro_usuario_corto)
            } else if (!usuario.matches(Regex("[a-zA-Z0-9_]+"))) {
                getString(ClienteR.string.cliente_registro_usuario_formato)
            } else {
                null
            }

            campoContrasena.error = if (contrasena.isBlank()) {
                getString(ClienteR.string.cliente_registro_campo_obligatorio)
            } else if (contrasena.length < 6) {
                getString(ClienteR.string.cliente_registro_contrasena_corta)
            } else {
                null
            }

            //Si hay un error, deja abierto el formulario
            if (
                campoNombres.error != null ||
                campoApellidoPaterno.error != null ||
                campoApellidoMaterno.error != null ||
                campoFecha.error != null ||
                campoCelular.error != null ||
                campoUsuario.error != null ||
                campoContrasena.error != null
            ) {
                return@setOnClickListener
            }

            //Desactiva el botón mientras se guarda la cuenta
            val repositorio = RepositorioFirestore.obtener()
            botonCrear.isEnabled = false

            lifecycleScope.launch {
                val resultado = repositorio.registrarCliente(
                    nombres = nombres,
                    apellidoPaterno = apellidoPaterno,
                    apellidoMaterno = apellidoMaterno,
                    fechaNacimiento = fecha,
                    numeroCelular = celular,
                    usuario = usuario,
                    contrasena = contrasena
                )

                when (resultado) {
                    is ResultadoAmbulantitos.Exito -> {
                        //Cierra la sesión automática antes de volver al login
                        repositorio.cerrarSesion()
                        Toast.makeText(
                            this@RegistroClienteActivity,
                            "Cuenta creada",
                            Toast.LENGTH_SHORT
                        ).show()
                        finish()
                    }

                    is ResultadoAmbulantitos.Error -> {
                        botonCrear.isEnabled = true
                        Toast.makeText(
                            this@RegistroClienteActivity,
                            resultado.mensaje,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }

        aplicarBordes()
    }

    private fun validarNombre(campo: TextInputLayout) {
        val texto = campo.editText?.text?.toString().orEmpty().trim()

        //Permite letras con acentos y espacios entre palabras
        val letrasYEspacios = Regex("""(?:\p{L}\p{M}*)+(?: +(?:\p{L}\p{M}*)+)*""")

        campo.error = if (texto.isEmpty()) {
            getString(ClienteR.string.cliente_registro_campo_obligatorio)
        } else if (!texto.matches(letrasYEspacios)) {
            getString(ClienteR.string.cliente_registro_solo_letras)
        } else {
            null
        }
    }

    private fun abrirCalendario(campoFecha: TextInputLayout) {
        val hoy = Calendar.getInstance()

        val calendario = DatePickerDialog(
            this,
            { _, anio, mes, dia ->
                //Android cuenta los meses desde cero
                val diaTexto = dia.toString().padStart(2, '0')
                val mesTexto = (mes + 1).toString().padStart(2, '0')
                campoFecha.editText?.setText("$diaTexto/$mesTexto/$anio")
                campoFecha.error = null
            },
            hoy.get(Calendar.YEAR),
            hoy.get(Calendar.MONTH),
            hoy.get(Calendar.DAY_OF_MONTH)
        )

        calendario.show()
    }
}