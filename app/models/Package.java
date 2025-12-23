/*
Copyright (c) 2025 B123400 <i@b123400.net>

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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Package {
    public String name;
    public String dosage;
    public String units;
    public String efp;
    public String pp;
    public String fap;
    public String fep;
    public String vat;
    public String flags;
    public String gtin;
    public String phar;

    public Medication medication;

    public Package(String packageString, Medication medication) {
        this.medication = medication;
        String[] parts = packageString.split("\\|");

        this.name = parts.length > 0 ? parts[0] : "";
        this.dosage = parts.length > 1 ? parts[1] : "";
        this.units = parts.length > 2 ? parts[2] : "";
        this.efp = parts.length > 3 ? parts[3] : "";
        this.pp = parts.length > 4 ? parts[4] : "";
        this.fap = parts.length > 5 ? parts[5] : "";
        this.fep = parts.length > 6 ? parts[6] : "";
        this.vat = parts.length > 7 ? parts[7] : "";
        this.flags = parts.length > 8 ? parts[8] : "";
        this.gtin = parts.length > 9 ? parts[9] : "";
        this.phar = parts.length > 10 ? parts[10] : "";
    }

    String[] parsedFlags() {
        return this.flags.split(",");
    }

    public String selbstbehalt() {
        for (String flag : this.parsedFlags()) {
            if (flag.startsWith("SB ")) {
                return flag.substring(3);
            }
        }
        return "";
    }

    public boolean isGeneric() {
        for (String flag : this.parsedFlags()) {
            if (flag.equals("G")) {
                return true;
            }
        }
        return false;
    }

    public boolean isOriginal() {
        for (String flag : this.parsedFlags()) {
            if (flag.equals("O")) {
                return true;
            }
        }
        return false;
    }

    public String parsedDosageFromName() {

        Pattern regex1 = Pattern.compile("((\\d+)(\\.\\d+)?\\s*(ml|mg|g))");
        Matcher match1 = regex1.matcher(this.name);
        String dosage1 = match1.find() ? match1.group() : "";

        Pattern regex2 = Pattern.compile("(((\\d+)(\\.\\d+)?(Ds|ds|mg)?)(\\/(\\d+)(\\.\\d+)?\\s*(Ds|ds|mg|ml|mg|g)?)+)");
        Matcher match2 = regex2.matcher(this.name);
        String dosage2 = match2.find() ? match2.group() : "";

        if (dosage1.isEmpty() || dosage2.contains(dosage1)) {
            return dosage2;
        }

        return dosage1;
    }

    static public boolean dosageEqual(Package p1, Package p2) {
        String dosage1 = p1.parsedDosageFromName().replaceAll("\\s", "");
        String dosage2 = p2.parsedDosageFromName().replaceAll("\\s", "");
        if (dosage1.equals(dosage2)) {
            return true;
        }
        String numOnly1 = takeNumOnly(dosage1);
        String numOnly2 = takeNumOnly(dosage2);
        boolean is1WithoutUnit = dosage1.toLowerCase().endsWith("ds") || numOnly1.equals(dosage1);
        boolean is2WithoutUnit = dosage2.toLowerCase().endsWith("ds") || numOnly2.equals(dosage2);
        if (is1WithoutUnit || is2WithoutUnit) {
            return numOnly1.equals(numOnly2);
        }
        return false;
    }

    static private String takeNumOnly(String str) {
        str = str.trim();
        try {
            for (int i = 0; i < str.length(); i++) {
                if (!Character.isDigit(str.charAt(i))) {
                    return str.substring(0, i);
                }
            }
            return str;
        } catch (NumberFormatException e) {}
        return "";
    }
}
