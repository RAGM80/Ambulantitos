package com.ambulantitos.feature.vendedor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import android.content.Intent
import android.view.View
import androidx.lifecycle.ViewModelProvider

class PanelVendedorActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(
            window,
            false
        )

        setContentView(R.layout.vendedor_panel)

        aplicarBordesVendedor()
        val modeloPanel = ViewModelProvider(this)[
            PanelVendedorViewModel::class.java
        ]

        val botonCerrarSesion = findViewById<View>(
            R.id.vendedor_panel_btn_cerrar_sesion
        )

        botonCerrarSesion.setOnClickListener {
            botonCerrarSesion.isEnabled = false

            modeloPanel.cerrarSesion()

            val abrirLogin = Intent(
                this,
                LoginVendedorActivity::class.java
            )

            abrirLogin.flags =
                Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TASK

            startActivity(abrirLogin)
        }
    }
}