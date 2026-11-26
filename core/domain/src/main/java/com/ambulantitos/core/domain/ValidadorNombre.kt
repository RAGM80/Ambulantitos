package com.ambulantitos.core.domain

object ValidadorNombre {

    private val patron = Regex(
        """\p{L}[\p{L}\p{M}]*(?:(?: +|['’-])\p{L}[\p{L}\p{M}]*)*"""
    )

    fun esValido(valor: String): Boolean {
        return patron.matches(valor.trim())
    }
}
