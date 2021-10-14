/*
Copyright (c) 2016 ML <cybrmx@gmail.com>

This file is part of AmikoWeb.

AmikoRose is free software: you can redistribute it and/or modify
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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import play.libs.ws.*;
import static play.libs.Json.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.concurrent.CompletionStage;

/**
 * Created by maxl on 06.12.2016.
 */
public class InteractionsData {

    private static volatile InteractionsData instance;

    private static Map<String, String> m_interactions_de_map = null;
    private static Map<String, String> m_interactions_fr_map = null;
    private static Map<String, String> m_interactions_map = null;

    private static String[] m_section_titles = null;
    private static String[] m_section_anchors = null;
    private static String m_images_dir = "../../assets/images/";

    private InteractionsData() {}

    /**
     * Get the only instance of this class. Singleton pattern.
     *
     * @return
     */
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

    public void loadAllGermanFiles() {
        m_interactions_de_map = readFromCsvToMap("sqlite/drug_interactions_csv_de.csv");
        int num_entries = -1;
        if (m_interactions_de_map != null)
            num_entries = m_interactions_de_map.size();
        // Default interactions map
        m_interactions_map = m_interactions_de_map;
        System.out.println("found " + num_entries + " entries");
    }

    public void loadAllFrenchFiles() {
        m_interactions_fr_map = readFromCsvToMap("sqlite/drug_interactions_csv_fr.csv");
        int num_entries = -1;
        if (m_interactions_fr_map != null)
            num_entries = m_interactions_fr_map.size();
        System.out.println("found " + num_entries + " entries");
    }

    public void setLang(String lang) {
        if (lang.equals("de"))
            m_interactions_map = m_interactions_de_map;
        else if (lang.equals("fr"))
            m_interactions_map = m_interactions_fr_map;
    }

    public String[] sectionTitles() {
        return m_section_titles;
    }

    public String[] sectionAnchors() { return m_section_anchors; }

    public CompletionStage<String> updateHtml(WSClient ws, Map<String, Medication> med_basket, String lang) {
        return this.dataFromEpha(ws, med_basket, lang).thenApply((ephaRes)-> {
            // Redisplay selected meds
            String basket_html_str = "<table id=\"Interaktionen\" width=\"100%25\">";
            String delete_all_button_str = "";
            String epha_report_html_str = "";
            String interactions_html_str = "";
            String top_note_html_str = "";
            String legend_html_str = "";
            String bottom_note_html_str = "";
            String atc_code1 = "";
            String atc_code2 = "";
            String name1 = "";
            String delete_all_text = "alle löschen";
            String[] m_code1 = null;
            String[] m_code2 = null;
            int med_counter = 1;

            if (lang.equals("de")) {
                delete_all_text = "alle löschen";
            } else if (lang.equals("fr")) {
                delete_all_text = "tout supprimer";
            }

            // Build interaction basket table
            if (med_basket.size() > 0) {
                for (Map.Entry<String, Medication> entry1 : med_basket.entrySet()) {
                    String atc = entry1.getValue().getAtcCode();
                    if (atc!=null && !atc.isEmpty()) {
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
                String show_epha_button_name = lang.equals("de")
                    ? "EPha API Details anzeigen"
                    : "Afficher les détails de l'API EPha";
                delete_all_button_str = "<div id=\"Delete_all\"><input type=\"button\" value=\"" + delete_all_text
                        + "\" style=\"cursor: pointer; background: transparent; border: 1px solid #aaaaaa;\" onclick=\"deleteRow('Delete_all',this)\" />"
                        + "<input type=\"button\" value=\"" + show_epha_button_name + "\" style=\"cursor: pointer; background: transparent; border: 1px solid #aaaaaa;float:right;\" onclick=\"window.open('" + ephaRes.get("link").asText() + "')\" />"
                        + "</div>";

                epha_report_html_str = htmlForEpha(ephaRes, lang);
            } else {
                // Medikamentenkorb ist leer
                if (lang.equals("de"))
                    basket_html_str = "<div>Ihr Medikamentenkorb ist leer.<br><br></div>";
                else if (lang.equals("fr"))
                    basket_html_str = "<div>Votre panier de médicaments est vide.<br><br></div>";
            }

            // Build list of interactions
            ArrayList<String> section_str = new ArrayList<>();
            ArrayList<String> section_anchors = new ArrayList<>();
            // Add table to section titles
            if (lang.equals("de")) {
                section_str.add("Interaktionen");
                section_anchors.add("Interaktionen");
            } else if (lang.equals("fr")) {
                section_str.add("Interactions");
                section_anchors.add("Interactions");
            }
            if (med_counter > 1) {
                for (Map.Entry<String, Medication> entry1 : med_basket.entrySet()) {
                    String atc1 = entry1.getValue().getAtcCode();
                    if (atc1!=null && !atc1.isEmpty()) {
                        m_code1 = atc1.split(";");
                        if (m_code1.length > 1) {
                            // Get ATC code of first drug, make sure to get the first in the list (the second one is not used)
                            atc_code1 = m_code1[0].split(",")[0];
                            for (Map.Entry<String, Medication> entry2 : med_basket.entrySet()) {
                                String atc2 = entry2.getValue().getAtcCode();
                                if (atc2!=null && !atc2.isEmpty()) {
                                    m_code2 = atc2.split(";");
                                    String title1 = entry1.getValue().getTitle();
                                    String title2 = entry2.getValue().getTitle();
                                    String ean1 = entry1.getKey();
                                    String ean2 = entry2.getKey();
                                    if (m_code2.length > 1) {
                                        // Get ATC code of second drug
                                        atc_code2 = m_code2[0];
                                        if (atc_code1 != null && atc_code2 != null && !atc_code1.equals(atc_code2)) {

                                            if(lang.equals("fr")){
                                                    m_interactions_map=m_interactions_fr_map;
                                                }
                                            else if(lang.equals("de")){
                                                    m_interactions_map=m_interactions_de_map;
                                            }

                                            // Get html interaction content from drug interactions map
                                            // Anchors: use titles and not eancodes
                                            String inter = m_interactions_map.get(atc_code1 + "-" + atc_code2);
                                            if (inter != null) {
                                                // This changes the "id" tag
                                                inter = inter.replaceAll(atc_code1 + "-", ean1 + "-").replaceAll(atc_code1, shortTitle(title1));
                                                inter = inter.replaceAll("-" + atc_code2, "-" + ean2).replaceAll(atc_code2, shortTitle(title2));
                                                interactions_html_str += (inter + "");
                                                // Add title to section title list
                                                if (!inter.isEmpty()) {
                                                    section_anchors.add(ean1 + "-" + ean2);
                                                    section_str.add("<html>" + shortTitle(title1) + " &rarr; " + shortTitle(title2) + "</html>");
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (med_basket.size() > 0 && section_str.size() < 2) {
                // Add note to indicate that there are no interactions
                if (lang.equals("de")) {
                    top_note_html_str = "<div><p class=\"paragraph0\">Zur Zeit sind keine Interaktionen zwischen diesen Medikamenten in der EPha.ch-Datenbank vorhanden. "
                            + "Weitere Informationen finden Sie in der Fachinformation. "
                            + "<i>Möchten Sie eine Interaktion melden? Senden Sie bitte eine Email an: zdavatz@ywesee.com.</i></p></div><br><br>";
                } else if (lang.equals("fr")) {
                    top_note_html_str = "<p class=\"paragraph0\">Il n’y a aucune information dans la banque de données EPha.ch à propos d’une interaction entre les médicaments sélectionnés. Veuillez consulter les informations professionelles.</p><br><br>";
                }
            } else if (med_basket.size() > 0 && section_str.size() > 1) {
                // Add color legend
                legend_html_str = addColorLegend(lang);
                // Add legend to section titles
                if (lang.equals("de")) {
                    section_str.add("Legende");
                    section_anchors.add("Legende");
                } else if (lang.equals("fr")) {
                    section_str.add("Légende");
                    section_anchors.add("Légende");
                }
            }

            if (lang.equals("de"))
                bottom_note_html_str += "<p class=\"footnote\">1. Datenquelle: Public Domain Daten von EPha.ch.</p>";
            else if (lang.equals("fr"))
                bottom_note_html_str += "<p class=\"footnote\">1. Source des données: données du domaine publique de EPha.ch.</p>";

            String html_str = "<html><head><meta http-equiv=\"content-type\" content=\"text/html; charset=UTF-8\" /></head><body><div id=\"interactions\">"
                    + basket_html_str + epha_report_html_str + delete_all_button_str + "<br><br>" + top_note_html_str
                    + interactions_html_str + "<br>" + legend_html_str + "<br>" + bottom_note_html_str + "</div></body></html>";

            // Update section titles
            m_section_titles = section_str.toArray(new String[section_str.size()]);
            m_section_anchors = section_anchors.toArray(new String[section_anchors.size()]);

            return html_str;
        });
    }

    private CompletionStage<JsonNode> dataFromEpha(WSClient ws, Map<String, Medication> med_basket, String lang) {
        if (med_basket.size() == 0) {
            return CompletableFuture.completedFuture(null);
        }
        ArrayNode arr = newArray();
        for (Map.Entry<String, Medication> entry1 : med_basket.entrySet()) {
            String atc = entry1.getValue().getAtcCode();
            if (atc!=null && !atc.isEmpty()) {
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

        return response.thenApply((res) -> {
            return res.get("data");
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
            "</div><BR>";
        html_str += "0</div><BR><BR>";

        if (lang.equals("de")) {
            html_str += "Risikofaktoren<BR>";
            html_str += "<p class='risk-description'>Je tiefer das Risiko, desto sicherer die Kombination.</p>";
        } else {
            html_str += "Facteurs de risque<BR>";
            html_str += "<p class='risk-description'>Plus le risque est faible, plus la combinaison est sûre.</p>";
        }

        if (lang.equals("de")) {
            html_str += "<table class='risk-table'>";
            html_str += "<tr><td class='risk-name'>";
            html_str += "Pharmakokinetik";
            html_str += "</td>";
            html_str += "<td>";
            html_str += "<div class='risk'>0";
            html_str += "<div class='gradient'><div class='pin' style='left: " + kinetic + "%'>" + kinetic + "</div></div>";
            html_str += "100</div>";
            html_str += "</td></tr>";
            html_str += "<tr><td class='risk-name'>";
            html_str += "Verlängerung der QT-Zeit";
            html_str += "</td>";
            html_str += "<td>";
            html_str += "<div class='risk'>0";
            html_str += "<div class='gradient'><div class='pin' style='left: " + qtc + "%'>" + qtc + "</div></div>";
            html_str += "100</div>";
            html_str += "</td></tr>";
            html_str += "<tr><td class='risk-name'>";
            html_str += "Warnhinweise";
            html_str += "</td>";
            html_str += "<td>";
            html_str += "<div class='risk'>0";
            html_str += "<div class='gradient'><div class='pin' style='left: " + warning + "%'>" + warning + "</div></div>";
            html_str += "100</div>";
            html_str += "</td></tr>";
            html_str += "<tr><td class='risk-name'>";
            html_str += "Serotonerge Effekte";
            html_str += "</td>";
            html_str += "<td>";
            html_str += "<div class='risk'>0";
            html_str += "<div class='gradient'><div class='pin' style='left: " + serotonerg + "%'>" + serotonerg + "</div></div>";
            html_str += "100</div>";
            html_str += "</td></tr>";
            html_str += "<tr><td class='risk-name'>";
            html_str += "Anticholinerge Effekte";
            html_str += "</td>";
            html_str += "<td>";
            html_str += "<div class='risk'>0";
            html_str += "<div class='gradient'><div class='pin' style='left: " + anticholinergic + "%'>" + anticholinergic + "</div></div>";
            html_str += "100</div>";
            html_str += "</td></tr>";
            html_str += "<tr><td class='risk-name'>";
            html_str += "Allgemeine Nebenwirkungen";
            html_str += "</td>";
            html_str += "<td>";
            html_str += "<div class='risk'>0";
            html_str += "<div class='gradient'><div class='pin' style='left: " + adverse + "%'>" + adverse + "</div></div>";
            html_str += "100</div>";
            html_str += "</td></tr>";
            html_str += "</table>";
        } else {
            html_str += "<table class='risk-table'>";
            html_str += "<tr><td class='risk-name'>";
            html_str += "Pharmacocinétique";
            html_str += "</td>";
            html_str += "<td>";
            html_str += "<div class='risk'>0";
            html_str += "<div class='gradient'><div class='pin' style='left: " + kinetic + "%'>" + kinetic + "</div></div>";
            html_str += "100</div>";
            html_str += "</td></tr>";
            html_str += "<tr><td class='risk-name'>";
            html_str += "Allongement du temps QT";
            html_str += "</td>";
            html_str += "<td>";
            html_str += "<div class='risk'>0";
            html_str += "<div class='gradient'><div class='pin' style='left: " + qtc + "%'>" + qtc + "</div></div>";
            html_str += "100</div>";
            html_str += "</td></tr>";
            html_str += "<tr><td class='risk-name'>";
            html_str += "Avertissements";
            html_str += "</td>";
            html_str += "<td>";
            html_str += "<div class='risk'>0";
            html_str += "<div class='gradient'><div class='pin' style='left: " + warning + "%'>" + warning + "</div></div>";
            html_str += "100</div>";
            html_str += "</td></tr>";
            html_str += "<tr><td class='risk-name'>";
            html_str += "Effets sérotoninergiques";
            html_str += "</td>";
            html_str += "<td>";
            html_str += "<div class='risk'>0";
            html_str += "<div class='gradient'><div class='pin' style='left: " + serotonerg + "%'>" + serotonerg + "</div></div>";
            html_str += "100</div>";
            html_str += "</td></tr>";
            html_str += "<tr><td class='risk-name'>";
            html_str += "Effets anticholinergiques";
            html_str += "</td>";
            html_str += "<td>";
            html_str += "<div class='risk'>0";
            html_str += "<div class='gradient'><div class='pin' style='left: " + anticholinergic + "%'>" + anticholinergic + "</div></div>";
            html_str += "100</div>";
            html_str += "</td></tr>";
            html_str += "<tr><td class='risk-name'>";
            html_str += "Effets secondaires généraux";
            html_str += "</td>";
            html_str += "<td>";
            html_str += "<div class='risk'>0";
            html_str += "<div class='gradient'><div class='pin' style='left: " + adverse + "%'>" + adverse + "</div></div>";
            html_str += "100</div>";
            html_str += "</td></tr>";
            html_str += "</table>";
        }

        return html_str;
    }

    private String shortTitle(String title) {
        String[] t = title.split(" ");
        if (t.length>1)
            title = t[0] + " " + t[1];
        // Remove trailing commas
        title = title.replaceAll(",\\s*$", "");
        return title;
    }

    private String addColorLegend(String lang) {
        String legend = "<table id=\"Legende\" width=\"100%25\">";
        /*
	     Risikoklassen
	     -------------
		     A: Keine Massnahmen notwendig (grün)
		     B: Vorsichtsmassnahmen empfohlen (gelb)
		     C: Regelmässige Überwachung (orange)
		     D: Kombination vermeiden (pinky)
		     X: Kontraindiziert (hellrot)
		     0: Keine Angaben (grau)
	    */
        // Sets the anchor
        if (lang.equals("de")) {
            legend = "<table id=\"Legende\" width=\"100%25\">";
            legend += "<tr><td bgcolor=\"#caff70\"></td>" +
                    "<td>A</td>" +
                    "<td>Keine Massnahmen notwendig</td></tr>";
            legend += "<tr><td bgcolor=\"#ffec8b\"></td>" +
                    "<td>B</td>" +
                    "<td>Vorsichtsmassnahmen empfohlen</td></tr>";
            legend += "<tr><td bgcolor=\"#ffb90f\"></td>" +
                    "<td>C</td>" +
                    "<td>Regelmässige Überwachung</td></tr>";
            legend += "<tr><td bgcolor=\"#ff82ab\"></td>" +
                    "<td>D</td>" +
                    "<td>Kombination vermeiden</td></tr>";
            legend += "<tr><td bgcolor=\"#ff6a6a\"></td>" +
                    "<td>X</td>" +
                    "<td>Kontraindiziert</td></tr>";
        } else if (lang.equals("fr")) {
            legend = "<table id=\"Légende\" width=\"100%25\">";
            legend += "<tr><td bgcolor=\"#caff70\"></td>" +
                    "<td>A</td>" +
                    "<td>Aucune mesure nécessaire</td></tr>";
            legend += "<tr><td bgcolor=\"#ffec8b\"></td>" +
                    "<td>B</td>" +
                    "<td>Mesures de précaution sont recommandées</td></tr>";
            legend += "<tr><td bgcolor=\"#ffb90f\"></td>" +
                    "<td>C</td>" +
                    "<td>Doit être régulièrement surveillée</td></tr>";
            legend += "<tr><td bgcolor=\"#ff82ab\"></td>" +
                    "<td>D</td>" +
                    "<td>Eviter la combinaison</td></tr>";
            legend += "<tr><td bgcolor=\"#ff6a6a\"></td>" +
                    "<td>X</td>" +
                    "<td>Contre-indiquée</td></tr>";
        }
		/*
		legend += "<tr><td bgcolor=\"#dddddd\"></td>" +
				"<td>0</td>" +
				"<td>Keine Angaben</td></tr>";
		*/
        legend += "</table>";

        return legend;
    }

    private Map<String, String> readFromCsvToMap(String filename) {
        Map<String, String> map = new TreeMap<String, String>();
        try {
            filename = System.getProperty("user.dir") + "/" + filename;
            File file = new File(filename);
            if (!file.exists()) {
                System.out.println("File " + filename + " not found!");
                return null;
            }
            FileInputStream fis = new FileInputStream(filename);
            BufferedReader br = new BufferedReader(new InputStreamReader(fis, "UTF-8"));
            String line;
            while ((line = br.readLine()) != null) {
                String token[] = line.split("\\|\\|");
                map.put(token[0] + "-" + token[1], token[2]);
            }
            br.close();
        } catch (Exception e) {
            System.err.println(">> Error in reading csv file: " + filename);
        }

        return map;
    }
}
