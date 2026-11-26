package com.ambulantitos.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import android.content.Intent
import android.view.View

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
                //Carga el formulario del cliente
                setContentView(
                    com.ambulantitos.feature.cliente.R.layout.pantalla_login_cliente
                )

                //Busca el botón Iniciar sesión en el XML del cliente
                val botonEntrar = findViewById<View>(
                    com.ambulantitos.feature.cliente.R.id.btn_login_cliente
                )

                botonEntrar.setOnClickListener {
                    //Abre Vendedores cercanos para probar la continuidad de interfaces
                    val intent = Intent(this, BusquedaClienteActivity::class.java)
                    startActivity(intent)
                }
            }

            ROL_VENDEDOR -> {
                val abrirVendedor = Intent(
                    this,
                    com.ambulantitos.feature.vendedor.LoginVendedorActivity::class.java
                )
                startActivity(abrirVendedor)

                finish()
                return
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