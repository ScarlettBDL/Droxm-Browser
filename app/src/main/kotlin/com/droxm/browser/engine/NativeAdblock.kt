package com.droxm.browser.engine

import java.io.File

/**
 * Puente hacia el motor de filtrado escrito en Rust (`rust/jni`). Si la librería no está en el APK
 * (por ejemplo si falló su compilación), [available] queda en false y la app usa el bloqueador de reserva.
 *
 * Costo por pedido: una sola llamada nativa que devuelve un entero (bit 0 = bloquear, resto = tipo de recurso).
 * Las listas grandes nunca pasan por la JVM: se compilan y se cargan desde archivos.
 */
object NativeAdblock {

    val available: Boolean = try {
        System.loadLibrary("droxm_adblock")
        true
    } catch (_: Throwable) {
        false
    }

    /** true cuando hay un conjunto de reglas cargado en el motor. */
    @Volatile
    var ready: Boolean = false
        private set

    @JvmStatic
    external fun nativeCompile(lists: Array<String>, out: String): String?

    @JvmStatic
    external fun nativeLoad(path: String): Int

    @JvmStatic
    external fun nativeCheck(url: String, source: String?, accept: String?, dest: String?): Int

    @JvmStatic
    external fun nativeCosmeticHost(host: String): String?

    @JvmStatic
    external fun nativeCosmeticNames(host: String, names: String): String?

    /** CSS que oculta elementos de anuncios para este sitio (reglas del dominio y genéricas complejas). "" si no hay. */
    fun cosmeticForHost(host: String): String =
        if (!ready) "" else try { nativeCosmeticHost(host).orEmpty() } catch (e: Throwable) { "" }

    /** CSS para las clases e ids que la página reporta (uno por línea, con `.` o `#` delante). */
    fun cosmeticForNames(host: String, names: String): String =
        if (!ready) "" else try { nativeCosmeticNames(host, names).orEmpty() } catch (e: Throwable) { "" }

    /** Compila las listas a un bloque binario. Devuelve "bloqueo;excepciones;duplicadas;badfilter;cosméticas;regex;no soportadas" o "ERR:...". */
    fun compile(lists: List<File>, out: File): String {
        if (!available) return "ERR:librería nativa no disponible"
        return try {
            nativeCompile(lists.map { it.absolutePath }.toTypedArray(), out.absolutePath) ?: "ERR:sin respuesta"
        } catch (e: Throwable) {
            "ERR:${e.message}"
        }
    }

    /** Carga un bloque compilado. Devuelve la cantidad de reglas, o -1 si falla. */
    fun load(file: File): Int {
        if (!available || !file.exists()) return -1
        val n = try {
            nativeLoad(file.absolutePath)
        } catch (e: Throwable) {
            -1
        }
        ready = n > 0
        return n
    }

    fun check(url: String, source: String?, accept: String?, dest: String?): Int =
        if (!ready) 0 else nativeCheck(url, source, accept, dest)
}
