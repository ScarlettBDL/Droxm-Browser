//! Motor de excepciones: orden de precedencia entre reglas de bloqueo, `$important` y excepciones (`@@`).
//!
//! 1. Una regla de bloqueo `$important` gana siempre, incluso sobre excepciones.
//! 2. Si no, una excepción gana sobre cualquier bloqueo común.
//! 3. Si no hay excepción, el bloqueo común se aplica.
//! Las excepciones solo se consultan cuando ya hay un bloqueo: el caso común (nada coincide) no las toca.

use crate::engine::{Index, Request};

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Reason {
    NoMatch,
    Blocked,
    Important,
    Allowed,
}

impl Reason {
    pub fn blocks(self) -> bool {
        matches!(self, Reason::Blocked | Reason::Important)
    }
}

pub(crate) fn decide(important: &Index, blocks: &Index, exceptions: &Index, req: &Request) -> Reason {
    if important.matches(req) {
        return Reason::Important;
    }
    if blocks.matches(req) {
        return if exceptions.matches(req) { Reason::Allowed } else { Reason::Blocked };
    }
    Reason::NoMatch
}
