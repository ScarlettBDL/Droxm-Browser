//! Matching de dominios: extraer el host de una URL, recorrer sus dominios padre, calcular el dominio
//! registrable y decidir si un pedido es de tercero. Todo trabaja sobre cortes (`&str`) de la URL original:
//! no se reserva memoria por pedido.

use crate::hash::hash_lower;

/// Posición (inicio, fin) del host dentro de la URL: `scheme://[usuario@]host[:puerto]/...`.
pub fn host_range(url: &str) -> Option<(usize, usize)> {
    let scheme_end = url.find("://")?;
    if scheme_end == 0 || scheme_end > 16 {
        return None;
    }
    let start = scheme_end + 3;
    let rest = &url[start..];
    let auth_len = rest.find(['/', '?', '#']).unwrap_or(rest.len());
    let authority = &rest[..auth_len];

    let host_part = match authority.rfind('@') {
        Some(i) => &authority[i + 1..],
        None => authority,
    };
    let host_off = start + (authority.len() - host_part.len());

    let host_len = if host_part.starts_with('[') {
        // IPv6: [::1]:8080
        host_part.find(']').map_or(host_part.len(), |i| i + 1)
    } else {
        host_part.rfind(':').unwrap_or(host_part.len())
    };
    if host_len == 0 {
        return None;
    }
    Some((host_off, host_off + host_len))
}

/// Llama a `f` con el host y con cada dominio padre (`a.b.com` → `a.b.com`, `b.com`), sin llegar al TLD suelto.
/// Se detiene y devuelve `true` apenas `f` devuelve `true`.
pub fn for_each_suffix(host: &str, mut f: impl FnMut(&str) -> bool) -> bool {
    let mut s = host;
    loop {
        if f(s) {
            return true;
        }
        match s.find('.') {
            Some(i) => {
                s = &s[i + 1..];
                if !s.contains('.') {
                    return false;
                }
            }
            None => return false,
        }
    }
}

/// Hashes del host y de todos sus dominios padre (para las condiciones `domain=` de las reglas).
pub const MAX_SUFFIXES: usize = 12;

pub fn suffix_hashes(host: &str) -> ([u64; MAX_SUFFIXES], usize) {
    let mut out = [0u64; MAX_SUFFIXES];
    let mut n = 0;
    for_each_suffix(host, |s| {
        if n < MAX_SUFFIXES {
            out[n] = hash_lower(s.as_bytes());
            n += 1;
        }
        n >= MAX_SUFFIXES
    });
    (out, n)
}

fn is_ip(host: &str) -> bool {
    host.contains(':') || host.bytes().all(|b| b.is_ascii_digit() || b == b'.')
}

/// Sufijos públicos de dos etiquetas más comunes (no es la lista completa de sufijos públicos: es una aproximación).
fn is_two_label_suffix(s: &str) -> bool {
    // Se pasa a minúsculas en un buffer de pila: esta función corre en cada pedido y no debe reservar memoria.
    let bytes = s.as_bytes();
    let mut buf = [0u8; 20];
    if bytes.len() > buf.len() {
        return false;
    }
    for (slot, &c) in buf.iter_mut().zip(bytes) {
        *slot = c.to_ascii_lowercase();
    }
    let Ok(lower) = std::str::from_utf8(&buf[..bytes.len()]) else {
        return false;
    };
    matches!(
        lower,
        "co.uk" | "org.uk" | "ac.uk" | "gov.uk" | "me.uk" | "ltd.uk" | "plc.uk" | "net.uk" | "sch.uk"
            | "com.au" | "net.au" | "org.au" | "edu.au" | "gov.au"
            | "co.nz" | "org.nz" | "net.nz" | "govt.nz"
            | "co.jp" | "ne.jp" | "or.jp" | "ac.jp" | "go.jp"
            | "co.kr" | "or.kr" | "co.in" | "net.in" | "org.in" | "firm.in" | "gen.in" | "ind.in"
            | "com.br" | "net.br" | "org.br" | "gov.br" | "edu.br"
            | "com.ar" | "net.ar" | "org.ar" | "gob.ar" | "gov.ar" | "edu.ar"
            | "com.mx" | "org.mx" | "gob.mx" | "edu.mx"
            | "com.co" | "com.pe" | "com.ve" | "com.uy" | "com.py" | "com.bo" | "com.ec" | "com.gt"
            | "com.do" | "com.pa" | "com.sv" | "com.ni" | "com.hn" | "com.cu"
            | "com.cn" | "net.cn" | "org.cn" | "gov.cn" | "edu.cn"
            | "com.hk" | "com.tw" | "com.sg" | "com.my" | "com.ph" | "com.vn" | "com.tr" | "com.ua"
            | "com.pl" | "co.za" | "org.za" | "co.il" | "co.id" | "or.id" | "com.eg" | "com.sa"
            | "com.ng" | "co.ke"
            // Hosting donde cada cliente es un sitio distinto (sufijos "privados")
            | "github.io" | "gitlab.io" | "blogspot.com" | "herokuapp.com" | "netlify.app" | "vercel.app"
            | "pages.dev" | "workers.dev" | "web.app" | "firebaseapp.com" | "appspot.com"
            | "cloudfront.net" | "azurewebsites.net" | "amazonaws.com" | "wordpress.com"
    )
}

/// Dominio registrable aproximado (`a.b.example.co.uk` → `example.co.uk`). Las IP se devuelven tal cual.
pub fn registrable_domain(host: &str) -> &str {
    if is_ip(host) {
        return host;
    }
    let mut dots = host.rmatch_indices('.').map(|(i, _)| i);
    if dots.next().is_none() {
        return host;
    }
    let second = match dots.next() {
        Some(i) => i,
        None => return host,
    };
    if is_two_label_suffix(&host[second + 1..]) {
        match dots.next() {
            Some(i) => &host[i + 1..],
            None => host,
        }
    } else {
        &host[second + 1..]
    }
}

/// ¿El pedido es de un sitio distinto al de la página que lo originó?
pub fn is_third_party(request_host: &str, source_host: &str) -> bool {
    !registrable_domain(request_host).eq_ignore_ascii_case(registrable_domain(source_host))
}
