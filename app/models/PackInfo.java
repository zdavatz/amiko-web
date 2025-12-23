package models;

import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PackInfo {
    public String color;
    public String title;
    public String gtin;

    public PackInfo(String color, String title, String gtin) {
        this.color = color;
        this.title = title;
        this.gtin = gtin;
    }
}
