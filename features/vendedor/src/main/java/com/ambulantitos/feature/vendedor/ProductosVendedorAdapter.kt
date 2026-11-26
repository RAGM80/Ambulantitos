package com.ambulantitos.feature.vendedor

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil3.load
import coil3.request.error
import coil3.request.fallback
import coil3.request.placeholder
import com.ambulantitos.core.domain.EstadoProducto
import com.ambulantitos.core.domain.Product
import java.text.NumberFormat
import java.util.Locale

class ProductosVendedorAdapter(
    private val alPulsarProducto: (Product) -> Unit
) : ListAdapter<Product, ProductosVendedorAdapter.ProductoViewHolder>(
    Diferencias
) {

    private val formatoMoneda =
        NumberFormat.getCurrencyInstance(Locale("es", "MX"))

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ProductoViewHolder {
        val vista = LayoutInflater.from(parent.context).inflate(
            R.layout.vendedor_item_producto,
            parent,
            false
        )

        return ProductoViewHolder(vista)
    }

    override fun onBindViewHolder(
        holder: ProductoViewHolder,
        position: Int
    ) {
        holder.mostrar(getItem(position))
    }
    inner class ProductoViewHolder(
        vista: View
    ) : RecyclerView.ViewHolder(vista) {

        private val imagen = vista.findViewById<ImageView>(
            R.id.vendedor_detalle_producto_icono
        )

        private val nombre = vista.findViewById<TextView>(
            R.id.vendedor_item_producto_nombre
        )

        private val precio = vista.findViewById<TextView>(
            R.id.vendedor_item_producto_precio
        )

        private val existencias = vista.findViewById<TextView>(
            R.id.vendedor_item_producto_existencias
        )

        private val estado = vista.findViewById<TextView>(
            R.id.vendedor_item_producto_estado
        )

        fun mostrar(producto: Product) {
            nombre.text = producto.nombre
            precio.text = formatoMoneda.format(producto.precio)

            existencias.text = itemView.context.getString(
                R.string.vendedor_item_producto_existencias_formato,
                producto.existencias
            )

            val disponible = producto.visibleEnCatalogoCliente

            estado.text = if (disponible) {
                EstadoProducto.EN_EXISTENCIA
            } else {
                EstadoProducto.AGOTADO
            }

            estado.setBackgroundResource(
                if (disponible) {
                    R.drawable.vendedor_producto_fondo_disponible
                } else {
                    R.drawable.vendedor_producto_fondo_agotado
                }
            )

            estado.setTextColor(
                Color.parseColor(
                    if (disponible) "#385B32" else "#9E2412"
                )
            )

            imagen.load(producto.imagenUrl) {
                placeholder(android.R.drawable.ic_menu_gallery)
                error(android.R.drawable.ic_menu_gallery)
                fallback(android.R.drawable.ic_menu_gallery)
            }

            itemView.setOnClickListener {
                alPulsarProducto(producto)
            }
        }
    }
    private object Diferencias : DiffUtil.ItemCallback<Product>() {

        override fun areItemsTheSame(
            anterior: Product,
            nuevo: Product
        ): Boolean {
            return anterior.productId == nuevo.productId
        }

        override fun areContentsTheSame(
            anterior: Product,
            nuevo: Product
        ): Boolean {
            return anterior == nuevo
        }
    }
}
