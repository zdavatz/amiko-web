import * as EPrescription from "./amiko.eprescriptions.js";
import { Plain } from "./types";

declare var XHRCSRFToken: string;

function yyyyMMdd(date: Date) {
    return (
        date.getFullYear() +
        "-" +
        ("0" + (date.getMonth() + 1)).slice(-2) +
        "-" +
        ("0" + date.getDate()).slice(-2)
    );
}

class ZurRoseAddress {
    title?: string; // optional
    titleCode?: number; // optional
    lastName: string;
    firstName?: string; // optional
    street: string;
    zipCode?: string;
    city: string;
    kanton?: string; // optional
    country?: string; // optional
    phoneNrBusiness?: string; // optional
    phoneNrHome?: string; // optional
    faxNr?: string; // optional
    email?: string; // optional

    constructor(arg: Plain<ZurRoseAddress>) {
        this.title = arg.title;
        this.titleCode = arg.titleCode;
        this.lastName = arg.lastName;
        this.firstName = arg.firstName;
        this.street = arg.street;
        this.zipCode = arg.zipCode;
        this.city = arg.city;
        this.kanton = arg.kanton;
        this.country = arg.country;
        this.phoneNrBusiness = arg.phoneNrBusiness;
        this.phoneNrHome = arg.phoneNrHome;
        this.faxNr = arg.faxNr;
        this.email = arg.email;
    }

    writeBodyToXMLElement(element: Element) {
        if (this.title !== undefined) {
            element.setAttribute("title", this.title);
        }
        if (this.titleCode !== undefined) {
            element.setAttribute("titleCode", String(this.titleCode));
        }

        element.setAttribute("lastName", this.lastName);
        if (this.firstName !== undefined) {
            element.setAttribute("firstName", this.firstName);
        }
        element.setAttribute("street", this.street);
        element.setAttribute("zipCode", this.zipCode ?? "");
        element.setAttribute("city", this.city);
        element.setAttribute("kanton", this.kanton ?? "");
        if (this.country !== undefined) {
            element.setAttribute("country", this.country);
        }
        if (this.phoneNrBusiness !== undefined) {
            element.setAttribute("phoneNrBusiness", this.phoneNrBusiness);
        }
        if (this.phoneNrHome !== undefined) {
            element.setAttribute("phoneNrHome", this.phoneNrHome);
        }
        if (this.faxNr !== undefined) {
            element.setAttribute("faxNr", this.faxNr);
        }
        if (this.email !== undefined) {
            element.setAttribute("email", this.email);
        }
    }
}

class ZurRosePatientAddress extends ZurRoseAddress {
    birthday?: Date;
    langCode: number = 1; // 1 = de, 2 = fr, 3 = it
    coverCardId?: string; // optional
    sex: number = 1; // 1 = m, 2 = f
    patientNr: string;
    phoneNrMobile?: string; // optional
    room?: string; // optional
    section?: string; // optional

    constructor(arg: Plain<ZurRosePatientAddress> & Plain<ZurRoseAddress>) {
        super(arg);
        this.birthday = arg.birthday;
        this.langCode = arg.langCode;
        this.coverCardId = arg.coverCardId;
        this.sex = arg.sex;
        this.patientNr = arg.patientNr;
        this.phoneNrMobile = arg.phoneNrMobile;
        this.room = arg.room;
        this.section = arg.section;
    }

    toXML(doc: XMLDocument): Element {
        const element = doc.createElementNS("http://estudio.clustertec.ch/schemas/prescription", "patientAddress");
        super.writeBodyToXMLElement(element);

        element.setAttribute(
            "birthday",
            this.birthday ? yyyyMMdd(this.birthday) : "",
        );
        element.setAttribute("langCode", String(this.langCode));
        if (this.coverCardId !== undefined) {
            element.setAttribute("coverCardId", this.coverCardId);
        }
        element.setAttribute("sex", String(this.sex));
        element.setAttribute("patientNr", this.patientNr);
        if (this.phoneNrMobile !== undefined) {
            element.setAttribute("phoneNrMobile", this.phoneNrMobile);
        }
        if (this.room !== undefined) {
            element.setAttribute("room", this.room);
        }
        if (this.section !== undefined) {
            element.setAttribute("section", this.section);
        }
        return element;
    }
}

class ZurRosePrescriptorAddress extends ZurRoseAddress {
    langCode: number = 1; // 1 = de, 2 = fr, 3 = it
    clientNrClustertec: string;
    zsrId: string;
    eanId?: string; // optional

    constructor(arg: Plain<ZurRosePrescriptorAddress> & Plain<ZurRoseAddress>) {
        super(arg);
        this.langCode = arg.langCode;
        this.clientNrClustertec = arg.clientNrClustertec;
        this.zsrId = arg.zsrId;
        this.eanId = arg.eanId;
    }

    toXML(doc: XMLDocument): Element {
        const element = doc.createElementNS("http://estudio.clustertec.ch/schemas/prescription", "prescriptorAddress");
        super.writeBodyToXMLElement(element);

        element.setAttribute("langCode", String(this.langCode));
        element.setAttribute("clientNrClustertec", this.clientNrClustertec);
        element.setAttribute("zsrId", this.zsrId);
        if (this.eanId !== undefined) {
            element.setAttribute("eanId", this.eanId);
        }
        return element;
    }
}

enum ZurRosePrescriptionDeliveryType {
    Patient = 1,
    Doctor = 2,
    Address = 3,
}

class ZurRoseProduct {
    pharmacode?: string; // optional
    eanId?: string; // optional
    description?: string; // optional
    repetition: boolean;
    nrOfRepetitions?: number; // optional, 0 - 99
    quantity?: number; // 0 - 999
    validityRepetition?: string; // optional
    notSubstitutableForBrandName?: number; // optional
    remark?: string; // optional
    dailymed?: number; // optional boolean
    dailymed_mo?: number; // optional boolean
    dailymed_tu?: number; // optional boolean
    dailymed_we?: number; // optional boolean
    dailymed_th?: number; // optional boolean
    dailymed_fr?: number; // optional boolean
    dailymed_sa?: number; // optional boolean
    dailymed_su?: number; // optional boolean

    insuranceEanId?: string; // optional
    insuranceBsvNr?: string; // optional
    insuranceInsuranceName?: string; // optional
    insuranceBillingType: number; // required
    insuranceInsureeNr?: string; // optional

    posology: ZurRosePosology[] = [];

    constructor(arg: Plain<ZurRoseProduct>) {
        this.pharmacode = arg.pharmacode;
        this.eanId = arg.eanId;
        this.description = arg.description;
        this.repetition = arg.repetition;
        this.nrOfRepetitions = arg.nrOfRepetitions;
        this.quantity = arg.quantity;
        this.validityRepetition = arg.validityRepetition;
        this.notSubstitutableForBrandName = arg.notSubstitutableForBrandName;
        this.remark = arg.remark;
        this.dailymed = arg.dailymed;
        this.dailymed_mo = arg.dailymed_mo;
        this.dailymed_tu = arg.dailymed_tu;
        this.dailymed_we = arg.dailymed_we;
        this.dailymed_th = arg.dailymed_th;
        this.dailymed_fr = arg.dailymed_fr;
        this.dailymed_sa = arg.dailymed_sa;
        this.dailymed_su = arg.dailymed_su;

        this.insuranceEanId = arg.insuranceEanId;
        this.insuranceBsvNr = arg.insuranceBsvNr;
        this.insuranceInsuranceName = arg.insuranceInsuranceName;
        this.insuranceBillingType = arg.insuranceBillingType;
        this.insuranceInsureeNr = arg.insuranceInsureeNr;

        this.posology = arg.posology;
    }
    toXML(doc: XMLDocument): Element {
        const element = doc.createElementNS("http://estudio.clustertec.ch/schemas/prescription", "product");

        if (this.pharmacode !== undefined) {
            element.setAttribute("pharmacode", this.pharmacode);
        }
        if (this.eanId !== undefined) {
            element.setAttribute("eanId", this.eanId);
        }
        if (this.description !== undefined) {
            element.setAttribute("description", this.description);
        }
        element.setAttribute("repetition", this.repetition ? "true" : "false");
        if (this.nrOfRepetitions !== undefined) {
            element.setAttribute(
                "nrOfRepetitions",
                String(this.nrOfRepetitions),
            );
        }
        element.setAttribute("quantity", String(this.quantity || 1));
        if (this.validityRepetition !== undefined) {
            element.setAttribute("validityRepetition", this.validityRepetition);
        }
        if (this.notSubstitutableForBrandName !== undefined) {
            element.setAttribute(
                "notSubstitutableForBrandName",
                String(this.notSubstitutableForBrandName),
            );
        }
        if (this.remark !== undefined) {
            element.setAttribute("remark", this.remark);
        }
        if (this.dailymed !== undefined) {
            element.setAttribute("dailymed", this.dailymed ? "true" : "false");
        }
        if (this.dailymed_mo !== undefined) {
            element.setAttribute(
                "dailymed_mo",
                this.dailymed_mo ? "true" : "false",
            );
        }
        if (this.dailymed_tu !== undefined) {
            element.setAttribute(
                "dailymed_tu",
                this.dailymed_tu ? "true" : "false",
            );
        }
        if (this.dailymed_we !== undefined) {
            element.setAttribute(
                "dailymed_we",
                this.dailymed_we ? "true" : "false",
            );
        }
        if (this.dailymed_th !== undefined) {
            element.setAttribute(
                "dailymed_th",
                this.dailymed_th ? "true" : "false",
            );
        }
        if (this.dailymed_fr !== undefined) {
            element.setAttribute(
                "dailymed_fr",
                this.dailymed_fr ? "true" : "false",
            );
        }
        if (this.dailymed_sa !== undefined) {
            element.setAttribute(
                "dailymed_sa",
                this.dailymed_sa ? "true" : "false",
            );
        }
        if (this.dailymed_su !== undefined) {
            element.setAttribute(
                "dailymed_su",
                this.dailymed_su ? "true" : "false",
            );
        }

        const insuranceElement = doc.createElementNS("http://estudio.clustertec.ch/schemas/prescription", "insurance");
        element.appendChild(insuranceElement);

        if (this.insuranceEanId !== undefined) {
            insuranceElement.setAttribute("eanId", this.insuranceEanId);
        }
        if (this.insuranceBsvNr !== undefined) {
            insuranceElement.setAttribute("bsvNr", this.insuranceBsvNr);
        }
        if (this.insuranceInsuranceName !== undefined) {
            insuranceElement.setAttribute(
                "insuranceName",
                this.insuranceInsuranceName,
            );
        }
        insuranceElement.setAttribute(
            "billingType",
            String(this.insuranceBillingType),
        );
        if (this.insuranceInsureeNr !== undefined) {
            insuranceElement.setAttribute("insureeNr", this.insuranceInsureeNr);
        }

        for (const p of this.posology) {
            element.appendChild(p.toXML(doc));
        }

        return element;
    }
}

class ZurRosePosology {
    qtyMorning?: number; // optional
    qtyMidday?: number; // optional
    qtyEvening?: number; // optional
    qtyNight?: number; // optional
    qtyMorningString?: string; // optional
    qtyMiddayString?: string; // optional
    qtyEveningString?: string; // optional
    qtyNightString?: string; // optional
    posologyText?: string; // optional
    label?: number; // optional, boolean

    constructor() {
        this.qtyMorning = -1;
        this.qtyMidday = -1;
        this.qtyEvening = -1;
        this.qtyNight = -1;
    }

    toXML(doc: XMLDocument): Element {
        const element = doc.createElementNS("http://estudio.clustertec.ch/schemas/prescription", "posology");

        if (this.qtyMorning !== undefined) {
            element.setAttribute("qtyMorning", String(this.qtyMorning));
        }
        if (this.qtyMidday !== undefined) {
            element.setAttribute("qtyMidday", String(this.qtyMidday));
        }
        if (this.qtyEvening !== undefined) {
            element.setAttribute("qtyEvening", String(this.qtyEvening));
        }
        if (this.qtyNight !== undefined) {
            element.setAttribute("qtyNight", String(this.qtyNight));
        }
        if (this.qtyMorningString !== undefined) {
            element.setAttribute("qtyMorningString", this.qtyMorningString);
        }
        if (this.qtyMiddayString !== undefined) {
            element.setAttribute("qtyMiddayString", this.qtyMiddayString);
        }
        if (this.qtyEveningString !== undefined) {
            element.setAttribute("qtyEveningString", this.qtyEveningString);
        }
        if (this.qtyNightString !== undefined) {
            element.setAttribute("qtyNightString", this.qtyNightString);
        }
        if (this.posologyText !== undefined) {
            element.setAttribute("posologyText", this.posologyText);
        }
        if (this.label !== undefined) {
            element.setAttribute("label", this.label ? "true" : "false");
        }
        return element;
    }
}

export class ZurRosePrescription {
    issueDate: Date;
    validity?: Date;
    user: string;
    password: string;
    prescriptionNr?: string; // optional
    deliveryType: ZurRosePrescriptionDeliveryType;
    ignoreInteractions: boolean;
    interactionsWithOldPres: boolean;
    remark?: string; // optional
    prescriptorAddress: ZurRosePrescriptorAddress;
    patientAddress: ZurRosePatientAddress;

    products: ZurRoseProduct[];

    constructor(arg: Plain<ZurRosePrescription>) {
        this.issueDate = arg.issueDate;
        this.validity = arg.validity;
        this.user = arg.user;
        this.password = arg.password;
        this.prescriptionNr = arg.prescriptionNr;
        this.deliveryType = arg.deliveryType;
        this.ignoreInteractions = arg.ignoreInteractions;
        this.interactionsWithOldPres = arg.interactionsWithOldPres;
        this.remark = arg.remark;
        this.prescriptorAddress = arg.prescriptorAddress;
        this.patientAddress = arg.patientAddress;
        this.products = arg.products;
    }

    toXML(): XMLDocument {
        const doc = document.implementation.createDocument(
            "http://estudio.clustertec.ch/schemas/prescription",
            "prescription",
        );
        const root = doc.documentElement;

        root.setAttribute("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance");
        root.setAttribute("xsi:schemaLocation", "http://estudio.clustertec.ch/schemas/prescription/ prescription.xsd");
        root.setAttribute("issueDate", yyyyMMdd(this.issueDate));
        root.setAttribute(
            "validity",
            yyyyMMdd(this.validity ?? this.issueDate),
        );
        root.setAttribute("user", this.user);
        root.setAttribute("password", this.password);
        if (this.prescriptionNr !== undefined) {
            root.setAttribute("prescriptionNr", this.prescriptionNr);
        }
        root.setAttribute("deliveryType", String(this.deliveryType));
        root.setAttribute(
            "ignoreInteractions",
            this.ignoreInteractions ? "true" : "false",
        );
        root.setAttribute(
            "interactionsWithOldPres",
            this.interactionsWithOldPres ? "true" : "false",
        );
        if (this.remark !== undefined) {
            root.setAttribute("remark", this.remark);
        }

        root.appendChild(this.prescriptorAddress.toXML(doc));
        root.appendChild(this.patientAddress.toXML(doc));

        for (const p of this.products) {
            root.appendChild(p.toXML(doc));
        }
        return doc;
        // return new XMLSerializer().serializeToString(doc);
    }

    static async fromEPrescription(
        ePrescription: EPrescription.EPrescription,
    ): Promise<ZurRosePrescription> {
        const mapping = await ZurRosePrescription.getZipToKantonMap();

        var patientZip = ePrescription.patientZip || "";
        var kanton = mapping[patientZip];

        var patientId =
            ePrescription.patientIds.length &&
            ePrescription.patientIds[0].type === 1
                ? ePrescription.patientIds[0].value
                : null;
        let healthCardNumber: string | undefined = undefined,
            insuranceGln: string | undefined = undefined;

        if (!patientId) {
            patientId = ePrescription.patientReceiverGLN;
        }
        if (patientId) {
            if (patientId.includes(".") || patientId.length === 20) {
                healthCardNumber = patientId;
            } else if (patientId.length === 13) {
                insuranceGln = patientId;
            }
        }

        return new ZurRosePrescription({
            issueDate: ePrescription.date || new Date(),
            prescriptionNr: String(Math.random()).substring(2, 11),
            remark: ePrescription.rmk || "",
            validity: undefined,
            user: "",
            password: "",
            deliveryType: 1,
            ignoreInteractions: false,
            interactionsWithOldPres: false,

            prescriptorAddress: new ZurRosePrescriptorAddress({
                zsrId: ePrescription.zsr || "",
                lastName: ePrescription.auth || "",
                langCode: 1,
                clientNrClustertec: "888870",
                street: "",
                zipCode: "",
                city: "",
            }),
            patientAddress: new ZurRosePatientAddress({
                lastName: ePrescription.patientLastName || "",
                firstName: ePrescription.patientFirstName || "",
                street: ePrescription.patientStreet || "",
                city: ePrescription.patientCity || "",
                kanton: kanton,
                zipCode: patientZip,
                birthday: ePrescription.patientBirthdate,
                phoneNrHome: ePrescription.patientPhone || "",
                sex: ePrescription.patientGender || 1, // same, m = 1, f = 2
                email: ePrescription.patientEmail || "",
                langCode:
                    ePrescription.patientLang === "de"
                        ? 1
                        : ePrescription.patientLang === "fr"
                          ? 2
                          : ePrescription.patientLang === "it"
                            ? 3
                            : 1,
                coverCardId: healthCardNumber,
                patientNr: "",
            }),
            products: (ePrescription.medicaments || []).map((m) => {
                let repetition: boolean = false;
                let validityRepetition: Date | null = null;
                let pos = new ZurRosePosology();
                for (const mediPos of m.pos) {
                    if (mediPos.d.length) {
                        pos.qtyMorning = mediPos.d[0];
                        pos.qtyMidday = mediPos.d[1];
                        pos.qtyEvening = mediPos.d[2];
                        pos.qtyNight = mediPos.d[3];
                        pos.posologyText = m.appInstr;
                    }
                    if (mediPos.dtTo) {
                        repetition = true;
                        validityRepetition = mediPos.dtTo;
                    }
                }
                const product = {
                    eanId: m.idType === 2 ? m.medicamentId : undefined,
                    pharmacode: m.idType === 3 ? m.medicamentId : undefined,
                    quantity: m.nbPack || 1,
                    remark: m.appInstr,
                    insuranceBillingType: 1,
                    insuranceEanId: insuranceGln,
                    validityRepetition: validityRepetition
                        ? yyyyMMdd(validityRepetition)
                        : undefined,
                    repetition,
                    posology: [],
                };
                return new ZurRoseProduct(product);
            }),
        });
    }
    static zipToKantonMap: Record<string, string> | null = null;
    static async getZipToKantonMap(): Promise<Record<string, string>> {
        if (ZurRosePrescription.zipToKantonMap) {
            return ZurRosePrescription.zipToKantonMap;
        }
        const res = await fetch("/assets/javascripts/swiss-zip-to-kanton.json");
        const mapping = await res.json();
        ZurRosePrescription.zipToKantonMap = mapping;
        return mapping;
    }

    async send() {
        const string = new XMLSerializer().serializeToString(this.toXML());
        console.log(string);
        const res = await fetch("/zurrose/prescription", {
            method: 'POST',
            headers: {
                'Content-Type': 'application/xml',
                "Csrf-Token": XHRCSRFToken,
            },
            body: string
        });
        const resText = await res.text();
        console.log(resText);
    }
}
