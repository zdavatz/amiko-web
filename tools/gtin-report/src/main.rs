// Copyright (C) 2026 Zeno R.R. Davatz - GPL-3.0, siehe LICENSE im Repo.
//
// Liest Apache-Logs (other_vhosts_access.log, auch .gz) von amiko.zurrose.ch
// und schreibt ein PDF mit zwei Abschnitten:
//
//   1. Fachinformationen: welche Praeparate (GTIN) wie oft geoeffnet wurden,
//      Namen aus Refdata.Articles.xml. Gezaehlt werden /de/fi/gtin/<GTIN>
//      (Barcode, Direktlink) und /de/fi?gtin=<GTIN> (aus der Suche heraus).
//   2. Nutzer: jede IP-Adresse mit aktiven Tagen, Geraet und Aktionen
//      (Fachinfos, Suchen, uebrige Seiten). Skripte, Styles, Bilder zaehlen nicht.
//      Die Suche feuert bei jedem Buchstaben und an mehrere Endpunkte zugleich
//      ("ibu", "ibupr" an Name, Volltext, Inhaberin ...). Als eine Suche zaehlt
//      deshalb pro Nutzer nur der laengste Begriff, dessen Vorstufen wegfallen.
//
// Aufruf:
//   gtin-report --refdata Refdata.Articles.xml --since 2026-09-29 --until 2026-10-19 \
//               --out abrufe.pdf [--host amiko.zurrose.ch] [--geoip dbip.mmdb] [--nur-ch] LOG...
//
// --geoip (DB-IP Country Lite, abgefragt ueber das Kommando `mmdblookup`) ordnet
// jeder IP ein Land zu. --nur-ch wertet ausschliesslich Schweizer IPs aus.
// Neben dem PDF entsteht ein CSV mit den Fachinfo-Zeilen inklusive IP-Liste.

use anyhow::{anyhow, bail, Context, Result};
use flate2::read::GzDecoder;
use genpdf::{elements, fonts, style, Alignment, Document, Element, Margins, SimplePageDecorator};
use std::collections::{BTreeMap, BTreeSet, HashMap};
use std::fs::File;
use std::io::{BufRead, BufReader, Read};
use std::path::{Path, PathBuf};
use std::process::Command;

struct Args {
    refdata: PathBuf,
    since: u32,
    until: u32,
    out: PathBuf,
    host: String,
    geoip: Option<PathBuf>,
    nur_ch: bool,
    logs: Vec<PathBuf>,
}

struct Artikel {
    name_de: String,
    inhaberin: String,
}

#[derive(Default)]
struct Treffer {
    abrufe: u32,
    ips: BTreeSet<String>,
}

struct FiZeile {
    gtin: String,
    name: String,
    inhaberin: String,
    t: Treffer,
}

#[derive(Default)]
struct Nutzer {
    tage: BTreeSet<u32>,
    fachinfos: u32,
    seiten: u32,
    geraet: String,
    land: String,
    begriffe: BTreeSet<String>,
}

impl Nutzer {
    /// Zahl der echten Suchen: Begriffe, die nicht Vorstufe eines laengeren sind.
    fn suchen(&self) -> u32 {
        self.begriffe
            .iter()
            .filter(|b| !self.begriffe.iter().any(|o| o.len() > b.len() && o.starts_with(b.as_str())))
            .count() as u32
    }
}

struct Logzeile<'a> {
    ip: &'a str,
    tag: u32,
    pfad: &'a str,
    status: &'a str,
    ua: &'a str,
}

// Farben wie in den anderen genpdf-Blaettern: Titel bordeaux, Kopf dunkelblau.
const BORDEAUX: style::Color = style::Color::Rgb(128, 0, 32);
const BLAU: style::Color = style::Color::Rgb(0, 51, 102);
const GRUEN: style::Color = style::Color::Rgb(0, 110, 50);
const GRAU: style::Color = style::Color::Rgb(140, 140, 140);

fn main() -> Result<()> {
    let args = parse_args()?;
    let refdata = lies_refdata(&args.refdata)?;
    eprintln!("Refdata: {} Packungen mit GTIN", refdata.len());

    let mut fi: HashMap<String, Treffer> = HashMap::new();
    let mut nutzer: BTreeMap<String, Nutzer> = BTreeMap::new();
    let mut laender: HashMap<String, String> = HashMap::new();
    let mut gelesen = 0usize;
    for log in &args.logs {
        for line in oeffne(log)?.lines() {
            let line = line?;
            gelesen += 1;
            let Some(z) = parse_zeile(&line, &args.host, args.since, args.until) else { continue };
            if z.status != "200" || technisch(z.pfad) {
                continue;
            }
            let land = match &args.geoip {
                Some(db) => laender.entry(z.ip.to_string()).or_insert_with(|| land(db, z.ip)).clone(),
                None => String::new(),
            };
            if args.nur_ch && land != "CH" {
                continue;
            }
            let n = nutzer.entry(z.ip.to_string()).or_default();
            n.tage.insert(z.tag);
            n.land = land;
            if n.geraet.is_empty() {
                n.geraet = geraet(z.ua).to_string();
            }
            if let Some(g) = gtin_aus_pfad(z.pfad) {
                n.fachinfos += 1;
                let t = fi.entry(g).or_default();
                t.abrufe += 1;
                t.ips.insert(z.ip.to_string());
            } else if let Some(begriff) = suchbegriff(z.pfad) {
                if !begriff.is_empty() {
                    n.begriffe.insert(begriff);
                }
            } else {
                n.seiten += 1;
            }
        }
    }
    eprintln!(
        "{} Logzeilen, {} Nutzer, {} Fachinfo-Abrufe zu {} GTINs",
        gelesen,
        nutzer.len(),
        fi.values().map(|t| t.abrufe).sum::<u32>(),
        fi.len()
    );

    let mut fi_zeilen: Vec<FiZeile> = fi
        .into_iter()
        .map(|(gtin, t)| match refdata.get(&gtin) {
            Some(a) => FiZeile { gtin, name: a.name_de.clone(), inhaberin: a.inhaberin.clone(), t },
            None => FiZeile { gtin, name: "(nicht in Refdata)".into(), inhaberin: String::new(), t },
        })
        .collect();
    fi_zeilen.sort_by(|a, b| b.t.abrufe.cmp(&a.t.abrufe).then_with(|| a.name.cmp(&b.name)));

    schreibe_csv(&args.out.with_extension("csv"), &fi_zeilen)?;
    schreibe_pdf(&args, &fi_zeilen, &nutzer)?;
    eprintln!("geschrieben: {}", args.out.display());
    Ok(())
}

fn parse_args() -> Result<Args> {
    let mut refdata = None;
    let mut since = None;
    let mut until = None;
    let mut out = None;
    let mut host = "amiko.zurrose.ch".to_string();
    let mut geoip = None;
    let mut nur_ch = false;
    let mut logs = Vec::new();
    let mut it = std::env::args().skip(1);
    while let Some(a) = it.next() {
        let mut wert = || it.next().ok_or_else(|| anyhow!("Wert fehlt nach {}", a));
        match a.as_str() {
            "--refdata" => refdata = Some(PathBuf::from(wert()?)),
            "--since" => since = Some(datum(&wert()?)?),
            "--until" => until = Some(datum(&wert()?)?),
            "--out" => out = Some(PathBuf::from(wert()?)),
            "--host" => host = wert()?,
            "--geoip" => geoip = Some(PathBuf::from(wert()?)),
            "--nur-ch" => nur_ch = true,
            _ if a.starts_with("--") => bail!("unbekannte Option {}", a),
            _ => logs.push(PathBuf::from(a)),
        }
    }
    if logs.is_empty() {
        bail!("keine Logdateien angegeben");
    }
    if nur_ch && geoip.is_none() {
        bail!("--nur-ch braucht --geoip");
    }
    Ok(Args {
        refdata: refdata.ok_or_else(|| anyhow!("--refdata fehlt"))?,
        since: since.unwrap_or(0),
        until: until.unwrap_or(99_999_999),
        out: out.ok_or_else(|| anyhow!("--out fehlt"))?,
        host,
        geoip,
        nur_ch,
        logs,
    })
}

/// "2026-09-29" -> 20260929
fn datum(s: &str) -> Result<u32> {
    let t: Vec<&str> = s.split('-').collect();
    if t.len() != 3 {
        bail!("Datum {} nicht im Format JJJJ-MM-TT", s);
    }
    Ok(t[0].parse::<u32>()? * 10_000 + t[1].parse::<u32>()? * 100 + t[2].parse::<u32>()?)
}

fn datum_lesbar(d: u32) -> String {
    format!("{}.{}.{}", d % 100, (d / 100) % 100, d / 10_000)
}

fn oeffne(p: &Path) -> Result<Box<dyn BufRead>> {
    let f = File::open(p).with_context(|| format!("oeffne {}", p.display()))?;
    let r: Box<dyn Read> = if p.extension().map_or(false, |e| e == "gz") {
        Box::new(GzDecoder::new(f))
    } else {
        Box::new(f)
    };
    Ok(Box::new(BufReader::new(r)))
}

/// Apache other_vhosts-Format:
/// host:port ip - - [dd/Mon/yyyy:hh:mm:ss +zone] "GET /pfad HTTP/1.1" 200 bytes "referer" "user-agent"
fn parse_zeile<'a>(line: &'a str, host: &str, since: u32, until: u32) -> Option<Logzeile<'a>> {
    let mut f = line.split_whitespace();
    if f.next()?.split(':').next()? != host {
        return None;
    }
    let ip = f.next()?;
    f.next()?;
    f.next()?;
    let tag = logdatum(f.next()?.trim_start_matches('['))?;
    if tag < since || tag > until {
        return None;
    }
    f.next()?; // zone
    f.next()?; // "GET
    let pfad = f.next()?;
    f.next()?; // HTTP/1.1"
    let status = f.next()?;
    // User-Agent ist der letzte Bereich in Anfuehrungszeichen.
    let ua = line.rsplit('"').nth(1).unwrap_or("");
    Some(Logzeile { ip, tag, pfad, status, ua })
}

/// "12/Oct/2026:00:11:05" -> 20261012
fn logdatum(ts: &str) -> Option<u32> {
    let t: Vec<&str> = ts.split(':').next()?.split('/').collect();
    if t.len() != 3 {
        return None;
    }
    const MONATE: [&str; 12] = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];
    let m = MONATE.iter().position(|x| *x == t[1])? as u32 + 1;
    Some(t[2].parse::<u32>().ok()? * 10_000 + m * 100 + t[0].parse::<u32>().ok()?)
}

/// Skripte, Styles, Bilder und Routing-Tabellen, die der Browser von sich aus laedt.
fn technisch(pfad: &str) -> bool {
    pfad.starts_with("/assets/")
        || pfad.starts_with("/javascriptRoutes")
        || pfad.starts_with("/favicon")
        || pfad.starts_with("/robots.txt")
        || pfad.starts_with("/de/interactions/null")
        || pfad.starts_with("/fr/interactions/null")
}

/// GTIN aus /de/fi/gtin/<GTIN> oder /de/fi?gtin=<GTIN>&...
fn gtin_aus_pfad(pfad: &str) -> Option<String> {
    let rest = if let Some(i) = pfad.find("/gtin/") {
        &pfad[i + 6..]
    } else if let Some(i) = pfad.find("?gtin=").or_else(|| pfad.find("&gtin=")) {
        &pfad[i + 6..]
    } else {
        return None;
    };
    let gtin: String = rest.chars().take_while(|c| c.is_ascii_digit()).collect();
    if gtin.len() >= 8 {
        Some(gtin)
    } else {
        None
    }
}

/// Suchendpunkte der Web-App; liefert den getippten Begriff (leer, wenn der
/// Endpunkt keinen Klartext traegt, etwa fulltext_hash).
fn suchbegriff(pfad: &str) -> Option<String> {
    let (weg, query) = pfad.split_once('?').unwrap_or((pfad, ""));
    let weg = weg.trim_start_matches("/de").trim_start_matches("/fr");
    let param = match weg {
        "/name" => "name",
        "/fulltext" => "key",
        "/regnr" => "regnr",
        "/regnrs" => "regnrs",
        "/owner" => "owner",
        "/atc" => "atc",
        "/therapy" => "therapy",
        "/fulltext_hash" => return Some(String::new()),
        _ => return None,
    };
    let wert = query.split('&').find_map(|kv| kv.strip_prefix(&format!("{}=", param))).unwrap_or("");
    Some(url_decode(wert).trim().to_lowercase())
}

fn url_decode(s: &str) -> String {
    let b = s.as_bytes();
    let mut out = Vec::with_capacity(b.len());
    let mut i = 0;
    while i < b.len() {
        match b[i] {
            b'+' => out.push(b' '),
            b'%' if i + 2 < b.len() => {
                match u8::from_str_radix(std::str::from_utf8(&b[i + 1..i + 3]).unwrap_or("zz"), 16) {
                    Ok(v) => {
                        out.push(v);
                        i += 2;
                    }
                    Err(_) => out.push(b'%'),
                }
            }
            c => out.push(c),
        }
        i += 1;
    }
    String::from_utf8_lossy(&out).into_owned()
}

fn geraet(ua: &str) -> &'static str {
    if ua.contains("iPhone") || ua.contains("iPad") {
        "iPhone/iPad"
    } else if ua.contains("Android") {
        "Android"
    } else if ua.contains("Macintosh") {
        "Mac"
    } else if ua.contains("Windows") {
        "Windows"
    } else if ua.contains("Linux") {
        "Linux"
    } else if ua.contains("bot") || ua.contains("Bot") || ua.contains("crawl") || ua.contains("compatible;") {
        "Crawler"
    } else {
        "unbekannt"
    }
}

/// Laendercode ueber `mmdblookup` (libmaxminddb); "??" wenn unbekannt.
fn land(db: &Path, ip: &str) -> String {
    let out = Command::new("mmdblookup").arg("--file").arg(db).args(["--ip", ip, "country", "iso_code"]).output();
    match out {
        Ok(o) if o.status.success() => {
            let s = String::from_utf8_lossy(&o.stdout);
            s.split('"').nth(1).map(|c| c.to_string()).unwrap_or_else(|| "??".into())
        }
        _ => "??".into(),
    }
}

/// Refdata.Articles.xml ist zeilenweise formatiert, ein Element pro Zeile. Pro
/// <PackagedProduct> werden DataCarrierIdentifier (GTIN), der deutsche FullName
/// und der Name der Zulassungsinhaberin eingesammelt.
fn lies_refdata(p: &Path) -> Result<HashMap<String, Artikel>> {
    let mut map = HashMap::new();
    let mut in_packung = false;
    let mut in_holder = false;
    let mut sprache = String::new();
    let mut gtin = String::new();
    let mut name_de = String::new();
    let mut inhaberin = String::new();
    for line in oeffne(p)?.lines() {
        let line = line?;
        let t = line.trim();
        if t == "<PackagedProduct>" {
            in_packung = true;
            gtin.clear();
            name_de.clear();
            inhaberin.clear();
        } else if t == "</PackagedProduct>" {
            if !gtin.is_empty() {
                map.insert(gtin.clone(), Artikel { name_de: name_de.clone(), inhaberin: inhaberin.clone() });
            }
            in_packung = false;
        } else if in_packung {
            if t == "<Holder>" {
                in_holder = true;
            } else if t == "</Holder>" {
                in_holder = false;
            } else if let Some(v) = inhalt(t, "DataCarrierIdentifier") {
                gtin = v;
            } else if let Some(v) = inhalt(t, "Language") {
                sprache = v;
            } else if let Some(v) = inhalt(t, "FullName") {
                if sprache == "DE" && name_de.is_empty() {
                    name_de = entitaeten(&v);
                }
            } else if let Some(v) = inhalt(t, "Name") {
                if in_holder {
                    inhaberin = entitaeten(&v);
                }
            }
        }
    }
    if map.is_empty() {
        bail!("keine Packungen in {} gefunden", p.display());
    }
    Ok(map)
}

fn inhalt(t: &str, tag: &str) -> Option<String> {
    let a = format!("<{}>", tag);
    let z = format!("</{}>", tag);
    if t.starts_with(&a) && t.ends_with(&z) {
        Some(t[a.len()..t.len() - z.len()].to_string())
    } else {
        None
    }
}

fn entitaeten(s: &str) -> String {
    s.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'")
}

fn schreibe_csv(p: &Path, zeilen: &[FiZeile]) -> Result<()> {
    let mut s = String::from("gtin;praeparat;zulassungsinhaberin;abrufe;ips\n");
    for z in zeilen {
        let ips: Vec<&str> = z.t.ips.iter().map(|x| x.as_str()).collect();
        s.push_str(&format!("{};{};{};{};{}\n", z.gtin, z.name, z.inhaberin, z.t.abrufe, ips.join(",")));
    }
    std::fs::write(p, s).with_context(|| format!("schreibe {}", p.display()))
}

fn zeitraum(args: &Args) -> String {
    match (args.since, args.until) {
        (0, 99_999_999) => "gesamter Logbestand".into(),
        (s, 99_999_999) => format!("ab {}", datum_lesbar(s)),
        (0, u) => format!("bis {}", datum_lesbar(u)),
        (s, u) => format!("{} bis {}", datum_lesbar(s), datum_lesbar(u)),
    }
}

// ---------------------------------------------------------------------------
// PDF. genpdf 0.2 wiederholt Tabellenkoepfe nicht ueber Seitengrenzen. Deshalb
// schaetzt `Seitenplaner` die Zeilenhoehe, teilt jede Tabelle in Portionen und
// gibt jeder Portion ihren eigenen Kopf. Schaetzung: 9 pt DejaVu Sans, mittlere
// Zeichenbreite 1.8 mm, Zeilenhoehe 3.8 mm plus 1.8 mm Zellrand.

const NUTZBREITE: f64 = 180.0; // A4 minus 15 mm links und rechts
const SEITENHOEHE: f64 = 297.0 - 12.0 - 15.0 - 6.0; // Raender und Kopfzeile

struct Spalte {
    titel: &'static str,
    breite: usize,
    rechts: bool,
    stil: style::Style,
}

struct Seitenplaner {
    verbleibend: f64,
}

impl Seitenplaner {
    fn zeilen_in(text: &str, breite_mm: f64) -> usize {
        let pro_zeile = (breite_mm / 1.8).max(1.0) as usize;
        text.split_whitespace()
            .fold((1usize, 0usize), |(zeilen, belegt), wort| {
                let w = wort.chars().count();
                if belegt == 0 {
                    (zeilen, w)
                } else if belegt + 1 + w <= pro_zeile {
                    (zeilen, belegt + 1 + w)
                } else {
                    (zeilen + 1, w)
                }
            })
            .0
    }

    /// Stellt sicher, dass `h` mm auf der Seite frei sind, und verbucht
    /// `belegt` mm davon als verbraucht (Titel, Hinweis, Abstand).
    fn brauche(&mut self, doc: &mut Document, h: f64) {
        if h > self.verbleibend {
            doc.push(elements::PageBreak::new());
            self.verbleibend = SEITENHOEHE;
        }
        self.verbleibend -= 22.0;
    }

    /// Schreibt eine Tabelle; `zellen` liefert pro Zeile Text und Stil je Spalte.
    fn tabelle(
        &mut self,
        doc: &mut Document,
        spalten: &[Spalte],
        zeilen: &[Vec<(String, style::Style)>],
    ) -> Result<()> {
        let summe: usize = spalten.iter().map(|s| s.breite).sum();
        let mm = |s: &Spalte| NUTZBREITE * s.breite as f64 / summe as f64 - 2.0;
        let zelle = |t: &str, st: style::Style, rechts: bool| {
            let p = elements::Paragraph::new(t);
            let p = if rechts { p.aligned(Alignment::Right) } else { p };
            p.styled(st).padded(1)
        };
        let mut aktuell: Option<elements::TableLayout> = None;
        for zeile in zeilen {
            let n = zeile
                .iter()
                .zip(spalten)
                .map(|((t, _), s)| Self::zeilen_in(t, mm(s)))
                .max()
                .unwrap_or(1);
            let h = n as f64 * 3.8 + 1.8;
            if aktuell.is_none() || h > self.verbleibend {
                if let Some(t) = aktuell.take() {
                    doc.push(t);
                    doc.push(elements::PageBreak::new());
                    self.verbleibend = SEITENHOEHE;
                }
                let mut t = elements::TableLayout::new(spalten.iter().map(|s| s.breite).collect());
                t.set_cell_decorator(elements::FrameCellDecorator::new(true, true, false));
                let mut r = t.row();
                for s in spalten {
                    r = r.element(zelle(s.titel, s.stil.clone().bold().with_font_size(8), s.rechts));
                }
                r.push()?;
                self.verbleibend -= 7.0;
                aktuell = Some(t);
            }
            let t = aktuell.as_mut().unwrap();
            let mut r = t.row();
            for ((text, st), s) in zeile.iter().zip(spalten) {
                r = r.element(zelle(text, st.clone(), s.rechts));
            }
            r.push()?;
            self.verbleibend -= h;
        }
        if let Some(t) = aktuell {
            doc.push(t);
        }
        Ok(())
    }
}

fn schreibe_pdf(args: &Args, fi: &[FiZeile], nutzer: &BTreeMap<String, Nutzer>) -> Result<()> {
    let font_dir = Path::new(env!("CARGO_MANIFEST_DIR")).join("fonts");
    let lade = |datei: &str| -> Result<fonts::FontData> {
        let pfad = font_dir.join(datei);
        let data = std::fs::read(&pfad).with_context(|| format!("lese Schrift {}", pfad.display()))?;
        fonts::FontData::new(data, None).map_err(|e| anyhow!("Schrift {}: {}", datei, e))
    };
    let family = fonts::FontFamily {
        regular: lade("DejaVuSans.ttf")?,
        bold: lade("DejaVuSans-Bold.ttf")?,
        italic: lade("DejaVuSans-Oblique.ttf")?,
        bold_italic: lade("DejaVuSans-BoldOblique.ttf")?,
    };
    let mut doc = Document::new(family);
    let wo = if args.nur_ch { "aus der Schweiz" } else { "weltweit" };
    doc.set_title(format!("Nutzung von {} {}", args.host, wo));
    doc.set_font_size(9);
    let mut deco = SimplePageDecorator::new();
    deco.set_margins(Margins::trbl(12, 15, 15, 15));
    let kopfzeile = format!("Nutzung von {} {}, {}", args.host, wo, zeitraum(args));
    deco.set_header(move |seite| {
        let mut p = elements::Paragraph::default();
        p.push_styled(format!("{}   Seite {}", kopfzeile, seite), style::Style::new().with_font_size(8).with_color(GRAU));
        p.aligned(Alignment::Right).padded(Margins::trbl(0, 0, 4, 0))
    });
    doc.set_page_decorator(deco);

    let normal = style::Style::new();
    let grau = style::Style::new().with_color(GRAU);
    let gruen = style::Style::new().bold().with_color(GRUEN);
    let blau = style::Style::new().with_color(BLAU);
    let akzent = if args.nur_ch { GRUEN } else { BLAU };
    let titel = |t: String| elements::Paragraph::new(t).styled(style::Style::new().bold().with_font_size(13).with_color(BORDEAUX));

    // Kennzahlen
    let fi_abrufe: u32 = fi.iter().map(|z| z.t.abrufe).sum();
    let suchen: u32 = nutzer.values().map(|n| n.suchen()).sum();
    let seiten: u32 = nutzer.values().map(|n| n.seiten).sum();
    let tage: BTreeSet<u32> = nutzer.values().flat_map(|n| n.tage.iter().copied()).collect();
    let wiederkehrer = nutzer.values().filter(|n| n.tage.len() >= 2).count();

    let mut plan = Seitenplaner { verbleibend: SEITENHOEHE };

    doc.push(
        elements::Paragraph::new(format!("Nutzung von {} {}", args.host, wo))
            .styled(style::Style::new().bold().with_font_size(15).with_color(BORDEAUX)),
    );
    doc.push(elements::Paragraph::new(format!("Zeitraum {}", zeitraum(args))).styled(grau.clone()));
    doc.push(elements::Break::new(0.5));
    let mut p = elements::Paragraph::default();
    p.push_styled(
        format!(
            "{} Nutzer (IP-Adressen) an {} Tagen, davon {} an mehreren Tagen aktiv.",
            nutzer.len(),
            tage.len(),
            wiederkehrer
        ),
        style::Style::new().bold().with_color(akzent),
    );
    doc.push(p);
    let mut p = elements::Paragraph::default();
    p.push_styled(
        format!(
            "{} Fachinformationen geöffnet ({} verschiedene Präparate), {} Suchanfragen, {} weitere Seitenaufrufe.",
            fi_abrufe,
            fi.len(),
            suchen,
            seiten
        ),
        style::Style::new().bold().with_color(akzent),
    );
    doc.push(p);
    doc.push(
        elements::Paragraph::new(
            "Quelle: Apache-Zugriffslogs, nur erfolgreiche Antworten (HTTP 200). Skripte, Styles und Bilder, die der Browser \
             automatisch nachlädt, sind nicht gezählt. Eine Suche zählt einmal, auch wenn sie beim Tippen mehrere Anfragen auslöst. \
             Namen aus Refdata.Articles.xml, Land per DB-IP Country Lite.",
        )
        .styled(style::Style::new().with_font_size(8).with_color(GRAU)),
    );
    plan.verbleibend -= 40.0;

    // 1. Nutzer
    doc.push(elements::Break::new(1));
    doc.push(titel("1. Nutzer".into()));
    doc.push(elements::Break::new(0.3));
    plan.verbleibend -= 14.0;
    let sp = vec![
        Spalte { titel: "IP-Adresse", breite: 9, rechts: false, stil: blau.clone() },
        Spalte { titel: "Gerät", breite: 6, rechts: false, stil: blau.clone() },
        Spalte { titel: "Erster Tag", breite: 6, rechts: false, stil: blau.clone() },
        Spalte { titel: "Letzter Tag", breite: 6, rechts: false, stil: blau.clone() },
        Spalte { titel: "Tage", breite: 3, rechts: true, stil: blau.clone() },
        Spalte { titel: "Fachinfos", breite: 5, rechts: true, stil: blau.clone() },
        Spalte { titel: "Suchen", breite: 4, rechts: true, stil: blau.clone() },
        Spalte { titel: "Seiten", breite: 4, rechts: true, stil: blau.clone() },
    ];
    let mut liste: Vec<(&String, &Nutzer)> = nutzer.iter().collect();
    liste.sort_by(|a, b| {
        (b.1.fachinfos + b.1.suchen() + b.1.seiten)
            .cmp(&(a.1.fachinfos + a.1.suchen() + a.1.seiten))
            .then_with(|| a.0.cmp(b.0))
    });
    let zeilen: Vec<Vec<(String, style::Style)>> = liste
        .iter()
        .map(|(ip, n)| {
            let aktiv = n.tage.len() >= 2;
            let st = if aktiv { gruen.clone() } else { normal.clone() };
            vec![
                (ip.to_string(), grau.clone()),
                (n.geraet.clone(), normal.clone()),
                (datum_lesbar(*n.tage.iter().next().unwrap()), normal.clone()),
                (datum_lesbar(*n.tage.iter().last().unwrap()), normal.clone()),
                (n.tage.len().to_string(), st.clone()),
                (n.fachinfos.to_string(), if n.fachinfos > 0 { gruen.clone() } else { grau.clone() }),
                (n.suchen().to_string(), if n.suchen() > 0 { normal.clone() } else { grau.clone() }),
                (n.seiten.to_string(), normal.clone()),
            ]
        })
        .collect();
    plan.tabelle(&mut doc, &sp, &zeilen)?;

    // 2. Fachinformationen. Platz fuer Titel, Hinweis, Tabellenkopf und erste
    // Zeilen reservieren, sonst steht der Titel allein am Seitenende.
    plan.brauche(&mut doc, 40.0);
    doc.push(elements::Break::new(1));
    doc.push(titel("2. Geöffnete Fachinformationen".into()));
    doc.push(
        elements::Paragraph::new("Aufrufe über Barcode, Direktlink oder aus der Trefferliste der Suche. IP-Adressen dazu stehen im CSV.")
            .styled(style::Style::new().with_font_size(8).with_color(GRAU)),
    );
    doc.push(elements::Break::new(0.3));
    let sp = vec![
        Spalte { titel: "GTIN", breite: 9, rechts: false, stil: blau.clone() },
        Spalte { titel: "Präparat", breite: 18, rechts: false, stil: blau.clone() },
        Spalte { titel: "Zulassungsinhaberin", breite: 11, rechts: false, stil: blau.clone() },
        Spalte { titel: "Abrufe", breite: 4, rechts: true, stil: blau.clone() },
        Spalte { titel: "Nutzer", breite: 4, rechts: true, stil: blau.clone() },
    ];
    let zeilen: Vec<Vec<(String, style::Style)>> = fi
        .iter()
        .map(|z| {
            vec![
                (z.gtin.clone(), grau.clone()),
                (z.name.clone(), normal.clone()),
                (z.inhaberin.clone(), normal.clone()),
                (z.t.abrufe.to_string(), gruen.clone()),
                (z.t.ips.len().to_string(), normal.clone()),
            ]
        })
        .collect();
    plan.tabelle(&mut doc, &sp, &zeilen)?;

    doc.render_to_file(&args.out).with_context(|| format!("schreibe {}", args.out.display()))
}
