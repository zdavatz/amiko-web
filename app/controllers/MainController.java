/*
Copyright (c) 2016 ML <cybrmx@gmail.com>

This file is part of AmiKoWeb.

AmiKoWeb is free software: you can redistribute it and/or modify
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

package controllers;

import models.Constants;
import models.FullTextEntry;
import models.InteractionsData;
import models.Medication;
import play.db.NamedDatabase;
import play.db.Database;
import play.mvc.*;
import views.html.*;

import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.inject.Inject;

import static play.libs.Json.*;

public class MainController extends Controller {

    private static final String KEY_ROWID = "_id";
    private static final String KEY_TITLE = "title";
    private static final String KEY_AUTH = "auth";
    private static final String KEY_ATCCODE = "atc";
    private static final String KEY_SUBSTANCES = "substances";
    private static final String KEY_REGNRS = "regnrs";
    private static final String KEY_ATCCLASS = "atc_class";
    private static final String KEY_THERAPY = "tindex_str";
    private static final String KEY_APPLICATION = "application_str";
    private static final String KEY_INDICATIONS = "indications_str";
    private static final String KEY_CUSTOMER_ID = "customer_id";
    private static final String KEY_PACK_INFO = "pack_info_str";
    private static final String KEY_ADDINFO = "add_info_str";
    private static final String KEY_PACKAGES = "packages";
    private static final String KEY_SECTION_IDS = "ids_str";
    private static final String KEY_SECTION_TITLES = "titles_str";

    private static final String DATABASE_TABLE = "amikodb";
    private static final String FREQUENCY_TABLE = "frequency";

    public static boolean ASC = true;
    public static boolean DESC = false;

    /**
     * Table columns used for fast queries
     */
    private static final String SHORT_TABLE = String.format("%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s",
            KEY_ROWID, KEY_TITLE, KEY_AUTH, KEY_ATCCODE, KEY_SUBSTANCES, KEY_REGNRS,
            KEY_ATCCLASS, KEY_THERAPY, KEY_APPLICATION, KEY_INDICATIONS,
            KEY_CUSTOMER_ID, KEY_PACK_INFO, KEY_ADDINFO, KEY_PACKAGES);

    private static final String FT_SEARCH_TABLE = String.format("%s,%s,%s,%s,%s", KEY_ROWID, KEY_TITLE, KEY_REGNRS,
            KEY_SECTION_IDS, KEY_SECTION_TITLES);

    @Inject @NamedDatabase("german") Database german_db;
    @Inject @NamedDatabase("french") Database french_db;
    @Inject @NamedDatabase("frequency_ge") Database frequency_db;


    private static Map<Integer, String> sortByComparator(Map<Integer, String> unsort_map, final boolean order)
    {
        List<Map.Entry<Integer, String>> list = new LinkedList<>(unsort_map.entrySet());

        // Sorting the list based on values
        Collections.sort(list,
                (Map.Entry<Integer, String> o1, Map.Entry<Integer, String> o2) -> {
                    if (order)
                        return o1.getValue().compareTo(o2.getValue());
                    else
                        return o2.getValue().compareTo(o1.getValue());
                });

        // Maintaining insertion order with the help of LinkedList
        Map<Integer, String> sort_map = new LinkedHashMap<>();
        for (Map.Entry<Integer, String> entry : list) {
            sort_map.put(entry.getKey(), entry.getValue());
        }

        return sort_map;
    }

    private static List<Article> sortByComparator(List<Article> list, final boolean order) {
        Collections.sort(list, (Article a1, Article a2) -> {
            if (order)
                return a1.title.compareTo(a2.title);
            else
                return a2.title.compareTo(a1.title);
        });

        return list;
    }

    private class Article {
        // Private
        private String _title = "";
        private String _author = "";
        private String _atccode = "";
        private String _regnrs = "";
        private String _therapy = "";
        private String _packinfo = "";
        private String _atcclass = "";
        private String _packages = "";
        private String _titles = "";
        private String _sections = "";

        // Interface variables
        public long id = 0;
        public String title = "";
        public String author = "";
        public String atccode = "";
        public String regnrs = "";
        public String therapy = "";
        public String packinfo = "";
        public String eancode = "";
        public String titles = "";
        public String sections = "";

        Article(long _id, String _title, String _author, String _atccode, String _atcclass, String _regnrs, String _therapy, String _packinfo, String _packages, String _titles, String _sections) {
            // Private
            this._title = _title;
            this._author = _author;
            this._atccode = _atccode;
            this._regnrs = _regnrs;
            this._therapy = _therapy;
            this._packinfo = _packinfo;
            this._atcclass = _atcclass;
            this._packages = _packages;
            this._titles = _titles;
            this._sections = _sections;

            // Interface
            this.id = _id;
            this.title = _title;
            this.packinfo = packinfoStr();
            this.author = _author;
            this.atccode = atccodeStr();
            this.regnrs = _regnrs;
            this.therapy = therapyStr();
            this.eancode = eancodeStr();
            this.titles = _titles;
            this.sections = _sections;
        }

        String packinfoStr() {
            String pack_info_str = "";
            Pattern p_red = Pattern.compile(".*O]");
            Pattern p_green = Pattern.compile(".*G]");
            Scanner pack_str_scanner = new Scanner(_packinfo);
            while (pack_str_scanner.hasNextLine()) {
                String pack_str_line = pack_str_scanner.nextLine();
                Matcher m_red = p_red.matcher(pack_str_line);
                Matcher m_green = p_green.matcher(pack_str_line);
                if (m_red.find())
                    pack_info_str += "<p style='color:red;'>" + pack_str_line	+ "</p>";
                else if (m_green.find())
                    pack_info_str += "<p style='color:green;'>" + pack_str_line + "</p>";
                else
                    pack_info_str += "<p style='color:gray;'>" + pack_str_line + "</p>";
            }
            pack_str_scanner.close();
            return pack_info_str;
        }

        String atccodeStr() {
            String atc_code_str = "";
            String atc_title_str = "";

            if (_atccode != null) {
                String[] m_code = _atccode.split(";");
                if (m_code.length > 1) {
                    atc_code_str = m_code[0];
                    atc_title_str = m_code[1];
                }
                if (_atcclass != null) {
                    String[] m_class = _atcclass.split(";");
                    String atc_class_str;
                    if (m_class.length == 1) {
                        atc_code_str = "<p>" + atc_code_str + " - " + atc_title_str + "</p><p>" + m_class[0] + "</p>";
                    } else if (m_class.length == 2) { // *** Ver.<1.2.4
                        atc_code_str = "<p>" + atc_code_str + " - " + atc_title_str + "</p><p>" + m_class[1] + "</p>";
                    } else if (m_class.length == 3) { // *** Ver. 1.2.4 and above
                        atc_class_str = "";
                        String[] atc_class_l4_and_l5 = m_class[2].split("#");
                        if (atc_class_l4_and_l5.length > 0)
                            atc_class_str = atc_class_l4_and_l5[atc_class_l4_and_l5.length - 1];
                        atc_code_str = "<p>" + atc_code_str + " - " + atc_title_str + "</p><p>" + atc_class_str + "</p><p>" + m_class[1] + "</p>";
                    }
                } else {
                    atc_code_str = "<p>" + atc_code_str + " - " + atc_title_str + "</p><p>k.A.</p>";
                }
            }
            return atc_code_str;
        }

        String therapyStr() {
            String application_str = "";
            if (_therapy != null) {
                /*  Alternative----
                    application_str = _therapy.replaceAll(";", "<p>");
                    application_str = "<font color=gray size=-1>" + application_str + "</font>";
                */
                String[] apps = _therapy.split(";");
                if (apps.length>1)
                    application_str = "<p>" + apps[0] + "</p><p>" + apps[1] + "</p>";
                else if(apps.length==1)
                    application_str = "<p>" + apps[0] + "</p>";
            }
            return application_str;
        }

        String eancodeStr() {
            if (_packages!=null) {
                String[] packs = _packages.split("\n");
                // Extract first ean code
                if (packs.length>0) {
                    String p[] = packs[0].split("\\|");
                    if (p.length>9)
                        return p[9];
                }
            }
            return _regnrs;
        }

        Map<Integer, String> index_to_titles_map() {
            Map<Integer, String> map = new HashMap<>();

            String[] tt = titles.split(";");
            String[] ids = sections.split(",");
            // Assuming both arrays have the same length - they should!
            int N = tt.length>ids.length ? ids.length : tt.length;
            for (int i=0; i<N; ++i) {
                String section_id = ids[i].replaceAll("(s|S)ection", "");
                map.put(Integer.parseInt(section_id), tt[i]);
            }

            return map;
        }
    }

    public Result index() {
        return ok(index.render("", "", ""));
    }

    public Result javascriptRoutes() {
        return ok(
                play.routing.JavaScriptReverseRouter.create("jsRoutes",
                        controllers.routes.javascript.MainController.setLang(),
                        controllers.routes.javascript.MainController.getFachinfo(),
                        controllers.routes.javascript.MainController.interactionsBasket(),
                        controllers.routes.javascript.MainController.showFullTextSearchResult(),
                        controllers.routes.javascript.MainController.index()))
                .as("text/javascript");
    }

    public Result setLang(String lang) {
        // response().discardCookie("PLAY_LANG");
        response().setHeader("Accept-Language", lang);
        ctx().changeLang(lang);
        // ctx().setTransientLang(lang);
        return index();
    }

    public Result fachinfoId(String lang, long id) {
        Medication m = getMedicationWithId(lang, id);
        return retrieveFachinfo(lang, m, "");
    }

    public Result getFachinfo(String lang, long id) {
        return redirect(controllers.routes.MainController.fachinfoId(lang, id));
    }

    public Result fachinfoEanWithHigh(String lang, String ean, String highlight) {
        Medication m = getMedicationWithEan(lang, ean);
        return retrieveFachinfo(lang, m, highlight);
    }

    public Result fachinfoEan(String lang, String ean) {
        Medication m = getMedicationWithEan(lang, ean);
        return retrieveFachinfo(lang, m, "");
    }

    public Result interactionsBasket() {
        String name = "";
        String interactions_html = "";
        String titles_html = "";
        return ok(index.render(interactions_html, titles_html, name));
    }

    public Result interactionsBasket(String lang, String basket) {
        String article_title = "";

        // Decompose string coming from client and fill up linkedhashmap
        Map<String, Medication> med_basket = new LinkedHashMap<>();
        if (!basket.isEmpty() && !basket.equals("null")) {
            // Decompose the basket string
            String[] eans = basket.split(",", -1);
            for (String ean : eans) {
                if (!ean.isEmpty()) {
                    Medication m = getMedicationWithEan(lang, ean);
                    if (m != null) {
                        article_title = m.getTitle();
                        med_basket.put(ean, m);
                    }
                }
            }
        }
        InteractionsData inter_data = InteractionsData.getInstance();
        String interactions_html = inter_data.updateHtml(med_basket, lang);
        // Associate section titles and anchors
        String[] section_titles = inter_data.sectionTitles();
        String[] section_anchors = inter_data.sectionAnchors();
        String titles_html = "<ul style=\"list-style-type:none;\n\">";
        for (int i = 0; i < section_titles.length; ++i) {
            // Spaces before and after of &rarr; are important...
            String anchor = section_anchors[i]; // section_titles[i].replaceAll("<html>", "").replaceAll("</html>", "").replaceAll(" &rarr; ", "-");
            titles_html += "<li><a onclick=\"move_to_anchor('" + anchor + "')\">" + section_titles[i] + "</a></li>";
        }
        titles_html += "</ul>";
        if (interactions_html == null)
            interactions_html = "";

        return ok(index.render(interactions_html, titles_html, article_title));
    }

    static long ft_row_id;
    static String ft_content;
    static String ft_titles_html;

    public Result showFullTextSearchResult(String lang, String id, String key) {
        long row_id = 0;
        if (id!=null)
            row_id = Long.parseLong(id);

        key = key.replaceAll("\\(.*?\\)", "").trim();

        if (ft_row_id!=row_id) {
            FullTextEntry entry = getFullTextEntryWithId(lang, row_id);

            long startTime = System.currentTimeMillis();

            List<Article> list_of_articles = getArticlesFromRegnrs(lang, entry.getRegnrs()).join();
            // Sort list of articles
            sortByComparator(list_of_articles, ASC);

            Map<String, String> map_of_chapters = entry.getMapOfChapters();

            // List of titles to be displayed in right pane
            LinkedList<String> list_of_titles = new LinkedList<>();

            String content = "<div id=\"fulltext\"><ul>";
            for (Article a : list_of_articles) {

                String first_letter = a.title.substring(0, 1).toUpperCase();
                if (!list_of_titles.contains(first_letter)) {
                    list_of_titles.add(first_letter);
                    content += "<li id=\"" + first_letter + "\">";
                } else {
                    content += "<li>";
                }

                content += "<a onclick=\"display_fachinfo(" + a.eancode + ",'" + key + "')\"><b><small>" + a.title + "</small></b></a><br>";

                Map<Integer, String> index_to_titles_map = a.index_to_titles_map();
                String[] list_of_regs = a.regnrs.split(",");
                for (String r : list_of_regs) {
                    if (map_of_chapters.containsKey(r)) {
                        String[] chapters = map_of_chapters.get(r).split(",");
                        for (String ch : chapters) {
                            if (!ch.isEmpty()) {
                                int c = Integer.parseInt(ch.trim());
                                if (index_to_titles_map.containsKey(c))
                                    content += "<small>" + index_to_titles_map.get(c) + "</small><br>";
                            }
                        }
                    }
                }
                // Find chapters
                content += "</li>";
            }
            content += "</ul></div>";

            String titles_html = "<ul>";
            for (String title : list_of_titles) {
                titles_html += "<li><a onclick=\"move_to_anchor('" + title + "')\">" + title + "</a></li>";
            }
            titles_html += "</ul>";

            content = "<html>" + content + "</html>";

            ft_row_id = row_id;
            ft_titles_html = titles_html;
            ft_content = content;

            long time_for_search = System.currentTimeMillis() - startTime;
            System.out.println(">> Time for search = " + time_for_search / 1000.0f + "s");
        }

        return ok(index.render(ft_content, ft_titles_html, key));
    }

    public Result getName(String lang, String name) {
        CompletableFuture<List<Medication>> future = CompletableFuture.supplyAsync(()->searchName(lang, name));
        CompletableFuture<List<Article>> names = future.thenApplyAsync(a -> a.stream()
                .map(n -> new Article(n.getId(), n.getTitle(), n.getAuth(), n.getAtcCode(), n.getAtcClass(), n.getRegnrs(), n.getApplication(), n.getPackInfo(), n.getPackages(), "", ""))
                .collect(Collectors.toList()));
        return names.thenApply(f -> ok(toJson(f))).join();
    }

    public Result getOwner(String lang, String owner) {
        CompletableFuture<List<Medication>> future = CompletableFuture.supplyAsync(()->searchOwner(lang, owner));
        CompletableFuture<List<Article>> names = future.thenApplyAsync(a -> a.stream()
                .map(n -> new Article(n.getId(), n.getTitle(), n.getAuth(), n.getAtcCode(), n.getAtcClass(), n.getRegnrs(), n.getApplication(), n.getPackInfo(), n.getPackages(), "", ""))
                .collect(Collectors.toList()));
        return names.thenApply(f -> ok(toJson(f))).join();
    }

    public Result getATC(String lang, String atc) {
        CompletableFuture<List<Medication>> future = CompletableFuture.supplyAsync(()->searchATC(lang, atc));
        CompletableFuture<List<Article>> names = future.thenApplyAsync(a -> a.stream()
                .map(n -> new Article(n.getId(), n.getTitle(), n.getAuth(), n.getAtcCode(), n.getAtcClass(), n.getRegnrs(), n.getApplication(), n.getPackInfo(), n.getPackages(), "", ""))
                .collect(Collectors.toList()));
        return names.thenApply(f -> ok(toJson(f))).join();
    }

    public Result getRegnr(String lang, String regnr) {
        CompletableFuture<List<Medication>> future = CompletableFuture.supplyAsync(()->searchRegnr(lang, regnr));
        CompletableFuture<List<Article>> names = future.thenApplyAsync(a -> a.stream()
                .map(n -> new Article(n.getId(), n.getTitle(), n.getAuth(), n.getAtcCode(), n.getAtcClass(), n.getRegnrs(), n.getApplication(), n.getPackInfo(), n.getPackages(), "", ""))
                .collect(Collectors.toList()));
        return names.thenApply(f -> ok(toJson(f))).join();
    }

    public Result getTherapy(String lang, String therapy) {
        CompletableFuture<List<Medication>> future = CompletableFuture.supplyAsync(()->searchTherapy(lang, therapy));
        CompletableFuture<List<Article>> names = future.thenApplyAsync(a -> a.stream()
                .map(n -> new Article(n.getId(), n.getTitle(), n.getAuth(), n.getAtcCode(), n.getAtcClass(), n.getRegnrs(), n.getApplication(), n.getPackInfo(), n.getPackages(), "", ""))
                .collect(Collectors.toList()));
        return names.thenApply(f -> ok(toJson(f))).join();
    }

    public Result getFullText(String lang, String key) {
        CompletableFuture<List<FullTextEntry>> future = CompletableFuture.supplyAsync(()->searchFullText(lang, key));
        CompletableFuture<List<Article>> names = future.thenApplyAsync(a -> a.stream()
                .map(n -> new Article(n.getId(), n.getKeyword(), "", "", "", n.getRegnrs(), "", "", "", "", ""))
                .collect(Collectors.toList()));
        return names.thenApply(f -> ok(toJson(f))).join();
    }

    public CompletableFuture<List<Article>> getArticlesFromRegnrs(String lang, String regnrs) {
        ArrayList<String> list_of_regnrs = new ArrayList<>();
        String[] regs = regnrs.split(",");
        for (String r : regs)
            list_of_regnrs.add(r);
        CompletableFuture<List<Medication>> future = CompletableFuture.supplyAsync(()->searchListRegnrs(lang, list_of_regnrs));
        CompletableFuture<List<Article>> list_of_articles = future.thenApplyAsync(a -> a.stream()
                .map(m -> new Article(m.getId(), m.getTitle(), "", "", "", m.getRegnrs(), "", "", "", m.getSectionTitles(), m.getSectionIds()))
                .collect(Collectors.toList()));
        return list_of_articles;
    }

    private Result retrieveFachinfo(String lang, Medication m, String highlight) {
        if (m!=null) {
            String name = "";
            String titles_html = "";
            //
            String content = m.getContent();
            if (content!=null && !content.isEmpty()) {
                content = content.replaceAll("<html>|</html>|<body>|</body>|<head>|</head>", "");
                if (highlight.length()>3) {
                    content = content.replaceAll(highlight, "<mark>" + highlight + "</mark>");
                    String first_upper = highlight.substring(0,1).toUpperCase() + highlight.substring(1, highlight.length());
                    content = content.replaceAll(first_upper, "<mark>" + first_upper + "</mark>");
                }
                String[] titles = getSectionTitles(lang, m);
                String[] section_ids = m.getSectionIds().split(",");
                name = m.getTitle();
                titles_html = "<ul style=\"list-style-type:none;\n\">";
                for (int i = 0; i < titles.length; ++i) {
                    if (i < section_ids.length)
                        titles_html += "<li><a onclick=\"move_to_anchor('" + section_ids[i] + "')\">" + titles[i] + "</a></li>";
                }
                titles_html += "</ul>";
            } else {
                if (lang.equals("de"))
                    content = "Zu diesem GTIN kann keine Fachinfo gefunden werden.";
                else if (lang.equals("fr"))
                    content = "Votre mot clé de recherche n'a abouti à aucun résultat.";
            }
            // Text-based HTTP response, default encoding: utf-8
            if (content != null) {
                return ok(index.render(content, titles_html, name));
            }
        }
        return ok("Hasta la vista, baby! You just terminated me.");
    }

    private String[] getSectionTitles(String lang, Medication m) {
        // Get section titles from chapters
        String[] section_titles = m.getSectionTitles().split(";");
        // Use abbreviations...
        String[] section_titles_abbr = lang.equals("de") ? models.Constants.SectionTitle_DE : Constants.SectionTitle_FR;
        for (int i = 0; i < section_titles.length; ++i) {
            for (String s : section_titles_abbr) {
                String titleA = section_titles[i].replaceAll(" ", "");
                String titleB = m.getTitle().replaceAll(" ", "");
                // Are we analysing the name of the article?
                if (titleA.toLowerCase().contains(titleB.toLowerCase())) {
                    if (section_titles[i].contains("®"))
                        section_titles[i] = section_titles[i].substring(0, section_titles[i].indexOf("®") + 1);
                    else
                        section_titles[i] = section_titles[i].split(" ")[0].replaceAll("/-", "");
                    break;
                } else if (section_titles[i].toLowerCase().contains(s.toLowerCase())) {
                    section_titles[i] = s;
                    break;
                }
            }
        }
        return section_titles;
    }

    private int numRecords(String lang) {
        int num_rec = -1;
        try {
            Connection conn = lang.equals("de") ? german_db.getConnection() : french_db.getConnection();
            Statement stat = conn.createStatement();
            String query = "select count(*) from amikodb";
            ResultSet rs = stat.executeQuery(query);
            num_rec = rs.getInt(1);
            conn.close();
        } catch (SQLException e) {
            System.err.println(">> SqlDatabase: SQLException in numRecords");
        }
        return num_rec;
    }

    private List<Medication> searchName(String lang, String name) {
        List<Medication> med_titles = new ArrayList<>();

        try {
            Connection conn = lang.equals("de") ? german_db.getConnection() : french_db.getConnection();
            Statement stat = conn.createStatement();
            ResultSet rs;
            // Allow for search to start inside a word...
            if (name.length()>2) {
                String query = "select " + SHORT_TABLE + " from " + DATABASE_TABLE + " where "
                        + KEY_TITLE + " like " + "'" + name + "%' or "
                        + KEY_TITLE + " like " + "'%" + name + "%'";
                rs = stat.executeQuery(query);
            } else {
                String query = "select " + SHORT_TABLE + " from " + DATABASE_TABLE + " where "
                        + KEY_TITLE + " like " + "'" + name + "%'";
                rs = stat.executeQuery(query);
            }
            if (rs!=null) {
                while (rs.next()) {
                    med_titles.add(cursorToShortMedi(rs));
                }
            }
            conn.close();
        } catch (SQLException e) {
            System.err.println(">> SqlDatabase: SQLException in searchName for " + name);
        }

        return med_titles;
    }

    private List<Medication> searchOwner(String lang, String owner) {
        List<Medication> med_auth = new ArrayList<>();

        try {
            Connection conn = lang.equals("de") ? german_db.getConnection() : french_db.getConnection();
            Statement stat = conn.createStatement();
            String query = "select " + SHORT_TABLE + " from " + DATABASE_TABLE
                    + " where " + KEY_AUTH + " like " + "'" + owner + "%'";
            ResultSet rs = stat.executeQuery(query);
            if (rs!=null) {
                while (rs.next()) {
                    med_auth.add(cursorToShortMedi(rs));
                }
            }
            conn.close();
        } catch (SQLException e) {
            System.err.println(">> SqlDatabase: SQLException in searchOwner for " + owner);
        }

        return med_auth;
    }

    private List<Medication> searchATC(String lang, String atccode) {
        List<Medication> med_auth = new ArrayList<>();

        try {
            Connection conn = lang.equals("de") ? german_db.getConnection() : french_db.getConnection();
            Statement stat = conn.createStatement();
            String query = "select " + SHORT_TABLE + " from " + DATABASE_TABLE + " where "
                    + KEY_ATCCODE + " like " + "'%;" + atccode + "%' or "
                    + KEY_ATCCODE + " like " + "'" + atccode + "%' or "
                    + KEY_ATCCODE + " like " + "'% " + atccode + "%' or "
                    + KEY_ATCCLASS + " like " + "'" + atccode + "%' or "
                    + KEY_ATCCLASS + " like " + "'%;" + atccode + "%' or "
                    + KEY_ATCCLASS + " like " + "'%#" + atccode + "%' or "
                    + KEY_SUBSTANCES + " like " + "'%, " + atccode + "%' or "
                    + KEY_SUBSTANCES + " like " + "'" + atccode + "%'";
            ResultSet rs = stat.executeQuery(query);
            if (rs!=null) {
                while (rs.next()) {
                    med_auth.add(cursorToShortMedi(rs));
                }
            }
            conn.close();
        } catch (SQLException e) {
            System.err.println(">> SqlDatabase: SQLException in searcATC!");
        }

        return med_auth;
    }

    private List<Medication> searchRegnr(String lang, String regnr) {
        List<Medication> med_auth = new ArrayList<>();

        try {
            Connection conn = lang.equals("de") ? german_db.getConnection() : french_db.getConnection();
            Statement stat = conn.createStatement();
            String query = "select " + SHORT_TABLE + " from " + DATABASE_TABLE + " where "
                    + KEY_REGNRS + " like " + "'%, " + regnr + "%' or "
                    + KEY_REGNRS + " like " + "'" + regnr + "%'";
            ResultSet rs = stat.executeQuery(query);
            if (rs!=null) {
                while (rs.next()) {
                    med_auth.add(cursorToShortMedi(rs));
                }
            }
            conn.close();
        } catch (SQLException e) {
            System.err.println(">> SqlDatabase: SQLException in searchRegnr!");
        }

        return med_auth;
    }

    private List<Medication> searchTherapy(String lang, String application) {
        List<Medication> med_auth = new ArrayList<>();

        try {
            Connection conn = lang.equals("de") ? german_db.getConnection() : french_db.getConnection();
            Statement stat = conn.createStatement();
            String query = "select " + SHORT_TABLE + " from " + DATABASE_TABLE + " where "
                    + KEY_APPLICATION + " like " + "'%," + application + "%' or "
                    + KEY_APPLICATION + " like " + "'" + application + "%' or "
                    + KEY_APPLICATION + " like " + "'%" + application + "%' or "
                    + KEY_APPLICATION + " like " + "'% " + application + "%' or "
                    + KEY_APPLICATION + " like " + "'%;" + application + "%' or "
                    + KEY_THERAPY + " like " + "'" + application + "%' or "
                    + KEY_INDICATIONS + " like " + "'" + application + "%' or "
                    + KEY_INDICATIONS + " like " + "'%;" + application + "%'";
            ResultSet rs = stat.executeQuery(query);
            if (rs!=null) {
                while (rs.next()) {
                    med_auth.add(cursorToShortMedi(rs));
                }
            }
            conn.close();
        } catch (SQLException e) {
            System.err.println(">> SqlDatabase: SQLException in searchTherapy!");
        }

        return med_auth;
    }

    private Medication getMedicationWithId(String lang, long rowId) {
        try {
            Connection conn = lang.equals("de") ? german_db.getConnection() : french_db.getConnection();
            Statement stat = conn.createStatement();
            String query = "select * from " + DATABASE_TABLE + " where " + KEY_ROWID + "=" + rowId;
            ResultSet rs = stat.executeQuery(query);
            Medication m = cursorToMedi(rs);
            conn.close();
            if (m!=null)
                return m;
        } catch (SQLException e) {
            System.err.println(">> SqlDatabase: SQLException in getContentWithId!");
        }
        return null;
    }

    private Medication getMedicationWithEan(String lang, String eancode) {
        try {
            Connection conn = lang.equals("de") ? german_db.getConnection() : french_db.getConnection();
            Statement stat = conn.createStatement();
            String search_key = KEY_PACKAGES;
            if (eancode.length()==5)
                search_key = KEY_REGNRS;
            String query = "select * from " + DATABASE_TABLE + " where " + search_key + " like " + "'%" + eancode + "%'";
            ResultSet rs = stat.executeQuery(query);
            Medication m = cursorToMedi(rs);
            conn.close();
            if (m!=null)
                return m;
        } catch (SQLException e) {
            System.err.println(">> SqlDatabase: SQLException in getContentWithId!");
        }
        return null;
    }

    private Medication cursorToMedi(ResultSet result) {
        Medication medi = new Medication();
        try {
            medi.setId(result.getLong(1));              // KEY_ROWID
            medi.setTitle(result.getString(2));         // KEY_TITLE
            medi.setAuth(result.getString(3));          // KEY_AUTH
            medi.setAtcCode(result.getString(4));       // KEY_ATCCODE
            medi.setSubstances(result.getString(5));    // KEY_SUBSTANCES
            medi.setRegnrs(result.getString(6));        // KEY_REGNRS
            medi.setAtcClass(result.getString(7));      // KEY_ATCCLASS
            medi.setTherapy(result.getString(8));       // KEY_THERAPY
            medi.setApplication(result.getString(9));   // KEY_APPLICATION
            medi.setIndications(result.getString(10));  // KEY_INDICATIONS
            medi.setCustomerId(result.getInt(11));      // KEY_CUSTOMER_ID
            medi.setPackInfo(result.getString(12));     // KEY_PACK_INFO
            medi.setAddInfo(result.getString(13));      // KEY_ADD_INFO
            medi.setSectionIds(result.getString(14));   // KEY_SECTION_IDS
            medi.setSectionTitles(result.getString(15)); // KEY_SECTION_TITLES
            medi.setContent(result.getString(16));      // KEY_CONTENT
            // KEY_STYLE... (ignore)
            medi.setPackages(result.getString(18)); // KEY_PACKAGES
        } catch (SQLException e) {
            System.err.println(">> SqlDatabase: SQLException in cursorToMedi");
        }
        return medi;
    }

    private Medication cursorToShortMedi(ResultSet result) {
        Medication medi = new Medication();
        try {
            medi.setId(result.getLong(1));              // KEY_ROWID
            medi.setTitle(result.getString(2));         // KEY_TITLE
            medi.setAuth(result.getString(3));          // KEY_AUTH
            medi.setAtcCode(result.getString(4));       // KEY_ATCCODE
            medi.setSubstances(result.getString(5));    // KEY_SUBSTANCES
            medi.setRegnrs(result.getString(6));        // KEY_REGNRS
            medi.setAtcClass(result.getString(7));      // KEY_ATCCLASS
            medi.setTherapy(result.getString(8));       // KEY_THERAPY
            medi.setApplication(result.getString(9));   // KEY_APPLICATION
            medi.setIndications(result.getString(10));  // KEY_INDICATIONS
            medi.setCustomerId(result.getInt(11));      // KEY_CUSTOMER_ID
            medi.setPackInfo(result.getString(12));     // KEY_PACK_INFO
            medi.setAddInfo(result.getString(13));      // KEY_ADD_INFO
            medi.setPackages(result.getString(14));     // KEY_PACKAGES
        } catch (SQLException e) {
            System.err.println(">> SqlDatabase: SQLException in cursorToShortMedi");
        }
        return medi;
    }

    private Medication cursorToVeryShortMedi(ResultSet result) {
        Medication medi = new Medication();
        try {
            medi.setId(result.getLong(1));              // KEY_ROWID
            medi.setTitle(result.getString(2));         // KEY_TITLE
            medi.setRegnrs(result.getString(3));        // KEY_REGNRS
            medi.setSectionIds(result.getString(4));    // KEY_SECTION_IDS
            medi.setSectionTitles(result.getString(5)); // KEY_SECTION_TITLES
        } catch (SQLException e) {
            System.err.println(">> SqlDatabase: SQLException in cursorToVeryShortMedi");
        }
        return medi;
    }

    /**
     * The list of registration numbers fed to the function is split in two lists which
     * are processed separately. We simulate a batch sqlite query by assemblying a long
     * search query containing N registration numbers. We have to take care of the left
     * overs, list_B. The speed up substantial.
     * @param lang
     * @param list_of_regnrs
     * @return list of medications (only titles and id)
     */
    private List<Medication> searchListRegnrs(String lang, ArrayList<String> list_of_regnrs) {
        List<Medication> med_auth = new ArrayList<>();
        int N = 50;
        int A = (list_of_regnrs.size()/N) * N;
        List<String> list_A = list_of_regnrs.subList(0, A); // First list, contains most of the articles
        List<String> list_B = list_of_regnrs.subList(A, list_of_regnrs.size()); // Second list, left overs
        try {
            Connection conn = lang.equals("de") ? german_db.getConnection() : french_db.getConnection();
            Statement stat = conn.createStatement();
            int count = 0;
            String sub_query = "";
            for (String regnr : list_A) {
                sub_query += KEY_REGNRS + " like " + "'%, " + regnr + "%' or "
                        + KEY_REGNRS + " like " + "'" + regnr + "%'";
                count++;
                if (count%N ==0) {
                    String query = "select " + FT_SEARCH_TABLE + " from " + DATABASE_TABLE + " where " + sub_query;
                    ResultSet rs = stat.executeQuery(query);
                    if (rs != null) {
                        while (rs.next()) {
                            med_auth.add(cursorToVeryShortMedi(rs));
                        }
                    }
                    sub_query = "";
                }
                else {
                    sub_query += " or ";
                }
            }
            for (String regnr : list_B) {
                sub_query += KEY_REGNRS + " like " + "'%, " + regnr + "%' or "
                        + KEY_REGNRS + " like " + "'" + regnr + "%' or ";
            }
            String query = "select " + FT_SEARCH_TABLE + " from " + DATABASE_TABLE + " where " + sub_query.substring(0, sub_query.length()-4);
            ResultSet rs = stat.executeQuery(query);
            if (rs != null) {
                while (rs.next()) {
                    med_auth.add(cursorToVeryShortMedi(rs));
                }
            }

            conn.close();
        } catch (SQLException e) {
            System.err.println(">> SqlDatabase: SQLException in searchRegnr!");
        }

        return med_auth;
    }

    private FullTextEntry getFullTextEntryWithId(String lang, long rowId) {
        try {
            Connection conn = frequency_db.getConnection();
            Statement stat = conn.createStatement();
            String query = "select * from " + FREQUENCY_TABLE + " where id=" + rowId;
            ResultSet rs = stat.executeQuery(query);
            FullTextEntry full_text_entry = cursorToFullTextEntry(rs);
            conn.close();
            if (full_text_entry!=null)
                return full_text_entry;
        } catch (SQLException e) {
            System.err.println(">> Frequency DB: SQLException in getFullTextEntryWithId");
        }
        return null;
    }

    private List<FullTextEntry> searchFullText(String lang, String word) {
        List<FullTextEntry> search_results = new ArrayList<>();

        try {
            Connection conn = frequency_db.getConnection();
            Statement stat = conn.createStatement();
            ResultSet rs = null;
            if (word.length()>2) {
                String query = "select * from " + FREQUENCY_TABLE + " where keyword like " + "'" + word + "%'";
                rs = stat.executeQuery(query);
            }
            if (rs!=null) {
                while (rs.next()) {
                    search_results.add(cursorToFullTextEntry(rs));
                }
            }
            conn.close();
        } catch (SQLException e) {
            System.err.println(">> Frequency DB: SQLException in searchFullText for " + word);
        }

        return search_results;
    }

    private FullTextEntry cursorToFullTextEntry(ResultSet result) {
        FullTextEntry entry = new FullTextEntry();
        try {
            entry.setId(result.getLong(1));         // ID
            entry.setKeyword(result.getString(2));  // Keyword
            String regnr = result.getString(3);

            String[] r = regnr.split("\\|", -1);
            Map<String, String> map = new HashMap<>();
            for (String reg : r) {
                String chapters = "";
                // Extract chapters
                if (reg.contains("(") && reg.contains(")"))
                    chapters = reg.substring(reg.indexOf("(")+1, reg.indexOf(")"));
                // Remove parentheses
                reg = reg.replaceAll("\\(.*?\\)", "");
                if (map.containsKey(reg)) {
                    chapters += ", " + map.get(reg);
                }
                map.put(reg, chapters);
            }
            entry.setMapOfChapters(map);            // Map of chapters
            entry.setNumHits(r.length);
        } catch (SQLException e) {
            System.err.println(">> Frequency DB: SQLException in cursorToFullTextEntry");
        }
        return entry;
    }
}

