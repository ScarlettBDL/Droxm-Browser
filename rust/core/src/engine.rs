//! Motor de filtrado: índices de reglas y evaluación de pedidos.
//!
//! Un pedido nunca se compara contra todas las reglas. Cada regla se guarda bajo una "clave de índice":
//! - reglas `||dominio^` simples: el dominio completo (se busca recorriendo los dominios padre del host);
//! - el resto: el token más raro de su patrón (se buscan los tokens de la URL);
//! - sin token utilizable: lista genérica (pocas reglas).
//! Los índices son tablas de direccionamiento abierto con las reglas en disposición contigua.

use crate::domains::{for_each_suffix, host_range, is_third_party, suffix_hashes, MAX_SUFFIXES};
use crate::exceptions::{decide, Reason};
use crate::hash::{hash_lower, mix, IdBuild};
use crate::pattern::{for_each_url_token, index_tokens, matches, MAX_PATTERN_TOKENS};
use crate::resources::{classify, ResourceType};
use crate::store::*;
use std::collections::HashMap;

/// Tope de tokens de URL que se prueban (acota el peor caso con URLs enormes).
const MAX_URL_TOKENS: usize = 96;

/// Tabla hash de direccionamiento abierto: clave de 64 bits → tramo de ids de regla.
pub(crate) struct Table {
    mask: usize,
    keys: Vec<u64>,
    spans: Vec<(u32, u32)>,
    items: Vec<u32>,
    len: usize,
}

impl Table {
    fn build(map: HashMap<u64, Vec<u32>, IdBuild>) -> Table {
        let len = map.len();
        let cap = (len * 2).max(4).next_power_of_two();
        let mut t = Table { mask: cap - 1, keys: vec![0; cap], spans: vec![(0, 0); cap], items: Vec::new(), len };
        for (key, ids) in map {
            let mut i = mix(key) as usize & t.mask;
            while t.keys[i] != 0 {
                i = (i + 1) & t.mask;
            }
            t.keys[i] = key;
            t.spans[i] = (t.items.len() as u32, ids.len() as u32);
            t.items.extend(ids);
        }
        t
    }

    #[inline]
    fn get(&self, key: u64) -> &[u32] {
        let mut i = mix(key) as usize & self.mask;
        loop {
            let k = self.keys[i];
            if k == key {
                let (s, n) = self.spans[i];
                return &self.items[s as usize..(s + n) as usize];
            }
            if k == 0 {
                return &[];
            }
            i = (i + 1) & self.mask;
        }
    }
}

/// Un pedido ya preparado: se calcula una sola vez y se comparte entre todas las comprobaciones.
pub struct Request<'a> {
    url: &'a str,
    host: &'a str,
    host_range: (usize, usize),
    pub primary: ResourceType,
    mask: u32,
    third: bool,
    src: [u64; MAX_SUFFIXES],
    src_n: usize,
}

impl<'a> Request<'a> {
    /// `None` si la URL no tiene host (data:, blob:, about:...): esos pedidos no se filtran.
    pub fn new(url: &'a str, source: Option<&str>, accept: Option<&str>, dest: Option<&str>) -> Option<Request<'a>> {
        let range = host_range(url)?;
        let host = &url[range.0..range.1];
        let class = classify(url, accept, dest);
        let src_host = source.and_then(|s| host_range(s).map(|(a, b)| &s[a..b]));
        let (src, src_n, third) = match src_host {
            Some(h) => {
                let (hashes, n) = suffix_hashes(h);
                (hashes, n, is_third_party(host, h))
            }
            // Sin página de origen conocida se asume tercero: es lo que importa para bloquear.
            None => ([0; MAX_SUFFIXES], 0, true),
        };
        Some(Request { url, host, host_range: range, primary: class.primary, mask: class.mask, third, src, src_n })
    }
}

pub(crate) struct Index {
    set: RuleSet,
    host: Table,
    tokens: Table,
    generic: Vec<u32>,
}

/// Regla `||dominio.tld^` sin nada más: devuelve el dominio.
fn host_only(set: &RuleSet, r: &Rule) -> Option<u64> {
    if r.flags & (F_HOST | F_START | F_END) != F_HOST {
        return None;
    }
    let p = set.pattern(r);
    let (&last, dom) = p.split_last()?;
    if last != b'^' || !dom.contains(&b'.') || !dom.iter().all(|&c| c.is_ascii_alphanumeric() || c == b'.' || c == b'-') {
        return None;
    }
    Some(hash_lower(dom))
}

impl Index {
    pub(crate) fn build(src: &RuleSet, keep: impl Fn(&Rule) -> bool) -> Index {
        let mut set = RuleSet::default();
        for r in src.rules.iter().filter(|r| keep(r)) {
            set.copy_rule_from(src, r);
        }

        // Pasada 1: cuántas reglas comparte cada token (para elegir el más raro).
        let mut freq: HashMap<u64, u32, IdBuild> = HashMap::with_hasher(IdBuild::default());
        let mut toks = [0u64; MAX_PATTERN_TOKENS];
        for r in &set.rules {
            if host_only(&set, r).is_some() {
                continue;
            }
            let n = index_tokens(set.pattern(r), r.flags & F_HOST != 0, r.flags & F_START != 0, r.flags & F_END != 0, &mut toks);
            for &t in &toks[..n] {
                *freq.entry(t).or_insert(0) += 1;
            }
        }

        let mut host_map: HashMap<u64, Vec<u32>, IdBuild> = HashMap::with_hasher(IdBuild::default());
        let mut tok_map: HashMap<u64, Vec<u32>, IdBuild> = HashMap::with_hasher(IdBuild::default());
        let mut generic = Vec::new();
        for (id, r) in set.rules.iter().enumerate() {
            let id = id as u32;
            if let Some(h) = host_only(&set, r) {
                host_map.entry(h).or_default().push(id);
                continue;
            }
            let n = index_tokens(set.pattern(r), r.flags & F_HOST != 0, r.flags & F_START != 0, r.flags & F_END != 0, &mut toks);
            match toks[..n].iter().min_by_key(|t| freq.get(t).copied().unwrap_or(0)) {
                Some(&t) => tok_map.entry(t).or_default().push(id),
                None => generic.push(id),
            }
        }
        Index { set, host: Table::build(host_map), tokens: Table::build(tok_map), generic }
    }

    pub(crate) fn len(&self) -> usize {
        self.set.len()
    }

    /// Condiciones de la regla que no dependen del patrón: tipo, partido y dominios de la página.
    #[inline]
    fn rule_ok(&self, r: &Rule, req: &Request) -> bool {
        if r.type_mask != 0 && r.type_mask & req.mask == 0 {
            return false;
        }
        match r.party {
            PARTY_THIRD if !req.third => return false,
            PARTY_FIRST if req.third => return false,
            _ => {}
        }
        if r.inc_n != 0 {
            let inc = self.set.includes(r);
            if !req.src[..req.src_n].iter().any(|h| inc.contains(h)) {
                return false;
            }
        }
        if r.exc_n != 0 {
            let exc = self.set.excludes(r);
            if req.src[..req.src_n].iter().any(|h| exc.contains(h)) {
                return false;
            }
        }
        true
    }

    #[inline]
    fn full_match(&self, r: &Rule, req: &Request) -> bool {
        self.rule_ok(r, req)
            && matches(
                req.url,
                req.host_range,
                self.set.pattern(r),
                r.flags & F_HOST != 0,
                r.flags & F_START != 0,
                r.flags & F_END != 0,
            )
    }

    /// ¿Alguna regla del índice aplica al pedido?
    pub(crate) fn matches(&self, req: &Request) -> bool {
        if self.set.is_empty() {
            return false;
        }
        if self.host.len > 0
            && for_each_suffix(req.host, |s| {
                self.host.get(hash_lower(s.as_bytes())).iter().any(|&i| self.rule_ok(&self.set.rules[i as usize], req))
            })
        {
            return true;
        }
        if self.tokens.len > 0 {
            let mut found = false;
            for_each_url_token(req.url.as_bytes(), MAX_URL_TOKENS, |h| {
                found = self.tokens.get(h).iter().any(|&i| self.full_match(&self.set.rules[i as usize], req));
                found
            });
            if found {
                return true;
            }
        }
        self.generic.iter().any(|&i| self.full_match(&self.set.rules[i as usize], req))
    }
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct Verdict {
    pub block: bool,
    pub reason: Reason,
    /// Tipo de recurso deducido (para elegir la respuesta vacía adecuada).
    pub resource: ResourceType,
}

impl Verdict {
    /// Un solo entero para devolver por JNI: bit 0 = bloquear, resto = tipo de recurso.
    pub fn pack(self) -> i32 {
        (self.block as i32) | ((self.resource as i32) << 1)
    }
}

pub struct Engine {
    important: Index,
    blocks: Index,
    exceptions: Index,
}

impl Engine {
    pub fn from_ruleset(set: &RuleSet) -> Engine {
        Engine {
            important: Index::build(set, |r| r.flags & F_EXC == 0 && r.flags & F_IMP != 0),
            blocks: Index::build(set, |r| r.flags & (F_EXC | F_IMP) == 0),
            exceptions: Index::build(set, |r| r.flags & F_EXC != 0),
        }
    }

    pub fn from_bytes(bytes: &[u8]) -> Result<Engine, String> {
        Ok(Engine::from_ruleset(&RuleSet::from_bytes(bytes)?))
    }

    pub fn rule_count(&self) -> usize {
        self.important.len() + self.blocks.len() + self.exceptions.len()
    }

    /// Evalúa un pedido. `source` es la URL de la página que lo origina; `accept` y `dest`
    /// (`Sec-Fetch-Dest`) sirven para deducir el tipo de recurso.
    pub fn check(&self, url: &str, source: Option<&str>, accept: Option<&str>, dest: Option<&str>) -> Verdict {
        match Request::new(url, source, accept, dest) {
            None => Verdict { block: false, reason: Reason::NoMatch, resource: ResourceType::Other },
            Some(req) => {
                let reason = decide(&self.important, &self.blocks, &self.exceptions, &req);
                Verdict { block: reason.blocks(), reason, resource: req.primary }
            }
        }
    }
}
