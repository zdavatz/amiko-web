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

import models.Medication;
import play.db.Database;
import play.mvc.*;
import views.html.*;
import play.data.FormFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.CompletableFuture;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.SQLException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
    public static final String KEY_PACKAGES = "packages";

    private static final String DATABASE_TABLE = "amikodb";

    /**
     * Table columns used for fast queries
     */
    private static final String SHORT_TABLE = String.format("%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s",
            KEY_ROWID, KEY_TITLE, KEY_AUTH, KEY_ATCCODE, KEY_SUBSTANCES, KEY_REGNRS,
            KEY_ATCCLASS, KEY_THERAPY, KEY_APPLICATION, KEY_INDICATIONS,
            KEY_CUSTOMER_ID, KEY_PACK_INFO, KEY_ADDINFO, KEY_PACKAGES);

    @Inject Database db;
    @Inject FormFactory formFactory;

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

        // Interface variables
        public long id = 0;
        public String title = "";
        public String author = "";
        public String atccode = "";
        public String regnrs = "";
        public String therapy = "";
        public String packinfo = "";
        public String eancode = "";

        Article(long _id, String _title, String _author, String _atccode, String _atcclass, String _regnrs, String _therapy, String _packinfo, String _packages) {
            // Private
            this._title = _title;
            this._author = _author;
            this._atccode = _atccode;
            this._regnrs = _regnrs;
            this._therapy = _therapy;
            this._packinfo = _packinfo;
            this._atcclass = _atcclass;
            this._packages = _packages;

            // Interface
            this.id = _id;
            this.title = _title;
            this.packinfo = packinfoStr();
            this.author = _author;
            this.atccode = atccodeStr();
            this.regnrs = _regnrs;
            this.therapy = therapyStr();
            this.eancode = eancodeStr();
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
                    pack_info_str += "<font color=red size=-1><p>" + pack_str_line	+ "</p></font>";
                else if (m_green.find())
                    pack_info_str += "<font color=green size=-1><p>" + pack_str_line + "</p></font>";
                else
                    pack_info_str += "<font color=gray size=-1><p>" + pack_str_line + "</p></font>";
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
                        atc_code_str = "<font color=gray size=-1><p>" + atc_code_str + " - " + atc_title_str + "</p><p>" + m_class[0] + "</p></font>";
                    } else if (m_class.length == 2) { // *** Ver.<1.2.4
                        atc_code_str = "<font color=gray size=-1><p>" + atc_code_str + " - " + atc_title_str + "</p><p>" + m_class[1] + "</p></font>";
                    } else if (m_class.length == 3) { // *** Ver. 1.2.4 and above
                        atc_class_str = "";
                        String[] atc_class_l4_and_l5 = m_class[2].split("#");
                        if (atc_class_l4_and_l5.length > 0)
                            atc_class_str = atc_class_l4_and_l5[atc_class_l4_and_l5.length - 1];
                        atc_code_str = "<font color=gray size=-1><p>" + atc_code_str + " - " + atc_title_str + "</p><p>" + atc_class_str + "</p><p>" + m_class[1] + "</p></font>";
                    }
                } else {
                    atc_code_str = "<font color=gray size=-1><p>" + atc_code_str + " - " + atc_title_str + "</p><p>k.A.</p></font>";
                }
            }
            return atc_code_str;
        }

        String therapyStr() {
            String application_str = "";
            if (_therapy != null) {
                application_str = _therapy.replaceAll(";", "<p>");
                application_str = "<font color=gray size=-1>" + application_str + "</font>";
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
            return "0000000000000";
        }
    }

    public Result index() {
        return ok(index.render("", "", ""));
    }

    public Result retrieveFachinfo(Medication m) {
        if (m!=null) {
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
            if (content != null) {
                return ok(index.render(content, titles_html, name));
            }
        }
        return ok("Hasta la vista, baby! You just terminated me.");
    }

    public Result fachinfoId(long id) {
        Medication m = getMedicationWithId(id);
        return retrieveFachinfo(m);
    }

    public Result fachinfoEan(String ean) {
        Medication m = getMedicationWithEan(ean);
        return retrieveFachinfo(m);
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

    public Result getFachinfo(long id) {
        return redirect(controllers.routes.MainController.fachinfoId(id));
    }

    public Result getName(String name) {
        CompletableFuture<List<Medication>> future = CompletableFuture.supplyAsync(()->searchName(name));
        CompletableFuture<List<Article>> names = future.thenApplyAsync(a -> a.stream()
                .map(n -> new Article(n.getId(), n.getTitle(), n.getAuth(), n.getAtcCode(), n.getAtcClass(), n.getRegnrs(), n.getApplication(), n.getPackInfo(), n.getPackages()))
                .collect(Collectors.toList()));
        return names.thenApply(f -> ok(toJson(f))).join();
    }

    public Result getOwner(String owner) {
        CompletableFuture<List<Medication>> future = CompletableFuture.supplyAsync(()->searchOwner(owner));
        CompletableFuture<List<Article>> names = future.thenApplyAsync(a -> a.stream()
                .map(n -> new Article(n.getId(), n.getTitle(), n.getAuth(), n.getAtcCode(), n.getAtcClass(), n.getRegnrs(), n.getApplication(), n.getPackInfo(), n.getPackages()))
                .collect(Collectors.toList()));
        return names.thenApply(f -> ok(toJson(f))).join();
    }

    public Result getATC(String atc) {
        CompletableFuture<List<Medication>> future = CompletableFuture.supplyAsync(()->searchATC(atc));
        CompletableFuture<List<Article>> names = future.thenApplyAsync(a -> a.stream()
                .map(n -> new Article(n.getId(), n.getTitle(), n.getAuth(), n.getAtcCode(), n.getAtcClass(), n.getRegnrs(), n.getApplication(), n.getPackInfo(), n.getPackages()))
                .collect(Collectors.toList()));
        return names.thenApply(f -> ok(toJson(f))).join();
    }

    public Result getRegnr(String regnr) {
        CompletableFuture<List<Medication>> future = CompletableFuture.supplyAsync(()->searchRegnr(regnr));
        CompletableFuture<List<Article>> names = future.thenApplyAsync(a -> a.stream()
                .map(n -> new Article(n.getId(), n.getTitle(), n.getAuth(), n.getAtcCode(), n.getAtcClass(), n.getRegnrs(), n.getApplication(), n.getPackInfo(), n.getPackages()))
                .collect(Collectors.toList()));
        return names.thenApply(f -> ok(toJson(f))).join();
    }

    public Result getTherapy(String therapy) {
        CompletableFuture<List<Medication>> future = CompletableFuture.supplyAsync(()->searchTherapy(therapy));
        CompletableFuture<List<Article>> names = future.thenApplyAsync(a -> a.stream()
                .map(n -> new Article(n.getId(), n.getTitle(), n.getAuth(), n.getAtcCode(), n.getAtcClass(), n.getRegnrs(), n.getApplication(), n.getPackInfo(), n.getPackages()))
                .collect(Collectors.toList()));
        return names.thenApply(f -> ok(toJson(f))).join();
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

    public List<Medication> searchName(String name) {
        List<Medication> med_titles = new ArrayList<>();

        try {
            Connection conn = db.getConnection();
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
            System.err.println(">> SqlDatabase: SQLException in searchName!");
        }

        return med_titles;
    }

    public List<Medication> searchOwner(String owner) {
        List<Medication> med_auth = new ArrayList<>();

        try {
            Connection conn = db.getConnection();
            Statement stat = conn.createStatement();
            ResultSet rs;
            String query = "select " + SHORT_TABLE + " from " + DATABASE_TABLE
                    + " where " + KEY_AUTH + " like " + "'" + owner + "%'";
            rs = stat.executeQuery(query);
            if (rs!=null) {
                while (rs.next()) {
                    med_auth.add(cursorToShortMedi(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println(">> SqlDatabase: SQLException in searchOwner!");
        }

        return med_auth;
    }

    public List<Medication> searchATC(String atccode) {
        List<Medication> med_auth = new ArrayList<>();

        try {
            Connection conn = db.getConnection();
            Statement stat = conn.createStatement();
            ResultSet rs;
            String query = "select " + SHORT_TABLE + " from " + DATABASE_TABLE + " where "
                    + KEY_ATCCODE + " like " + "'%;" + atccode + "%' or "
                    + KEY_ATCCODE + " like " + "'" + atccode + "%' or "
                    + KEY_ATCCODE + " like " + "'% " + atccode + "%' or "
                    + KEY_ATCCLASS + " like " + "'" + atccode + "%' or "
                    + KEY_ATCCLASS + " like " + "'%;" + atccode + "%' or "
                    + KEY_ATCCLASS + " like " + "'%#" + atccode + "%' or "
                    + KEY_SUBSTANCES + " like " + "'%, " + atccode + "%' or "
                    + KEY_SUBSTANCES + " like " + "'" + atccode + "%'";
            rs = stat.executeQuery(query);
            if (rs!=null) {
                while (rs.next()) {
                    med_auth.add(cursorToShortMedi(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println(">> SqlDatabase: SQLException in searchOwner!");
        }

        return med_auth;
    }

    public List<Medication> searchRegnr(String regnr) {
        List<Medication> med_auth = new ArrayList<>();

        try {
            Connection conn = db.getConnection();
            Statement stat = conn.createStatement();
            ResultSet rs;
            String query = "select " + SHORT_TABLE + " from " + DATABASE_TABLE + " where "
                    + KEY_REGNRS + " like " + "'%, " + regnr + "%' or "
                    + KEY_REGNRS + " like " + "'" + regnr + "%'";
            rs = stat.executeQuery(query);
            if (rs!=null) {
                while (rs.next()) {
                    med_auth.add(cursorToShortMedi(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println(">> SqlDatabase: SQLException in searchOwner!");
        }

        return med_auth;
    }

    public List<Medication> searchTherapy(String application) {
        List<Medication> med_auth = new ArrayList<>();

        try {
            Connection conn = db.getConnection();
            Statement stat = conn.createStatement();
            ResultSet rs;
            String query = "select " + SHORT_TABLE + " from " + DATABASE_TABLE
                    + " where " + KEY_APPLICATION + " like " + "'%,"
                    + application + "%' or " + KEY_APPLICATION + " like " + "'"
                    + application + "%' or " + KEY_APPLICATION + " like "
                    + "'% " + application + "%' or " + KEY_APPLICATION
                    + " like " + "'%;" + application + "%' or "
                    + KEY_INDICATIONS + " like " + "'" + application + "%' or "
                    + KEY_INDICATIONS + " like " + "'%;" + application + "%'";
            rs = stat.executeQuery(query);
            if (rs!=null) {
                while (rs.next()) {
                    med_auth.add(cursorToShortMedi(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println(">> SqlDatabase: SQLException in searchOwner!");
        }

        return med_auth;
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

    public Medication getMedicationWithEan(String eancode) {
        try {
            Connection conn = db.getConnection();
            Statement stat = conn.createStatement();
            String query = "select * from " + DATABASE_TABLE + " where " + KEY_PACKAGES + " like " + "'%" + eancode + "%'";
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
            medi.setPackages(result.getString(14));     // KEY_PACKAGES
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

