/*
Copyright (c) 2016 ML <cybrmx@gmail.com>

This file is part of AmikoWeb.

AmiKoRose is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program. If not, see <http://www.gnu.org/licenses/>.
*/

package models;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import play.libs.ws.*;
import static play.libs.Json.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.concurrent.CompletionStage;

/**
 * Created by maxl on 06.12.2016.
 * Refactored to use SDIF interactions.db (3-strategy search) instead of CSV lookups.
 */
public class InteractionsData {

    private static volatile InteractionsData instance;

    private static String[] m_section_titles = null;
    private static String[] m_section_anchors = null;
    private static String m_images_dir = "../../assets/images/";

    private InteractionsData() {}

    public static InteractionsData getInstance() {
        if (instance == null) {
            synchronized (InteractionsData.class) {
                if (instance == null) {
                    instance = new InteractionsData();
                }
            }
        }
        return instance;
    }

    public String[] sectionTitles() {
        return m_section_titles;
    }

    public String[] sectionAnchors() { return m_section_anchors; }

    public CompletionStage<String> updateHtml(WSClient ws, Connection interactionsConn,
                                              Map<String, Medication> med_basket, String lang) {
        return this.dataFromEpha(ws, med_basket, lang).thenApply((ephaRes)-> {
            String basket_html_str = "<table id=\"Interaktionen\" width=\"100%25\">";
            String delete_all_button_str = "";
            String epha_report_html_str = "";
            String interactions_html_str = "";
            String top_note_html_str = "";
            String legend_html_str = "";
            String bottom_note_html_str = "";
            String atc_code1 = "";
            String name1 = "";
            String[] m_code1 = null;
            int med_counter = 1;

            String delete_all_text = lang.equals("fr") ? "tout supprimer" : "alle löschen";

            // Build interaction basket table
            if (med_basket.size() > 0) {
                for (Map.Entry<String, Medication> entry1 : med_basket.entrySet()) {
                    String atc = entry1.getValue().getAtcCode();
                    if (atc != null && !atc.isEmpty()) {
                        m_code1 = atc.split(";");
                        atc_code1 = "k.A.";
                        name1 = "k.A.";
                        if (m_code1.length > 1) {
                            atc_code1 = m_code1[0];
                            name1 = m_code1[1];
                        }
                        basket_html_str += "<tr>";
                        if (med_counter % 2 == 0)
                            basket_html_str += "<tr style=\"background-color:var(--background-color-gray);\">";
                        else
                            basket_html_str += "<tr style=\"background-color:var(--background-color-normal);\">";
                        basket_html_str += "<td>" + med_counter + "</td>"
                                + "<td>" + entry1.getKey() + "</td>"
                                + "<td>" + entry1.getValue().getTitle() + "</td>"
                                + "<td>" + atc_code1 + "</td>"
                                + "<td>" + name1 + "</td>"
                                + "<td style=\"text-align:center;\">" + "<button type=\"button\" class=\"interaction-delete-button\" style=\"background-color:transparent; border:none; cursor: pointer;\""
                                + " onclick=\"deleteRow('Interaktionen',this)\">"
                                + "<img height=20 src=\"" + m_images_dir + "rubbish-bin.png\" /></button>" + "</td>";

                        basket_html_str += "</tr>";
                        med_counter++;
                    }
                }
                basket_html_str += "</table>";
                // Medikamentenkorb löschen
                delete_all_button_str = "<div id=\"Delete_all\"><input type=\"button\" value=\"" + delete_all_text
                        + "\" style=\"cursor: pointer; background: transparent; border: 1px solid #aaaaaa;\" onclick=\"deleteRow('Delete_all',this)\" />"
                        + "</div>";

                if (ephaRes != null) {
                    epha_report_html_str = htmlForEpha(ephaRes, lang);
                }
            } else {
                if (lang.equals("fr"))
                    basket_html_str = "<div>Votre panier de médicaments est vide.<br><br></div>";
                else
                    basket_html_str = "<div>Ihr Medikamentenkorb ist leer.<br><br></div>";
            }

            // --- SDIF 3-strategy interaction check ---
            ArrayList<String> section_str = new ArrayList<>();
            ArrayList<String> section_anchors = new ArrayList<>();
            if (lang.equals("fr")) {
                section_str.add("Interactions");
                section_anchors.add("Interactions");
            } else {
                section_str.add("Interaktionen");
                section_anchors.add("Interaktionen");
            }

            if (med_basket.size() >= 2 && interactionsConn != null) {
                // Resolve basket drugs from interactions.db
                List<InteractionsSearch.BasketDrug> basketDrugs = new ArrayList<>();
                // Keep mapping from basket drug index to EAN key for anchors
                List<String> basketEans = new ArrayList<>();
                List<String> basketTitles = new ArrayList<>();

                for (Map.Entry<String, Medication> entry : med_basket.entrySet()) {
                    InteractionsSearch.BasketDrug bd = InteractionsSearch.resolveDrug(interactionsConn, entry.getValue());
                    if (bd != null) {
                        basketDrugs.add(bd);
                        basketEans.add(entry.getKey());
                        basketTitles.add(entry.getValue().getTitle());
                    }
                }

                // Run 3-strategy check
                List<InteractionsSearch.InteractionResult> interactionResults =
                    InteractionsSearch.checkBasket(interactionsConn, basketDrugs);

                if (!interactionResults.isEmpty()) {
                    interactions_html_str = renderInteractionsHtml(interactionResults, section_str, section_anchors, lang);
                }
            }

            if (med_basket.size() > 0 && section_str.size() < 2) {
                if (lang.equals("fr")) {
                    top_note_html_str = "<p class=\"paragraph0\">Il n'y a aucune interaction connue entre les médicaments sélectionnés dans la base de données SDIF. "
                            + "Veuillez consulter les informations professionnelles.</p><br><br>";
                } else {
                    top_note_html_str = "<div><p class=\"paragraph0\">Zur Zeit sind keine Interaktionen zwischen diesen Medikamenten in der SDIF-Datenbank vorhanden. "
                            + "Weitere Informationen finden Sie in der Fachinformation.</p></div><br><br>";
                }
            } else if (med_basket.size() > 0 && section_str.size() > 1) {
                legend_html_str = addSeverityLegend(lang);
                if (lang.equals("fr")) {
                    section_str.add("Légende");
                    section_anchors.add("Légende");
                } else {
                    section_str.add("Legende");
                    section_anchors.add("Legende");
                }
            }

            if (lang.equals("fr"))
                bottom_note_html_str += "<p class=\"footnote\">1. Source des données: SDIF (Swiss Drug Interaction Finder) — basé sur les informations professionnelles Swissmedic.</p>";
            else
                bottom_note_html_str += "<p class=\"footnote\">1. Datenquelle: SDIF (Swiss Drug Interaction Finder) — basierend auf Swissmedic-Fachinformationen.</p>";

            String html_str = "<html><head><meta http-equiv=\"content-type\" content=\"text/html; charset=UTF-8\" /></head><body><div id=\"interactions\">"
                    + basket_html_str + epha_report_html_str + delete_all_button_str + "<br><br>" + top_note_html_str
                    + interactions_html_str + "<br>" + legend_html_str + "<br>" + bottom_note_html_str + "</div></body></html>";

            m_section_titles = section_str.toArray(new String[0]);
            m_section_anchors = section_anchors.toArray(new String[0]);

            return html_str;
        });
    }

    /**
     * Render SDIF interaction results as HTML.
     */
    private String renderInteractionsHtml(List<InteractionsSearch.InteractionResult> results,
                                          ArrayList<String> sectionStr, ArrayList<String> sectionAnchors,
                                          String lang) {
        StringBuilder html = new StringBuilder();

        // Group by drug pair for section navigation
        // Track unique drug pairs we've already added to sections
        java.util.Set<String> addedPairs = new java.util.LinkedHashSet<>();

        for (InteractionsSearch.InteractionResult ir : results) {
            String pairKey = ir.drugA + "-" + ir.drugB;
            String reversePairKey = ir.drugB + "-" + ir.drugA;

            if (!addedPairs.contains(pairKey) && !addedPairs.contains(reversePairKey)) {
                addedPairs.add(pairKey);
                String anchor = sanitizeAnchor(ir.drugA) + "-" + sanitizeAnchor(ir.drugB);
                sectionAnchors.add(anchor);
                sectionStr.add("<html>" + shortTitle(ir.drugA) + " &rarr; " + shortTitle(ir.drugB) + "</html>");
            }
        }

        // Render each interaction result
        for (InteractionsSearch.InteractionResult ir : results) {
            String color = InteractionsSearch.severityColor(ir.severityScore);
            String anchor = sanitizeAnchor(ir.drugA) + "-" + sanitizeAnchor(ir.drugB);

            html.append("<div class=\"interaction-entry\" id=\"").append(anchor).append("\">\n");
            html.append("<p class=\"paragraph1\" style=\"background-color:").append(color).append(";\">");

            // Type badge
            String typeBadge;
            switch (ir.interactionType) {
                case "substance":
                    typeBadge = lang.equals("fr") ? "Substance" : "Wirkstoff";
                    break;
                case "class-level":
                    typeBadge = lang.equals("fr") ? "Classe" : "Klasse";
                    break;
                case "CYP":
                    typeBadge = "CYP";
                    break;
                default:
                    typeBadge = ir.interactionType;
            }

            html.append("<b>").append(ir.drugA).append(" &harr; ").append(ir.drugB).append("</b>");
            html.append(" &nbsp; <span style=\"font-size:0.85em;\">[").append(typeBadge).append("]</span>");
            html.append(" &nbsp; <span style=\"font-size:0.85em;\">")
                .append(ir.severityIndicator).append(" ").append(ir.severityLabel).append("</span>");
            html.append("</p>\n");

            // Description
            html.append("<p class=\"paragraph0\">");
            if (!ir.keyword.isEmpty()) {
                html.append("<b>").append(escapeHtml(ir.keyword)).append(":</b> ");
            }
            html.append(escapeHtml(ir.description));
            html.append("</p>\n");

            // Explanation
            if (!ir.explanation.isEmpty()) {
                html.append("<p class=\"paragraph0\" style=\"font-size:0.85em; color:#666;\">");
                html.append(escapeHtml(ir.explanation));
                html.append("</p>\n");
            }

            html.append("</div>\n");
        }

        return html.toString();
    }

    private String shortTitle(String title) {
        if (title == null) return "";
        String[] t = title.split(" ");
        if (t.length > 1)
            title = t[0] + " " + t[1];
        title = title.replaceAll(",\\s*$", "");
        return title;
    }

    private String sanitizeAnchor(String s) {
        if (s == null) return "";
        return s.replaceAll("[^a-zA-Z0-9äöüÄÖÜéèêàâîôûç-]", "_");
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private String addSeverityLegend(String lang) {
        String legend;
        if (lang.equals("fr")) {
            legend = "<table id=\"Légende\" width=\"100%25\">";
            legend += "<tr><td bgcolor=\"#ff6a6a\" width=\"20\"></td><td><b>###</b></td><td>Contre-indiqué</td></tr>";
            legend += "<tr><td bgcolor=\"#ff82ab\"></td><td><b>##</b></td><td>Grave</td></tr>";
            legend += "<tr><td bgcolor=\"#ffb90f\"></td><td><b>#</b></td><td>Précaution</td></tr>";
            legend += "<tr><td bgcolor=\"#caff70\"></td><td><b>-</b></td><td>Pas de classification</td></tr>";
        } else {
            legend = "<table id=\"Legende\" width=\"100%25\">";
            legend += "<tr><td bgcolor=\"#ff6a6a\" width=\"20\"></td><td><b>###</b></td><td>Kontraindiziert</td></tr>";
            legend += "<tr><td bgcolor=\"#ff82ab\"></td><td><b>##</b></td><td>Schwerwiegend</td></tr>";
            legend += "<tr><td bgcolor=\"#ffb90f\"></td><td><b>#</b></td><td>Vorsicht</td></tr>";
            legend += "<tr><td bgcolor=\"#caff70\"></td><td><b>-</b></td><td>Keine Einstufung</td></tr>";
        }
        legend += "</table>";
        return legend;
    }

    private CompletionStage<JsonNode> dataFromEpha(WSClient ws, Map<String, Medication> med_basket, String lang) {
        if (med_basket.size() == 0) {
            return CompletableFuture.completedFuture(null);
        }
        ArrayNode arr = newArray();
        for (Map.Entry<String, Medication> entry1 : med_basket.entrySet()) {
            String atc = entry1.getValue().getAtcCode();
            if (atc != null && !atc.isEmpty()) {
                ObjectNode map = newObject();
                map.put("type", "drug");
                map.put("gtin", entry1.getKey());
                arr.add(map);
            }
        }
        if (lang == null) {
            lang = "en";
        }
        String postBody = stringify(toJson(arr));
        CompletionStage<JsonNode> response = ws
            .url("https://api.epha.health/clinic/advice/" + lang + "/")
            .setContentType("application/json")
            .post(postBody)
            .thenApply(WSResponse::asJson);

        return response.thenCompose((res) -> {
            int code = res.get("meta").get("code").asInt();
            if (code >= 200 && code < 300) {
                return CompletableFuture.completedFuture(res.get("data"));
            }
            throw new RuntimeException(res.toString());
        })
        .exceptionally((ex)-> {
            ex.printStackTrace();
            return null;
        });
    }

    private String htmlForEpha(JsonNode j, String lang) {
        int safety = j.get("safety").asInt();
        int kinetic = j.get("risk").get("kinetic").asInt();
        int qtc = j.get("risk").get("qtc").asInt();
        int warning = j.get("risk").get("warning").asInt();
        int serotonerg = j.get("risk").get("serotonerg").asInt();
        int anticholinergic = j.get("risk").get("anticholinergic").asInt();
        int adverse = j.get("risk").get("adverse").asInt();

        String html_str = "";

        if (lang.equals("de")) {
            html_str += "Sicherheit<BR>";
            html_str += "<p class='risk-description'>Je höher die Sicherheit, desto sicherer die Kombination.</p>";
        } else {
            html_str += "Sécurité<BR>";
            html_str += "<p class='risk-description'>Plus la sécurité est élevée, plus la combinaison est sûre.</p>";
        }

        html_str += "<div class='risk'>100";
        html_str += "<div class='gradient'>" +
                "<div class='pin' style='left: " + (100-safety) + "%'>" + safety + "</div>" +
            "</div>";
        html_str += "0</div><BR><BR>";

        if (lang.equals("de")) {
            html_str += "Risikofaktoren<BR>";
            html_str += "<p class='risk-description'>Je tiefer das Risiko, desto sicherer die Kombination.</p>";
        } else {
            html_str += "Facteurs de risque<BR>";
            html_str += "<p class='risk-description'>Plus le risque est faible, plus la combinaison est sûre.</p>";
        }

        html_str += "<table class='risk-table'>";
        html_str += "<tr><td class='risk-name'>";
        html_str += lang.equals("de") ? "Pharmakokinetik" : "Pharmacocinétique";
        html_str += "</td>";
        html_str += "<td>";
        html_str += "<div class='risk'>0";
        html_str += "<div class='gradient'><div class='pin' style='left: " + kinetic + "%'>" + kinetic + "</div></div>";
        html_str += "100</div>";
        html_str += "</td></tr>";
        html_str += "<tr><td class='risk-name'>";
        html_str += lang.equals("de") ? "Verlängerung der QT-Zeit" : "Allongement du temps QT";
        html_str += "</td>";
        html_str += "<td>";
        html_str += "<div class='risk'>0";
        html_str += "<div class='gradient'><div class='pin' style='left: " + qtc + "%'>" + qtc + "</div></div>";
        html_str += "100</div>";
        html_str += "</td></tr>";
        html_str += "<tr><td class='risk-name'>";
        html_str += lang.equals("de") ? "Warnhinweise" : "Avertissements";
        html_str += "</td>";
        html_str += "<td>";
        html_str += "<div class='risk'>0";
        html_str += "<div class='gradient'><div class='pin' style='left: " + warning + "%'>" + warning + "</div></div>";
        html_str += "100</div>";
        html_str += "</td></tr>";
        html_str += "<tr><td class='risk-name'>";
        html_str += lang.equals("de") ? "Serotonerge Effekte" : "Effets sérotoninergiques";
        html_str += "</td>";
        html_str += "<td>";
        html_str += "<div class='risk'>0";
        html_str += "<div class='gradient'><div class='pin' style='left: " + serotonerg + "%'>" + serotonerg + "</div></div>";
        html_str += "100</div>";
        html_str += "</td></tr>";
        html_str += "<tr><td class='risk-name'>";
        html_str += lang.equals("de") ? "Anticholinerge Effekte" : "Effets anticholinergiques";
        html_str += "</td>";
        html_str += "<td>";
        html_str += "<div class='risk'>0";
        html_str += "<div class='gradient'><div class='pin' style='left: " + anticholinergic + "%'>" + anticholinergic + "</div></div>";
        html_str += "100</div>";
        html_str += "</td></tr>";
        html_str += "<tr><td class='risk-name'>";
        html_str += lang.equals("de") ? "Allgemeine Nebenwirkungen" : "Effets secondaires généraux";
        html_str += "</td>";
        html_str += "<td>";
        html_str += "<div class='risk'>0";
        html_str += "<div class='gradient'><div class='pin' style='left: " + adverse + "%'>" + adverse + "</div></div>";
        html_str += "100</div>";
        html_str += "</td></tr>";
        html_str += "</table>";

        return html_str;
    }
}
