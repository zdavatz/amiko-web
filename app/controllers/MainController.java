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
import com.typesafe.config.Config;
import play.api.i18n.I18nSupport;
import play.api.i18n.MessagesProvider;
import play.data.DynamicForm;
import play.data.FormFactory;
import play.db.NamedDatabase;
import play.db.Database;
import play.mvc.*;
import play.i18n.Messages;
import play.i18n.MessagesApi;
import play.i18n.Lang;
import views.html.*;

import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import javax.inject.Inject;

import play.libs.ws.*;
import static play.libs.Json.*;
import java.util.concurrent.CompletionStage;

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

    @Inject private Config configuration;
    @Inject WSClient ws;
    @Inject private MessagesApi messagesApi;

    /**
     * Table columns used for fast queries
     */
    private static final String SHORT_TABLE = String.format("%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s",
            KEY_ROWID, KEY_TITLE, KEY_AUTH, KEY_ATCCODE, KEY_SUBSTANCES, KEY_REGNRS,
            KEY_ATCCLASS, KEY_THERAPY, KEY_APPLICATION, KEY_INDICATIONS,
            KEY_CUSTOMER_ID, KEY_PACK_INFO, KEY_ADDINFO, KEY_PACKAGES);

    private static final String FT_SEARCH_TABLE = String.format("%s,%s,%s,%s,%s,%s", KEY_ROWID, KEY_TITLE, KEY_AUTH,
            KEY_REGNRS, KEY_SECTION_IDS, KEY_SECTION_TITLES);

    /**
     * Inject all databases
     */
    @Inject @NamedDatabase("german") Database german_db;
    @Inject @NamedDatabase("french") Database french_db;
    @Inject @NamedDatabase("frequency_de") Database frequency_de_db;
    @Inject @NamedDatabase("frequency_fr") Database frequency_fr_db;

    @Inject FormFactory formFactory;

    Boolean getShowInteractions() {
        try {
            return configuration.getBoolean("feature.interactions");
        } catch (com.typesafe.config.ConfigException.Missing e_) {
            return true;
        }
    }

    Boolean getShowPrescriptions() {
        try {
            return configuration.getBoolean("feature.prescriptions");
        } catch (com.typesafe.config.ConfigException.Missing e_) {
            return false;
        }
    }

    ViewContext getViewContext(Http.Request request) {
        String host = request.host();
        ViewContext ctx = new ViewContext();
        ctx.showInteraction = getShowInteractions();
        ctx.showPrescriptions = getShowPrescriptions();
        if (host.contains("zurrose")) {
            ctx.logo = "ZURROSE";
            ctx.googleAnalyticsId = "UA-20151536-22";
        } else {
            ctx.logo = "DESITIN";
            ctx.googleAnalyticsId = "UA-47115045-2";
        }
        return ctx;
    }

    /**
     * Absolute minimal html-rendering
     * @return
     */
    public Result index(Http.Request request, String atc_query) {
        ViewContext vc = getViewContext(request);
        Messages messages = messagesApi.preferred(request);
        if (!atc_query.equals("")) {
            return ok(index.render("", "", atc_query, "atc", "", vc, messages));
        }
        return ok(index.render("", "", "", "", "", vc, messages));
    }

    public Result prescription(Http.Request request, String lang, String key) {
        ViewContext vc = getViewContext(request);
        Messages messages = messagesApi.preferred(request);
        String html = prescriptions.render("the name").toString();
        return ok(index.render(html, "titles", "", "", "", vc, messages));
    }

    /**
     * These is the list of functions which are called from javascripts/coffeescripts
     * @return
     */
    public Result javascriptRoutes(Http.Request request) {
        return ok(
                play.routing.JavaScriptReverseRouter.create("jsRoutes", "jQuery.ajax", request.host(),
                        controllers.routes.javascript.MainController.setLang(),
                        controllers.routes.javascript.MainController.getFachinfo(),
                        controllers.routes.javascript.MainController.interactionsBasket(),
                        controllers.routes.javascript.MainController.fachinfoRequest(),
                        controllers.routes.javascript.MainController.showFullTextSearchResult(),
                        controllers.routes.javascript.MainController.index()))
                .as("text/javascript");
    }

    /**
     * Set app language
     * @param lang
     * @return
     */
    public Result setLang(Http.Request request, String lang) {
        Lang l = Lang.forCode(lang);
        request = request.withTransientLang(l);
        return index(request, "").withLang(l, messagesApi);
    }

    /**
     * Given an id, retrieve the corresponding medication instance
     * Corresponding route: /fi/id/:id
     * @param lang
     * @param id
     * @return
     */
    public Result fachinfoId(Http.Request request, String lang, long id) {
        Medication m = getMedicationWithId(lang, id);
        return retrieveFachinfo(request, lang, m, "", "", "");
    }

    /**
     * Simple redirect to fachinfoId
     * @param lang
     * @param id
     * @return
     */
    public Result getFachinfo(String lang, long id) {
        return redirect(controllers.routes.MainController.fachinfoId(lang, id));
    }

    /**
     * Given an ean, retrieve corresponding medication instance
     * Corresponding routes: /de/fi/gtin/:gtin && /fr/fi/gtin/:gtin
     * Legacy function
     * @param lang
     * @param ean
     * @return
     */
    public Result fachinfoDirect(Http.Request request, String lang, String ean) {
        Medication m = getMedicationWithEan(lang, ean);
        return retrieveFachinfo(request, lang, m, "", "", "");
    }

    /**
     * Given an ean and a bunch of other parameters, retrieve corresponding medication instance
     * Corresponding routes: /de/fi && /fr/fi
     * @param lang
     * @param ean
     * @param type
     * @param key
     * @param highlight
     * @param anchor
     * @param filter
     * @return
     */
    public Result fachinfoRequest(Http.Request request, String lang, String ean, String type, String key, String highlight, String anchor, String filter) {
        Medication m = getMedicationWithEan(lang, ean);
        if (key.isEmpty())
            return retrieveFachinfo(request, lang, m, type, anchor, highlight);
        else
            return retrieveFachinfo(request, lang, m, type, key, "");
    }

    /**
     * Route: /epha
     * https://github.com/zdavatz/amiko-web/issues/38
     */

    public Result interactionsBasket(Http.Request request) {
        String interactions_html = "";
        String titles_html = "";
        String name = "";
        ViewContext vc = getViewContext(request);
        Messages messages = messagesApi.preferred(request);
        return ok(index.render(interactions_html, titles_html, name, "", "", vc, messages));
    }

    /**
     * Renders the interaction basket given a string of comma separated eans or regnrs
     * Corresponding route: /interactions/:basket
     * @param lang
     * @param basket
     * @return
     */
    public CompletionStage<Result> interactionsBasket(Http.Request request, String lang, String basket) {
        String subdomainLang = request.transientLang().orElse(Lang.forCode(lang)).language();
        boolean showInteraction = getShowInteractions();
        final ViewContext vc = getViewContext(request);
        if (!showInteraction) {
            return CompletableFuture.completedFuture(notFound("Interactions is not enabled"));
        }

        if (!subdomainLang.equals(lang)) {
            // Change lang so message it is another lang
            request = request.withTransientLang(lang);
            Messages messages = messagesApi.preferred(request);
            String baseUrl = messages.at("web_url");
            String sslUrl = baseUrl.replace("http://", "https://");
            String path = controllers.routes.MainController.interactionsBasket(lang, basket).path();
            String newUrl = sslUrl + path;
            return CompletableFuture.completedFuture(redirect(newUrl));
        }

        // Decompose string coming from client and fill up linkedhashmap
        // @maxl 15.03.2017: Allow a max of 90 interactions
        String article_title = "";
        Map<String, Medication> med_basket = new LinkedHashMap<>();
        if (!basket.isEmpty() && !basket.equals("null")) {
            // Decompose the basket string
            String[] eans = basket.split(",", -1);
            int N = eans.length > 90 ? 90 : eans.length;
            for (int i=0; i<N; ++i) {
                if (!eans[i].isEmpty()) {
                    Medication m = getMedicationWithEan(lang, eans[i]);
                    if (m != null) {
                        article_title = m.getTitle();
                        med_basket.put(eans[i], m);
                    }
                }
            }
        }
        final String final_article_title = article_title;
        InteractionsData inter_data = InteractionsData.getInstance();
        final Messages messages = messagesApi.preferred(request);
        return inter_data.updateHtml(ws, med_basket, lang).thenApply((interactions_html)-> {
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
            return ok(index.render(interactions_html, titles_html, final_article_title, "", "", vc, messages));
        });
    }

    public Result interactionsBasketWithoutLang(Http.Request request, String basket) {
        String lang = request.transientLang().orElse(Lang.forCode("de")).language();
        return redirect(controllers.routes.MainController.interactionsBasket(lang, basket));
    }

    /**
     * Given an id (hash value), retrieve entry in frequency database and display list of related articles
     * Corresponding routes: /fulltext && /de/fulltext && /fr/fulltext
     * @param lang
     * @param keyword
     * @param key
     * @param filter
     * @return
     */
    public Result showFullTextSearchResult(Http.Request request, String lang, String keyword, String key, String filter) {
        FullTextEntry entry = getFullTextEntryWithKeyword(lang, keyword);

        if (entry == null) {
            return notFound("Result with this id is not found");
        }

        // This operation takes time...
        List<Article> list_of_articles = getArticlesFromRegnrs(lang, entry.getRegnrs()).join();

        FullTextSearch full_text_search = FullTextSearch.getInstance();

        Pair<String, String> fts = full_text_search.updateHtml(lang, list_of_articles, entry.getMapOfChapters(), keyword, key, filter);

        ViewContext vc = getViewContext(request);
        Messages messages = messagesApi.preferred(request);
        return ok(index.render(fts.first, fts.second, key, "", "", vc, messages));
    }

    public Result getName(String lang, String name) {
        CompletableFuture<List<Medication>> future = CompletableFuture.supplyAsync(()->searchName(lang, name));
        CompletableFuture<List<Article>> names = future.thenApplyAsync(a -> a.stream()
                .map(n -> new Article(n.getId(), "", n.getTitle(), "", n.getAuth(), n.getAtcCode(), n.getAtcClass(), n.getRegnrs(), n.getApplication(), n.getPackInfo(), n.getPackages(), "", ""))
                .collect(Collectors.toList()));
        return names.thenApply(f -> ok(toJson(f))).join();
    }

    public Result getOwner(String lang, String owner) {
        CompletableFuture<List<Medication>> future = CompletableFuture.supplyAsync(()->searchOwner(lang, owner));
        CompletableFuture<List<Article>> names = future.thenApplyAsync(a -> a.stream()
                .map(n -> new Article(n.getId(), "", n.getTitle(), "", n.getAuth(), n.getAtcCode(), n.getAtcClass(), n.getRegnrs(), n.getApplication(), n.getPackInfo(), n.getPackages(), "", ""))
                .collect(Collectors.toList()));
        return names.thenApply(f -> ok(toJson(f))).join();
    }

    public Result getATC(String lang, String atc) {
        CompletableFuture<List<Medication>> future = CompletableFuture.supplyAsync(()->searchATC(lang, atc));
        CompletableFuture<List<Article>> names = future.thenApplyAsync(a -> a.stream()
                .map(n -> new Article(n.getId(), "", n.getTitle(), "", n.getAuth(), n.getAtcCode(), n.getAtcClass(), n.getRegnrs(), n.getApplication(), n.getPackInfo(), n.getPackages(), "", ""))
                .collect(Collectors.toList()));
        return names.thenApply(f -> ok(toJson(f))).join();
    }

    public Result getRegnr(String lang, String regnr) {
        CompletableFuture<List<Medication>> future = CompletableFuture.supplyAsync(()->searchRegnr(lang, regnr));
        CompletableFuture<List<Article>> names = future.thenApplyAsync(a -> a.stream()
                .map(n -> new Article(n.getId(), "", n.getTitle(), "", n.getAuth(), n.getAtcCode(), n.getAtcClass(), n.getRegnrs(), n.getApplication(), n.getPackInfo(), n.getPackages(), "", ""))
                .collect(Collectors.toList()));
        return names.thenApply(f -> ok(toJson(f))).join();
    }

    public Result getTherapy(String lang, String therapy) {
        CompletableFuture<List<Medication>> future = CompletableFuture.supplyAsync(()->searchTherapy(lang, therapy));
        CompletableFuture<List<Article>> names = future.thenApplyAsync(a -> a.stream()
                .map(n -> new Article(n.getId(), "", n.getTitle(), "", n.getAuth(), n.getAtcCode(), n.getAtcClass(), n.getRegnrs(), n.getApplication(), n.getPackInfo(), n.getPackages(), "", ""))
                .collect(Collectors.toList()));
        return names.thenApply(f -> ok(toJson(f))).join();
    }

    public Result getFullText(String lang, String key) {
        CompletableFuture<List<FullTextEntry>> future = CompletableFuture.supplyAsync(()->searchFullText(lang, key));
        CompletableFuture<List<Article>> names = future.thenApplyAsync(a -> a.stream()
                .map(n -> new Article(0, n.getHash(), n.getTitle(), n.getKeyword(), "", "", "", n.getRegnrs(), "", "", "", "", ""))
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
                .map(m -> new Article(m.getId(), "", m.getTitle(), "", m.getAuth(), "", "", m.getRegnrs(), "", "", "", m.getSectionTitles(), m.getSectionIds()))
                .collect(Collectors.toList()));
        return list_of_articles;
    }

    /**
     * Main html-rendering function in Compendium mode
     * @param lang
     * @param m
     * @param type
     * @param key
     * @param highlight
     * @return
     */
    private Result retrieveFachinfo(Http.Request request, String lang, Medication m, String type, String key, String highlight) {
        if (m!=null) {
            String name = "";
            String titles_html = "";
            //
            String content = m.getContent();
            if (content!=null && !content.isEmpty()) {
                content = content.replaceAll("<html>|</html>|<body>|</body>|<head>|</head>", "");
                if (highlight.length()>3) {
                    // Marks the keyword in the html
                    String first_upper = highlight.substring(0,1).toUpperCase() + highlight.substring(1, highlight.length());
                    content += "<script>highlightText(document.body, '" + highlight + "')</script>";
                    content += "<script>highlightText(document.body, '" + first_upper + "')</script>";
                }
                content = content.replaceAll("#EEEEEE", "var(--background-color-gray)");
                content = content.replaceAll("#eeeeee", "var(--background-color-gray)");
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

            ViewContext vc = getViewContext(request);
            Messages messages = messagesApi.preferred(request);
            // Text-based HTTP response, default encoding: utf-8
            if (content != null) {
                if (highlight.length() > 3) {
                    return ok(index.render(content, titles_html, name, "", "'" + key + "'", vc, messages));
                } else {
                    return ok(index.render(content, titles_html, key, "", "", vc, messages));
                }
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
            String replaced = name.toLowerCase()
                .replaceAll("[aáàäâã]", "\\[aáàäâã\\]")
                .replaceAll("[eéèëê]", "\\[eéèëê\\]")
                .replaceAll("[iíìî]", "\\[iíìî\\]")
                .replaceAll("[oóòöôõ]", "\\[oóòöôõ\\]")
                .replaceAll("[uúùüû]", "\\[uúùüû\\]")
                .replace("*", "[*]")
                .replace("?", "[?]");

            if (name.length()>2) {
                String query = "select " + SHORT_TABLE + " from " + DATABASE_TABLE + " where "
                        + "lower(" + KEY_TITLE + ") GLOB " + "'*" + replaced + "*'";
                rs = stat.executeQuery(query);
            } else {
                String query = "select " + SHORT_TABLE + " from " + DATABASE_TABLE + " where "
                        + "lower(" + KEY_TITLE + ") GLOB " + "'" + replaced + "*'";
                rs = stat.executeQuery(query);
            }
            if (rs!=null) {
                while (rs.next()) {
                    med_titles.add(cursorToShortMedi(rs));
                }
            }
            conn.close();
        } catch (SQLException e) {
            System.err.println(">> SqlDatabase: SQLException in searchName for " + name + ": " + e.toString());
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
        Medication m = null;
        try {
            Connection conn = lang.equals("de") ? german_db.getConnection() : french_db.getConnection();
            Statement stat = conn.createStatement();
            String query = "select * from " + DATABASE_TABLE + " where " + KEY_ROWID + "=" + rowId;
            ResultSet rs = stat.executeQuery(query);
            if (rs!=null)
                m = cursorToMedi(rs);
            conn.close();
        } catch (SQLException e) {
            System.err.println(">> SqlDatabase: SQLException in getContentWithId!");
        }
        return m;
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
            System.err.println(">> SqlDatabase: SQLException in cursorToMedi " + e.toString());
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
     * @param lang
     * @param keyword
     * @return
     */
    public FullTextEntry getFullTextEntryWithKeyword(String lang, String keyword) {
        try {
            Connection conn = lang.equals("de") ? frequency_de_db.getConnection() : frequency_fr_db.getConnection();
            String query = "select * from " + FREQUENCY_TABLE + " where keyword = ?";
            PreparedStatement stat = conn.prepareStatement(query);
            stat.setString(1, keyword);

            ResultSet rs = stat.executeQuery();
            if (!rs.next()) {
                return null;
            }
            FullTextEntry full_text_entry = cursorToFullTextEntry(rs);
            conn.close();
            if (full_text_entry!=null)
                return full_text_entry;
        } catch (SQLException e) {
            System.err.println(">> Frequency DB: SQLException in getFullTextEntryWithKeyword: " + e.getMessage());
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

