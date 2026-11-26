package com.ambulantitos.feature.vendedor

import androidx.lifecycle.ViewModel
import com.ambulantitos.core.data.RepositorioFirestore

class PanelVendedorViewModel : ViewModel() {

    private val repositorio = RepositorioFirestore.obtener()

    fun cerrarSesion() {
        repositorio.cerrarSesion()
    }
}
