package com.ambulantitos.feature.cliente

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ambulantitos.core.data.RepositorioFirestore
import com.ambulantitos.core.domain.Product
import com.ambulantitos.core.network.p2p.CatalogoP2p
import com.ambulantitos.feature.cliente.BusquedaClienteActivity.Companion.EXTRA_CATALOGO_LOCAL
import com.ambulantitos.feature.cliente.BusquedaClienteActivity.Companion.EXTRA_NOMBRE_VENDEDOR
import com.ambulantitos.feature.cliente.BusquedaClienteActivity.Companion.EXTRA_VENDOR_ID
import com.google.android.material.appbar.MaterialToolbar
import java.io.IOException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class ProductosClienteActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val vendorId = intent.getStringExtra(EXTRA_VENDOR_ID)?.takeIf { it.isNotBlank() }
        if (vendorId == null) {
            finish()
            return
        }

        enableEdgeToEdge()
        setContentView(R.layout.pantalla_productos_cliente)
        val barra = findViewById<MaterialToolbar>(R.id.barra_productos_cliente)
        barra.setNavigationOnClickListener { finish() }
        findViewById<View>(R.id.boton_regresar_productos_cliente).setOnClickListener { finish() }
        val estado = findViewById<TextView>(R.id.texto_indicacion_productos_cliente)
        var negocio = intent.getStringExtra(EXTRA_NOMBRE_VENDEDOR).orEmpty()
        texto(R.id.texto_vendedor_productos_cliente, negocio)

        var miniaturas = emptyMap<String, ByteArray>()
        val adapter = CatalogoClienteAdapter({ id -> miniaturas[id] }) { producto ->
            val seleccionado = ProductoPedido(
                vendorId, negocio, producto.productId, producto.nombre, producto.iconoClave,
                producto.precio, producto.existencias, producto.imagenUrl, miniaturas[producto.productId],
            )
            startActivity(Intent(this, CantidadClienteActivity::class.java).putExtra(EXTRA_PRODUCTO, seleccionado))
        }
        findViewById<RecyclerView>(R.id.lista_productos_cliente).adapter = adapter
        aplicarBordes()

        val datosLocales = intent.getByteArrayExtra(EXTRA_CATALOGO_LOCAL)
        if (datosLocales != null) {
            val local = try {
                CatalogoP2p.decodificar(datosLocales).takeIf { it.vendorId == vendorId }
            } catch (_: IOException) {
                null
            }
            if (local == null) {
                estado.setText(R.string.cliente_catalogo_error)
            } else {
                negocio = local.nombre.ifBlank { negocio }
                texto(R.id.texto_vendedor_productos_cliente, negocio)
                miniaturas = local.miniaturas
                adapter.submitList(local.productos)
                estado.setText(
                    if (local.productos.isEmpty()) R.string.cliente_catalogo_vacio
                    else R.string.cliente_catalogo_local,
                )
            }
            return
        }

        estado.setText(R.string.cliente_catalogo_cargando)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                RepositorioFirestore.obtener().escucharCatalogoCliente(vendorId)
                    .catch {
                        adapter.submitList(emptyList())
                        estado.visibility = View.VISIBLE
                        estado.setText(R.string.cliente_catalogo_error)
                    }
                    .collect { productos ->
                        adapter.submitList(productos)
                        estado.setText(R.string.cliente_catalogo_vacio)
                        estado.visibility = if (productos.isEmpty()) View.VISIBLE else View.GONE
                    }
            }
        }
    }
}

private class CatalogoClienteAdapter(
    private val miniatura: (String) -> ByteArray?,
    private val seleccionado: (Product) -> Unit,
) : ListAdapter<Product, CatalogoClienteAdapter.Fila>(object : DiffUtil.ItemCallback<Product>() {
    override fun areItemsTheSame(anterior: Product, nuevo: Product) = anterior.productId == nuevo.productId
    override fun areContentsTheSame(anterior: Product, nuevo: Product) = anterior == nuevo
}) {
    class Fila(vista: View) : RecyclerView.ViewHolder(vista) {
        val imagen: ImageView = vista.findViewById(R.id.imagen_producto_cliente)
        val nombre: TextView = vista.findViewById(R.id.nombre_producto_cliente)
        val precio: TextView = vista.findViewById(R.id.precio_producto_cliente)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Fila =
        Fila(LayoutInflater.from(parent.context).inflate(R.layout.elemento_producto_cliente, parent, false))

    override fun onBindViewHolder(holder: Fila, position: Int) {
        val producto = getItem(position)
        holder.imagen.mostrarFotoProducto(producto.imagenUrl, miniatura(producto.productId))
        holder.nombre.text = producto.nombre
        holder.precio.text = dinero(producto.precio)
        holder.itemView.setOnClickListener { seleccionado(producto) }
    }
}
