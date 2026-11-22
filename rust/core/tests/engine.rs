use droxm_adblock_core::*;

fn engine(list: &str) -> Engine {
    let mut b = Builder::new();
    b.add_list(list);
    let (set, _) = b.finish();
    Engine::from_bytes(&set.to_bytes()).unwrap()
}

fn blocked(e: &Engine, url: &str, src: Option<&str>, accept: Option<&str>, dest: Option<&str>) -> bool {
    e.check(url, src, accept, dest).block
}

const SRC: Option<&str> = Some("https://news.example.org/article");

#[test]
fn host_rules_and_subdomains() {
    let e = engine("||ads.example.com^\n||tracker.net^$third-party\n");
    assert!(blocked(&e, "https://ads.example.com/x.js", SRC, None, None));
    assert!(blocked(&e, "https://cdn.ads.example.com/x.js", SRC, None, None));
    assert!(blocked(&e, "https://ADS.Example.COM:8080/x", SRC, None, None));
    assert!(!blocked(&e, "https://notads.example.com/x", SRC, None, None));
    assert!(!blocked(&e, "https://ads.example.com.evil.org/x", SRC, None, None));
    assert!(blocked(&e, "https://tracker.net/p", SRC, None, None));
    // mismo sitio: la regla es solo de terceros
    assert!(!blocked(&e, "https://tracker.net/p", Some("https://www.tracker.net/"), None, None));
}

#[test]
fn path_patterns_and_separators() {
    let e = engine("/banner/*/img^\n||site.com/ads/*.gif\n|https://x.com/exact|\n");
    assert!(blocked(&e, "https://a.com/banner/300/img?x=1", SRC, None, None));
    assert!(!blocked(&e, "https://a.com/banner/300/imgs", SRC, None, None));
    assert!(blocked(&e, "https://www.site.com/ads/one/b.gif", SRC, None, None));
    assert!(!blocked(&e, "https://other.com/ads/one/b.gif", SRC, None, None));
    assert!(blocked(&e, "https://x.com/exact", SRC, None, None));
    assert!(!blocked(&e, "https://x.com/exact/more", SRC, None, None));
}

#[test]
fn exceptions_and_important() {
    let e = engine("||ads.com^\n@@||ads.com/allowed/\n||bad.com^$important\n@@||bad.com^\n");
    assert!(blocked(&e, "https://ads.com/x", SRC, None, None));
    let v = e.check("https://ads.com/allowed/y", SRC, None, None);
    assert!(!v.block && v.reason == Reason::Allowed);
    assert!(blocked(&e, "https://bad.com/x", SRC, None, None));
}

#[test]
fn types_and_domain_option() {
    let e = engine("||cdn.com^$script\n||img.com^$~image\n||only.com^$domain=news.example.org|~sub.news.example.org\n");
    assert!(blocked(&e, "https://cdn.com/a.js", SRC, None, None));
    assert!(!blocked(&e, "https://cdn.com/a.png", SRC, None, None));
    assert!(blocked(&e, "https://cdn.com/api", SRC, None, Some("script")));
    assert!(!blocked(&e, "https://img.com/a.png", SRC, None, None));
    assert!(blocked(&e, "https://img.com/a.js", SRC, None, None));
    assert!(blocked(&e, "https://only.com/x", SRC, None, None));
    assert!(!blocked(&e, "https://only.com/x", Some("https://other.org/"), None, None));
    assert!(!blocked(&e, "https://only.com/x", Some("https://sub.news.example.org/"), None, None));
}

#[test]
fn classification_signals() {
    let e = engine("||t.com^$xhr\n");
    assert!(blocked(&e, "https://t.com/collect", SRC, None, Some("empty")));
    assert!(blocked(&e, "https://t.com/collect", SRC, Some("application/json, */*"), None));
    assert!(!blocked(&e, "https://t.com/p.png", SRC, None, None));
    assert_eq!(e.check("https://t.com/p.png", SRC, None, None).resource, ResourceType::Image);
}

#[test]
fn non_http_is_untouched() {
    let e = engine("||ads.com^\n*\n");
    assert!(!blocked(&e, "data:text/plain,hi", SRC, None, None));
    assert!(!blocked(&e, "about:blank", None, None, None));
}

#[test]
fn badfilter_and_unsupported_lines_do_not_block() {
    let e = engine("||a.com^\n||a.com^$badfilter\n||b.com^$redirect=x\n/ads[0-9]/\nsite.com##.ad\n");
    assert!(!blocked(&e, "https://a.com/x", SRC, None, None));
    assert!(!blocked(&e, "https://b.com/x", SRC, None, None));
    assert_eq!(e.rule_count(), 0);
}

#[test]
fn separator_matches_end_of_url() {
    let e = engine("||x.com/track^\n");
    assert!(blocked(&e, "https://x.com/track", SRC, None, None));
    assert!(blocked(&e, "https://x.com/track?a=1", SRC, None, None));
    assert!(!blocked(&e, "https://x.com/tracker", SRC, None, None));
}
