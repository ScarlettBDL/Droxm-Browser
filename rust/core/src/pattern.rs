//! Comparación de patrones de reglas contra URLs, sin expresiones regulares y sin reservar memoria.
//!
//! Sintaxis soportada (la de Adblock Plus / uBlock Origin para reglas de red):
//! - `*` comodín de cualquier largo;
//! - `^` separador: cualquier carácter que no sea letra, número, `_`, `-`, `.` o `%`, o el final de la URL;
//! - `|` al inicio / al final: ancla al comienzo / al final de la URL;
//! - `||` al inicio: ancla al comienzo de un dominio o subdominio.
//!
//! Los patrones se guardan en minúsculas; la URL se compara ignorando mayúsculas.

use crate::hash::hash_lower;

#[inline]
fn is_separator(b: u8) -> bool {
    !(b.is_ascii_alphanumeric() || b == b'_' || b == b'-' || b == b'.' || b == b'%')
}

/// Compara un segmento (sin `*`) en `pos`. Devuelve dónde termina la coincidencia.
fn match_segment_at(url: &[u8], pos: usize, seg: &[u8]) -> Option<usize> {
    let mut p = pos;
    for &c in seg {
        if c == b'^' {
            if p == url.len() {
                continue; // el final de la URL cuenta como separador (sin ocupar lugar)
            }
            if is_separator(url[p]) {
                p += 1;
            } else {
                return None;
            }
        } else {
            if p >= url.len() || url[p].to_ascii_lowercase() != c {
                return None;
            }
            p += 1;
        }
    }
    Some(p)
}

/// Busca el segmento más a la izquierda a partir de `from`. Devuelve dónde termina.
fn find_segment(url: &[u8], from: usize, seg: &[u8]) -> Option<usize> {
    let first = seg[0];
    let mut i = from;
    while i <= url.len() {
        if first != b'^' {
            // Salta directo a la próxima aparición del primer carácter.
            match url[i..].iter().position(|&b| b.to_ascii_lowercase() == first) {
                Some(off) => i += off,
                None => return None,
            }
        }
        if let Some(end) = match_segment_at(url, i, seg) {
            return Some(end);
        }
        i += 1;
    }
    None
}

/// El segmento tiene que terminar exactamente al final de la URL.
fn match_ending_at_end(url: &[u8], from: usize, seg: &[u8]) -> bool {
    let low = url.len().saturating_sub(seg.len()).max(from);
    (low..=url.len()).any(|s| match_segment_at(url, s, seg) == Some(url.len()))
}

/// Compara todos los segmentos en orden. `start` fija dónde tiene que empezar el primero (ancla);
/// con `None` puede aparecer en cualquier lugar.
fn match_from(url: &[u8], pat: &[u8], start: Option<usize>, end_anchor: bool) -> bool {
    let mut segments = pat.split(|&b| b == b'*').peekable();
    let mut pos = 0usize;
    let mut first = true;

    while let Some(seg) = segments.next() {
        let last = segments.peek().is_none();
        if seg.is_empty() {
            // Comodín al principio, al final o doble: no agrega condiciones.
            if last {
                return true;
            }
            first = false;
            continue;
        }
        if first {
            if let Some(s) = start {
                match match_segment_at(url, s, seg) {
                    Some(e) => pos = e,
                    None => return false,
                }
                if last && end_anchor && pos != url.len() {
                    return false;
                }
                first = false;
                continue;
            }
        }
        if last && end_anchor {
            return match_ending_at_end(url, pos, seg);
        }
        match find_segment(url, pos, seg) {
            Some(e) => pos = e,
            None => return false,
        }
        first = false;
    }
    true
}

/// ¿La URL cumple el patrón? `host` es el rango del host dentro de la URL (para el ancla `||`).
pub fn matches(
    url: &str,
    host: (usize, usize),
    pat: &[u8],
    host_anchor: bool,
    start_anchor: bool,
    end_anchor: bool,
) -> bool {
    let u = url.as_bytes();
    if host_anchor {
        let (hs, he) = host;
        if match_from(u, pat, Some(hs), end_anchor) {
            return true;
        }
        // También puede empezar justo después de cada punto del host (subdominios).
        (hs..he.min(u.len())).any(|i| u[i] == b'.' && match_from(u, pat, Some(i + 1), end_anchor))
    } else if start_anchor {
        match_from(u, pat, Some(0), end_anchor)
    } else {
        match_from(u, pat, None, end_anchor)
    }
}

/// Máximo de tokens por regla que se consideran como clave de índice.
pub const MAX_PATTERN_TOKENS: usize = 8;
/// Largo mínimo de un token para indexar.
pub const MIN_TOKEN_LEN: usize = 2;

/// Tokens (tramos de letras y números) del patrón que sirven para indexar la regla: los que, si la regla
/// coincide, tienen que aparecer completos como token de la URL. Un token pegado a un `*` o al borde sin
/// ancla podría ser solo un pedazo de un token más largo de la URL, así que no se usa.
pub fn index_tokens(
    pat: &[u8],
    host_anchor: bool,
    start_anchor: bool,
    end_anchor: bool,
    out: &mut [u64; MAX_PATTERN_TOKENS],
) -> usize {
    let mut n = 0;
    let mut i = 0;
    while i < pat.len() && n < MAX_PATTERN_TOKENS {
        if !pat[i].is_ascii_alphanumeric() {
            i += 1;
            continue;
        }
        let s = i;
        while i < pat.len() && pat[i].is_ascii_alphanumeric() {
            i += 1;
        }
        let e = i;

        let left_ok = if s == 0 { host_anchor || start_anchor } else { pat[s - 1] != b'*' };
        let right_ok = if e == pat.len() { end_anchor } else { pat[e] != b'*' };
        if left_ok && right_ok && e - s >= MIN_TOKEN_LEN {
            out[n] = hash_lower(&pat[s..e]);
            n += 1;
        }
    }
    n
}

/// Recorre los tokens de una URL (tramos de letras y números de al menos [`MIN_TOKEN_LEN`]).
/// Se corta en `limit` tokens para acotar el peor caso con URLs enormes.
pub fn for_each_url_token(url: &[u8], limit: usize, mut f: impl FnMut(u64) -> bool) {
    let mut count = 0;
    let mut i = 0;
    while i < url.len() && count < limit {
        if !url[i].is_ascii_alphanumeric() {
            i += 1;
            continue;
        }
        let s = i;
        while i < url.len() && url[i].is_ascii_alphanumeric() {
            i += 1;
        }
        if i - s >= MIN_TOKEN_LEN {
            count += 1;
            if f(hash_lower(&url[s..i])) {
                return;
            }
        }
    }
}
