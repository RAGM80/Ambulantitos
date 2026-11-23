package com.ambulantitos.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import com.ambulantitos.feature.cliente.R as ClienteR
import com.google.android.material.appbar.MaterialToolbar

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

        //Deja espacio para las barras del teléfono y el teclado
        aplicarBordes()
    }
}