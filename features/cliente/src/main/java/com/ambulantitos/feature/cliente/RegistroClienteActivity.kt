package com.ambulantitos.feature.cliente

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

        //Muestra el formulario de registro del cliente
        setContentView(ClienteR.layout.pantalla_registro_cliente)

        //La flecha cierra el formulario y vuelve al inicio de sesión
        val barra = findViewById<MaterialToolbar>(ClienteR.id.barra_registro_cliente)
        barra.setNavigationOnClickListener {
            finish()
        }

        //Busca los recuadros donde se muestran los errores y el botón Crear cuenta
        val campoUsuario = findViewById<TextInputLayout>(ClienteR.id.contenedor_usuario)
        val campoContrasena = findViewById<TextInputLayout>(ClienteR.id.contenedor_contrasena)
        val botonCrear = findViewById<View>(ClienteR.id.boton_crear_cuenta)

        botonCrear.setOnClickListener {
            //Lee los dos campos al tocar Crear cuenta
            val usuario = campoUsuario.editText?.text?.toString().orEmpty().trim()
            val contrasena = campoContrasena.editText?.text?.toString().orEmpty()

            //Revisa que el usuario esté lleno y tenga al menos 3 caracteres
            //Solo permite letras de la A a la Z, números y guion bajo
            campoUsuario.error = if (usuario.isEmpty()) {
                getString(ClienteR.string.cliente_registro_campo_obligatorio)
            } else if (usuario.length < 3) {
                getString(ClienteR.string.cliente_registro_usuario_corto)
            } else if (!usuario.matches(Regex("[a-zA-Z0-9_]+"))) {
                getString(ClienteR.string.cliente_registro_usuario_formato)
            } else {
                null
            }

            //Revisa que la contraseña esté llena y tenga al menos 6 caracteres
            campoContrasena.error = if (contrasena.isBlank()) {
                getString(ClienteR.string.cliente_registro_campo_obligatorio)
            } else if (contrasena.length < 6) {
                getString(ClienteR.string.cliente_registro_contrasena_corta)
            } else {
                null
            }

            //Después de revisar los dos campos, deja abierto el formulario si hay un error
            if (campoUsuario.error != null || campoContrasena.error != null) {
                return@setOnClickListener
            }

            //En la demo, vuelve al login cuando los dos campos son válidos
            finish()
        }

        //Deja espacio para las barras del teléfono y el teclado
        aplicarBordes()
    }
}