package com.ambulantitos.feature.cliente

import android.R
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.recyclerview.widget.RecyclerView
import com.ambulantitos.feature.cliente.R as ClienteR
import com.google.android.material.appbar.MaterialToolbar

class ProductosClienteActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContentView(ClienteR.layout.pantalla_productos_cliente)

        //Conecta la flecha de la barra y el botón Regresar
        val barra = findViewById<MaterialToolbar>(ClienteR.id.barra_productos_cliente)
        barra.setNavigationOnClickListener {
            finish()
        }

        val botonRegresar = findViewById<View>(ClienteR.id.boton_regresar_productos_cliente)
        botonRegresar.setOnClickListener {
            finish()
        }

        //Conecta la lista con el adaptador que dibuja la tarjeta de prueba
        val lista = findViewById<RecyclerView>(ClienteR.id.lista_productos_cliente)
        lista.adapter = ProductoPruebaAdapter {
            //Abre Cantidad e indicaciones al tocar la tarjeta
            val intent = Intent(this, CantidadClienteActivity::class.java)
            startActivity(intent)
        }

        aplicarBordes()
    }
}

private class ProductoPruebaAdapter(
    private val alTocarProducto: () -> Unit
) : RecyclerView.Adapter<ProductoPruebaAdapter.FilaProducto>() {

    //Guarda las referencias a los elementos que vamos a llenar en la tarjeta
    class FilaProducto(vista: View) : RecyclerView.ViewHolder(vista) {
        val imagen: ImageView = vista.findViewById(ClienteR.id.imagen_producto_cliente)
        val nombre: TextView = vista.findViewById(ClienteR.id.nombre_producto_cliente)
        val precio: TextView = vista.findViewById(ClienteR.id.precio_producto_cliente)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FilaProducto {
        //Carga el XML que ya diseñamos para una sola tarjeta
        val vista = LayoutInflater.from(parent.context).inflate(
            ClienteR.layout.elemento_producto_cliente,
            parent,
            false
        )
        return FilaProducto(vista)
    }

    override fun onBindViewHolder(holder: FilaProducto, position: Int) {
        //Coloca los datos de prueba y conecta el toque en toda la tarjeta
        holder.imagen.setImageResource(R.drawable.ic_menu_gallery)
        holder.nombre.setText(ClienteR.string.cliente_demo_nombre_esquite)
        holder.precio.setText(ClienteR.string.cliente_demo_precio_esquite)
        holder.itemView.setOnClickListener {
            alTocarProducto()
        }
    }

    override fun getItemCount(): Int {
        //Por ahora el catálogo muestra un solo producto de prueba
        return 1
    }
}