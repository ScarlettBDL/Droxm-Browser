//! Almacenamiento compacto de reglas ya compiladas y su serialización a un bloque binario.
//!
//! Todas las reglas comparten un único arreglo de bytes (`arena`) para los patrones y otro de hashes de
//! dominio: nada de un `String` o `Vec` por regla. El bloque binario permite que la app arranque sin volver a
//! parsear las listas de texto.

use crate::hash::Fnv;

pub const F_HOST: u8 = 1;
pub const F_START: u8 = 2;
pub const F_END: u8 = 4;
pub const F_EXC: u8 = 8;
pub const F_IMP: u8 = 16;
const F_KNOWN: u8 = F_HOST | F_START | F_END | F_EXC | F_IMP;

pub const PARTY_ANY: u8 = 0;
pub const PARTY_THIRD: u8 = 1;
pub const PARTY_FIRST: u8 = 2;

const MAGIC: &[u8; 4] = b"DXAB";
const VERSION: u32 = 1;
const RULE_BYTES: usize = 22;

#[derive(Clone, Copy, Debug)]
pub struct Rule {
    pub pat_off: u32,
    pub pat_len: u32,
    pub flags: u8,
    pub party: u8,
    pub inc_n: u16,
    pub exc_n: u16,
    /// 0 = todos los tipos.
    pub type_mask: u32,
    pub dom_off: u32,
}

#[derive(Default, Clone)]
pub struct RuleSet {
    pub rules: Vec<Rule>,
    pub arena: Vec<u8>,
    pub doms: Vec<u64>,
}

impl RuleSet {
    pub fn push(&mut self, flags: u8, party: u8, type_mask: u32, pattern: &[u8], inc: &[u64], exc: &[u64]) {
        let rule = Rule {
            pat_off: self.arena.len() as u32,
            pat_len: pattern.len() as u32,
            flags,
            party,
            inc_n: inc.len() as u16,
            exc_n: exc.len() as u16,
            type_mask,
            dom_off: self.doms.len() as u32,
        };
        self.arena.extend_from_slice(pattern);
        self.doms.extend_from_slice(inc);
        self.doms.extend_from_slice(exc);
        self.rules.push(rule);
    }

    pub fn copy_rule_from(&mut self, src: &RuleSet, r: &Rule) {
        self.push(r.flags, r.party, r.type_mask, src.pattern(r), src.includes(r), src.excludes(r));
    }

    #[inline]
    pub fn pattern(&self, r: &Rule) -> &[u8] {
        &self.arena[r.pat_off as usize..(r.pat_off + r.pat_len) as usize]
    }

    #[inline]
    pub fn includes(&self, r: &Rule) -> &[u64] {
        let s = r.dom_off as usize;
        &self.doms[s..s + r.inc_n as usize]
    }

    #[inline]
    pub fn excludes(&self, r: &Rule) -> &[u64] {
        let s = r.dom_off as usize + r.inc_n as usize;
        &self.doms[s..s + r.exc_n as usize]
    }

    pub fn len(&self) -> usize {
        self.rules.len()
    }

    pub fn is_empty(&self) -> bool {
        self.rules.is_empty()
    }

    pub fn to_bytes(&self) -> Vec<u8> {
        let mut o = Vec::with_capacity(20 + self.rules.len() * RULE_BYTES + self.arena.len() + self.doms.len() * 8 + 8);
        o.extend_from_slice(MAGIC);
        o.extend_from_slice(&VERSION.to_le_bytes());
        o.extend_from_slice(&(self.rules.len() as u32).to_le_bytes());
        o.extend_from_slice(&(self.arena.len() as u32).to_le_bytes());
        o.extend_from_slice(&(self.doms.len() as u32).to_le_bytes());
        for r in &self.rules {
            o.extend_from_slice(&r.pat_off.to_le_bytes());
            o.extend_from_slice(&r.pat_len.to_le_bytes());
            o.push(r.flags);
            o.push(r.party);
            o.extend_from_slice(&r.inc_n.to_le_bytes());
            o.extend_from_slice(&r.exc_n.to_le_bytes());
            o.extend_from_slice(&r.type_mask.to_le_bytes());
            o.extend_from_slice(&r.dom_off.to_le_bytes());
        }
        o.extend_from_slice(&self.arena);
        for d in &self.doms {
            o.extend_from_slice(&d.to_le_bytes());
        }
        let mut f = Fnv::new();
        f.write(&o);
        o.extend_from_slice(&f.finish().to_le_bytes());
        o
    }

    /// Carga un bloque verificando checksum y límites: un archivo corrupto devuelve error, nunca provoca pánico.
    pub fn from_bytes(b: &[u8]) -> Result<RuleSet, String> {
        if b.len() < 28 {
            return Err("bloque demasiado corto".into());
        }
        let (body, tail) = b.split_at(b.len() - 8);
        let mut f = Fnv::new();
        f.write(body);
        if f.finish().to_le_bytes() != tail {
            return Err("checksum inválido".into());
        }
        let mut rd = Reader { b: body, p: 0 };
        if rd.take(4)? != MAGIC {
            return Err("firma inválida".into());
        }
        if rd.u32()? != VERSION {
            return Err("versión no soportada".into());
        }
        let n_rules = rd.u32()? as usize;
        let arena_len = rd.u32()? as usize;
        let doms_len = rd.u32()? as usize;
        let need = n_rules
            .checked_mul(RULE_BYTES)
            .and_then(|x| x.checked_add(arena_len))
            .and_then(|x| doms_len.checked_mul(8).and_then(|d| x.checked_add(d)))
            .ok_or("tamaños inválidos")?;
        if need != body.len() - rd.p {
            return Err("tamaños inconsistentes".into());
        }

        let mut set = RuleSet { rules: Vec::with_capacity(n_rules), arena: Vec::new(), doms: Vec::with_capacity(doms_len) };
        for _ in 0..n_rules {
            let r = Rule {
                pat_off: rd.u32()?,
                pat_len: rd.u32()?,
                flags: rd.u8()?,
                party: rd.u8()?,
                inc_n: rd.u16()?,
                exc_n: rd.u16()?,
                type_mask: rd.u32()?,
                dom_off: rd.u32()?,
            };
            let pat_end = r.pat_off as u64 + r.pat_len as u64;
            let dom_end = r.dom_off as u64 + r.inc_n as u64 + r.exc_n as u64;
            if r.pat_len == 0 || pat_end > arena_len as u64 || dom_end > doms_len as u64 || r.flags & !F_KNOWN != 0 || r.party > PARTY_FIRST {
                return Err("regla fuera de rango".into());
            }
            set.rules.push(r);
        }
        set.arena = rd.take(arena_len)?.to_vec();
        for _ in 0..doms_len {
            set.doms.push(rd.u64()?);
        }
        Ok(set)
    }
}

struct Reader<'a> {
    b: &'a [u8],
    p: usize,
}

impl<'a> Reader<'a> {
    fn take(&mut self, n: usize) -> Result<&'a [u8], String> {
        let e = self.p.checked_add(n).filter(|&e| e <= self.b.len()).ok_or("bloque truncado")?;
        let s = &self.b[self.p..e];
        self.p = e;
        Ok(s)
    }
    fn u8(&mut self) -> Result<u8, String> {
        Ok(self.take(1)?[0])
    }
    fn u16(&mut self) -> Result<u16, String> {
        Ok(u16::from_le_bytes(self.take(2)?.try_into().unwrap()))
    }
    fn u32(&mut self) -> Result<u32, String> {
        Ok(u32::from_le_bytes(self.take(4)?.try_into().unwrap()))
    }
    fn u64(&mut self) -> Result<u64, String> {
        Ok(u64::from_le_bytes(self.take(8)?.try_into().unwrap()))
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn roundtrip_and_corruption() {
        let mut s = RuleSet::default();
        s.push(F_HOST, PARTY_THIRD, 8, b"ads.x.com^", &[1, 2], &[3]);
        s.push(F_EXC, PARTY_ANY, 0, b"/banner/", &[], &[]);
        let bytes = s.to_bytes();
        let t = RuleSet::from_bytes(&bytes).unwrap();
        assert_eq!(t.len(), 2);
        assert_eq!(t.pattern(&t.rules[0]), b"ads.x.com^");
        assert_eq!(t.includes(&t.rules[0]), &[1, 2]);
        assert_eq!(t.excludes(&t.rules[0]), &[3]);

        let mut bad = bytes.clone();
        bad[30] ^= 0xff;
        assert!(RuleSet::from_bytes(&bad).is_err());
        assert!(RuleSet::from_bytes(&bytes[..bytes.len() - 3]).is_err());
        assert!(RuleSet::from_bytes(&[]).is_err());
    }
}
