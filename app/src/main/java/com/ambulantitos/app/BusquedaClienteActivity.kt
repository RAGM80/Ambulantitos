package com.ambulantitos.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import com.ambulantitos.feature.cliente.R as ClienteR
import com.google.android.material.appbar.MaterialToolbar
import android.content.Intent
import android.view.View

class BusquedaClienteActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        //Abre el diseño de Vendedores cercanos que ya tenemos en el módulo cliente
        setContentView(ClienteR.layout.pantalla_buscar_vendedores_cliente)

        //Busca la barra de arriba para conectar la flecha de regreso
        val barra = findViewById<MaterialToolbar>(ClienteR.id.barra_busqueda)
        barra.setNavigationOnClickListener {
            //Cierra esta pantalla y vuelve al login desde donde entramos
            finish()
        }
        //Busca el botón Ver productos que está en la tarjeta del vendedor
        val botonVerProductos = findViewById<View>(ClienteR.id.boton_ver_productos)

        botonVerProductos.setOnClickListener {
            //Abre la pantalla de Productos disponibles
            val intent = Intent(this, ProductosClienteActivity::class.java)
            startActivity(intent)
        }
        //Deja espacio para las barras del teléfono y el teclado
        aplicarBordes()
    }
}