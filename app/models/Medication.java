package models;

/**
 * Created by maxl on 24.04.2016.
 */
public class Medication {
    private long id;
    private String title;
    private String auth;
    private String atccode;
    private String substances;
    private String regnrs;
    private String atcclass;
    private String therapy;
    private String application;
    private String indications;
    private int customer_id;
    private String pack_info;
    private String addinfo;
    private String sectionIds;
    private String sectionTitles;
    private String content;
    private String packages;

    public long getId() {
        return this.id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getTitle() {
        return this.title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAuth() {
        return this.auth;
    }

    public void setAuth(String auth) {
        this.auth = auth;
    }

    public String getAtcCode() {
        return this.atccode;
    }

    public void setAtcCode(String atccode) {
        this.atccode = atccode;
    }

    public String getSubstances() {
        return this.substances;
    }

    public void setSubstances(String substances) {
        this.substances = substances;
    }

    public String getRegnrs() {
        return this.regnrs;
    }

    public void setRegnrs(String regnrs) {
        this.regnrs = regnrs;
    }

    public String getAtcClass() {
        return this.atcclass;
    }

    public void setAtcClass(String atcclass) {
        this.atcclass = atcclass;
    }

    public String getTherapy() {
        return this.therapy;
    }

    public void setTherapy(String therapy) {
        this.therapy = therapy;
    }

    public String getApplication() {
        return this.application;
    }

    public void setApplication(String application) {
        this.application = application;
    }

    public String getIndications() {
        return this.indications;
    }

    public void setIndications(String indications) {
        this.indications = indications;
    }

    public int getCustomerId() {
        return this.customer_id;
    }

    public void setCustomerId(int customer_id) {
        this.customer_id = customer_id;
    }

    public String getPackInfo() {
        return this.pack_info;
    }

    public void setPackInfo(String pack_info) {
        this.pack_info = pack_info;
    }

    public String getAddInfo() {
        return this.addinfo;
    }

    public void setAddInfo(String addinfo) {
        this.addinfo = addinfo;
    }

    public String getSectionIds() {
        return this.sectionIds;
    }

    public void setSectionIds(String sectionIds) {
        this.sectionIds = sectionIds;
    }

    public String getSectionTitles() {
        return this.sectionTitles;
    }

    public void setSectionTitles(String sectionTitles) {
        this.sectionTitles = sectionTitles;
    }

    public String getContent() { return this.content; }

    public void setContent(String content) {
        this.content = content;
    }

    public String getPackages() {
        return this.packages;
    }

    public void setPackages(String packages) {
        this.packages = packages;
    }
}
