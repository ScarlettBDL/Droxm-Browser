//! Puente JNI del bloqueador. Diseñado para cruzar la frontera lo menos posible:
//! - `nativeCompile` y `nativeLoad` reciben RUTAS de archivo (las listas grandes nunca pasan por la JVM);
//! - `nativeCheck` hace una sola llamada por pedido y devuelve un entero empaquetado (bit 0 = bloquear,
//!   el resto = tipo de recurso), sin crear objetos en Java.
//! Ningún pánico cruza hacia Java: todo va envuelto en `catch_unwind`.

use droxm_adblock_core::{Builder, Engine};
use jni::objects::{JClass, JObjectArray, JString};
use jni::sys::{jint, jstring};
use jni::JNIEnv;
use std::panic::{catch_unwind, AssertUnwindSafe};
use std::sync::{Arc, RwLock};

static ENGINE: RwLock<Option<Arc<Engine>>> = RwLock::new(None);

fn opt_string(env: &mut JNIEnv, s: &JString) -> Option<String> {
    if s.as_raw().is_null() {
        return None;
    }
    env.get_string(s).ok().map(String::from)
}

fn compile(paths: &[String], out: &str) -> Result<String, String> {
    let mut b = Builder::new();
    for p in paths {
        let bytes = std::fs::read(p).map_err(|e| format!("{p}: {e}"))?;
        b.add_list(&String::from_utf8_lossy(&bytes));
    }
    let (set, st) = b.finish();
    if set.is_empty() {
        return Err("las listas no tienen reglas".into());
    }
    // Se escribe a un archivo temporal y se renombra: un corte a mitad de camino no deja un bloque roto.
    let tmp = format!("{out}.tmp");
    std::fs::write(&tmp, set.to_bytes()).map_err(|e| e.to_string())?;
    std::fs::rename(&tmp, out).map_err(|e| e.to_string())?;
    Ok(format!(
        "{};{};{};{};{};{};{}",
        st.blocks, st.exceptions, st.duplicates, st.badfilter_removed, st.cosmetic, st.regex, st.unsupported
    ))
}

/// Compila las listas (rutas) a un bloque binario. Devuelve "bloqueo;excepciones;duplicadas;badfilter;cosméticas;regex;no soportadas"
/// o "ERR:mensaje".
#[no_mangle]
pub extern "system" fn Java_com_droxm_browser_engine_NativeAdblock_nativeCompile<'l>(
    mut env: JNIEnv<'l>,
    _class: JClass<'l>,
    lists: JObjectArray<'l>,
    out: JString<'l>,
) -> jstring {
    let result = catch_unwind(AssertUnwindSafe(|| -> Result<String, String> {
        let n = env.get_array_length(&lists).map_err(|e| e.to_string())?;
        let mut paths = Vec::with_capacity(n as usize);
        for i in 0..n {
            let o = env.get_object_array_element(&lists, i).map_err(|e| e.to_string())?;
            let s = JString::from(o);
            paths.push(opt_string(&mut env, &s).ok_or("ruta nula")?);
        }
        let out = opt_string(&mut env, &out).ok_or("ruta de salida nula")?;
        compile(&paths, &out)
    }))
    .unwrap_or_else(|_| Err("pánico interno".into()));

    let text = match result {
        Ok(s) => s,
        Err(e) => format!("ERR:{e}"),
    };
    env.new_string(text).map(|s| s.into_raw()).unwrap_or(std::ptr::null_mut())
}

/// Carga un bloque compilado. Devuelve la cantidad de reglas, o -1 si falla.
#[no_mangle]
pub extern "system" fn Java_com_droxm_browser_engine_NativeAdblock_nativeLoad<'l>(
    mut env: JNIEnv<'l>,
    _class: JClass<'l>,
    path: JString<'l>,
) -> jint {
    catch_unwind(AssertUnwindSafe(|| -> Option<jint> {
        let path = opt_string(&mut env, &path)?;
        let bytes = std::fs::read(path).ok()?;
        let engine = Engine::from_bytes(&bytes).ok()?;
        let n = engine.rule_count() as jint;
        // El motor nuevo se arma fuera del candado: solo el intercambio bloquea a los lectores.
        *ENGINE.write().ok()? = Some(Arc::new(engine));
        Some(n)
    }))
    .ok()
    .flatten()
    .unwrap_or(-1)
}

/// Evalúa un pedido. Devuelve `bloquear | (tipo << 1)`, o 0 si no hay motor cargado.
#[no_mangle]
pub extern "system" fn Java_com_droxm_browser_engine_NativeAdblock_nativeCheck<'l>(
    mut env: JNIEnv<'l>,
    _class: JClass<'l>,
    url: JString<'l>,
    source: JString<'l>,
    accept: JString<'l>,
    dest: JString<'l>,
) -> jint {
    catch_unwind(AssertUnwindSafe(|| -> Option<jint> {
        let engine = ENGINE.read().ok()?.as_ref().cloned()?;
        let url = opt_string(&mut env, &url)?;
        let source = opt_string(&mut env, &source);
        let accept = opt_string(&mut env, &accept);
        let dest = opt_string(&mut env, &dest);
        Some(engine.check(&url, source.as_deref(), accept.as_deref(), dest.as_deref()).pack())
    }))
    .ok()
    .flatten()
    .unwrap_or(0)
}
