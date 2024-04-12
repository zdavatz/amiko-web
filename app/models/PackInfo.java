package models;

import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PackInfo {
    public String color;
    public String title;

    public PackInfo(String color, String title) {
        this.color = color;
        this.title = title;
    }
}
