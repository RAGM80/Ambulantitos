package com.ambulantitos.app

import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import com.ambulantitos.feature.cliente.R as ClienteR
import com.google.android.material.appbar.MaterialToolbar

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

        //En esta prueba Crear cuenta también vuelve al inicio de sesión
        val botonCrear = findViewById<View>(ClienteR.id.boton_crear_cuenta)
        botonCrear.setOnClickListener {
            finish()
        }

        aplicarBordes()
    }
}