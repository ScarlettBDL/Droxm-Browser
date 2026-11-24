//! Filtros cosméticos: reglas `##selector` que esconden elementos de la página (los huecos de los anuncios).
//!
//! - Reglas de un dominio (`sitio.com##.banner`): se buscan por el host de la página (y sus dominios padre).
//! - Reglas genéricas simples (`##.clase`, `###id`): se indexan por nombre. La página avisa qué clases e ids tiene
//!   y se devuelve solo el CSS de los que coinciden (así no se carga una hoja de estilos enorme en cada sitio).
//! - Reglas genéricas complejas (`##a[href^="..."]`): se aplican siempre, con un tope de cantidad.
//! - Excepciones (`sitio.com#@#.banner`) y exclusiones (`~sitio.com##.banner`).
//! No se soportan las reglas procedurales de uBlock/ABP (`:has-text()`, `:xpath()`, `+js()`...): se descartan.

use crate::domains::{for_each_suffix, suffix_hashes, MAX_SUFFIXES};
use crate::hash::{hash_lower, Fnv, IdBuild};
use std::collections::{HashMap, HashSet};

const MAX_COMPLEX_GENERIC: usize = 3000;
const MAX_SELECTOR_LEN: usize = 300;
const MAX_NAMES_PER_CALL: usize = 5000;
/// Selectores por regla CSS (`:is(...)` ignora los inválidos en vez de anular todo el grupo).
const CHUNK: usize = 150;

const UNSUPPORTED: &[&str] = &[
    "+js(", ":has-text(", ":-abp-", ":xpath(", ":matches-", ":upward(", ":min-text", ":watch-attr", ":remove(",
    ":nth-ancestor", ":others(", ":style(", ":contains(", ":if(", ":if-not(", ":not(:has-text", ":-js-",
];

pub struct ParsedCosmetic<'a> {
    pub exception: bool,
    pub domains: Vec<&'a str>,
    pub selector: &'a str,
}

/// Interpreta una línea cosmética. `None` si no lo es o no se puede soportar.
pub fn parse_cosmetic(line: &str) -> Option<ParsedCosmetic<'_>> {
    let line = line.trim();
    let normal = line.find("##");
    let exc = line.find("#@#");
    let (idx, exception, skip) = match (normal, exc) {
        (Some(n), Some(e)) if e < n => (e, true, 3),
        (Some(n), _) => (n, false, 2),
        (None, Some(e)) => (e, true, 3),
        (None, None) => return None,
    };
    let selector = line[idx + skip..].trim();
    if selector.is_empty() || selector.len() > MAX_SELECTOR_LEN || selector.starts_with('^') {
        return None;
    }
    if selector.contains(['{', '}', '\n']) || UNSUPPORTED.iter().any(|u| selector.contains(u)) {
        return None;
    }
    let mut domains = Vec::new();
    for d in line[..idx].split(',') {
        let d = d.trim();
        if d.is_empty() {
            continue;
        }
        if d.contains(['*', '/']) {
            return None; // dominios con comodín o expresión regular
        }
        domains.push(d);
    }
    Some(ParsedCosmetic { exception, domains, selector })
}

/// Acumula reglas cosméticas de varias listas (sin repetidas) y las devuelve normalizadas en texto.
#[derive(Default)]
pub struct CosmeticBuilder {
    seen: HashSet<u64, IdBuild>,
    text: String,
    pub count: u32,
}

impl CosmeticBuilder {
    pub fn add(&mut self, line: &str) {
        if parse_cosmetic(line).is_none() {
            return;
        }
        let line = line.trim();
        let mut k = Fnv::new();
        k.write(line.to_ascii_lowercase().as_bytes());
        if !self.seen.insert(k.finish()) {
            return;
        }
        self.text.push_str(line);
        self.text.push('\n');
        self.count += 1;
    }

    pub fn into_text(self) -> String {
        self.text
    }
}

pub struct Cosmetic {
    selectors: Vec<String>,
    sel_hash: Vec<u64>,
    by_host: HashMap<u64, Vec<u32>, IdBuild>,
    simple: HashMap<u64, Vec<u32>, IdBuild>,
    complex: Vec<u32>,
    /// (hash del dominio, hash del selector); dominio 0 = todos.
    exceptions: HashSet<(u64, u64)>,
}

fn is_simple(sel: &str) -> bool {
    let b = sel.as_bytes();
    b.len() > 1 && (b[0] == b'.' || b[0] == b'#') && b[1..].iter().all(|&c| c.is_ascii_alphanumeric() || c == b'_' || c == b'-')
}

impl Cosmetic {
    pub fn from_text(text: &str) -> Cosmetic {
        let mut c = Cosmetic {
            selectors: Vec::new(),
            sel_hash: Vec::new(),
            by_host: HashMap::with_hasher(IdBuild::default()),
            simple: HashMap::with_hasher(IdBuild::default()),
            complex: Vec::new(),
            exceptions: HashSet::new(),
        };
        let mut interned: HashMap<&str, u32> = HashMap::new();

        for line in text.lines() {
            let Some(p) = parse_cosmetic(line) else { continue };
            let h = hash_lower(p.selector.as_bytes());

            if p.exception {
                if p.domains.is_empty() {
                    c.exceptions.insert((0, h));
                }
                for d in p.domains.iter().filter(|d| !d.starts_with('~')) {
                    c.exceptions.insert((hash_lower(d.as_bytes()), h));
                }
                continue;
            }

            let id = *interned.entry(p.selector).or_insert_with(|| {
                c.selectors.push(p.selector.to_string());
                c.sel_hash.push(h);
                (c.selectors.len() - 1) as u32
            });

            let positive: Vec<&str> = p.domains.iter().copied().filter(|d| !d.starts_with('~')).collect();
            if !positive.is_empty() {
                for d in positive {
                    c.by_host.entry(hash_lower(d.as_bytes())).or_default().push(id);
                }
            } else {
                // Genérica (con o sin dominios excluidos).
                for d in p.domains.iter().filter_map(|d| d.strip_prefix('~')) {
                    c.exceptions.insert((hash_lower(d.as_bytes()), h));
                }
                if is_simple(p.selector) {
                    c.simple.entry(hash_lower(p.selector.as_bytes())).or_default().push(id);
                } else if c.complex.len() < MAX_COMPLEX_GENERIC {
                    c.complex.push(id);
                }
            }
        }
        c
    }

    pub fn len(&self) -> usize {
        self.selectors.len()
    }

    pub fn is_empty(&self) -> bool {
        self.selectors.is_empty()
    }

    fn excluded(&self, hosts: &[u64], sel: u64) -> bool {
        self.exceptions.contains(&(0, sel)) || hosts.iter().any(|&h| self.exceptions.contains(&(h, sel)))
    }

    fn emit(&self, mut ids: Vec<u32>) -> String {
        ids.sort_unstable();
        ids.dedup();
        let mut out = String::new();
        for chunk in ids.chunks(CHUNK) {
            out.push_str(":is(");
            for (i, &id) in chunk.iter().enumerate() {
                if i > 0 {
                    out.push(',');
                }
                out.push_str(&self.selectors[id as usize]);
            }
            out.push_str("){display:none!important}\n");
        }
        out
    }

    /// CSS que se aplica apenas empieza la página: reglas del dominio y genéricas complejas.
    pub fn for_host(&self, host: &str) -> String {
        let (hosts, n) = suffix_hashes(host);
        let hosts = &hosts[..n];
        let mut ids = Vec::new();
        for_each_suffix(host, |s| {
            if let Some(list) = self.by_host.get(&hash_lower(s.as_bytes())) {
                ids.extend(list.iter().copied());
            }
            false
        });
        ids.extend(self.complex.iter().copied());
        ids.retain(|&id| !self.excluded(hosts, self.sel_hash[id as usize]));
        self.emit(ids)
    }

    /// CSS de las reglas genéricas simples que coinciden con las clases e ids (`.clase`, `#id`, uno por línea) de la página.
    pub fn for_names(&self, host: &str, names: &str) -> String {
        let (hosts, n): ([u64; MAX_SUFFIXES], usize) = suffix_hashes(host);
        let hosts = &hosts[..n];
        let mut ids = Vec::new();
        for name in names.lines().take(MAX_NAMES_PER_CALL) {
            if let Some(list) = self.simple.get(&hash_lower(name.trim().as_bytes())) {
                ids.extend(list.iter().copied().filter(|&id| !self.excluded(hosts, self.sel_hash[id as usize])));
            }
        }
        self.emit(ids)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn build(lines: &str) -> Cosmetic {
        let mut b = CosmeticBuilder::default();
        for l in lines.lines() {
            b.add(l);
        }
        Cosmetic::from_text(&b.into_text())
    }

    #[test]
    fn domain_generic_and_exceptions() {
        let c = build(
            "site.com##.promo\n##.ad-box\n###ad_top\nother.com#@#.ad-box\n~safe.org###ad_top\n##a[href^=\"https://ads.\"]\nsite.com#@#.promo\nsub.site.com##.promo\n",
        );
        // El dominio tiene la regla propia y la compleja genérica; `.promo` está exceptuada en site.com pero sub.site.com la hereda de su regla propia.
        let css = c.for_host("www.site.com");
        assert!(css.contains("a[href^=\"https://ads.\"]"));
        assert!(!css.contains(".promo"));
        // La excepción de site.com también cubre sus subdominios.
        assert!(!c.for_host("sub.site.com").contains(".promo"));

        let names = ".ad-box\n#ad_top\n.nada";
        let css = c.for_names("news.org", names);
        assert!(css.contains(".ad-box") && css.contains("#ad_top"));
        // other.com exceptúa .ad-box; safe.org excluye #ad_top
        assert!(!c.for_names("other.com", names).contains(".ad-box"));
        assert!(!c.for_names("safe.org", names).contains("#ad_top"));
        assert!(c.for_names("www.safe.org", names).contains(".ad-box"));
    }

    #[test]
    fn unsupported_and_dedupe() {
        let mut b = CosmeticBuilder::default();
        b.add("a.com##.x:has-text(Publicidad)");
        b.add("a.com##+js(abort-on-property-read, foo)");
        b.add("*.com##.y");
        b.add("a.com##.ok");
        b.add("A.com##.OK");
        b.add("a.com#?#.proc");
        b.add("sitio.com###x{color:red}");
        assert_eq!(b.count, 1);
    }

    #[test]
    fn chunks_use_is() {
        let c = build("a.com##.uno\na.com##.dos\n");
        let css = c.for_host("a.com");
        assert!(css.starts_with(":is(") && css.contains(".uno,.dos") && css.trim_end().ends_with("{display:none!important}"));
    }
}
