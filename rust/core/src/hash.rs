//! Hash FNV-1a de 64 bits que ignora mayúsculas, sin dependencias.
//!
//! Se usa en todo el motor: los tokens de las URLs, los dominios y las claves de deduplicación.
//! Ignorar mayúsculas al hashear permite comparar sin crear copias en minúsculas de cada URL.

use std::hash::{BuildHasherDefault, Hasher};

const OFFSET: u64 = 0xcbf2_9ce4_8422_2325;
const PRIME: u64 = 0x0000_0100_0000_01b3;

/// Hash de `bytes` tratando A-Z como a-z. Nunca devuelve 0 (el 0 queda libre para marcar casillas vacías).
#[inline]
pub fn hash_lower(bytes: &[u8]) -> u64 {
    let mut h = OFFSET;
    for &b in bytes {
        h ^= u64::from(b.to_ascii_lowercase());
        h = h.wrapping_mul(PRIME);
    }
    nonzero(h)
}

#[inline]
pub fn nonzero(h: u64) -> u64 {
    if h == 0 {
        1
    } else {
        h
    }
}

/// Mezcla final para repartir bien los bits bajos antes de usar el hash como índice de tabla.
#[inline]
pub fn mix(key: u64) -> u64 {
    let mut x = key;
    x ^= x >> 33;
    x = x.wrapping_mul(0xff51_afd7_ed55_8ccd);
    x ^= x >> 33;
    x
}

/// Hasher para `HashMap`/`HashSet` cuyas claves ya son hashes de 64 bits: no vuelve a hashear.
#[derive(Default)]
pub struct IdHasher(u64);

impl Hasher for IdHasher {
    #[inline]
    fn finish(&self) -> u64 {
        self.0
    }

    #[inline]
    fn write(&mut self, bytes: &[u8]) {
        for &b in bytes {
            self.0 = (self.0 ^ u64::from(b)).wrapping_mul(PRIME);
        }
    }

    #[inline]
    fn write_u64(&mut self, i: u64) {
        self.0 = i;
    }
}

pub type IdBuild = BuildHasherDefault<IdHasher>;

/// Acumulador incremental con el mismo algoritmo (para claves compuestas).
pub struct Fnv(u64);

impl Fnv {
    pub fn new() -> Self {
        Fnv(OFFSET)
    }

    pub fn write(&mut self, bytes: &[u8]) {
        for &b in bytes {
            self.0 ^= u64::from(b);
            self.0 = self.0.wrapping_mul(PRIME);
        }
    }

    pub fn finish(&self) -> u64 {
        nonzero(self.0)
    }
}

impl Default for Fnv {
    fn default() -> Self {
        Self::new()
    }
}
