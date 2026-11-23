package com.droxm.browser.util

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

/**
 * Cosas que solo la actividad puede hacer: pedir permisos de Android y abrir el selector de archivos.
 * Tiene que crearse dentro de onCreate (registra los pedidos de resultado antes de que la actividad arranque).
 */
class SystemBridge(private val activity: ComponentActivity) {

    private var permissionCallback: ((Map<String, Boolean>) -> Unit)? = null
    private val permissionLauncher = activity.registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val cb = permissionCallback
        permissionCallback = null
        cb?.invoke(result)
    }

    private var fileCallback: ((Int, Intent?) -> Unit)? = null
    private val fileLauncher = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val cb = fileCallback
        fileCallback = null
        cb?.invoke(result.resultCode, result.data)
    }

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED

    /** Pide los permisos de Android que falten y devuelve, por cada uno, si quedó concedido. */
    fun requestPermissions(permissions: List<String>, onResult: (Map<String, Boolean>) -> Unit) {
        val missing = permissions.filter { !granted(it) }
        if (missing.isEmpty()) {
            onResult(permissions.associateWith { true })
            return
        }
        // Si había otro pedido pendiente, se cierra (su resultado se calcula con lo que haya).
        permissionCallback?.invoke(emptyMap())
        permissionCallback = { result ->
            onResult(permissions.associateWith { result[it] == true || granted(it) })
        }
        permissionLauncher.launch(missing.toTypedArray())
    }

    /** Abre el selector de archivos (el intent lo arma la página). Siempre responde, aunque se cancele. */
    fun chooseFile(intent: Intent, onResult: (Int, Intent?) -> Unit) {
        fileCallback?.invoke(Activity.RESULT_CANCELED, null)
        fileCallback = onResult
        try {
            fileLauncher.launch(intent)
        } catch (e: Exception) {
            fileCallback = null
            onResult(Activity.RESULT_CANCELED, null)
        }
    }
}
