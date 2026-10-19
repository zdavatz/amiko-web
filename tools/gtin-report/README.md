# gtin-report

Wertet die Apache-Logs von amiko.zurrose.ch aus und schreibt ein PDF
(plus CSV) mit zwei Abschnitten:

1. **Nutzer**: jede IP-Adresse mit Gerät, erstem und letztem Tag, aktiven
   Tagen, geöffneten Fachinfos, Suchen und weiteren Seitenaufrufen.
2. **Geöffnete Fachinformationen**: pro GTIN Präparat, Zulassungsinhaberin
   (aus `Refdata.Articles.xml`), Abrufe und Zahl der Nutzer.

Gezählt werden nur erfolgreiche Antworten (HTTP 200). Skripte, Styles,
Bilder und `javascriptRoutes` zählen nicht. Eine Fachinfo gilt als geöffnet
bei `/de/fi/gtin/<GTIN>` (Barcode, Direktlink) und `/de/fi?gtin=<GTIN>`
(aus der Trefferliste der Suche). Die Suche feuert bei jedem Buchstaben und
an mehrere Endpunkte; als eine Suche zählt pro Nutzer nur der fertig
getippte Begriff.

## Bauen und aufrufen

    cargo build --release
    ./target/release/gtin-report \
        --refdata Refdata.Articles.xml \
        --since 2026-09-29 --until 2026-10-19 \
        --geoip dbip-country-lite.mmdb --nur-ch \
        --out amiko-nutzung-ch.pdf \
        /var/log/apache2/other_vhosts_access.log*

Optionen:

- `--refdata` Pfad zu `Refdata.Articles.xml` (Pflicht).
- `--since`, `--until` Datumsgrenzen `JJJJ-MM-TT`, beide optional.
- `--out` PDF-Ziel (Pflicht); das CSV entsteht daneben mit `.csv`.
- `--host` vhost im Log, Standard `amiko.zurrose.ch`.
- `--geoip` DB-IP Country Lite (mmdb, kostenlos von db-ip.com), abgefragt
  über das Kommando `mmdblookup` (libmaxminddb).
- `--nur-ch` nur Schweizer IP-Adressen auswerten; braucht `--geoip`.

Logdateien dürfen `.gz` sein. Schriften: DejaVu Sans aus `fonts/`, wie in
den anderen genpdf-Projekten. Baut offline, wenn die Crates im Cargo-Cache
liegen (`cargo build --release --offline`).

## Logs vom Server holen

Die Logs gehören root:adm. Auf dem Server:

    sudo tar czf ~/apache2-logs.tgz -C /var/log apache2 && sudo chown $USER: ~/apache2-logs.tgz

Logrotate ist auf `rotate 180` gestellt, es stehen also bis zu sechs Monate
zur Verfügung.
