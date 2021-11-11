/*
Copyright (c) 2017 ML <cybrmx@gmail.com>

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

package models;

import java.util.Map;

public class FullTextEntry {
    private long id = 0;
    private String hash = "";
    private int num_hits;
    private String keyword;
    private Map<String, String> map_of_chapters;

    public long getId() { return this.id; }

    public void setId(long id) { this.id = id; }

    public String getHash() { return this.hash; }

    public void setHash(String hash) { this.hash = hash; }

    public void setNumHits(int num_hits) { this.num_hits = num_hits; }

    public String getKeyword() { return this.keyword; }

    public void setKeyword(String keyword) { this.keyword = keyword; }

    public String getTitle() { return this.keyword + " (" + this.num_hits + ")"; }

    public String getRegnrs() {
        String regnrs_str = "";
        for (String key : map_of_chapters.keySet()) {
            regnrs_str += key + ",";
        }
        return regnrs_str;
    }

    public Map<String, String> getMapOfChapters() { return this.map_of_chapters; }

    public void setMapOfChapters(Map<String, String> map_of_chapters) { this.map_of_chapters = map_of_chapters; }
}
