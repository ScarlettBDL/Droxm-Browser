# Reglas de R8 para el build release.
# androidx.webkit y Compose traen sus propias reglas (consumer rules), no hace falta agregar nada por ahora.

# Métodos nativos del bloqueador (Rust): JNI los busca por nombre, R8 no puede renombrarlos.
-keep class com.droxm.browser.engine.NativeAdblock {
    native <methods>;
}
