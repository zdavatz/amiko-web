package models;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by maxl on 10.03.2017.
 */
public class Article {
    // Private
    private String _title = "";
    private String _keyword = "";
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
    public String hash = "";
    public String title = "";
    public String keyword = "";
    public String author = "";
    public String atccode = "";
    public String atcclass = "";
    public String regnrs = "";
    public String therapy = "";
    public ArrayList<PackInfo> packinfos = new ArrayList();
    public String eancode = "";
    public String titles = "";
    public String sections = "";

    public Article(long _id, String _hash, String _title, String _keyword, String _author, String _atccode, String _atcclass, String _regnrs, String _therapy, String _packinfo, String _packages, String _titles, String _sections) {
        // Private
        this._title = _title;
        this._keyword = _keyword;
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
        this.hash = _hash;
        this.title = _title;
        this.keyword = _keyword;;
        this.packinfos = makePackInfos();
        this.author = _author;
        this.atccode = _atccode;
        this.atcclass = _atcclass;
        this.regnrs = _regnrs;
        this.therapy = therapyStr();
        this.eancode = eancodeStr();
        this.titles = _titles;
        this.sections = _sections;
    }

    public Article(String _title, String _titles) {
        // Private
        this._title = _title;
        this._titles = _titles;
    }

    public ArrayList<PackInfo> makePackInfos() {
        String[] packageArray = _packages.split("\n");
        Pattern p_red = Pattern.compile(".*O]");
        Pattern p_green = Pattern.compile(".*G]");
        Scanner pack_str_scanner = new Scanner(_packinfo);
        ArrayList<PackInfo> result = new ArrayList<PackInfo>();
        int i = 0;
        while (pack_str_scanner.hasNextLine()) {
            String pack_str_line = pack_str_scanner.nextLine();
            Matcher m_red = p_red.matcher(pack_str_line);
            Matcher m_green = p_green.matcher(pack_str_line);
            String color = "";
            if (m_red.find()) {
                color = "red";
            } else if (m_green.find()) {
                color = "green";
            }
            else {
                color = "gray";
            }
            String gtin = null;
            if (packageArray.length > i) {
                String packageStr = packageArray[i];
                String[] packageParts = packageStr.split("\\|");
                if (packageParts.length > 9) {
                    gtin = packageParts[9];
                }
            }
            result.add(new PackInfo(color, pack_str_line, gtin));
            i++;
        }
        pack_str_scanner.close();
        return result;
    }

    public String therapyStr() {
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

    public String eancodeStr() {
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

    public String[] sectionTitles(String lang) {
        // Get section titles from chapters
        String[] section_titles = _titles.split(";");

        // Use abbreviations...
        String[] section_titles_abbr = lang.equals("de") ? models.Constants.SectionTitle_DE : Constants.SectionTitle_FR;
        for (int i = 0; i < section_titles.length; ++i) {
            for (String s_abbr : section_titles_abbr) {
                String titleA = section_titles[i].replaceAll("\\s*/\\s*", "/").toLowerCase().trim();
                String titleB = _title.replaceAll("\\s*/\\s*", "/").toLowerCase().trim(); // _title.replaceAll(" ", ""); // Removes all spaces

                if (titleA.contains(titleB.toLowerCase())) {
                    // Are we analysing the name of the article?
                    if (section_titles[i].contains("®"))
                        section_titles[i] = section_titles[i].substring(0, section_titles[i].indexOf("®") + 1);
                    else
                        section_titles[i] = section_titles[i].split(" ")[0].replaceAll("/-", "");
                    if (section_titles[i].length()>28)
                        section_titles[i] = section_titles[i].substring(0, 28);
                    break;
                } else if (titleA.contains(s_abbr.toLowerCase())) {
                    // These are proper section titles
                    section_titles[i] = s_abbr;
                    break;
                }
            }
        }
        return section_titles;
    }

    public Map<Integer, String> index_to_titles_map(String lang) {
        Map<Integer, String> map = new HashMap<>();

        String[] tt = sectionTitles(lang);
        String[] ids = _sections.split(",");
        // Assuming both arrays have the same length - they should!
        int N = tt.length>ids.length ? ids.length : tt.length;
        for (int i=0; i<N; ++i) {
            String section_id = ids[i].replaceAll("(s|S)ection", "");
            map.put(Integer.parseInt(section_id), tt[i]);
        }

        return map;
    }
}
