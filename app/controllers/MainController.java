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

import models.*;
import play.db.NamedDatabase;
import play.db.Database;
import play.mvc.*;
import views.html.*;

import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
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

    /**
     * Table columns used for fast queries
     */
    private static final String SHORT_TABLE = String.format("%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s",
            KEY_ROWID, KEY_TITLE, KEY_AUTH, KEY_ATCCODE, KEY_SUBSTANCES, KEY_REGNRS,
            KEY_ATCCLASS, KEY_THERAPY, KEY_APPLICATION, KEY_INDICATIONS,
            KEY_CUSTOMER_ID, KEY_PACK_INFO, KEY_ADDINFO, KEY_PACKAGES);

    private static final String FT_SEARCH_TABLE = String.format("%s,%s,%s,%s,%s,%s", KEY_ROWID, KEY_TITLE, KEY_AUTH,
            KEY_REGNRS, KEY_SECTION_IDS, KEY_SECTION_TITLES);

    @Inject @NamedDatabase("german") Database german_db;
    @Inject @NamedDatabase("french") Database french_db;
    @Inject @NamedDatabase("frequency_de") Database frequency_de_db;
    @Inject @NamedDatabase("frequency_fr") Database frequency_fr_db;

    public Result index() {
        return ok(index.render("", "", "", ""));
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
        return retrieveFachinfo(lang, m, "", "");
    }

    public Result getFachinfo(String lang, long id) {
        return redirect(controllers.routes.MainController.fachinfoId(lang, id));
    }

    public Result fachinfoEanWithHigh(String lang, String ean, String highlight, String anchor, String filter) {
        Medication m = getMedicationWithEan(lang, ean);
        return retrieveFachinfo(lang, m, highlight, anchor);
    }

    public Result fachinfoEan(String lang, String ean) {
        Medication m = getMedicationWithEan(lang, ean);
        return retrieveFachinfo(lang, m, "", "");
    }

    public Result interactionsBasket() {
        String name = "";
        String interactions_html = "";
        String titles_html = "";
        return ok(index.render(interactions_html, titles_html, name, ""));
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

        return ok(index.render(interactions_html, titles_html, article_title, ""));
    }

    /**
     * REFACTORING! Refactor the following code later on!
     * Goes in a separate class
     */

    static String ft_row_id = "";
    static String ft_filter = "";
    static String ft_titles = "";
    static String ft_content = "";

    public Result showFullTextSearchResult(String lang, String id, String key, String filter) {
        String row_id = id;

        if (!ft_row_id.equals(row_id) || !ft_filter.equals(filter)) {
            ft_filter = filter;
            ft_row_id = row_id;

            FullTextEntry entry = getFullTextEntryWithId(lang, row_id);

            // This operation takes time...
            List<Article> list_of_articles = getArticlesFromRegnrs(lang, entry.getRegnrs()).join();

            FullTextSearch full_text_search = FullTextSearch.getInstance();

            Pair<String, String> fts = full_text_search.updateHtml(lang, list_of_articles, entry.getMapOfChapters(), id, key, filter);
            ft_content = fts.first;
            ft_titles = fts.second;
        }

        return ok(index.render(ft_content, ft_titles, key, ""));
    }

    public Result getName(String lang, String name) {
        CompletableFuture<List<Medication>> future = CompletableFuture.supplyAsync(()->searchName(lang, name));
        CompletableFuture<List<Article>> names = future.thenApplyAsync(a -> a.stream()
                .map(n -> new Article(n.getId(), "", n.getTitle(), n.getAuth(), n.getAtcCode(), n.getAtcClass(), n.getRegnrs(), n.getApplication(), n.getPackInfo(), n.getPackages(), "", ""))
                .collect(Collectors.toList()));
        return names.thenApply(f -> ok(toJson(f))).join();
    }

    public Result getOwner(String lang, String owner) {
        CompletableFuture<List<Medication>> future = CompletableFuture.supplyAsync(()->searchOwner(lang, owner));
        CompletableFuture<List<Article>> names = future.thenApplyAsync(a -> a.stream()
                .map(n -> new Article(n.getId(), "", n.getTitle(), n.getAuth(), n.getAtcCode(), n.getAtcClass(), n.getRegnrs(), n.getApplication(), n.getPackInfo(), n.getPackages(), "", ""))
                .collect(Collectors.toList()));
        return names.thenApply(f -> ok(toJson(f))).join();
    }

    public Result getATC(String lang, String atc) {
        CompletableFuture<List<Medication>> future = CompletableFuture.supplyAsync(()->searchATC(lang, atc));
        CompletableFuture<List<Article>> names = future.thenApplyAsync(a -> a.stream()
                .map(n -> new Article(n.getId(), "", n.getTitle(), n.getAuth(), n.getAtcCode(), n.getAtcClass(), n.getRegnrs(), n.getApplication(), n.getPackInfo(), n.getPackages(), "", ""))
                .collect(Collectors.toList()));
        return names.thenApply(f -> ok(toJson(f))).join();
    }

    public Result getRegnr(String lang, String regnr) {
        CompletableFuture<List<Medication>> future = CompletableFuture.supplyAsync(()->searchRegnr(lang, regnr));
        CompletableFuture<List<Article>> names = future.thenApplyAsync(a -> a.stream()
                .map(n -> new Article(n.getId(), "", n.getTitle(), n.getAuth(), n.getAtcCode(), n.getAtcClass(), n.getRegnrs(), n.getApplication(), n.getPackInfo(), n.getPackages(), "", ""))
                .collect(Collectors.toList()));
        return names.thenApply(f -> ok(toJson(f))).join();
    }

    public Result getTherapy(String lang, String therapy) {
        CompletableFuture<List<Medication>> future = CompletableFuture.supplyAsync(()->searchTherapy(lang, therapy));
        CompletableFuture<List<Article>> names = future.thenApplyAsync(a -> a.stream()
                .map(n -> new Article(n.getId(), "", n.getTitle(), n.getAuth(), n.getAtcCode(), n.getAtcClass(), n.getRegnrs(), n.getApplication(), n.getPackInfo(), n.getPackages(), "", ""))
                .collect(Collectors.toList()));
        return names.thenApply(f -> ok(toJson(f))).join();
    }

    public Result getFullText(String lang, String key) {
        CompletableFuture<List<FullTextEntry>> future = CompletableFuture.supplyAsync(()->searchFullText(lang, key));
        CompletableFuture<List<Article>> names = future.thenApplyAsync(a -> a.stream()
                .map(n -> new Article(0, n.getHash(), n.getKeyword(), "", "", "", n.getRegnrs(), "", "", "", "", ""))
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
                .map(m -> new Article(m.getId(), "", m.getTitle(), m.getAuth(), "", "", m.getRegnrs(), "", "", "", m.getSectionTitles(), m.getSectionIds()))
                .collect(Collectors.toList()));
        return list_of_articles;
    }

    private Result retrieveFachinfo(String lang, Medication m, String highlight, String anchor) {
        if (m!=null) {
            String name = "";
            String titles_html = "";
            //
            String content = m.getContent();
            if (content!=null && !content.isEmpty()) {
                content = content.replaceAll("<html>|</html>|<body>|</body>|<head>|</head>", "");
                if (highlight.length()>3) {
                    // Marks the keyword in the html
                    content = content.replaceAll(highlight, "<mark>" + highlight + "</mark>");
                    String first_upper = highlight.substring(0,1).toUpperCase() + highlight.substring(1, highlight.length());
                    content = content.replaceAll(first_upper, "<mark>" + first_upper + "</mark>");
                }
                Article article = new Article(m.getTitle(), m.getSectionTitles());
                String[] titles = article.sectionTitles(lang);
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
                return ok(index.render(content, titles_html, name, "'" + anchor + "'"));
            }
        }
        return ok("Hasta la vista, baby! You just terminated me.");
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
            medi.setAuth(result.getString(3));          // KEY_AUTH
            medi.setRegnrs(result.getString(4));        // KEY_REGNRS
            medi.setSectionIds(result.getString(5));    // KEY_SECTION_IDS
            medi.setSectionTitles(result.getString(6)); // KEY_SECTION_TITLES
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

            String query = "";
            if (sub_query.length()>4) {
                query = "select " + FT_SEARCH_TABLE + " from " + DATABASE_TABLE + " where " + sub_query.substring(0, sub_query.length() - 4);
            }
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

    /**
     * Note: the rowId is a 10 digit hash
     * @param lang
     * @param rowId
     * @return
     */
    public FullTextEntry getFullTextEntryWithId(String lang, String rowId) {
        try {
            Connection conn = lang.equals("de") ? frequency_de_db.getConnection() : frequency_fr_db.getConnection();
            Statement stat = conn.createStatement();
            String query = "select * from " + FREQUENCY_TABLE + " where id='" + rowId + "'";

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

    /**
     * Retrieve entries in the DB which contain the given word
     * @param lang
     * @param word
     * @return
     */
    private List<FullTextEntry> searchFullText(String lang, String word) {
        List<FullTextEntry> search_results = new ArrayList<>();

        try {
            Connection conn = lang.equals("de") ? frequency_de_db.getConnection() : frequency_fr_db.getConnection();
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
            entry.setHash(result.getString(1));     // ID/Hash
            entry.setKeyword(result.getString(2));  // Keyword
            String regnr = result.getString(3);

            String[] r = regnr.split("\\|", -1);
            HashSet<String> set_of_r = new HashSet<>(Arrays.asList(r));
            Map<String, String> map = new HashMap<>();
            for (String reg : set_of_r) {
                String chapters = "";
                // Extract chapters from parentheses, format 58444(6,7,8)
                if (reg.contains("(") && reg.contains(")"))
                    chapters = reg.substring(reg.indexOf("(")+1, reg.indexOf(")"));
                // Remove parentheses, what remains are the comma-separated chapters
                reg = reg.replaceAll("\\(.*?\\)", "");
                if (map.containsKey(reg)) {
                    chapters += "," + map.get(reg);
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

