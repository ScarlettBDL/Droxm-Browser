//! Benchmark sintético: lista del tamaño de EasyList + EasyPrivacy y pedidos variados.
//! Uso: cargo run --release --example bench
use droxm_adblock_core::*;
use std::time::Instant;

struct Rng(u64);
impl Rng {
    fn next(&mut self) -> u64 {
        self.0 ^= self.0 << 13;
        self.0 ^= self.0 >> 7;
        self.0 ^= self.0 << 17;
        self.0
    }
    fn word(&mut self, n: usize) -> String {
        (0..n).map(|_| (b'a' + (self.next() % 26) as u8) as char).collect()
    }
}

fn main() {
    let mut r = Rng(0x9e3779b97f4a7c15);
    let mut list = String::new();
    for i in 0..60_000 {
        match i % 6 {
            0 | 1 => list.push_str(&format!("||{}.{}.com^\n", r.word(7), r.word(5))),
            2 => list.push_str(&format!("||{}.net^$third-party\n", r.word(9))),
            3 => list.push_str(&format!("/{}/{}/*.js\n", r.word(6), r.word(4))),
            4 => list.push_str(&format!("||{}.org/{}^$script,domain={}.com\n", r.word(6), r.word(5), r.word(6))),
            _ => list.push_str(&format!("@@||{}.com/{}/\n", r.word(6), r.word(5))),
        }
        if i % 5 == 0 {
            list.push_str(&format!("{}.com##.ad-{}\n", r.word(6), r.word(4)));
        }
    }
    list.push_str("||doubleclick.net^\n||ads.example.com^\n/banner/*/ad^\n");

    let t = Instant::now();
    let mut b = Builder::new();
    b.add_list(&list);
    let (set, stats) = b.finish();
    let parse = t.elapsed();

    let t = Instant::now();
    let bytes = set.to_bytes();
    let ser = t.elapsed();
    let t = Instant::now();
    let engine = Engine::from_bytes(&bytes).unwrap();
    let load = t.elapsed();

    println!("lista: {} líneas, {:.1} MB texto", stats.lines, list.len() as f64 / 1e6);
    println!("reglas: {} bloqueo, {} excepciones, {} cosméticas descartadas", stats.blocks, stats.exceptions, stats.cosmetic);
    println!("parseo+dedupe: {:?}   serializar: {:?}   cargar bloque ({:.1} MB): {:?}", parse, ser, bytes.len() as f64 / 1e6, load);

    let src = Some("https://www.news-site.com/article/123");
    let mut urls: Vec<String> = Vec::new();
    for i in 0..4000 {
        urls.push(match i % 8 {
            0 => "https://www.google.com/images/branding/googlelogo/1x/googlelogo_color_272x92dp.png".into(),
            1 => "https://static.news-site.com/assets/app.bundle.8f3a2c.js".into(),
            2 => format!("https://cdn.{}.com/lib/jquery.min.js?v={}", r.word(6), i),
            3 => "https://ads.example.com/pixel.gif?id=1234567890&ref=https%3A%2F%2Fnews-site.com".into(),
            4 => "https://stats.g.doubleclick.net/j/collect?v=1&tid=UA-1&cid=555".into(),
            5 => format!("https://{}.{}.com/x/y/z/some/long/path/segment/file{}.css", r.word(7), r.word(5), i),
            6 => "https://fonts.gstatic.com/s/roboto/v30/KFOmCnqEu92Fr1Mu4mxK.woff2".into(),
            _ => "https://www.news-site.com/api/v2/articles/123/comments?page=2&limit=50".into(),
        });
    }

    let rounds = 250;
    let mut blocked = 0usize;
    for u in urls.iter().take(200) {
        engine.check(u, src, None, None);
    }
    let t = Instant::now();
    for _ in 0..rounds {
        for u in &urls {
            if engine.check(u, src, None, Some("script")).block {
                blocked += 1;
            }
        }
    }
    let el = t.elapsed();
    let n = rounds * urls.len();
    println!(
        "{} pedidos evaluados en {:?}: {:.0} ns/pedido ({:.2} M/s), {} bloqueados",
        n,
        el,
        el.as_nanos() as f64 / n as f64,
        n as f64 / el.as_secs_f64() / 1e6,
        blocked
    );
}
