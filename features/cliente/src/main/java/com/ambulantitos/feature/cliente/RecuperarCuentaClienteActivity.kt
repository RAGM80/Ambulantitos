package com.ambulantitos.feature.cliente

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import com.ambulantitos.feature.cliente.R as ClienteR
import com.google.android.material.appbar.MaterialToolbar

class RecuperarCuentaClienteActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        //Muestra el formulario para recuperar la cuenta
        setContentView(ClienteR.layout.pantalla_recuperar_cuenta_cliente)

        //La flecha cierra este formulario y vuelve al inicio de sesión
        val barra = findViewById<MaterialToolbar>(ClienteR.id.barra_recuperar_cuenta)
        barra.setNavigationOnClickListener {
            finish()
        }

        //Al tocar Siguiente, abre el formulario de nueva contraseña
        val botonSiguiente = findViewById<View>(ClienteR.id.boton_siguiente_recuperacion)
        botonSiguiente.setOnClickListener {
            val intent = Intent(this, NuevaContrasenaClienteActivity::class.java)
            startActivity(intent)
        }

        aplicarBordes()
    }
}