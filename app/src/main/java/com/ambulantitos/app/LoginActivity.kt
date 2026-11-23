package com.ambulantitos.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge

class LoginActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        //Deja que Android prepare la pantalla antes de cargar el diseño
        super.onCreate(savedInstanceState)

        //Permite que el fondo llegue hasta los bordes de la pantalla
        enableEdgeToEdge()

        //Lee el rol que MainActivity mandó al tocar Soy cliente o Soy vendedor
        val rol = intent.getStringExtra(EXTRA_ROL)

        //Revisa el rol recibido para elegir qué pantalla mostrar
        when (rol) {
            ROL_CLIENTE -> {
                //Muestra pantalla_login_cliente.xml del módulo cliente
                setContentView(
                    com.ambulantitos.feature.cliente.R.layout.pantalla_login_cliente
                )
            }

            ROL_VENDEDOR -> {
                //Muestra vendedor_login.xml del módulo vendedor
                setContentView(
                    com.ambulantitos.feature.vendedor.R.layout.vendedor_login
                )
            }

            else -> {
                //Si el rol falta o es distinto de cliente y vendedor, cierra esta pantalla
                finish()

                //Termina aquí porque no se cargó ningún diseño
                return
            }
        }

        //Deja espacio para que las barras y el teclado no tapen el diseño
        aplicarBordes()
    }

    companion object {
        //Nombre con el que MainActivity envía el rol y LoginActivity lo lee
        const val EXTRA_ROL = "rol"

        //Valor que se envía al tocar Soy cliente
        const val ROL_CLIENTE = "cliente"

        //Valor que se envía al tocar Soy vendedor
        const val ROL_VENDEDOR = "vendedor"
    }
}