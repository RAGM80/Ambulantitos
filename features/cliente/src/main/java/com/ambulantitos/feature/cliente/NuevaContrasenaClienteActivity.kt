package com.ambulantitos.feature.cliente

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import com.google.android.material.appbar.MaterialToolbar

class NuevaContrasenaClienteActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        //Muestra el formulario de nueva contraseña
        setContentView(R.layout.pantalla_nueva_contrasena_cliente)

        //La flecha vuelve a Recuperar cuenta
        val barra = findViewById<MaterialToolbar>(R.id.barra_nueva_contrasena)
        barra.setNavigationOnClickListener {
            finish()
        }

        //Aceptar regresa al login de Cliente en el recorrido de prueba
        val botonAceptar = findViewById<View>(R.id.boton_aceptar_nueva_contrasena)
        botonAceptar.setOnClickListener {
            val intent = Intent(this, LoginClienteActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
        }

        aplicarBordes()
    }
}