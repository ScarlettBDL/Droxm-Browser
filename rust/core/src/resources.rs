//! Clasificación de recursos: deducir qué tipo de recurso es un pedido (script, imagen, hoja de estilo...).
//!
//! WebView no le dice a `shouldInterceptRequest` el tipo del recurso, así que se deduce con lo que hay:
//! 1. el encabezado `Sec-Fetch-Dest`, si el sistema lo manda;
//! 2. el encabezado `Accept` (los navegadores piden distinto según el tipo);
//! 3. la extensión del archivo en la URL.
//! Si nada alcanza, el pedido queda como "ambiguo" (script, XHR u otro) y se prueban las reglas de esos tipos.

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
#[repr(u8)]
pub enum ResourceType {
    Other = 0,
    Document = 1,
    Subdocument = 2,
    Script = 3,
    Stylesheet = 4,
    Image = 5,
    Xhr = 6,
    Media = 7,
    Font = 8,
    Ping = 9,
    WebSocket = 10,
}

/// Máscara con todos los tipos.
pub const ALL_MASK: u32 = (1 << 11) - 1;

impl ResourceType {
    #[inline]
    pub const fn bit(self) -> u32 {
        1 << (self as u32)
    }

    pub fn from_code(code: u8) -> ResourceType {
        match code {
            1 => ResourceType::Document,
            2 => ResourceType::Subdocument,
            3 => ResourceType::Script,
            4 => ResourceType::Stylesheet,
            5 => ResourceType::Image,
            6 => ResourceType::Xhr,
            7 => ResourceType::Media,
            8 => ResourceType::Font,
            9 => ResourceType::Ping,
            10 => ResourceType::WebSocket,
            _ => ResourceType::Other,
        }
    }

    /// Nombre de la opción de una regla (`$script`, `$image`...) → tipo.
    pub fn from_option(name: &str) -> Option<ResourceType> {
        Some(match name {
            "script" => ResourceType::Script,
            "image" => ResourceType::Image,
            "stylesheet" | "css" => ResourceType::Stylesheet,
            "subdocument" | "frame" => ResourceType::Subdocument,
            "xmlhttprequest" | "xhr" => ResourceType::Xhr,
            "font" => ResourceType::Font,
            "media" => ResourceType::Media,
            "ping" | "beacon" => ResourceType::Ping,
            "websocket" => ResourceType::WebSocket,
            "other" | "object" | "object-subrequest" => ResourceType::Other,
            _ => return None,
        })
    }
}

/// Resultado de clasificar: el tipo más probable y el conjunto de tipos con los que se evalúan las reglas.
#[derive(Clone, Copy, Debug)]
pub struct Class {
    pub primary: ResourceType,
    pub mask: u32,
}

fn single(t: ResourceType) -> Class {
    Class { primary: t, mask: t.bit() }
}

pub fn classify(url: &str, accept: Option<&str>, dest: Option<&str>) -> Class {
    if let Some(c) = dest.and_then(from_dest) {
        return c;
    }
    if let Some(c) = accept.and_then(from_accept) {
        return c;
    }
    if let Some(t) = from_extension(url) {
        return single(t);
    }
    // Sin pistas (típico de `Accept: */*`): puede ser un script, un XHR o cualquier otra cosa.
    Class {
        primary: ResourceType::Other,
        mask: ResourceType::Script.bit() | ResourceType::Xhr.bit() | ResourceType::Other.bit(),
    }
}

fn from_dest(dest: &str) -> Option<Class> {
    let d = dest.trim();
    let eq = |s: &str| d.eq_ignore_ascii_case(s);
    Some(if eq("script") || eq("worker") || eq("sharedworker") || eq("serviceworker") || eq("audioworklet") || eq("paintworklet") {
        single(ResourceType::Script)
    } else if eq("style") || eq("xslt") {
        single(ResourceType::Stylesheet)
    } else if eq("image") {
        single(ResourceType::Image)
    } else if eq("font") {
        single(ResourceType::Font)
    } else if eq("audio") || eq("video") || eq("track") {
        single(ResourceType::Media)
    } else if eq("iframe") || eq("frame") {
        single(ResourceType::Subdocument)
    } else if eq("document") {
        single(ResourceType::Document)
    } else if eq("websocket") {
        single(ResourceType::WebSocket)
    } else if eq("empty") {
        // fetch(), XHR, sendBeacon...
        Class {
            primary: ResourceType::Xhr,
            mask: ResourceType::Xhr.bit() | ResourceType::Ping.bit() | ResourceType::Other.bit(),
        }
    } else if eq("embed") || eq("object") || eq("manifest") || eq("report") {
        single(ResourceType::Other)
    } else {
        return None;
    })
}

fn starts_with_ci(haystack: &str, needle: &str) -> bool {
    haystack.len() >= needle.len() && haystack.as_bytes()[..needle.len()].eq_ignore_ascii_case(needle.as_bytes())
}

fn contains_ci(haystack: &str, needle: &str) -> bool {
    let n = needle.as_bytes();
    haystack.as_bytes().windows(n.len()).any(|w| w.eq_ignore_ascii_case(n))
}

fn from_accept(accept: &str) -> Option<Class> {
    let a = accept.trim();
    if starts_with_ci(a, "text/html") || contains_ci(a, "application/xhtml") {
        // Los pedidos de página principal no llegan acá, así que un HTML es un iframe.
        Some(single(ResourceType::Subdocument))
    } else if starts_with_ci(a, "image/") {
        Some(single(ResourceType::Image))
    } else if starts_with_ci(a, "text/css") {
        Some(single(ResourceType::Stylesheet))
    } else if starts_with_ci(a, "application/javascript")
        || starts_with_ci(a, "text/javascript")
        || starts_with_ci(a, "application/x-javascript")
    {
        Some(single(ResourceType::Script))
    } else if starts_with_ci(a, "audio/") || starts_with_ci(a, "video/") {
        Some(single(ResourceType::Media))
    } else if starts_with_ci(a, "font/") {
        Some(single(ResourceType::Font))
    } else if contains_ci(a, "application/json") {
        Some(Class {
            primary: ResourceType::Xhr,
            mask: ResourceType::Xhr.bit() | ResourceType::Other.bit(),
        })
    } else {
        None
    }
}

fn from_extension(url: &str) -> Option<ResourceType> {
    let end = url.find(['?', '#']).unwrap_or(url.len());
    let path = &url[..end];
    let name = &path[path.rfind('/').map_or(0, |i| i + 1)..];
    let ext = &name[name.rfind('.')? + 1..];
    let eq = |s: &str| ext.eq_ignore_ascii_case(s);
    Some(if eq("js") || eq("mjs") {
        ResourceType::Script
    } else if eq("css") {
        ResourceType::Stylesheet
    } else if eq("png") || eq("jpg") || eq("jpeg") || eq("gif") || eq("webp") || eq("svg") || eq("ico") || eq("avif") || eq("bmp") {
        ResourceType::Image
    } else if eq("woff") || eq("woff2") || eq("ttf") || eq("otf") || eq("eot") {
        ResourceType::Font
    } else if eq("mp4") || eq("webm") || eq("mp3") || eq("ogg") || eq("m4a") || eq("m3u8") || eq("ts") || eq("wav") {
        ResourceType::Media
    } else if eq("html") || eq("htm") {
        ResourceType::Subdocument
    } else if eq("json") {
        ResourceType::Xhr
    } else {
        return None;
    })
}
