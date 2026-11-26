package com.ambulantitos.feature.vendedor
internal enum class ErrorNombreRegistro {
    OBLIGATORIO,
    SOLO_LETRAS
}
internal enum class ErrorUsuarioRegistro {
    OBLIGATORIO,
    CORTO,
    FORMATO_INVALIDO
}
internal enum class ErrorContrasenaRegistro {
    OBLIGATORIO,
    CORTA,
    LARGA
}
internal object ValidadorRegistroVendedor {

    // \p{L} representa letras, incluidas las vocales con acento y la ñ.
    private val patronSoloLetrasYEspacios = Regex(
        """(?:\p{L}\p{M}*)+(?: +(?:\p{L}\p{M}*)+)*"""
    )

    private val patronUsuario = Regex("[a-zA-Z0-9_]+")

    const val MIN_LONGITUD_USUARIO = 3
    const val MIN_LONGITUD_CONTRASENA = 6
    const val MAX_LONGITUD_CONTRASENA = 12

    fun validarNombre(valor: String): ErrorNombreRegistro? {
        val texto = valor.trim()
        return when {
            texto.isEmpty() -> ErrorNombreRegistro.OBLIGATORIO
            !texto.matches(patronSoloLetrasYEspacios) -> ErrorNombreRegistro.SOLO_LETRAS
            else -> null
        }
    }

    fun validarCampoObligatorio(valor: String): Boolean {
        return valor.isNotBlank()
    }

    fun validarUsuario(valor: String): ErrorUsuarioRegistro? {
        val usuario = valor.trim()
        return when {
            usuario.isEmpty() -> ErrorUsuarioRegistro.OBLIGATORIO
            usuario.length < MIN_LONGITUD_USUARIO -> ErrorUsuarioRegistro.CORTO
            !usuario.matches(patronUsuario) -> ErrorUsuarioRegistro.FORMATO_INVALIDO
            else -> null
        }
    }

    fun validarContrasena(valor: String): ErrorContrasenaRegistro? {
        return when {
            valor.isBlank() -> ErrorContrasenaRegistro.OBLIGATORIO
            valor.length < MIN_LONGITUD_CONTRASENA -> ErrorContrasenaRegistro.CORTA
            valor.length > MAX_LONGITUD_CONTRASENA -> ErrorContrasenaRegistro.LARGA
            else -> null
        }
    }
}