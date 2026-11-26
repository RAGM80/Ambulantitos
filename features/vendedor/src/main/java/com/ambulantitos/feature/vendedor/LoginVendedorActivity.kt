package com.ambulantitos.feature.vendedor

import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import android.content.Intent
import android.widget.EditText
import android.text.method.PasswordTransformationMethod
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch

class LoginVendedorActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContentView(R.layout.vendedor_login)

        aplicarBordesVendedor()
        val botonCrearCuenta = findViewById<View>(
            R.id.vendedor_login_btn_crear_cuenta
        )

        botonCrearCuenta.setOnClickListener {
            val abrirRegistro = Intent(
                this,
                RegistroVendedorActivity::class.java
            )

            startActivity(abrirRegistro)
        }
        val botonRecuperarCuenta = findViewById<View>(
            R.id.vendedor_login_btn_olvide_contraseña
        )

        botonRecuperarCuenta.setOnClickListener {
            val abrirRecuperacion = Intent(
                this,
                RecuperarCuentaVendedorActivity::class.java
            )

            startActivity(abrirRecuperacion)

        }
        val campoContrasena = findViewById<EditText>(
            R.id.vendedor_login_contraseña
        )

        val botonOjo = findViewById<View>(
            R.id.vendedor_login_btn_ojo
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
                    R.string.vendedor_login_ocultar_contrasena
                } else {
                    R.string.vendedor_login_mostrar_contraseña
                }
            )
        }

        val modeloLogin = ViewModelProvider(this)[
            LoginVendedorViewModel::class.java
        ]

        val campoUsuario = findViewById<EditText>(
            R.id.vendedor_login_usuario
        )

        val botonIngresar = findViewById<View>(
            R.id.vendedor_login_btn_ingreso
        )

        var avisoLoginVisible = false

        botonIngresar.setOnClickListener {
            if (avisoLoginVisible) {
                return@setOnClickListener
            }

            modeloLogin.iniciarSesion(
                usuario = campoUsuario.text.toString(),
                contrasena = campoContrasena.text.toString()
            )
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                modeloLogin.estado.collect { estado ->
                    val ocupado =
                        estado is EstadoLoginVendedor.Cargando ||
                                estado is EstadoLoginVendedor.Completado

                    botonIngresar.isEnabled = !ocupado
                    botonIngresar.alpha = if (ocupado) 0.5f else 1f

                    botonCrearCuenta.isEnabled = !ocupado
                    botonRecuperarCuenta.isEnabled = !ocupado

                    when (estado) {
                        EstadoLoginVendedor.Inicial,
                        EstadoLoginVendedor.Cargando -> Unit

                        is EstadoLoginVendedor.Error -> {
                            avisoLoginVisible = true

                            val aviso = Toast.makeText(
                                this@LoginVendedorActivity,
                                estado.mensaje,
                                Toast.LENGTH_LONG
                            )

                            aviso.addCallback(object : Toast.Callback() {
                                override fun onToastHidden() {
                                    avisoLoginVisible = false
                                }
                            })

                            aviso.show()
                            modeloLogin.limpiarError()
                        }

                        is EstadoLoginVendedor.Completado -> {
                            val abrirPanel = Intent(
                                this@LoginVendedorActivity,
                                PanelVendedorActivity::class.java
                            )

                            startActivity(abrirPanel)
                            finish()
                        }
                    }
                }
            }
        }
    }
}

internal fun ComponentActivity.aplicarBordesVendedor() {
    val contenido = findViewById<View>(android.R.id.content)

    ViewCompat.setOnApplyWindowInsetsListener(contenido) { vista, insets ->
        val bordes = insets.getInsets(
            WindowInsetsCompat.Type.systemBars() or
                    WindowInsetsCompat.Type.displayCutout() or
                    WindowInsetsCompat.Type.ime()
        )

        vista.setPadding(
            bordes.left,
            bordes.top,
            bordes.right,
            bordes.bottom
        )

        WindowInsetsCompat.CONSUMED
    }

    ViewCompat.requestApplyInsets(contenido)
}