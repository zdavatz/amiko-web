package models;

import java.sql.*;
import java.util.*;

/**
 * Drug interaction search engine using the pre-built interactions.db from SDIF.
 * Implements 3 detection strategies:
 *   1. Substance-level matching (direct DB lookup)
 *   2. ATC class-level matching (keyword search in interaction text)
 *   3. CYP enzyme-mediated interactions
 *
 * Ported from https://github.com/zdavatz/sdif (Rust)
 */
public class InteractionsSearch {

    // --- Inner classes ---

    public static class BasketDrug {
        public String brand;
        public List<String> substances;
        public String atcCode;
        public String interactionsText;

        public BasketDrug(String brand, List<String> substances, String atcCode, String interactionsText) {
            this.brand = brand;
            this.substances = substances;
            this.atcCode = atcCode;
            this.interactionsText = interactionsText;
        }
    }

    public static class InteractionResult {
        public String drugA;
        public String drugAAtc;
        public String drugB;
        public String drugBAtc;
        public String interactionType; // "substance", "class-level", "CYP"
        public int severityScore;      // 0-3
        public String severityLabel;
        public String severityIndicator;
        public String keyword;
        public String description;
        public String explanation;
    }

    public static class ClassHit {
        public String classKeyword;
        public String context;

        public ClassHit(String classKeyword, String context) {
            this.classKeyword = classKeyword;
            this.context = context;
        }
    }

    // --- ATC class keywords for Strategy 2 ---

    private static final String[][] CLASS_KEYWORDS = {
        {"B01A", "antikoagul", "warfarin", "cumarin", "coumarin", "vitamin-k-antagonist",
                 "vitamin k antagonist", "blutgerinnungshemm", "thrombozytenaggregationshemm",
                 "plättchenhemm", "antithrombotisch", "heparin", "thrombin-hemm",
                 "faktor-xa", "direktes orales antikoagulans", "doak"},
        {"B01AC", "thrombozytenaggregationshemm", "plättchenhemm", "thrombocytenaggregation"},
        {"M01A", "nsar", "nsaid", "nichtsteroidale antiphlogistika", "antiphlogistika",
                 "nichtsteroidale antirheumatika", "cox-2", "cox-hemmer", "cyclooxygenase",
                 "prostaglandinsynthesehemm", "entzündungshemm"},
        {"N02B", "analgetik", "antipyretik", "acetylsalicylsäure", "paracetamol"},
        {"N02A", "opioid", "opiat", "morphin", "atemdepression", "zns-depression"},
        {"C09A", "ace-hemmer", "ace-inhibitor", "ace inhibitor", "angiotensin-converting"},
        {"C09B", "ace-hemmer", "ace-inhibitor", "angiotensin-converting"},
        {"C09C", "angiotensin", "sartan", "at1-rezeptor", "at1-antagonist", "at1-blocker"},
        {"C09D", "angiotensin", "sartan", "at1-rezeptor", "at1-antagonist"},
        {"C07", "beta-blocker", "betablocker", "\u03b2-blocker", "betarezeptorenblocker", "beta-adrenozeptor"},
        {"C08", "calciumantagonist", "calciumkanalblocker", "kalziumantagonist",
                "kalziumkanalblocker", "calcium-antagonist"},
        {"C03", "diuretik", "thiazid", "schleifendiuretik", "kaliumsparend"},
        {"C03C", "schleifendiuretik", "furosemid", "torasemid"},
        {"C03A", "thiazid", "hydrochlorothiazid"},
        {"C01A", "herzglykosid", "digoxin", "digitalis", "digitoxin"},
        {"C01B", "antiarrhythmi", "amiodaron"},
        {"C10A", "statin", "hmg-coa", "lipidsenk", "cholesterinsenk"},
        {"N06AB", "ssri", "serotonin-wiederaufnahme", "serotonin reuptake",
                  "selektive serotonin", "serotonerg"},
        {"N06A", "antidepressiv", "trizyklisch", "serotonin", "snri", "maoh",
                 "mao-hemmer", "monoaminoxidase"},
        {"A10", "antidiabetik", "insulin", "blutzucker", "hypoglykämie", "orale antidiabetika",
                "sulfonylharnstoff", "metformin"},
        {"H02", "corticosteroid", "kortikosteroid", "glucocorticoid", "glukokortikoid",
                "kortison", "steroid"},
        {"L04", "immunsuppress", "ciclosporin", "tacrolimus", "mycophenolat", "azathioprin",
                "sirolimus"},
        {"L01", "antineoplast", "zytostatik", "methotrexat", "chemotherap"},
        {"N03", "antiepileptik", "antikonvulsiv", "krampflösend", "carbamazepin",
                "valproinsäure", "phenytoin", "enzymindukt"},
        {"N05A", "antipsychoti", "neuroleptik", "qt-verlänger", "qt-zeit"},
        {"N05B", "anxiolytik", "benzodiazepin"},
        {"N05C", "sedativ", "hypnotik", "schlafmittel", "zns-dämpfend", "zns-depression"},
        {"J01", "antibiotik", "antibakteriell"},
        {"J01FA", "makrolid", "erythromycin", "clarithromycin", "azithromycin"},
        {"J01MA", "fluorchinolon", "chinolon", "gyrasehemm"},
        {"J02A", "antimykotik", "azol-antimykotik", "triazol", "itraconazol",
                 "fluconazol", "voriconazol", "cyp3a4-hemm"},
        {"J05A", "antiviral", "proteasehemm", "protease-inhibitor", "hiv"},
        {"A02BC", "protonenpumpeninhibitor", "protonenpumpenhemm", "ppi", "säureblocker"},
        {"A02B", "antazid", "h2-blocker", "h2-antagonist", "säurehemm"},
        {"G03A", "kontrazeptiv", "östrogen", "orale kontrazeptiva", "hormonelle verhütung"},
        {"N07", "dopaminerg", "cholinerg", "anticholinerg"},
        {"R03", "bronchodilatat", "theophyllin", "sympathomimetik", "beta-2"},
        {"M04", "urikosurik", "gichtmittel", "harnsäure", "allopurinol"},
        {"B03", "eisen", "eisenpräparat", "eisensupplementation"},
        {"L02BA", "toremifen", "tamoxifen", "antiöstrogen", "östrogen-rezeptor",
                  "serm", "selektive östrogenrezeptor"},
        {"L02B", "hormonantagonist", "antihormon", "antiandrogen", "antiöstrogen"},
        {"V03AB", "sugammadex", "antidot", "antagonisierung", "neuromuskuläre blockade",
                  "verdrängung"},
        {"M03A", "muskelrelax", "neuromuskulär", "rocuronium", "vecuronium",
                 "succinylcholin", "curare"},
    };

    // --- CYP enzyme data for Strategy 3 ---
    // Each entry: {enzyme, textPatterns[], inhibitorAtcPrefixes[], inhibitorSubstances[], inducerAtcPrefixes[], inducerSubstances[]}

    private static final String[] CYP3A4_PATTERNS = {"cyp3a4", "cyp3a"};
    private static final String[] CYP3A4_INHIB_ATC = {"J05AE", "J02A", "J01FA"};
    private static final String[] CYP3A4_INHIB_SUBST = {"ritonavir", "cobicistat", "itraconazol", "ketoconazol",
            "voriconazol", "posaconazol", "fluconazol", "clarithromycin", "erythromycin", "diltiazem", "verapamil", "grapefruit"};
    private static final String[] CYP3A4_INDUC_ATC = {"J04AB", "N03AF", "N03AB"};
    private static final String[] CYP3A4_INDUC_SUBST = {"rifampicin", "rifabutin", "carbamazepin", "phenytoin",
            "phenobarbital", "johanniskraut", "efavirenz", "nevirapin"};

    private static final String[] CYP2D6_PATTERNS = {"cyp2d6"};
    private static final String[] CYP2D6_INHIB_ATC = {};
    private static final String[] CYP2D6_INHIB_SUBST = {"fluoxetin", "paroxetin", "bupropion", "chinidin",
            "terbinafin", "duloxetin", "ritonavir", "cobicistat"};
    private static final String[] CYP2D6_INDUC_ATC = {};
    private static final String[] CYP2D6_INDUC_SUBST = {"rifampicin"};

    private static final String[] CYP2C9_PATTERNS = {"cyp2c9"};
    private static final String[] CYP2C9_INHIB_ATC = {};
    private static final String[] CYP2C9_INHIB_SUBST = {"fluconazol", "amiodaron", "miconazol", "voriconazol", "fluvoxamin"};
    private static final String[] CYP2C9_INDUC_ATC = {};
    private static final String[] CYP2C9_INDUC_SUBST = {"rifampicin", "carbamazepin", "phenytoin"};

    private static final String[] CYP2C19_PATTERNS = {"cyp2c19"};
    private static final String[] CYP2C19_INHIB_ATC = {};
    private static final String[] CYP2C19_INHIB_SUBST = {"omeprazol", "esomeprazol", "fluvoxamin", "fluconazol",
            "voriconazol", "ticlopidin"};
    private static final String[] CYP2C19_INDUC_ATC = {};
    private static final String[] CYP2C19_INDUC_SUBST = {"rifampicin", "carbamazepin", "phenytoin", "johanniskraut"};

    private static final String[] CYP1A2_PATTERNS = {"cyp1a2"};
    private static final String[] CYP1A2_INHIB_ATC = {"J01MA"};
    private static final String[] CYP1A2_INHIB_SUBST = {"ciprofloxacin", "fluvoxamin", "enoxacin"};
    private static final String[] CYP1A2_INDUC_ATC = {};
    private static final String[] CYP1A2_INDUC_SUBST = {"rifampicin", "carbamazepin", "phenytoin", "johanniskraut"};

    private static final String[] CYP2C8_PATTERNS = {"cyp2c8"};
    private static final String[] CYP2C8_INHIB_ATC = {};
    private static final String[] CYP2C8_INHIB_SUBST = {"gemfibrozil", "clopidogrel", "trimethoprim"};
    private static final String[] CYP2C8_INDUC_ATC = {};
    private static final String[] CYP2C8_INDUC_SUBST = {"rifampicin"};

    private static final String[] CYP2B6_PATTERNS = {"cyp2b6"};
    private static final String[] CYP2B6_INHIB_ATC = {};
    private static final String[] CYP2B6_INHIB_SUBST = {"ticlopidin", "clopidogrel"};
    private static final String[] CYP2B6_INDUC_ATC = {};
    private static final String[] CYP2B6_INDUC_SUBST = {"rifampicin", "efavirenz"};

    private static final Object[][] CYP_MAP = {
        {"CYP3A4", CYP3A4_PATTERNS, CYP3A4_INHIB_ATC, CYP3A4_INHIB_SUBST, CYP3A4_INDUC_ATC, CYP3A4_INDUC_SUBST},
        {"CYP2D6", CYP2D6_PATTERNS, CYP2D6_INHIB_ATC, CYP2D6_INHIB_SUBST, CYP2D6_INDUC_ATC, CYP2D6_INDUC_SUBST},
        {"CYP2C9", CYP2C9_PATTERNS, CYP2C9_INHIB_ATC, CYP2C9_INHIB_SUBST, CYP2C9_INDUC_ATC, CYP2C9_INDUC_SUBST},
        {"CYP2C19", CYP2C19_PATTERNS, CYP2C19_INHIB_ATC, CYP2C19_INHIB_SUBST, CYP2C19_INDUC_ATC, CYP2C19_INDUC_SUBST},
        {"CYP1A2", CYP1A2_PATTERNS, CYP1A2_INHIB_ATC, CYP1A2_INHIB_SUBST, CYP1A2_INDUC_ATC, CYP1A2_INDUC_SUBST},
        {"CYP2C8", CYP2C8_PATTERNS, CYP2C8_INHIB_ATC, CYP2C8_INHIB_SUBST, CYP2C8_INDUC_ATC, CYP2C8_INDUC_SUBST},
        {"CYP2B6", CYP2B6_PATTERNS, CYP2B6_INHIB_ATC, CYP2B6_INHIB_SUBST, CYP2B6_INDUC_ATC, CYP2B6_INDUC_SUBST},
    };

    // --- Severity scoring keywords ---

    private static final String[] CONTRAINDICATED_KEYWORDS = {
        "kontraindiziert", "kontraindikation", "darf nicht",
        "nicht angewendet werden", "nicht verabreicht werden",
        "nicht kombiniert werden", "nicht gleichzeitig",
        "ist verboten", "absolut kontraindiziert", "streng kontraindiziert",
        "nicht zusammen", "nicht eingenommen werden", "nicht anwenden"
    };

    private static final String[] SERIOUS_KEYWORDS = {
        "erhöhtes risiko", "erhöhte gefahr", "schwerwiegend", "schwere",
        "lebensbedrohlich", "lebensgefährlich", "gefährlich",
        "stark erhöht", "stark verstärkt", "toxisch", "toxizität",
        "nephrotoxisch", "hepatotoxisch", "ototoxisch", "neurotoxisch", "kardiotoxisch",
        "tödlich", "fatale", "blutungsrisiko", "blutungsgefahr",
        "serotoninsyndrom", "serotonin-syndrom", "qt-verlängerung", "qt-zeit-verlängerung",
        "torsade", "rhabdomyolyse", "nierenversagen", "niereninsuffizienz",
        "nierenfunktionsstörung", "leberversagen", "atemdepression", "herzstillstand",
        "arrhythmie", "hyperkaliämie", "agranulozytose",
        "stevens-johnson", "anaphyla", "lymphoproliferation",
        "immundepression", "immunsuppression", "panzytopenie",
        "abgeraten", "wird nicht empfohlen"
    };

    private static final String[] CAUTION_KEYWORDS = {
        "vorsicht", "überwach", "monitor", "kontroll", "engmaschig",
        "dosisanpassung", "dosis reduz", "dosis anpassen", "dosisreduktion",
        "sorgfältig", "regelmässig", "regelmäßig", "aufmerksam",
        "cave", "beobacht", "verstärkt", "vermindert", "abgeschwächt",
        "erhöht", "erniedrigt", "beeinflusst", "wechselwirkung",
        "plasmaspiegel", "serumkonzentration", "bioverfügbarkeit",
        "subtherapeutisch", "supratherapeutisch", "therapieversagen",
        "wirkungsverlust", "wirkverlust"
    };

    // --- ATC class descriptions ---

    private static final Map<String, String> ATC_CLASS_DESCRIPTIONS = new LinkedHashMap<>();
    static {
        ATC_CLASS_DESCRIPTIONS.put("B01A", "Antikoagulantien");
        ATC_CLASS_DESCRIPTIONS.put("B01AC", "Thrombozytenaggregationshemmer");
        ATC_CLASS_DESCRIPTIONS.put("M01A", "NSAR (NSAIDs)");
        ATC_CLASS_DESCRIPTIONS.put("N02B", "Analgetika / Antipyretika");
        ATC_CLASS_DESCRIPTIONS.put("N02A", "Opioide");
        ATC_CLASS_DESCRIPTIONS.put("C09A", "ACE-Hemmer");
        ATC_CLASS_DESCRIPTIONS.put("C09B", "ACE-Hemmer (Kombination)");
        ATC_CLASS_DESCRIPTIONS.put("C09C", "Sartane (AT1-Antagonisten)");
        ATC_CLASS_DESCRIPTIONS.put("C09D", "Sartane (Kombination)");
        ATC_CLASS_DESCRIPTIONS.put("C07", "Beta-Blocker");
        ATC_CLASS_DESCRIPTIONS.put("C08", "Calciumkanalblocker");
        ATC_CLASS_DESCRIPTIONS.put("C03", "Diuretika");
        ATC_CLASS_DESCRIPTIONS.put("C03C", "Schleifendiuretika");
        ATC_CLASS_DESCRIPTIONS.put("C03A", "Thiazide");
        ATC_CLASS_DESCRIPTIONS.put("C01A", "Herzglykoside");
        ATC_CLASS_DESCRIPTIONS.put("C01B", "Antiarrhythmika");
        ATC_CLASS_DESCRIPTIONS.put("C10A", "Statine");
        ATC_CLASS_DESCRIPTIONS.put("N06AB", "SSRIs");
        ATC_CLASS_DESCRIPTIONS.put("N06A", "Antidepressiva");
        ATC_CLASS_DESCRIPTIONS.put("A10", "Antidiabetika");
        ATC_CLASS_DESCRIPTIONS.put("H02", "Corticosteroide");
        ATC_CLASS_DESCRIPTIONS.put("L04", "Immunsuppressiva");
        ATC_CLASS_DESCRIPTIONS.put("L01", "Antineoplastika");
        ATC_CLASS_DESCRIPTIONS.put("N03", "Antiepileptika");
        ATC_CLASS_DESCRIPTIONS.put("N05A", "Antipsychotika");
        ATC_CLASS_DESCRIPTIONS.put("N05B", "Anxiolytika");
        ATC_CLASS_DESCRIPTIONS.put("N05C", "Sedativa / Hypnotika");
        ATC_CLASS_DESCRIPTIONS.put("J01", "Antibiotika");
        ATC_CLASS_DESCRIPTIONS.put("J01FA", "Makrolide");
        ATC_CLASS_DESCRIPTIONS.put("J01MA", "Fluorchinolone");
        ATC_CLASS_DESCRIPTIONS.put("J02A", "Antimykotika");
        ATC_CLASS_DESCRIPTIONS.put("J05A", "Antivirale");
        ATC_CLASS_DESCRIPTIONS.put("A02BC", "PPI (Protonenpumpenhemmer)");
        ATC_CLASS_DESCRIPTIONS.put("A02B", "Ulkusmittel");
        ATC_CLASS_DESCRIPTIONS.put("G03A", "Hormonale Kontrazeptiva");
        ATC_CLASS_DESCRIPTIONS.put("N07", "Nervensystem (andere)");
        ATC_CLASS_DESCRIPTIONS.put("R03", "Bronchodilatatoren");
        ATC_CLASS_DESCRIPTIONS.put("M04", "Gichtmittel");
        ATC_CLASS_DESCRIPTIONS.put("B03", "Eisenpräparate");
        ATC_CLASS_DESCRIPTIONS.put("L02BA", "SERMs (Tamoxifen)");
        ATC_CLASS_DESCRIPTIONS.put("L02B", "Hormonantagonisten");
        ATC_CLASS_DESCRIPTIONS.put("V03AB", "Antidota");
        ATC_CLASS_DESCRIPTIONS.put("M03A", "Muskelrelaxantien");
    }

    // ATC prefix lookup order (most specific first)
    private static final String[] ATC_PREFIX_ORDER = {
        "B01AC", "B01A", "M01A", "N02B", "N02A", "C09A", "C09B", "C09C", "C09D",
        "C07", "C08", "C03C", "C03A", "C03", "C01A", "C01B", "C10A", "N06AB", "N06A",
        "A10", "H02", "L04", "L01", "N03", "N05A", "N05B", "N05C",
        "J01FA", "J01MA", "J01", "J02A", "J05A", "A02BC", "A02B", "G03A", "N07", "R03",
        "M04", "B03", "L02BA", "L02B", "V03AB", "M03A"
    };

    // --- Public API ---

    /**
     * Resolve a drug from interactions.db by brand name or ATC code from the Medication.
     */
    public static BasketDrug resolveDrug(Connection conn, Medication med) {
        if (med == null) return null;
        String title = med.getTitle();
        if (title == null || title.isEmpty()) return null;

        // Extract short brand name (first word(s) before comma or parens)
        String brandSearch = title.split("[,(]")[0].trim();

        try {
            // Try brand name match
            String pattern = "%" + brandSearch + "%";
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT brand_name, active_substances, atc_code, interactions_text " +
                    "FROM drugs WHERE brand_name LIKE ? ORDER BY length(interactions_text) DESC")) {
                stmt.setString(1, pattern);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return basketDrugFromRow(rs);
                    }
                }
            }

            // Try by ATC code from amiko DB
            String atc = med.getAtcCode();
            if (atc != null && !atc.isEmpty()) {
                String atcCode = atc.split("[;,]")[0].trim();
                if (!atcCode.isEmpty()) {
                    try (PreparedStatement stmt = conn.prepareStatement(
                            "SELECT brand_name, active_substances, atc_code, interactions_text " +
                            "FROM drugs WHERE atc_code = ? ORDER BY length(interactions_text) DESC")) {
                        stmt.setString(1, atcCode);
                        try (ResultSet rs = stmt.executeQuery()) {
                            if (rs.next()) {
                                return basketDrugFromRow(rs);
                            }
                        }
                    }
                }
            }

            // Try substance match
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT DISTINCT d.brand_name, d.active_substances, d.atc_code, d.interactions_text " +
                    "FROM substance_brand_map s JOIN drugs d ON d.brand_name = s.brand_name " +
                    "WHERE s.substance LIKE ? ORDER BY length(d.interactions_text) DESC LIMIT 1")) {
                stmt.setString(1, "%" + brandSearch.toLowerCase() + "%");
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return basketDrugFromRow(rs);
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Error resolving drug: " + e.getMessage());
        }
        return null;
    }

    private static BasketDrug basketDrugFromRow(ResultSet rs) throws SQLException {
        String brand = rs.getString("brand_name");
        String substStr = rs.getString("active_substances");
        String atc = rs.getString("atc_code");
        String text = rs.getString("interactions_text");
        List<String> substances = new ArrayList<>();
        if (substStr != null && !substStr.isEmpty()) {
            for (String s : substStr.split(", ")) {
                substances.add(s.toLowerCase().trim());
            }
        }
        return new BasketDrug(
            brand != null ? brand : "",
            substances,
            atc != null ? atc : "",
            text != null ? text : ""
        );
    }

    /**
     * Check all pairwise interactions for a basket of drugs.
     * Returns a list of InteractionResult sorted by severity (highest first).
     */
    public static List<InteractionResult> checkBasket(Connection conn, List<BasketDrug> drugs) {
        List<InteractionResult> results = new ArrayList<>();

        for (int i = 0; i < drugs.size(); i++) {
            for (int j = i + 1; j < drugs.size(); j++) {
                BasketDrug a = drugs.get(i);
                BasketDrug b = drugs.get(j);

                // Strategy 1: Substance match A->B
                for (String subst : b.substances) {
                    addSubstanceMatches(conn, results, a, b, subst);
                }
                // Strategy 1: Substance match B->A
                for (String subst : a.substances) {
                    addSubstanceMatches(conn, results, b, a, subst);
                }

                // Strategy 2: Class-level A->B
                for (ClassHit hit : findClassInteractions(a.interactionsText, b.atcCode)) {
                    int[] sev = scoreSeverity(hit.context);
                    String classDesc = atcClassDescriptionForCode(b.atcCode);
                    InteractionResult ir = new InteractionResult();
                    ir.drugA = a.brand;
                    ir.drugAAtc = a.atcCode;
                    ir.drugB = b.brand;
                    ir.drugBAtc = b.atcCode;
                    ir.interactionType = "class-level";
                    ir.severityScore = sev[0];
                    ir.severityLabel = severityLabel(sev[0]);
                    ir.severityIndicator = severityIndicator(sev[0]);
                    ir.keyword = hit.classKeyword;
                    ir.description = hit.context;
                    ir.explanation = String.format("%s [%s] gehört zur Klasse %s — Keyword «%s» gefunden in Fachinformation von %s",
                            b.brand, b.atcCode, classDesc, hit.classKeyword, a.brand);
                    results.add(ir);
                }
                // Strategy 2: Class-level B->A
                for (ClassHit hit : findClassInteractions(b.interactionsText, a.atcCode)) {
                    int[] sev = scoreSeverity(hit.context);
                    String classDesc = atcClassDescriptionForCode(a.atcCode);
                    InteractionResult ir = new InteractionResult();
                    ir.drugA = b.brand;
                    ir.drugAAtc = b.atcCode;
                    ir.drugB = a.brand;
                    ir.drugBAtc = a.atcCode;
                    ir.interactionType = "class-level";
                    ir.severityScore = sev[0];
                    ir.severityLabel = severityLabel(sev[0]);
                    ir.severityIndicator = severityIndicator(sev[0]);
                    ir.keyword = hit.classKeyword;
                    ir.description = hit.context;
                    ir.explanation = String.format("%s [%s] gehört zur Klasse %s — Keyword «%s» gefunden in Fachinformation von %s",
                            a.brand, a.atcCode, classDesc, hit.classKeyword, b.brand);
                    results.add(ir);
                }

                // Strategy 3: CYP A->B
                for (ClassHit hit : findCypInteractions(a.interactionsText, b.atcCode, b.substances)) {
                    int[] sev = scoreSeverity(hit.context);
                    InteractionResult ir = new InteractionResult();
                    ir.drugA = a.brand;
                    ir.drugAAtc = a.atcCode;
                    ir.drugB = b.brand;
                    ir.drugBAtc = b.atcCode;
                    ir.interactionType = "CYP";
                    ir.severityScore = sev[0];
                    ir.severityLabel = severityLabel(sev[0]);
                    ir.severityIndicator = severityIndicator(sev[0]);
                    ir.keyword = hit.classKeyword;
                    ir.description = hit.context;
                    ir.explanation = String.format("%s ist %s — Fachinformation von %s erwähnt dieses Enzym",
                            b.brand, hit.classKeyword, a.brand);
                    results.add(ir);
                }
                // Strategy 3: CYP B->A
                for (ClassHit hit : findCypInteractions(b.interactionsText, a.atcCode, a.substances)) {
                    int[] sev = scoreSeverity(hit.context);
                    InteractionResult ir = new InteractionResult();
                    ir.drugA = b.brand;
                    ir.drugAAtc = b.atcCode;
                    ir.drugB = a.brand;
                    ir.drugBAtc = a.atcCode;
                    ir.interactionType = "CYP";
                    ir.severityScore = sev[0];
                    ir.severityLabel = severityLabel(sev[0]);
                    ir.severityIndicator = severityIndicator(sev[0]);
                    ir.keyword = hit.classKeyword;
                    ir.description = hit.context;
                    ir.explanation = String.format("%s ist %s — Fachinformation von %s erwähnt dieses Enzym",
                            a.brand, hit.classKeyword, b.brand);
                    results.add(ir);
                }
            }
        }

        // Sort by severity descending
        results.sort((x, y) -> Integer.compare(y.severityScore, x.severityScore));
        return results;
    }

    // --- Strategy 1: Substance-level lookup ---

    private static void addSubstanceMatches(Connection conn, List<InteractionResult> results,
                                            BasketDrug source, BasketDrug other, String substance) {
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT description, severity_score, severity_label FROM interactions " +
                "WHERE drug_brand = ? AND interacting_substance = ?")) {
            stmt.setString(1, source.brand);
            stmt.setString(2, substance);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    InteractionResult ir = new InteractionResult();
                    ir.drugA = source.brand;
                    ir.drugAAtc = source.atcCode;
                    ir.drugB = other.brand;
                    ir.drugBAtc = other.atcCode;
                    ir.interactionType = "substance";
                    ir.severityScore = rs.getInt("severity_score");
                    ir.severityLabel = rs.getString("severity_label");
                    ir.severityIndicator = severityIndicator(ir.severityScore);
                    ir.keyword = substance;
                    ir.description = rs.getString("description");
                    ir.explanation = String.format("Wirkstoff «%s» wird in der Fachinformation von %s erwähnt",
                            substance, source.brand);
                    results.add(ir);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error in substance lookup: " + e.getMessage());
        }
    }

    // --- Strategy 2: Class-level interactions ---

    public static List<ClassHit> findClassInteractions(String interactionText, String otherAtc) {
        if (interactionText == null || interactionText.isEmpty() || otherAtc == null || otherAtc.isEmpty()) {
            return Collections.emptyList();
        }
        String textLower = interactionText.toLowerCase();
        List<ClassHit> hits = new ArrayList<>();

        for (String[] entry : CLASS_KEYWORDS) {
            String atcPrefix = entry[0];
            if (!otherAtc.startsWith(atcPrefix)) {
                continue;
            }
            for (int k = 1; k < entry.length; k++) {
                String keyword = entry[k];
                if (textLower.contains(keyword)) {
                    String context = extractContext(interactionText, keyword);
                    if (!context.isEmpty()) {
                        hits.add(new ClassHit(keyword, context));
                        break; // One hit per ATC prefix is enough
                    }
                }
            }
        }
        return hits;
    }

    // --- Strategy 3: CYP enzyme interactions ---

    public static List<ClassHit> findCypInteractions(String interactionText, String otherAtc, List<String> otherSubstances) {
        if (interactionText == null || interactionText.isEmpty()) {
            return Collections.emptyList();
        }
        String textLower = interactionText.toLowerCase();
        List<ClassHit> hits = new ArrayList<>();

        List<String> otherSubstLower = new ArrayList<>();
        if (otherSubstances != null) {
            for (String s : otherSubstances) {
                otherSubstLower.add(s.toLowerCase());
            }
        }

        for (Object[] cypEntry : CYP_MAP) {
            String enzyme = (String) cypEntry[0];
            String[] textPatterns = (String[]) cypEntry[1];
            String[] inhibAtc = (String[]) cypEntry[2];
            String[] inhibSubst = (String[]) cypEntry[3];
            String[] inducAtc = (String[]) cypEntry[4];
            String[] inducSubst = (String[]) cypEntry[5];

            // Check if interaction text mentions this CYP enzyme
            boolean mentioned = false;
            for (String p : textPatterns) {
                if (textLower.contains(p)) {
                    mentioned = true;
                    break;
                }
            }
            if (!mentioned) continue;

            // Check if the other drug is a known inhibitor
            boolean isInhibitor = false;
            if (otherAtc != null) {
                for (String prefix : inhibAtc) {
                    if (otherAtc.startsWith(prefix)) {
                        isInhibitor = true;
                        break;
                    }
                }
            }
            if (!isInhibitor) {
                for (String s : inhibSubst) {
                    if (otherSubstLower.contains(s)) {
                        isInhibitor = true;
                        break;
                    }
                }
            }

            // Check if the other drug is a known inducer
            boolean isInducer = false;
            if (otherAtc != null) {
                for (String prefix : inducAtc) {
                    if (otherAtc.startsWith(prefix)) {
                        isInducer = true;
                        break;
                    }
                }
            }
            if (!isInducer) {
                for (String s : inducSubst) {
                    if (otherSubstLower.contains(s)) {
                        isInducer = true;
                        break;
                    }
                }
            }

            if (isInhibitor || isInducer) {
                String role = isInhibitor ? "Hemmer" : "Induktor";
                String context = extractContext(interactionText, textPatterns[0]);
                if (!context.isEmpty()) {
                    hits.add(new ClassHit(enzyme + "-" + role, context));
                }
            }
        }

        return hits;
    }

    // --- Severity scoring ---

    /**
     * Score severity of a text snippet.
     * Returns int[]{score, 0} where score is 0-3.
     */
    public static int[] scoreSeverity(String text) {
        if (text == null || text.isEmpty()) return new int[]{0};
        String lower = text.toLowerCase();

        for (String kw : CONTRAINDICATED_KEYWORDS) {
            if (lower.contains(kw)) return new int[]{3};
        }
        for (String kw : SERIOUS_KEYWORDS) {
            if (lower.contains(kw)) return new int[]{2};
        }
        for (String kw : CAUTION_KEYWORDS) {
            if (lower.contains(kw)) return new int[]{1};
        }
        return new int[]{0};
    }

    public static String severityLabel(int score) {
        switch (score) {
            case 3: return "Kontraindiziert";
            case 2: return "Schwerwiegend";
            case 1: return "Vorsicht";
            default: return "Keine Einstufung";
        }
    }

    public static String severityIndicator(int score) {
        switch (score) {
            case 3: return "###";
            case 2: return "##";
            case 1: return "#";
            default: return "-";
        }
    }

    /**
     * Severity color for HTML rendering.
     */
    public static String severityColor(int score) {
        switch (score) {
            case 3: return "#ff6a6a"; // red - Kontraindiziert
            case 2: return "#ff82ab"; // pink - Schwerwiegend
            case 1: return "#ffb90f"; // orange - Vorsicht
            default: return "#caff70"; // green - Keine Einstufung
        }
    }

    // --- Context extraction ---

    /**
     * Extract the best context snippet containing the given keyword.
     * Picks the sentence with the highest severity score.
     */
    public static String extractContext(String text, String keyword) {
        if (text == null || keyword == null) return "";
        String lower = text.toLowerCase();
        String keyLower = keyword.toLowerCase();

        String bestSnippet = "";
        int bestSeverity = -1;
        int searchFrom = 0;

        while (true) {
            int pos = lower.indexOf(keyLower, searchFrom);
            if (pos < 0) break;

            // Find sentence boundaries
            int start = 0;
            for (int k = pos - 1; k >= 0; k--) {
                char c = text.charAt(k);
                if (c == '.' || c == ':') {
                    start = k + 1;
                    break;
                }
            }
            int end = text.length();
            int dotPos = lower.indexOf('.', pos);
            if (dotPos >= 0) {
                end = dotPos + 1;
            }
            if (end > text.length()) end = text.length();

            String snippet = text.substring(start, end).trim();
            int[] sev = scoreSeverity(snippet);

            if (sev[0] > bestSeverity || bestSnippet.isEmpty()) {
                bestSeverity = sev[0];
                if (snippet.length() > 500) {
                    bestSnippet = snippet.substring(0, 497) + "...";
                } else {
                    bestSnippet = snippet;
                }
                if (bestSeverity >= 3) break; // Can't do better
            }

            searchFrom = pos + keyLower.length();
        }

        return bestSnippet;
    }

    // --- ATC class description lookup ---

    public static String atcClassDescriptionForCode(String atcCode) {
        if (atcCode == null || atcCode.isEmpty()) return "";
        for (String prefix : ATC_PREFIX_ORDER) {
            if (atcCode.startsWith(prefix)) {
                String desc = ATC_CLASS_DESCRIPTIONS.get(prefix);
                return desc != null ? desc : "";
            }
        }
        return "";
    }
}
