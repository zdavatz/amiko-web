package models;

import controllers.MainController;

import java.util.*;

/**
 * Created by maxl on 10.03.2017.
 */
public class FullTextSearch {

    private static boolean ASC = true;
    private static boolean DESC = false;

    private String ft_row_id = "";
    private String ft_content = "";
    private String ft_titles_html = "";
    private String ft_filter = "";

    private static volatile FullTextSearch instance;

    private FullTextSearch() {
    }

    /**
     * Get the only instance of this class. Singleton pattern.
     *
     * @return
     */
    public static FullTextSearch getInstance() {
        if (instance == null) {
            synchronized (FullTextSearch.class) {
                if (instance == null) {
                    instance = new FullTextSearch();
                }
            }
        }
        return instance;
    }

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

    public Pair<String, String> updateHtml(String lang, List<Article> list_of_articles, Map<String, String> map_of_chapters, String id, String key, String filter) {
        // Remove unnecessary parentheses
        key = key.replaceAll("\\(.*?\\)", "").trim();
        // Sort list of articles
        sortByComparator(list_of_articles, ASC);

        // List of titles to be displayed in right pane
        LinkedList<String> list_of_titles = new LinkedList<>();
        // Sorted set of chapters
        TreeMap<String, Integer> chapters_count_map = new TreeMap<>();

        String content = "<div id=\"fulltext\"><ul>";
        int counter = 0;

        for (Article a : list_of_articles) {
            String content_style = "";
            String content_title = "";
            String content_chapters = "";

            String anchor = "?";
            String eancode = a.eancode.split(",")[0];
            content_title = "<a onclick=\"display_fachinfo(" + eancode + ",'" + key + "','" + anchor + "')\">"
                    + "<span style=\"font-size:0.85em\"><b>" + a.title + "</b></span></a><span style=\"font-size:x-small\"> | " + a.author + "</span><br>";

            Map<Integer, String> index_to_titles_map = a.index_to_titles_map(lang);

            boolean found_chapter = false;
            if (filter.equals("0"))
                found_chapter = true;

            String[] list_of_regs = a.regnrs.split(",");
            for (String r : list_of_regs) {
                if (map_of_chapters.containsKey(r)) {
                    String[] chapters = map_of_chapters.get(r).split(",");
                    // Loop through the list of chapters (these are ints)
                    for (String ch : chapters) {
                        if (!ch.isEmpty()) {
                            int c = Integer.parseInt(ch.trim());
                            if (index_to_titles_map.containsKey(c)) {
                                String chapter_str = index_to_titles_map.get(c);
                                if (filter.equals(chapter_str) || filter.equals("0")) {
                                    anchor = "section" + c;
                                    if (c > 100) {
                                        // These are "old" section titles, e.g. Section7900, Section8000, etc.
                                        anchor = "Section" + c;
                                    }
                                    content_chapters += "<span style=\"font-size:small; color:#0099cc\">"
                                            + "<a onclick=\"display_fachinfo(" + eancode + ",'" + key + "','" + anchor + "')\">" + chapter_str + "</a>"
                                            + "</span><br>";
                                    found_chapter = true;
                                }

                                int count = 0;
                                if (chapters_count_map.containsKey(chapter_str)) {
                                    count = chapters_count_map.get(chapter_str);
                                }
                                chapters_count_map.put(chapter_str, count + 1);
                            }
                        }
                    }
                }
            }
            // Find chapters
            if (found_chapter) {
                String first_letter = a.title.substring(0, 1).toUpperCase();
                if (!list_of_titles.contains(first_letter)) {
                    list_of_titles.add(first_letter);
                    if (counter % 2 == 0)
                        content_style = "<li style=\"background-color:whitesmoke;\" id=\"" + first_letter + "\">";
                    else
                        content_style = "<li style=\"background-color:white;\" id=\"" + first_letter + "\">";
                } else {
                    if (counter % 2 == 0)
                        content_style = "<li style=\"background-color:whitesmoke;\">";
                    else
                        content_style = "<li style=\"background-color:white;\">";
                }

                content += content_style + content_title + content_chapters + "</li>";
                counter++;
            }
        }
        content += "</ul></div>";

        // Add the list of alphabetical shortcuts
        int L = 12;
        String titles_html = "<table id=\"fulltext\">";
        for (int i = 0; i < list_of_titles.size() - L; i += L) {
            titles_html += "<tr>";
            for (int j = 0; j < L; ++j) {
                String t = list_of_titles.get(i + j);
                titles_html += "<td><a onclick=\"move_to_anchor('" + t + "')\">" + t + "</a></td>";
            }
            titles_html += "</tr>";
        }
        int rest = list_of_titles.size();
        if (rest > L)
            rest = list_of_titles.size() % L;
        titles_html += "<tr>";
        for (int i = list_of_titles.size() - rest; i < list_of_titles.size(); ++i) {
            String t = list_of_titles.get(i);
            titles_html += "<td><a onclick=\"move_to_anchor('" + t + "')\">" + t + "</a></td>";
        }
        titles_html += "</tr>";
        titles_html += "</table>";

        // Add the list of chapters on the right pane
        titles_html += "<hr>";
        titles_html += "<ul>";
        for (Map.Entry<String, Integer> e : chapters_count_map.entrySet()) {
            if (e.getKey().equals(filter)) {
                titles_html += "<li style=\"background-color:#eeeeee\"><span style=\"font-size:small\">"
                        + "<a onclick=\"show_full_text('" + id + "','" + key + "','" + e.getKey() + "')\">" + e.getKey() + "</a>"
                        + " (" + e.getValue() + ")"
                        + "</span></li>";
            } else {
                titles_html += "<li><span style=\"font-size:small\">"
                        + "<a onclick=\"show_full_text('" + id + "','" + key + "','" + e.getKey() + "')\">" + e.getKey() + "</a>"
                        + " (" + e.getValue() + ")"
                        + "</span></li>";
            }
        }
        titles_html += "</ul>";

        content = "<html>" + content + "</html>";

        return new Pair<>(content, titles_html);
    }
}
