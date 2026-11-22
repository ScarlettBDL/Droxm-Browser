//! Núcleo del bloqueador de anuncios de Droxm Browser. Sin dependencias: se prueba y mide en cualquier plataforma.
//!
//! Módulos: `parser` (listas), `lists` (unión y deduplicación), `store` (reglas compactas y bloque binario),
//! `engine` (índices y evaluación), `exceptions` (precedencia), `domains` (hosts y terceros),
//! `resources` (clasificación), `pattern` (comparación sin regex), `hash`.

pub mod domains;
pub mod engine;
pub mod exceptions;
pub mod hash;
pub mod lists;
pub mod parser;
pub mod pattern;
pub mod resources;
pub mod store;

pub use engine::{Engine, Verdict};
pub use exceptions::Reason;
pub use lists::{Builder, Stats};
pub use resources::ResourceType;
pub use store::RuleSet;
