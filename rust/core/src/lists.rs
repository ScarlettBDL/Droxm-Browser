//! Actualización de filtros: junta varias listas, descarta duplicados y aplica `$badfilter`.
//!
//! Cada regla recibe una clave de 64 bits calculada sobre su contenido normalizado (patrón, anclas, tipos,
//! partido, dominios ordenados). Dos reglas con la misma clave son la misma regla, se escriba como se escriba.

use crate::cosmetic::CosmeticBuilder;
use crate::hash::{hash_lower, Fnv, IdBuild};
use crate::parser::{parse_line, Party, Skip};
use crate::store::*;
use std::collections::HashSet;

#[derive(Default, Debug, Clone, Copy, PartialEq, Eq)]
pub struct Stats {
    pub lines: u32,
    pub blocks: u32,
    pub exceptions: u32,
    pub duplicates: u32,
    pub badfilter_removed: u32,
    pub cosmetic: u32,
    pub regex: u32,
    pub unsupported: u32,
}

pub struct Builder {
    set: RuleSet,
    keys: Vec<u64>,
    seen: HashSet<u64, IdBuild>,
    bad: HashSet<u64, IdBuild>,
    cosmetic: CosmeticBuilder,
    stats: Stats,
}

impl Default for Builder {
    fn default() -> Self {
        Self::new()
    }
}

impl Builder {
    pub fn new() -> Self {
        Builder {
            set: RuleSet::default(),
            keys: Vec::new(),
            seen: HashSet::with_hasher(IdBuild::default()),
            bad: HashSet::with_hasher(IdBuild::default()),
            cosmetic: CosmeticBuilder::default(),
            stats: Stats::default(),
        }
    }

    /// Procesa el texto completo de una lista. Se puede llamar varias veces (una por lista).
    pub fn add_list(&mut self, text: &str) {
        for line in text.lines() {
            self.add_line(line);
        }
    }

    pub fn add_line(&mut self, line: &str) {
        self.stats.lines += 1;
        let rule = match parse_line(line) {
            Ok(r) => r,
            Err(Skip::Cosmetic) => {
                self.stats.cosmetic += 1;
                self.cosmetic.add(line);
                return;
            }
            Err(Skip::Regex) => {
                self.stats.regex += 1;
                return;
            }
            Err(Skip::Unsupported) => {
                self.stats.unsupported += 1;
                return;
            }
            Err(_) => return,
        };
        if rule.include_domains.len() > 1000 || rule.exclude_domains.len() > 1000 {
            self.stats.unsupported += 1;
            return;
        }

        let mut flags = 0u8;
        if rule.host_anchor {
            flags |= F_HOST;
        }
        if rule.start_anchor {
            flags |= F_START;
        }
        if rule.end_anchor {
            flags |= F_END;
        }
        if rule.exception {
            flags |= F_EXC;
        }
        if rule.important {
            flags |= F_IMP;
        }
        let party = match rule.party {
            Party::Any => PARTY_ANY,
            Party::Third => PARTY_THIRD,
            Party::First => PARTY_FIRST,
        };
        let mut inc: Vec<u64> = rule.include_domains.iter().map(|d| hash_lower(d.as_bytes())).collect();
        let mut exc: Vec<u64> = rule.exclude_domains.iter().map(|d| hash_lower(d.as_bytes())).collect();
        inc.sort_unstable();
        inc.dedup();
        exc.sort_unstable();
        exc.dedup();

        let mut k = Fnv::new();
        k.write(&[flags, party]);
        k.write(&rule.type_mask.to_le_bytes());
        k.write(rule.pattern.as_bytes());
        k.write(&[0xff]);
        for d in &inc {
            k.write(&d.to_le_bytes());
        }
        k.write(&[0xfe]);
        for d in &exc {
            k.write(&d.to_le_bytes());
        }
        let key = k.finish();

        if rule.badfilter {
            self.bad.insert(key);
            return;
        }
        if !self.seen.insert(key) {
            self.stats.duplicates += 1;
            return;
        }
        self.set.push(flags, party, rule.type_mask, rule.pattern.as_bytes(), &inc, &exc);
        self.keys.push(key);
    }

    /// Termina: quita lo anulado por `$badfilter` y devuelve las reglas compactadas con sus estadísticas.
    pub fn finish(self) -> (RuleSet, Stats) {
        let (set, stats, _) = self.finish_with_cosmetic();
        (set, stats)
    }

    /// Igual que [`Builder::finish`], más las reglas cosméticas normalizadas (texto, una por línea).
    pub fn finish_with_cosmetic(self) -> (RuleSet, Stats, String) {
        let Builder { set, keys, bad, cosmetic, mut stats, .. } = self;
        let mut out = RuleSet::default();
        for (r, key) in set.rules.iter().zip(&keys) {
            if bad.contains(key) {
                stats.badfilter_removed += 1;
                continue;
            }
            if r.flags & F_EXC != 0 {
                stats.exceptions += 1;
            } else {
                stats.blocks += 1;
            }
            out.copy_rule_from(&set, r);
        }
        (out, stats, cosmetic.into_text())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn dedupes_and_badfilter() {
        let mut b = Builder::new();
        b.add_list("||a.com^\n||A.com^\n||b.com^$script,third-party\n||b.com^$third-party,script\n||c.com^\n||c.com^$badfilter\n! x\nfoo.com##.x\n");
        let (set, st) = b.finish();
        assert_eq!(set.len(), 2);
        assert_eq!(st.duplicates, 2);
        assert_eq!(st.badfilter_removed, 1);
        assert_eq!(st.cosmetic, 1);
    }
}
