package com.ambulantitos.feature.cliente

import android.view.View
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

internal fun ComponentActivity.aplicarBordes() {
    //Busca el contenedor donde Android muestra la pantalla
    val contenido = findViewById<View>(android.R.id.content)

    ViewCompat.setOnApplyWindowInsetsListener(contenido) { vista, insets ->
        //Calcula el espacio de las barras, la cámara y el teclado
        val bordes = insets.getInsets(
            WindowInsetsCompat.Type.systemBars() or
                    WindowInsetsCompat.Type.displayCutout() or
                    WindowInsetsCompat.Type.ime()
        )

        //Deja libre ese espacio para que el contenido no quede tapado
        vista.setPadding(bordes.left, bordes.top, bordes.right, bordes.bottom)

        WindowInsetsCompat.CONSUMED
    }

    //Aplica el ajuste al diseño de la pantalla
    ViewCompat.requestApplyInsets(contenido)
}