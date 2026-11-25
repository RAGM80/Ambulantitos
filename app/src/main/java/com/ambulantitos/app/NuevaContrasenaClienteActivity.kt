package com.ambulantitos.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import com.ambulantitos.feature.cliente.R as ClienteR
import com.google.android.material.appbar.MaterialToolbar

class NuevaContrasenaClienteActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        //Muestra el formulario de nueva contraseña
        setContentView(ClienteR.layout.pantalla_nueva_contrasena_cliente)

        //La flecha vuelve a Recuperar cuenta para poder corregir el formulario anterior
        val barra = findViewById<MaterialToolbar>(ClienteR.id.barra_nueva_contrasena)
        barra.setNavigationOnClickListener {
            finish()
        }

        //En la prueba, Aceptar vuelve al inicio de sesión del cliente
        val botonAceptar = findViewById<View>(ClienteR.id.boton_aceptar_nueva_contrasena)
        botonAceptar.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            intent.putExtra(LoginActivity.EXTRA_ROL, LoginActivity.ROL_CLIENTE)

            //Vuelve al login que ya estaba abierto y cierra las dos pantallas de recuperación
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
        }

        aplicarBordes()
    }
}