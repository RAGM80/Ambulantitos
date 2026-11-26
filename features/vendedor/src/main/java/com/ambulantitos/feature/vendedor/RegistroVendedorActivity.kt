package com.ambulantitos.feature.vendedor

import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import android.app.DatePickerDialog
import android.widget.EditText
import java.util.Calendar
import java.util.Locale
import android.text.method.PasswordTransformationMethod
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
class RegistroVendedorActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContentView(R.layout.vendedor_registro)

        aplicarBordesVendedor()

        val botonVolver = findViewById<View>(
            R.id.vendedor_registro_boton_volver
        )
        val campoFecha = findViewById<EditText>(
            R.id.vendedor_registro_fecha_nacimiento
        )

        val botonCalendario = findViewById<View>(
            R.id.vendedor_registro_btn_calendario
        )

        campoFecha.keyListener = null
        campoFecha.showSoftInputOnFocus = false
        val fechaActual = Calendar.getInstance()

        val selectorFecha = DatePickerDialog(
            this,
            { _, anio, mes, dia ->
                val fechaElegida = String.format(
                    Locale.getDefault(),
                    "%02d/%02d/%04d",
                    dia,
                    mes + 1,
                    anio
                )

                campoFecha.setText(fechaElegida)
                campoFecha.error = null
            },
            fechaActual.get(Calendar.YEAR),
            fechaActual.get(Calendar.MONTH),
            fechaActual.get(Calendar.DAY_OF_MONTH)
        )
        val abrirCalendario = View.OnClickListener {
            selectorFecha.datePicker.maxDate = System.currentTimeMillis()
            selectorFecha.show()
        }

        campoFecha.setOnClickListener(abrirCalendario)
        botonCalendario.setOnClickListener(abrirCalendario)
        val campoContrasena = findViewById<EditText>(
            R.id.vendedor_registro_contraseña
        )

        val botonOjo = findViewById<View>(
            R.id.vendedor_registro_btn_ojo
        )
        botonOjo.setOnClickListener {
            val estabaOculta =
                campoContrasena.transformationMethod is PasswordTransformationMethod

            campoContrasena.transformationMethod =
                if (estabaOculta) {
                    null
                } else {
                    PasswordTransformationMethod.getInstance()
                }

            campoContrasena.setSelection(campoContrasena.text.length)

            botonOjo.contentDescription = getString(
                if (estabaOculta) {
                    R.string.vendedor_registro_ocultar_contrasena
                } else {
                    R.string.vendedor_registro_mostrar_contraseña
                }
            )
        }
        val campoNombres = findViewById<EditText>(
            R.id.vendedor_registro_nombres
        )

        val campoApellidoPaterno = findViewById<EditText>(
            R.id.vendedor_registro_apellido_paterno
        )

        val campoApellidoMaterno = findViewById<EditText>(
            R.id.vendedor_registro_apellido_materno
        )

        val campoNegocio = findViewById<EditText>(
            R.id.vendedor_registro_nombre_negocio
        )

        val campoUsuario = findViewById<EditText>(
            R.id.vendedor_registro_usuario
        )

        val modeloRegistro = ViewModelProvider(this)[
            RegistroVendedorViewModel::class.java
        ]

        val botonCrearCuenta = findViewById<View>(
            R.id.vendedor_registro_btn_crear_cuenta
        )
        var avisoRegistroVisible = false
        botonCrearCuenta.setOnClickListener {
            if (avisoRegistroVisible) {
                return@setOnClickListener
            }

            validarNombre(campoNombres)
            validarNombre(campoApellidoPaterno)
            validarNombre(campoApellidoMaterno)

            val fecha = campoFecha.text.toString()
            val nombreNegocio = campoNegocio.text.toString()
            val usuario = campoUsuario.text.toString().trim()
            val contrasena = campoContrasena.text.toString()

            campoFecha.error = if (ValidadorRegistroVendedor.validarCampoObligatorio(fecha)) {
                null
            } else {
                getString(R.string.vendedor_registro_error_obligatorio)
            }

            campoNegocio.error = if (ValidadorRegistroVendedor.validarCampoObligatorio(nombreNegocio)) {
                null
            } else {
                getString(R.string.vendedor_registro_error_obligatorio)
            }

            campoUsuario.error = when (ValidadorRegistroVendedor.validarUsuario(usuario)) {
                ErrorUsuarioRegistro.OBLIGATORIO ->
                    getString(R.string.vendedor_registro_error_obligatorio)
                ErrorUsuarioRegistro.CORTO ->
                    getString(R.string.vendedor_registro_error_usuario_corto)
                ErrorUsuarioRegistro.FORMATO_INVALIDO ->
                    getString(R.string.vendedor_registro_error_usuario_formato)
                null -> null
            }

            campoContrasena.error = when (ValidadorRegistroVendedor.validarContrasena(contrasena)) {
                ErrorContrasenaRegistro.OBLIGATORIO ->
                    getString(R.string.vendedor_registro_error_obligatorio)
                ErrorContrasenaRegistro.CORTA ->
                    getString(R.string.vendedor_registro_error_contrasena_corta)
                ErrorContrasenaRegistro.LARGA ->
                    getString(R.string.vendedor_registro_error_contrasena_larga)
                null -> null
            }

            val primerCampoConError = listOf(
                campoNombres,
                campoApellidoPaterno,
                campoApellidoMaterno,
                campoFecha,
                campoNegocio,
                campoUsuario,
                campoContrasena
            ).firstOrNull { it.error != null }

            if (primerCampoConError != null) {
                primerCampoConError.requestFocus()
                return@setOnClickListener
            }

            modeloRegistro.registrar(
                nombres = campoNombres.text.toString().trim(),
                apellidoPaterno = campoApellidoPaterno.text.toString().trim(),
                apellidoMaterno = campoApellidoMaterno.text.toString().trim(),
                fechaNacimiento = fecha.trim(),
                nombreNegocio = nombreNegocio.trim(),
                usuario = usuario,
                contrasena = contrasena
            )
        }

        var ultimoEstadoMostrado: EstadoRegistroVendedor? = null

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                modeloRegistro.estado.collect { estado ->
                    val cargando =
                        estado is EstadoRegistroVendedor.Cargando

                    val completado =
                        estado is EstadoRegistroVendedor.Completado

                    botonCrearCuenta.isEnabled = !cargando && !completado
                    botonCrearCuenta.alpha = if (cargando) 0.5f else 1f

                    if (estado != ultimoEstadoMostrado) {
                        ultimoEstadoMostrado = estado

                        when (estado) {
                            EstadoRegistroVendedor.Inicial,
                            EstadoRegistroVendedor.Cargando -> Unit

                            is EstadoRegistroVendedor.Error -> {
                                avisoRegistroVisible = true

                                val aviso = Toast.makeText(
                                    this@RegistroVendedorActivity,
                                    estado.mensaje,
                                    Toast.LENGTH_LONG
                                )

                                aviso.addCallback(object : Toast.Callback() {
                                    override fun onToastHidden() {
                                        avisoRegistroVisible = false
                                    }
                                })

                                aviso.show()
                                modeloRegistro.limpiarError()
                            }

                            is EstadoRegistroVendedor.Completado -> {
                                Toast.makeText(
                                    this@RegistroVendedorActivity,
                                    R.string.vendedor_registro_cuenta_creada,
                                    Toast.LENGTH_LONG
                                ).show()

                                finish()
                            }
                        }
                    }
                }
            }
        }

        botonVolver.setOnClickListener {
            finish()
        }
    }

    private fun validarNombre(campo: EditText) {
        campo.error = when (ValidadorRegistroVendedor.validarNombre(campo.text.toString())) {
            ErrorNombreRegistro.OBLIGATORIO ->
                getString(R.string.vendedor_registro_error_obligatorio)
            ErrorNombreRegistro.SOLO_LETRAS ->
                getString(R.string.vendedor_registro_error_solo_letras)
            null -> null
        }
    }
}