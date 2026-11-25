package com.ambulantitos.app

import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import com.ambulantitos.feature.cliente.R as ClienteR
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.textfield.TextInputLayout

class RegistroClienteActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        //Muestra el formulario de registro que ya diseñamos
        setContentView(ClienteR.layout.pantalla_registro_cliente)

        //La flecha cierra el formulario y vuelve al inicio de sesión
        val barra = findViewById<MaterialToolbar>(ClienteR.id.barra_registro_cliente)
        barra.setNavigationOnClickListener {
            finish()
        }
        //Busca los recuadros de Usuario y Contraseña
        val campoUsuario = findViewById<TextInputLayout>(ClienteR.id.contenedor_usuario)
        val campoContrasena = findViewById<TextInputLayout>(ClienteR.id.contenedor_contrasena)
        val botonCrear = findViewById<View>(ClienteR.id.boton_crear_cuenta)

        botonCrear.setOnClickListener {
            //Lee lo que escribió el cliente al tocar Crear cuenta
            val usuario = campoUsuario.editText?.text?.toString().orEmpty().trim()
            val contrasena = campoContrasena.editText?.text?.toString().orEmpty()

            //Muestra el aviso de Usuario vacío o demasiado corto debajo del campo
            campoUsuario.error = if (usuario.isEmpty()) {
                getString(ClienteR.string.cliente_registro_campo_obligatorio)
            } else if (usuario.length < 3) {
                getString(ClienteR.string.cliente_registro_usuario_corto)
            } else {
                null
            }

            //Revisa si el usuario está vacío, es corto o tiene caracteres inválidos
            campoUsuario.error = if (usuario.isEmpty()) {
                getString(ClienteR.string.cliente_registro_campo_obligatorio)
            } else if (usuario.length < 3) {
                getString(ClienteR.string.cliente_registro_usuario_corto)
            } else if (!usuario.matches(Regex("[a-zA-Z0-9_]+"))) {
                getString(ClienteR.string.cliente_registro_usuario_formato)
            } else {
                null
            }

            //Si hay un aviso, termina la acción del botón y deja abierto el formulario
            if (campoUsuario.error != null || campoContrasena.error != null) {
                return@setOnClickListener
            }

            //Si los dos campos pasan la prueba, vuelve al login de la demo
            finish()
        }
        aplicarBordes()
    }
}