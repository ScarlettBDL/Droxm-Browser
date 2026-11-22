//! Parser de listas de filtros (sintaxis de Adblock Plus / uBlock Origin, solo reglas de red).
//!
//! Procesa línea por línea sobre cortes del texto original: no hace copias salvo cuando hace falta pasar
//! un patrón a minúsculas. Lo que no soporta lo descarta y lo cuenta, en lugar de interpretarlo mal.

use crate::resources::{ResourceType, ALL_MASK};
use std::borrow::Cow;

/// Por qué se descartó una línea.
#[derive(Debug, PartialEq, Eq, Clone, Copy)]
pub enum Skip {
    Empty,
    Comment,
    /// Regla de ocultamiento de elementos (`##`, `#@#`...): este motor es solo de red.
    Cosmetic,
    /// Expresión regular (`/.../`).
    Regex,
    /// Opciones que no soportamos (`$redirect`, `$csp`, `$popup`...) o regla imposible de cumplir.
    Unsupported,
}

#[derive(Debug, PartialEq, Eq, Clone, Copy)]
pub enum Party {
    Any,
    Third,
    First,
}

#[derive(Debug, Clone)]
pub struct ParsedRule<'a> {
    pub exception: bool,
    pub important: bool,
    pub badfilter: bool,
    pub host_anchor: bool,
    pub start_anchor: bool,
    pub end_anchor: bool,
    /// Patrón normalizado en minúsculas, sin anclas ni comodines sobrantes en los bordes.
    pub pattern: Cow<'a, str>,
    pub party: Party,
    /// Tipos a los que aplica (0 = a todos).
    pub type_mask: u32,
    pub include_domains: Vec<&'a str>,
    pub exclude_domains: Vec<&'a str>,
}

fn is_cosmetic(line: &str) -> bool {
    line.contains("##") || line.contains("#@#") || line.contains("#?#") || line.contains("#$#") || line.contains("#%#")
}

/// Separa `patrón$opciones`. El último `$` solo cuenta como separador si lo que sigue parece una lista de opciones.
fn split_options(rule: &str) -> (&str, Option<&str>) {
    if let Some(i) = rule.rfind('$') {
        let opts = &rule[i + 1..];
        let looks_like_options = !opts.is_empty()
            && opts
                .bytes()
                .all(|c| c.is_ascii_alphanumeric() || matches!(c, b',' | b'=' | b'|' | b'~' | b'-' | b'_' | b'.' | b':' | b'*'));
        if looks_like_options {
            return (&rule[..i], Some(opts));
        }
    }
    (rule, None)
}

pub fn parse_line(raw: &str) -> Result<ParsedRule<'_>, Skip> {
    let line = raw.trim();
    let first = *line.as_bytes().first().ok_or(Skip::Empty)?;
    if first == b'!' || first == b'[' {
        return Err(Skip::Comment);
    }
    if is_cosmetic(line) {
        return Err(Skip::Cosmetic);
    }

    let (exception, body) = match line.strip_prefix("@@") {
        Some(rest) => (true, rest),
        None => (false, line),
    };
    let (pattern_str, options) = split_options(body);

    let mut rule = ParsedRule {
        exception,
        important: false,
        badfilter: false,
        host_anchor: false,
        start_anchor: false,
        end_anchor: false,
        pattern: Cow::Borrowed(""),
        party: Party::Any,
        type_mask: 0,
        include_domains: Vec::new(),
        exclude_domains: Vec::new(),
    };

    if let Some(opts) = options {
        parse_options(opts, &mut rule)?;
    }

    // ----- patrón
    let mut p = pattern_str;
    if p.len() > 2 && p.starts_with('/') && p.ends_with('/') {
        return Err(Skip::Regex);
    }
    if let Some(rest) = p.strip_prefix("||") {
        rule.host_anchor = true;
        p = rest;
    } else if let Some(rest) = p.strip_prefix('|') {
        rule.start_anchor = true;
        p = rest;
    }
    if let Some(rest) = p.strip_suffix('|') {
        rule.end_anchor = true;
        p = rest;
    }
    if !rule.host_anchor && !rule.start_anchor {
        p = p.trim_start_matches('*');
    }
    if !rule.end_anchor {
        p = p.trim_end_matches('*');
    }
    if p.is_empty() || p.len() > 1024 {
        return Err(Skip::Unsupported);
    }
    rule.pattern = if p.bytes().any(|b| b.is_ascii_uppercase()) {
        Cow::Owned(p.to_ascii_lowercase())
    } else {
        Cow::Borrowed(p)
    };
    Ok(rule)
}

fn parse_options<'a>(opts: &'a str, rule: &mut ParsedRule<'a>) -> Result<(), Skip> {
    let mut positive = 0u32;
    let mut negative = 0u32;

    for opt in opts.split(',') {
        let opt = opt.trim();
        if opt.is_empty() {
            continue;
        }
        let (negated, rest) = match opt.strip_prefix('~') {
            Some(r) => (true, r),
            None => (false, opt),
        };
        let (name, value) = match rest.split_once('=') {
            Some((n, v)) => (n, Some(v)),
            None => (rest, None),
        };

        match name {
            "third-party" | "3p" => rule.party = if negated { Party::First } else { Party::Third },
            "first-party" | "1p" => rule.party = if negated { Party::Third } else { Party::First },
            "important" => rule.important = !negated,
            "badfilter" => rule.badfilter = true,
            "match-case" => {} // se ignora: se compara sin distinguir mayúsculas
            "all" => positive |= ALL_MASK,
            "domain" | "from" => {
                let list = value.ok_or(Skip::Unsupported)?;
                for d in list.split('|') {
                    let (neg, d) = match d.strip_prefix('~') {
                        Some(r) => (true, r),
                        None => (false, d),
                    };
                    // Dominios con comodín o expresión regular: no soportados.
                    if d.is_empty() || d.contains('*') || d.starts_with('/') {
                        return Err(Skip::Unsupported);
                    }
                    if neg {
                        rule.exclude_domains.push(d);
                    } else {
                        rule.include_domains.push(d);
                    }
                }
            }
            _ => match ResourceType::from_option(name) {
                Some(t) if value.is_none() => {
                    if negated {
                        negative |= t.bit();
                    } else {
                        positive |= t.bit();
                    }
                }
                // document, popup, redirect, csp, removeparam, generichide, denyallow...: no los soportamos.
                _ => return Err(Skip::Unsupported),
            },
        }
    }

    rule.type_mask = if positive != 0 {
        positive & !negative
    } else if negative != 0 {
        ALL_MASK & !negative
    } else {
        0
    };
    // Si las opciones se anulan entre sí (por ejemplo `$script,~script`), la regla nunca podría aplicar.
    if (positive != 0 || negative != 0) && rule.type_mask == 0 {
        return Err(Skip::Unsupported);
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn skips_comments_and_cosmetic() {
        assert_eq!(parse_line("").unwrap_err(), Skip::Empty);
        assert_eq!(parse_line("! comentario").unwrap_err(), Skip::Comment);
        assert_eq!(parse_line("[Adblock Plus 2.0]").unwrap_err(), Skip::Comment);
        assert_eq!(parse_line("example.com##.banner").unwrap_err(), Skip::Cosmetic);
        assert_eq!(parse_line("##.ad").unwrap_err(), Skip::Cosmetic);
    }

    #[test]
    fn parses_anchors_and_options() {
        let r = parse_line("||Ads.Example.com^$script,third-party,domain=a.com|~b.com").unwrap();
        assert!(r.host_anchor && !r.start_anchor && !r.end_anchor && !r.exception);
        assert_eq!(r.pattern, "ads.example.com^");
        assert_eq!(r.party, Party::Third);
        assert_eq!(r.type_mask, ResourceType::Script.bit());
        assert_eq!(r.include_domains, vec!["a.com"]);
        assert_eq!(r.exclude_domains, vec!["b.com"]);
    }

    #[test]
    fn parses_exception_and_important() {
        let r = parse_line("@@|https://x.com/ok|$important").unwrap();
        assert!(r.exception && r.important && r.start_anchor && r.end_anchor);
        assert_eq!(r.pattern, "https://x.com/ok");
    }

    #[test]
    fn negated_types_become_complement() {
        let r = parse_line("/banner/*$~image").unwrap();
        assert_eq!(r.type_mask, ALL_MASK & !ResourceType::Image.bit());
    }

    #[test]
    fn rejects_unsupported() {
        assert_eq!(parse_line("/ads[0-9]+/").unwrap_err(), Skip::Regex);
        assert_eq!(parse_line("||x.com^$redirect=noop.js").unwrap_err(), Skip::Unsupported);
        assert_eq!(parse_line("||x.com^$popup").unwrap_err(), Skip::Unsupported);
        assert_eq!(parse_line("@@||x.com^$document").unwrap_err(), Skip::Unsupported);
        assert_eq!(parse_line("||x.com^$script,~script").unwrap_err(), Skip::Unsupported);
        assert_eq!(parse_line("||x.com^$domain=a.*").unwrap_err(), Skip::Unsupported);
    }

    #[test]
    fn trims_redundant_wildcards() {
        let r = parse_line("*banner*").unwrap();
        assert_eq!(r.pattern, "banner");
        let r = parse_line("|*banner").unwrap();
        assert!(r.start_anchor);
    }
}
