package com.ambulantitos.feature.vendedor

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

class IngredientesVendedorAdapter(
    private val alQuitar: (String) -> Unit
) : ListAdapter<String, IngredientesVendedorAdapter.IngredienteViewHolder>(
    Diferencias
) {

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): IngredienteViewHolder {
        val vista = LayoutInflater.from(parent.context).inflate(
            R.layout.vendedor_item_ingrediente,
            parent,
            false
        )

        return IngredienteViewHolder(vista)
    }

    override fun onBindViewHolder(
        holder: IngredienteViewHolder,
        position: Int
    ) {
        holder.mostrar(getItem(position))
    }

    inner class IngredienteViewHolder(
        vista: View
    ) : RecyclerView.ViewHolder(vista) {

        private val nombre = vista.findViewById<TextView>(
            R.id.vendedor_item_ingrediente_nombre
        )

        private val botonQuitar = vista.findViewById<View>(
            R.id.vendedor_item_ingrediente_btn_quitar
        )

        fun mostrar(ingrediente: String) {
            nombre.text = ingrediente

            botonQuitar.setOnClickListener {
                alQuitar(ingrediente)
            }
        }
    }
    private object Diferencias : DiffUtil.ItemCallback<String>() {

        override fun areItemsTheSame(
            anterior: String,
            nuevo: String
        ): Boolean = anterior == nuevo

        override fun areContentsTheSame(
            anterior: String,
            nuevo: String
        ): Boolean = anterior == nuevo
    }
}