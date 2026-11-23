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

class MainActivity : ComponentActivity() {

    //Prepara el temporizador que usaremos para dejar visible el logo
    private val temporizador = Handler(Looper.getMainLooper())

    //Guarda la acción que muestra los botones cuando termina la espera
    private val abrirSelector = Runnable { mostrarSelector() }

    //Empieza en false porque todavía no mostramos los botones de cliente y vendedor
    private var selectorAbierto = false

    override fun onCreate(savedInstanceState: Bundle?) {
        //Deja que Android prepare esta pantalla antes de cargar el diseño
        super.onCreate(savedInstanceState)

        //Permite que el fondo llegue hasta los bordes de la pantalla
        enableEdgeToEdge()

        //Recupera si los botones ya estaban visibles antes de girar el celular
        //Si abrimos la app por primera vez, se usa false para mostrar el logo
        selectorAbierto = savedInstanceState?.getBoolean("selector_abierto") ?: false

        if (selectorAbierto) {
            //Si los botones ya estaban visibles, vuelve directo al selector
            mostrarSelector()
        } else {
            //Carga pantalla_arranque.xml, que contiene el logo y el círculo de espera
            setContentView(R.layout.pantalla_arranque)

            //Deja espacio para que las barras del celular no tapen el diseño
            aplicarBordes()

            //Espera 4500 milisegundos, que son 4 segundos y medio, y muestra el selector
            temporizador.postDelayed(abrirSelector, 4500L)
        }
    }

    private fun mostrarSelector() {
        //Marca que el logo ya pasó y que ahora se muestran los botones
        selectorAbierto = true

        //Cambia el diseño del logo por pantalla_inicio.xml dentro de MainActivity
        setContentView(R.layout.pantalla_inicio)
        aplicarBordes()

        //Busca cada botón por el nombre que tiene en pantalla_inicio.xml
        val botonCliente = findViewById<View>(R.id.btn_cliente)
        val botonVendedor = findViewById<View>(R.id.btn_vendedor)

        //Cuando tocas Soy cliente, abre el inicio de sesión de cliente
        botonCliente.setOnClickListener {
            abrirLogin(LoginActivity.ROL_CLIENTE)
        }

        //Cuando tocas Soy vendedor, abre el inicio de sesión de vendedor
        botonVendedor.setOnClickListener {
            abrirLogin(LoginActivity.ROL_VENDEDOR)
        }
    }

    private fun abrirLogin(rol: String) {
        //Crea la instrucción que le dice a Android que abra LoginActivity
        val intent = Intent(this, LoginActivity::class.java)

        //Agrega el rol elegido para que LoginActivity sepa qué diseño mostrar
        intent.putExtra(LoginActivity.EXTRA_ROL, rol)

        //Abre el inicio de sesión y permite volver al selector con el botón Atrás
        startActivity(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        //Guarda si ya mostramos el selector para recuperarlo al girar el celular
        outState.putBoolean("selector_abierto", selectorAbierto)

        //Deja que Android guarde también la información de la pantalla
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        //Si esta pantalla se cierra mientras se ve el logo, cancela la espera pendiente
        temporizador.removeCallbacks(abrirSelector)
        super.onDestroy()
    }
}

//Ajusta el espacio de los bordes en MainActivity y LoginActivity
internal fun ComponentActivity.aplicarBordes() {
    //Busca el contenedor donde Android muestra el diseño de la pantalla
    val contenido = findViewById<View>(android.R.id.content)

    //Vuelve a calcular los espacios cuando cambia la pantalla o aparece el teclado
    ViewCompat.setOnApplyWindowInsetsListener(contenido) { vista, insets ->
        //Revisa cuánto espacio ocupan las barras, el recorte de la cámara y el teclado
        val bordes = insets.getInsets(
            WindowInsetsCompat.Type.systemBars() or
                    WindowInsetsCompat.Type.displayCutout() or
                    WindowInsetsCompat.Type.ime()
        )

        //Deja espacio a la izquierda, arriba, a la derecha y abajo según se necesite
        vista.setPadding(bordes.left, bordes.top, bordes.right, bordes.bottom)

        //Indica que este contenedor ya aplicó el espacio necesario
        WindowInsetsCompat.CONSUMED
    }

    //Pide que el ajuste se aplique al diseño que acabamos de cargar
    ViewCompat.requestApplyInsets(contenido)
}