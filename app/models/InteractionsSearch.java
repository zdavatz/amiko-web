package models;

import java.sql.*;
import java.util.*;

/**
 * Drug interaction search engine using the pre-built interactions.db from SDIF.
 * Implements 3-tier detection (following Android reference pattern):
 *   Tier 1: EPha curated interactions (epha_interactions table) — highest priority
 *   Tier 2: Substance-level matching (interactions table)
 *   Tier 3: ATC class-level matching (class_keywords table + fachinfo text)
 *   + CYP enzyme-mediated interactions (cyp_rules table)
 *
 * Keywords, CYP rules, and EPha data are all loaded from database tables.
 */
public class InteractionsSearch {

    // --- Inner classes ---

    public static class BasketDrug {
        public String brand;
        public List<String> substances;
        public String atcCode;
        public String interactionsText;
        public String route;
        public String comboHint;

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
        public String interactionType; // "substance", "class-level", "CYP", "epha"
        public int severityScore;      // 0-3
        public String severityLabel;
        public String severityIndicator;
        public String keyword;
        public String description;
        public String explanation;
        // EPha-specific fields
        public String riskClass;
        public String riskLabel;
        public String effect;
        public String mechanism;
        public String measures;
        // Directional severity hint (Gegenrichtung)
        public String directionHint;
        // Route and combo hint
        public String drugARoute;
        public String drugBRoute;
        public String comboHint;
    }

    public static class ClassHit {
        public String classKeyword;
        public String context;

        public ClassHit(String classKeyword, String context) {
            this.classKeyword = classKeyword;
            this.context = context;
        }
    }

    private static class CypRule {
        String enzyme;
        String textPattern;
        String role; // "inhibitor" or "inducer"
        String atcPrefix; // nullable
        String substance; // nullable
    }

    // --- Cached DB data ---

    private static volatile Map<String, List<String>> cachedClassKeywords = null;
    private static volatile List<CypRule> cachedCypRules = null;

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
        "erhöh", "erniedrigt", "beeinflusst", "wechselwirkung",
        "plasmaspiegel", "plasmakonzentration", "serumkonzentration", "bioverfügbarkeit",
        "subtherapeutisch", "supratherapeutisch", "therapieversagen",
        "wirkungsverlust", "wirkverlust"
    };

    // --- DB data loading ---

    private static synchronized Map<String, List<String>> loadClassKeywords(Connection conn) {
        if (cachedClassKeywords != null) return cachedClassKeywords;
        Map<String, List<String>> keywords = new LinkedHashMap<>();
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT atc_prefix, keyword FROM class_keywords ORDER BY atc_prefix");
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                String prefix = rs.getString("atc_prefix");
                String kw = rs.getString("keyword");
                keywords.computeIfAbsent(prefix, k -> new ArrayList<>()).add(kw);
            }
        } catch (SQLException e) {
            System.err.println("Error loading class keywords: " + e.getMessage());
        }
        cachedClassKeywords = keywords;
        return cachedClassKeywords;
    }

    private static synchronized List<CypRule> loadCypRules(Connection conn) {
        if (cachedCypRules != null) return cachedCypRules;
        List<CypRule> rules = new ArrayList<>();
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT enzyme, text_pattern, role, atc_prefix, substance FROM cyp_rules");
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                CypRule rule = new CypRule();
                rule.enzyme = rs.getString("enzyme");
                rule.textPattern = rs.getString("text_pattern");
                rule.role = rs.getString("role");
                rule.atcPrefix = rs.getString("atc_prefix");
                rule.substance = rs.getString("substance");
                rules.add(rule);
            }
        } catch (SQLException e) {
            System.err.println("Error loading CYP rules: " + e.getMessage());
        }
        cachedCypRules = rules;
        return cachedCypRules;
    }

    // --- EPha lookup ---

    private static Map<String, String> findEphaInteraction(Connection conn, String atc1, String atc2) {
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT * FROM epha_interactions WHERE atc1 = ? AND atc2 = ? LIMIT 1")) {
            stmt.setString(1, atc1);
            stmt.setString(2, atc2);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Map<String, String> row = new LinkedHashMap<>();
                    ResultSetMetaData meta = rs.getMetaData();
                    for (int i = 1; i <= meta.getColumnCount(); i++) {
                        row.put(meta.getColumnName(i), rs.getString(i));
                    }
                    return row;
                }
            }
        } catch (SQLException e) {
            System.err.println("Error in EPha lookup: " + e.getMessage());
        }
        return null;
    }

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
                    "SELECT brand_name, active_substances, atc_code, interactions_text, route, combo_hint " +
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
                            "SELECT brand_name, active_substances, atc_code, interactions_text, route, combo_hint " +
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
                    "SELECT DISTINCT d.brand_name, d.active_substances, d.atc_code, d.interactions_text, d.route, d.combo_hint " +
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
        String route = rs.getString("route");
        String comboHint = rs.getString("combo_hint");
        List<String> substances = new ArrayList<>();
        if (substStr != null && !substStr.isEmpty()) {
            for (String s : substStr.split(", ")) {
                substances.add(s.toLowerCase().trim());
            }
        }
        BasketDrug bd = new BasketDrug(
            brand != null ? brand : "",
            substances,
            atc != null ? atc : "",
            text != null ? text : ""
        );
        bd.route = route != null ? route : "";
        bd.comboHint = comboHint != null ? comboHint : "";
        return bd;
    }

    /**
     * Check all pairwise interactions for a basket of drugs.
     * All tiers run for every pair (EPha + Swissmedic FI shown together).
     * Gegenrichtung hint computed across all results per drug pair.
     */
    public static List<InteractionResult> checkBasket(Connection conn, List<BasketDrug> drugs) {
        // Ensure DB data is loaded
        loadClassKeywords(conn);
        loadCypRules(conn);

        List<InteractionResult> results = new ArrayList<>();

        for (int i = 0; i < drugs.size(); i++) {
            for (int j = i + 1; j < drugs.size(); j++) {
                BasketDrug a = drugs.get(i);
                BasketDrug b = drugs.get(j);

                List<InteractionResult> pairResults = getInteractionsForPair(conn, a, b);
                results.addAll(pairResults);
            }
        }

        // Compute Gegenrichtung hints: for each drug pair (sorted),
        // find the max severity. Results below max get a hint.
        Map<String, Integer> pairMax = new LinkedHashMap<>();
        for (InteractionResult ir : results) {
            String key = pairKey(ir.drugA, ir.drugB);
            pairMax.merge(key, ir.severityScore, Math::max);
        }
        for (InteractionResult ir : results) {
            String key = pairKey(ir.drugA, ir.drugB);
            int max = pairMax.getOrDefault(key, 0);
            if (ir.severityScore < max) {
                ir.directionHint = String.format(
                    "Gegenrichtung hat höhere Einstufung — diese FI stuft die Interaktion tiefer ein");
            }
        }

        // Sort by severity descending
        results.sort((x, y) -> Integer.compare(y.severityScore, x.severityScore));
        return results;
    }

    private static String pairKey(String a, String b) {
        if (a == null) a = "";
        if (b == null) b = "";
        return a.compareTo(b) <= 0 ? a + "||" + b : b + "||" + a;
    }

    /**
     * All-tier interaction lookup for a drug pair.
     * EPha, substance, class-level, and CYP all run — nothing is skipped.
     */
    private static List<InteractionResult> getInteractionsForPair(Connection conn,
                                                                   BasketDrug drugA, BasketDrug drugB) {
        List<InteractionResult> results = new ArrayList<>();

        // EPha curated ATC-to-ATC (both directions)
        Map<String, String> ephaForward = findEphaInteraction(conn, drugA.atcCode, drugB.atcCode);
        Map<String, String> ephaReverse = findEphaInteraction(conn, drugB.atcCode, drugA.atcCode);
        if (ephaForward != null) {
            results.add(buildEphaResult(ephaForward, drugA, drugB));
        }
        if (ephaReverse != null) {
            results.add(buildEphaResult(ephaReverse, drugB, drugA));
        }

        // Substance match (both directions)
        for (String subst : drugB.substances) {
            addSubstanceMatches(conn, results, drugA, drugB, subst);
        }
        for (String subst : drugA.substances) {
            addSubstanceMatches(conn, results, drugB, drugA, subst);
        }

        // Class-level (both directions)
        addClassResults(conn, results, drugA, drugB);
        addClassResults(conn, results, drugB, drugA);

        // CYP enzyme (both directions)
        addCypResults(conn, results, drugA, drugB);
        addCypResults(conn, results, drugB, drugA);

        return results;
    }

    // --- Tier 1: EPha result builder ---

    private static InteractionResult buildEphaResult(Map<String, String> ephaRow,
                                                     BasketDrug drugA, BasketDrug drugB) {
        int severity = 0;
        try { severity = Integer.parseInt(ephaRow.get("severity_score")); } catch (Exception e) {}

        InteractionResult ir = new InteractionResult();
        ir.drugA = drugA.brand;
        ir.drugAAtc = drugA.atcCode;
        ir.drugB = drugB.brand;
        ir.drugBAtc = drugB.atcCode;
        ir.interactionType = "epha";
        ir.severityScore = severity;
        ir.severityLabel = severityLabel(severity);
        ir.severityIndicator = severityIndicator(severity);
        ir.riskClass = ephaRow.get("risk_class");
        ir.riskLabel = ephaRow.get("risk_label");
        ir.effect = ephaRow.get("effect");
        ir.mechanism = ephaRow.get("mechanism");
        ir.measures = ephaRow.get("measures");
        ir.keyword = "";
        ir.description = "";
        ir.explanation = String.format("EPha Interaktionsdatenbank (ATC %s \u2194 %s)",
                drugA.atcCode, drugB.atcCode);
        ir.drugARoute = drugA.route;
        ir.drugBRoute = drugB.route;
        ir.comboHint = drugA.comboHint;
        // directionHint computed centrally in checkBasket()

        return ir;
    }

    // --- Tier 2: Substance-level lookup (by drug_substance, matching Android reference) ---
    // One result per substance pair (highest severity), deduplicated across brands.

    private static void addSubstanceMatches(Connection conn, List<InteractionResult> results,
                                            BasketDrug source, BasketDrug other, String interactingSubstance) {
        if (source.substances == null || source.substances.isEmpty()) return;

        for (String sourceSubst : source.substances) {
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT drug_substance, interacting_substance, description, severity_score, severity_label " +
                    "FROM interactions WHERE LOWER(drug_substance) = LOWER(?) AND LOWER(interacting_substance) = LOWER(?) " +
                    "ORDER BY severity_score DESC LIMIT 1")) {
                stmt.setString(1, sourceSubst);
                stmt.setString(2, interactingSubstance);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        String drugSubst = rs.getString("drug_substance");
                        String interSubst = rs.getString("interacting_substance");
                        InteractionResult ir = new InteractionResult();
                        ir.drugA = source.brand;
                        ir.drugAAtc = source.atcCode;
                        ir.drugB = other.brand;
                        ir.drugBAtc = other.atcCode;
                        ir.interactionType = "substance";
                        ir.severityScore = rs.getInt("severity_score");
                        ir.severityLabel = rs.getString("severity_label");
                        ir.severityIndicator = severityIndicator(ir.severityScore);
                        ir.keyword = interSubst;
                        ir.description = rs.getString("description");
                        ir.explanation = String.format("Wirkstoff «%s» (%s) → «%s» (%s)",
                                drugSubst, source.brand, interSubst, other.brand);
                        ir.drugARoute = source.route;
                        ir.drugBRoute = other.route;
                        ir.comboHint = source.comboHint;

                        results.add(ir);
                    }
                }
            } catch (SQLException e) {
                System.err.println("Error in substance lookup: " + e.getMessage());
            }
        }
    }

    // --- Tier 3: Class-level interactions (from class_keywords table) ---

    private static void addClassResults(Connection conn, List<InteractionResult> results,
                                        BasketDrug source, BasketDrug other) {
        if (source.interactionsText == null || source.interactionsText.isEmpty()
                || other.atcCode == null || other.atcCode.isEmpty()) {
            return;
        }
        String textLower = source.interactionsText.toLowerCase();

        Map<String, List<String>> keywords = loadClassKeywords(conn);
        for (Map.Entry<String, List<String>> entry : keywords.entrySet()) {
            String prefix = entry.getKey();
            if (!other.atcCode.startsWith(prefix)) continue;

            for (String kw : entry.getValue()) {
                if (!textLower.contains(kw.toLowerCase())) continue;
                String context = extractContext(source.interactionsText, kw);
                if (context == null || context.isEmpty()) continue;

                int[] sev = scoreSeverity(context);

                InteractionResult ir = new InteractionResult();
                ir.drugA = source.brand;
                ir.drugAAtc = source.atcCode;
                ir.drugB = other.brand;
                ir.drugBAtc = other.atcCode;
                ir.interactionType = "class-level";
                ir.severityScore = sev[0];
                ir.severityLabel = severityLabel(sev[0]);
                ir.severityIndicator = severityIndicator(sev[0]);
                ir.keyword = kw;
                ir.description = context;
                ir.explanation = String.format(
                        "%s [%s] — Keyword «%s» gefunden in Fachinformation von %s",
                        other.brand, other.atcCode, kw, source.brand);
                ir.drugARoute = source.route;
                ir.drugBRoute = other.route;
                ir.comboHint = source.comboHint;

                results.add(ir);
                break; // One hit per ATC prefix is enough
            }
        }
    }

    // --- CYP enzyme interactions (from cyp_rules table) ---

    private static void addCypResults(Connection conn, List<InteractionResult> results,
                                      BasketDrug source, BasketDrug other) {
        if (source.interactionsText == null || source.interactionsText.isEmpty()) {
            return;
        }
        String textLower = source.interactionsText.toLowerCase();

        List<String> otherSubstLower = new ArrayList<>();
        if (other.substances != null) {
            for (String s : other.substances) {
                otherSubstLower.add(s.toLowerCase());
            }
        }

        List<CypRule> rules = loadCypRules(conn);

        // Group rules by enzyme
        Map<String, List<CypRule>> rulesByEnzyme = new LinkedHashMap<>();
        for (CypRule rule : rules) {
            rulesByEnzyme.computeIfAbsent(rule.enzyme, k -> new ArrayList<>()).add(rule);
        }

        Set<String> matchedEnzymes = new HashSet<>();

        for (Map.Entry<String, List<CypRule>> entry : rulesByEnzyme.entrySet()) {
            String enzyme = entry.getKey();
            List<CypRule> enzymeRules = entry.getValue();

            // Check if interaction text mentions this CYP enzyme
            boolean mentioned = false;
            String matchedPattern = null;
            for (CypRule rule : enzymeRules) {
                if (textLower.contains(rule.textPattern.toLowerCase())) {
                    mentioned = true;
                    matchedPattern = rule.textPattern;
                    break;
                }
            }
            if (!mentioned) continue;

            // Check if other drug matches any inhibitor or inducer rule for this enzyme
            boolean isInhibitor = false;
            boolean isInducer = false;
            for (CypRule rule : enzymeRules) {
                boolean matches = false;
                if (rule.atcPrefix != null && !rule.atcPrefix.isEmpty()
                        && other.atcCode != null && other.atcCode.startsWith(rule.atcPrefix)) {
                    matches = true;
                }
                if (rule.substance != null && !rule.substance.isEmpty()
                        && otherSubstLower.contains(rule.substance.toLowerCase())) {
                    matches = true;
                }
                if (matches) {
                    if ("inhibitor".equals(rule.role)) isInhibitor = true;
                    if ("inducer".equals(rule.role)) isInducer = true;
                }
            }

            if ((isInhibitor || isInducer) && !matchedEnzymes.contains(enzyme)) {
                matchedEnzymes.add(enzyme);
                String role = isInhibitor ? "Hemmer" : "Induktor";
                String context = extractContext(source.interactionsText, matchedPattern);
                if (context != null && !context.isEmpty()) {
                    int[] sev = scoreSeverity(context);
                    InteractionResult ir = new InteractionResult();
                    ir.drugA = source.brand;
                    ir.drugAAtc = source.atcCode;
                    ir.drugB = other.brand;
                    ir.drugBAtc = other.atcCode;
                    ir.interactionType = "CYP";
                    ir.severityScore = sev[0];
                    ir.severityLabel = severityLabel(sev[0]);
                    ir.severityIndicator = severityIndicator(sev[0]);
                    ir.keyword = enzyme + "-" + role;
                    ir.description = context;
                    ir.explanation = String.format("%s ist %s-%s — Fachinformation von %s erwähnt dieses Enzym",
                            other.brand, enzyme, role, source.brand);
                    ir.drugARoute = source.route;
                    ir.drugBRoute = other.route;
                    ir.comboHint = source.comboHint;
                    results.add(ir);
                }
            }
        }
    }

    private static String interactionsTextForAtc(Connection conn, String atcCode) {
        if (atcCode == null || atcCode.isEmpty()) return null;
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT interactions_text FROM drugs WHERE atc_code = ? AND length(interactions_text) > 0 LIMIT 1")) {
            stmt.setString(1, atcCode);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("interactions_text");
                }
            }
        } catch (SQLException e) {
            System.err.println("Error loading interactions text: " + e.getMessage());
        }
        return null;
    }

    // --- Severity scoring ---

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

    public static String severityColor(int score) {
        switch (score) {
            case 3: return "#ff6a6a"; // red - Kontraindiziert
            case 2: return "#ff82ab"; // pink - Schwerwiegend
            case 1: return "#ffb90f"; // orange - Vorsicht
            default: return "#caff70"; // green - Keine Einstufung
        }
    }

    /**
     * CSS paragraph class for EPha risk_class values.
     */
    public static String paragraphClassForRisk(String riskClass) {
        if (riskClass == null) return "paragraph0";
        switch (riskClass.trim()) {
            case "X": return "paragraphX";
            case "D": return "paragraphD";
            case "C": return "paragraphC";
            case "B": return "paragraphB";
            case "A": return "paragraphA";
            default: return "paragraph0";
        }
    }

    // --- Context extraction ---

    public static String extractContext(String text, String keyword) {
        if (text == null || keyword == null) return "";
        String lower = text.toLowerCase();
        String keyLower = keyword.toLowerCase();

        String bestSnippet = "";
        int bestSeverity = -1;
        boolean bestIsAnimal = false;
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

            // Deprioritize if substance appears after Tiermodell/Tierstudie/Tierversuch
            String prefixLower = lower.substring(start, pos);
            boolean isAnimal = prefixLower.contains("tiermodell")
                || prefixLower.contains("tierstudie")
                || prefixLower.contains("tierversuch");
            int effectiveSev = isAnimal ? 0 : sev[0];

            boolean dominated = effectiveSev > bestSeverity
                || (effectiveSev == bestSeverity && bestIsAnimal && !isAnimal)
                || bestSnippet.isEmpty();

            if (dominated) {
                bestSeverity = effectiveSev;
                bestIsAnimal = isAnimal;
                if (snippet.length() > 500) {
                    bestSnippet = snippet.substring(0, 497) + "...";
                } else {
                    bestSnippet = snippet;
                }
                if (bestSeverity >= 3) break;
            }

            searchFrom = pos + keyLower.length();
        }

        return bestSnippet;
    }
}
