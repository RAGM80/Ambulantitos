package com.ambulantitos.feature.vendedor

import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat

class RecuperarCuentaVendedorActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContentView(R.layout.vendedor_recuperar_cuenta)

        aplicarBordesVendedor()

        val botonVolver = findViewById<View>(
            R.id.vendedor_recuperar_boton_Volver
        )

        botonVolver.setOnClickListener {
            finish()
        }
    }
}