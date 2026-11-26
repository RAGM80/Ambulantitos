package com.ambulantitos.app

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.ambulantitos.feature.cliente.LoginClienteActivity

class MainActivity : ComponentActivity() {

    //Prepara la espera para mostrar el logo
    private val temporizador = Handler(Looper.getMainLooper())

    //Guarda la acción que muestra el selector al terminar la espera
    private val abrirSelector = Runnable { mostrarSelector() }

    //Guarda si los botones de Cliente y Vendedor ya están visibles
    private var selectorAbierto = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        //Recupera el estado del selector cuando Android vuelve a crear la pantalla
        selectorAbierto = savedInstanceState?.getBoolean("selector_abierto") ?: false

        if (selectorAbierto) {
            //Muestra el selector si ya estaba abierto antes de girar el celular
            mostrarSelector()
        } else {
            //Muestra el logo durante 4 segundos y medio
            setContentView(R.layout.pantalla_arranque)
            aplicarBordes()
            temporizador.postDelayed(abrirSelector, 4500L)
        }
    }

    private fun mostrarSelector() {
        selectorAbierto = true

        //Carga el diseño que tiene los botones de Cliente y Vendedor
        setContentView(R.layout.pantalla_inicio)
        aplicarBordes()

        val botonCliente = findViewById<View>(R.id.btn_cliente)
        val botonVendedor = findViewById<View>(R.id.btn_vendedor)

        //Abre el inicio de sesión que está en el módulo Cliente
        botonCliente.setOnClickListener {
            val intent = Intent(this, LoginClienteActivity::class.java)
            startActivity(intent)
        }

        //Abre el inicio de sesión del módulo Vendedor
        botonVendedor.setOnClickListener {
            val abrirVendedor = Intent(
                this,
                com.ambulantitos.feature.vendedor.LoginVendedorActivity::class.java
            )

            startActivity(abrirVendedor)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        //Guarda si ya mostramos el selector para recuperarlo al girar
        outState.putBoolean("selector_abierto", selectorAbierto)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        //Cancela la espera si Android cierra esta pantalla antes de mostrar el selector
        temporizador.removeCallbacks(abrirSelector)
        super.onDestroy()
    }
}

//Ajusta el contenido para que las barras y el teclado no lo tapen
internal fun ComponentActivity.aplicarBordes() {
    val contenido = findViewById<View>(android.R.id.content)

    ViewCompat.setOnApplyWindowInsetsListener(contenido) { vista, insets ->
        //Calcula el espacio de las barras, el recorte de la cámara y el teclado
        val bordes = insets.getInsets(
            WindowInsetsCompat.Type.systemBars() or
                    WindowInsetsCompat.Type.displayCutout() or
                    WindowInsetsCompat.Type.ime()
        )

        //Deja libre ese espacio en los cuatro lados del contenido
        vista.setPadding(bordes.left, bordes.top, bordes.right, bordes.bottom)
        WindowInsetsCompat.CONSUMED
    }

    ViewCompat.requestApplyInsets(contenido)
}