package com.ambulantitos.feature.cliente

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import com.ambulantitos.core.data.RepositorioFirestore
import com.ambulantitos.feature.cliente.R as ClienteR
import com.google.android.material.appbar.MaterialToolbar

class BusquedaClienteActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        //Muestra la pantalla de Vendedores cercanos
        setContentView(ClienteR.layout.pantalla_buscar_vendedores_cliente)

        //La flecha regresa a la pantalla anterior
        val barra = findViewById<MaterialToolbar>(ClienteR.id.barra_busqueda)
        barra.setNavigationOnClickListener {
            finish()
        }

        //Abre Productos disponibles
        val botonVerProductos = findViewById<View>(ClienteR.id.boton_ver_productos)
        botonVerProductos.setOnClickListener {
            val intent = Intent(this, ProductosClienteActivity::class.java)
            startActivity(intent)
        }

        //Abre la lista de pedidos
        val botonMisPedidos = findViewById<View>(ClienteR.id.boton_mis_pedidos)
        botonMisPedidos.setOnClickListener {
            val intent = Intent(this, MisPedidosClienteActivity::class.java)
            startActivity(intent)
        }

        //Busca el botón Cerrar sesión en el diseño
        val botonCerrarSesion = findViewById<View>(ClienteR.id.boton_cerrar_sesion_cliente)
        botonCerrarSesion.setOnClickListener {
            //Cierra la sesión del cliente en Firebase
            RepositorioFirestore.obtener().cerrarSesion()

            //Abre un login nuevo para que los campos aparezcan vacíos
            val intent = Intent(this, LoginClienteActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(intent)
            finish()
        }

        //Deja espacio para las barras del teléfono y el teclado
        aplicarBordes()
    }
}