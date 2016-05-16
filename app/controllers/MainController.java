package controllers;

import models.Medication;
import play.db.Database;
import play.mvc.*;
import views.html.*;
import play.data.FormFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.SQLException;
import java.util.stream.Collectors;

import javax.inject.Inject;

import static play.libs.Json.*;

public class MainController extends Controller {

    public static final String KEY_ROWID = "_id";
    public static final String KEY_TITLE = "title";
    public static final String KEY_AUTH = "auth";
    public static final String KEY_ATCCODE = "atc";
    public static final String KEY_SUBSTANCES = "substances";
    public static final String KEY_REGNRS = "regnrs";
    public static final String KEY_ATCCLASS = "atc_class";
    public static final String KEY_THERAPY = "tindex_str";
    public static final String KEY_APPLICATION = "application_str";
    public static final String KEY_INDICATIONS = "indications_str";
    public static final String KEY_CUSTOMER_ID = "customer_id";
    public static final String KEY_PACK_INFO = "pack_info_str";
    public static final String KEY_ADDINFO = "add_info_str";

    private static final String DATABASE_TABLE = "amikodb";

    /**
     * Table columns used for fast queries
     */
    private static final String SHORT_TABLE = String.format("%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s",
            KEY_ROWID, KEY_TITLE, KEY_AUTH, KEY_ATCCODE, KEY_SUBSTANCES, KEY_REGNRS,
            KEY_ATCCLASS, KEY_THERAPY, KEY_APPLICATION, KEY_INDICATIONS,
            KEY_CUSTOMER_ID, KEY_PACK_INFO, KEY_ADDINFO);

    @Inject Database db;
    @Inject FormFactory formFactory;

    private class Article {
        Article(long id, String title, String author, String atc_code, String substances) {
            this.id = id;
            this.title = title;
            this.author = author;
            this.atc_code = atc_code;
            this.substances = substances;
        }
        public long id;
        public String title;
        public String author;
        public String atc_code;
        public String substances;
    }

    public Result index() {
        return ok(index.render("", "", ""));
    }

    public Result fachinfo(long id) {
        Medication m = getMedicationWithId(id);
        String content = m.getContent().replaceAll("<html>|</html>|<body>|</body>|<head>|</head>", "");
        String[] titles = getSectionTitles(m);
        String[] section_ids = m.getSectionIds().split(",");
        String name = m.getTitle();
        String titles_html;
        titles_html = "<ul style=\"list-style-type:none;\n\">";
        for (int i = 0; i < titles.length; ++i) {
            if (i < section_ids.length)
                titles_html += "<li><a onclick=\"move_to_anchor('" + section_ids[i] + "')\">" + titles[i] + "</a></li>";
        }
        titles_html += "</ul>";
        // Text-based HTTP response, default encoding: utf-8
        if (content!=null) {
            return ok(index.render(content, titles_html, name));
        }
        return ok("WEIRD");
    }

    public String[] getSectionTitles(Medication m) {
        // Get section titles from chapters
        String[] section_titles = m.getSectionTitles().split(";");
        // Use abbreviations...
        String[] section_titles_abbr = models.Constants.SectionTitle_DE;
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

    public Result javascriptRoutes() {
        return ok(
                play.routing.JavaScriptReverseRouter.create("jsRoutes", routes.javascript.MainController.getFachinfo()))
                .as("text/javascript");
    }

    public Result getArticle(String name) {
        CompletableFuture<List<Medication>> future = CompletableFuture.supplyAsync(()->searchTitle(name));
        CompletableFuture<List<Article>> names = future.thenApplyAsync(a -> a.stream()
                .map(n -> new Article(n.getId(), n.getTitle(), n.getAuth(), n.getAtcCode(), n.getSubstances()))
                .collect(Collectors.toList()));
        return names.thenApply(f -> ok(toJson(f))).join();
    }

    public Result getFachinfo(long id) {
        return redirect(controllers.routes.MainController.fachinfo(id));
    }

    public Result getArticles() {
        String article = formFactory.form().bindFromRequest().get("name");
        CompletableFuture<List<Medication>> future = CompletableFuture.supplyAsync(()->searchTitle(article));
        CompletableFuture<List<String>> names = future.thenApplyAsync(a -> a.stream()
                .map(n -> n.getTitle())
                .collect(Collectors.toList()));
        Result r = names.thenApply(f -> ok(toJson(f))).join();
        return ok(index.render("", "", ""));
    }

    /*

     */
    public CompletionStage<Result> getArticlesAsync() {
        long starttime = java.lang.System.currentTimeMillis();
        String article = formFactory.form(String.class).bindFromRequest().get();
        CompletableFuture<List<Medication>> future = CompletableFuture.supplyAsync(()->searchTitle(article));
        CompletableFuture<List<String>> names = future.thenApplyAsync(a -> a.stream()
                .map(n -> n.getTitle())
                .collect(Collectors.toList()));
        // return names.thenApply(f -> ok(String.format("Time: %.3f\n", (System.currentTimeMillis() - starttime) / 1000.0) + toJson(f)));
        return names.thenApply(f -> ok(toJson(f)));
    }

    public CompletionStage<Result> asyncDBTest() {
        CompletableFuture<Integer> future = CompletableFuture.supplyAsync(()-> numRecords());
        return future.thenApply(f -> ok(String.format("Num records = %d\n", f)));
    }

    private int numRecords() {
        int num_rec = -1;
        try {
            Connection conn = db.getConnection();
            Statement stat = conn.createStatement();
            String query = "select count(*) from amikodb";
            ResultSet rs = stat.executeQuery(query);
            num_rec = rs.getInt(1);
            conn.close();
        } catch (SQLException e) {

        }
        return num_rec;
    }

    public List<Medication> searchTitle(String title) {
        List<Medication> med_titles = new ArrayList<>();

        try {
            Connection conn = db.getConnection();
            Statement stat = conn.createStatement();
            ResultSet rs;
            // Allow for search to start inside a word...
            if (title.length()>2) {
                String query = "select " + SHORT_TABLE + " from " + DATABASE_TABLE + " where "
                        + KEY_TITLE + " like " + "'" + title + "%' or "
                        + KEY_TITLE + " like " + "'%" + title + "%'";
                rs = stat.executeQuery(query);
            } else {
                String query = "select " + SHORT_TABLE + " from " + DATABASE_TABLE + " where "
                        + KEY_TITLE + " like " + "'" + title + "%'";
                rs = stat.executeQuery(query);
            }
            if (rs!=null) {
                while (rs.next()) {
                    med_titles.add(cursorToShortMedi(rs));
                }
            }
            conn.close();
        } catch (SQLException e) {
            System.err.println(">> SqlDatabase: SQLException in searchTitle!");
        }

        return med_titles;
    }

    public Medication getMedicationWithId(long rowId) {
        try {
            Connection conn = db.getConnection();
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
        } catch (SQLException e) {
            System.err.println(">> SqlDatabase: SQLException in cursorToShortMedi");
        }
        return medi;
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
}

