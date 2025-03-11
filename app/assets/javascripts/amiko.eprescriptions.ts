import {
    UI,
    Patient,
    Prescription,
    AMKPrescription,
    AMKPatient,
    PrescriptionSimplified,
} from "./amiko.prescriptions.js";

declare var QrcodeDecoder: any;
declare var pdfjsLib: any;

export let cachedQrScanner: any = null;

export async function scanQRCodeWithCamera() {
    var videoElem = document.querySelector(
        "#qrcode-scanner-video",
    ) as HTMLVideoElement;
    await ((window as any).QrcodeDecoder
        ? Promise.resolve()
        : Promise.resolve(
              $.getScript("/assets/javascripts/qrcode-decoder.min.js"),
          ));
    try {
        var modal = document.querySelector(
            "dialog#qrcode-scanner",
        ) as HTMLDialogElement;
        modal.showModal();

        videoElem.disablePictureInPicture = true;
        videoElem.playsInline = true;
        videoElem.muted = true;
        videoElem.hidden = false;
        var qrScanner = cachedQrScanner || new QrcodeDecoder.default();
        cachedQrScanner = qrScanner;
        const promise = qrScanner
            .decodeFromCamera(videoElem)
            .then(function (result) {
                qrScanner.stop();
                cachedQrScanner = null;
                importFromString(result.data).finally(function () {
                    modal.close();
                });
            });
        videoElem.play();
        return promise;
    } catch (_e) {
        videoElem.hidden = true;
    }
}

export async function scanAndImportQRCodeFromFile(file: File) {
    if (file.type === "application/pdf") {
        const images = await findImagesFromPDF(file);
        for (const image of images) {
            try {
                await scanAndImportQRCodeImage(image);
                return;
            } catch (e) {
                console.error(e);
                // noop, try the next image
            }
        }
        throw new Error("No QR Code found");
    }
    return new Promise((res, rej) => {
        var image = new Image();
        image.onload = function () {
            scanAndImportQRCodeImage(image).then(res).catch(rej);
        };
        image.src = URL.createObjectURL(file);
    });
}

export async function findImagesFromPDF(
    file: File,
): Promise<HTMLImageElement[]> {
    const typedArray = await new Promise((res) => {
        var fileReader = new FileReader();
        fileReader.onload = function () {
            var typedArray = new Uint8Array(this.result as ArrayBuffer);
            res(typedArray);
        };
        fileReader.readAsArrayBuffer(file);
    });
    pdfjsLib.GlobalWorkerOptions.workerSrc =
        "/assets/javascripts/pdf.worker.mjs";
    const pdf = await pdfjsLib.getDocument(typedArray).promise;
    (window as any).pdf = pdf;

    const page1 = await pdf.getPage(1);
    const ops = await page1.getOperatorList();
    await pdf.loadingTask.promise;
    const imageArgs = ops.fnArray
        .map((fn, i) =>
            fn === pdfjsLib.OPS.paintImageXObject ? ops.argsArray[i][0] : null,
        )
        .filter((a) => a);
    const bitmaps = (
        await Promise.all(
            imageArgs.map(async (arg) => {
                try {
                    const bitmapObj = (await new Promise((res) => {
                        page1.objs.get(arg, res);
                    })) as any;
                    // Sometimes it might me a video frame, need to convert it:
                    return await createImageBitmap(bitmapObj?.bitmap);
                } catch (_e) {
                    return null;
                }
            }),
        )
    ).filter((a) => a);
    const canvas = document.createElement("canvas");
    const ctx = canvas.getContext("2d");
    const result = [];
    for (const bitmap of bitmaps) {
        canvas.width = bitmap.width;
        canvas.height = bitmap.height;
        ctx.clearRect(0, 0, canvas.width, canvas.height);
        ctx.drawImage(bitmap, 0, 0, bitmap.width, bitmap.height);
        const url = canvas.toDataURL();
        const image = new Image();
        await new Promise((res) => {
            image.onload = () => res(image);
            image.src = url;
        });
        result.push(image);
    }

    // Export the entire pdf as image, this is slow:
    // var viewport = page1.getViewport(1.0);
    // canvas.height = viewport.height || 3000;
    // canvas.width = viewport.width || 3000;
    // const task = page1.render({canvasContext: ctx, viewport: viewport})
    // task.onError = (e)=> console.error(e);
    // await task.promise;
    // const url = canvas.toDataURL();
    // const fullImage = new Image();
    // await new Promise(res => {
    //     fullImage.onload = ()=> res(fullImage);
    //     fullImage.src = url;
    // });
    // result.push(fullImage);

    return result;
}

export async function scanAndImportQRCodeImage(imageElement: HTMLImageElement) {
    // I cannot find a reliable QRCode scanner library,
    // so here is it using multiple libraries.
    return Promise.any([
        scanImageWithDecoder(imageElement),
        scanImageWithZXing(imageElement),
    ])
        .catch(() => {
            // Sometimes it returns null even if the entire image (600x600) is a QRCode
            // However It somehow works when the image is reduced to 300x300, not sure why.
            console.log("Try again with resized image");
            const smallerCanvas = document.createElement("canvas");
            smallerCanvas.width = imageElement.width / 2;
            smallerCanvas.height = imageElement.height / 2;
            const ctx = smallerCanvas.getContext("2d");
            ctx.drawImage(
                imageElement,
                0,
                0,
                imageElement.width / 2,
                imageElement.height / 2,
            );
            return new Promise<HTMLImageElement>((res) => {
                const smallerImage = new Image();
                smallerImage.onload = () => res(smallerImage);
                smallerImage.src = smallerCanvas.toDataURL();
            }).then((smallerImage) =>
                Promise.any([
                    scanImageWithDecoder(smallerImage),
                    scanImageWithZXing(smallerImage),
                ]),
            );
        })
        .then(importFromString);
}

export async function scanImageWithDecoder(
    image: HTMLImageElement,
): Promise<string> {
    await ((window as any).QrcodeDecoder
        ? Promise.resolve()
        : Promise.resolve(
              $.getScript("/assets/javascripts/qrcode-decoder.min.js"),
          ));

    var qrScanner = cachedQrScanner || new QrcodeDecoder.default();
    const result = await qrScanner.decodeFromImage(image);

    if (!result) {
        console.log("No QRCode found with decoder");
        return Promise.reject(null);
    }
    cachedQrScanner = null;
    console.log("QRCode found with decoder");
    return result.data;
}

export async function scanImageWithZXing(
    image: HTMLImageElement,
): Promise<string> {
    await ((window as any).ZXing
        ? Promise.resolve()
        : Promise.resolve(
              $.getScript("/assets/javascripts/zxing-library-0.21.3.js"),
          ));
    try {
        const result = await Promise.race([
            // For some reason, this library sometimes doesn't response, we add a 1s timeout just in case
            new Promise((_res, rej) => {
                setTimeout(rej, 1000);
            }),
            new (window as any).ZXing.BrowserQRCodeReader().decodeFromImage(
                image,
            ),
        ]);

        if (result && result.text) {
            console.log("found with zxing");
            return result.text;
        } else {
            console.log("No QRCode found with zxing");
            return Promise.reject(null);
        }
    } catch (e) {
        console.log("No QRCode found with zxing");
        return Promise.reject(e);
    }
}

export function stopScanningQRCode() {
    if (cachedQrScanner) {
        cachedQrScanner.stop();
        cachedQrScanner = null;
    }
}

export async function importFromString(data: string) {
    const ep = await EPrescription.fromCHMED16A1String(data);
    console.log("importFromString", ep);
    const amk = await ep.toAmkDict();
    var now = new Date();
    var filename =
        "RZ_" +
        now.getFullYear() +
        ("0" + (now.getMonth() + 1)).slice(-2) +
        ("0" + now.getDate()).slice(-2) +
        ("0" + now.getHours()).slice(-2) +
        ("0" + now.getMinutes()).slice(-2) +
        ("0" + now.getSeconds()).slice(-2) +
        ".amk";
    (amk as AMKPrescription & { filename: string }).filename = filename;
    const saveResults = await Prescription.importAMKObjects([amk]);
    const prescription = await Prescription.readComplete(saveResults[0]);
    return UI.Prescription.show(prescription);
}

export function parseDateString(dateString: string): Date | null {
    if (!dateString) return null;
    var date = new Date(dateString);
    if (date.getTime()) return date;
    // Sometimes we get invalid date string like: 2024-11-1300:00:00+2:00
    var matches = dateString.match(
        /([0-9]{4})-([0-9]{2})-([0-9]{2})([0-9]{2}):([0-9]{2}):([0-9]{2})([\\+|\\-])([0-9]{1,2}):?([0-9]{1,2})$/,
    );
    if (!matches) return null;
    dateString =
        matches[1] +
        "-" +
        matches[2].padStart(2, "0") +
        "-" +
        matches[3].padStart(2, "0") +
        "T" +
        matches[4].padStart(2, "0") +
        ":" +
        matches[5].padStart(2, "0") +
        ":" +
        matches[6].padStart(2, "0") +
        matches[7] +
        matches[8].padStart(2, "0") +
        ":" +
        matches[9].padStart(2, "0");
    date = new Date(dateString);
    if (!date.getTime()) return null;
    return date;
}

class PatientId {
    type: number;
    value: string;
}

class PField {
    nm: string;
    value: string;
}

class TakingTime {
    off: number;
    du: number;
    doFrom: number;
    doTo: number;
    a: number;
    ma: number;
}

class Posology {
    dtFrom: Date;
    dtTo: Date;
    cyDu: number;
    inRes: number;
    d: number[];
    tt: TakingTime[];
}

class Medicament {
    appInstr: string;
    medicamentId: string;
    idType: number;
    unit: string;
    rep: number;
    nbPack: number;
    subs: number;
    pos: Posology[];
}

export class EPrescription {
    auth: string;
    date: Date;
    prescriptionId: string;
    medType: number;
    zsr: string;
    PFields: PField[];
    rmk: string;
    valBy: string; // The GLN of the healthcare professional who has validated the medication plan.
    valDt: Date; // Date of validation

    patientFirstName: string;
    patientLastName: string;
    patientBirthdate: Date;
    patientGender: number;
    patientStreet: string;
    patientCity: string;
    patientZip: string;
    patientLang: string; // Patient’s language (ISO 639-19 language code) (e.g. de)
    patientPhone: string;
    patientEmail: string;
    patientReceiverGLN: string;
    patientIds: PatientId[];
    patientPFields: PField[];

    medicaments: Medicament[];

    static async fromCHMED16A1String(string: string): Promise<EPrescription> {
        var prefix = "https://eprescription.hin.ch";
        if (string.startsWith(prefix)) {
            var sharpIndex = string.indexOf("#");
            string = string.substring(
                sharpIndex === -1 ? prefix.length : sharpIndex + 1,
            );
            var andIndex = string.indexOf("&");
            if (andIndex !== -1) {
                string = string.substring(0, andIndex);
            }
        }
        prefix = "CHMED16A0";
        if (string.startsWith(prefix)) {
            string = string.substring(prefix.length);
            return new EPrescription(JSON.parse(string));
        }
        prefix = "CHMED16A1";
        if (string.startsWith(prefix)) {
            string = string.substring(prefix.length);

            var byteCharacters = atob(string);
            var byteArrays = [];
            for (let i = 0; i < byteCharacters.length; i++) {
                byteArrays.push(byteCharacters.charCodeAt(i));
            }
            var byteArray = new Uint8Array(byteArrays);
            var blob = new Blob([byteArray], { type: "text/plain" });

            var ds = new DecompressionStream("gzip");
            var decompressedStream = blob.stream().pipeThrough(ds);
            const json = await new Response(decompressedStream).json();
            return new EPrescription(json);
        }
        return Promise.reject(new Error("Invalid prefix"));
    }

    static fromPrescription(
        prescription: PrescriptionSimplified,
    ): EPrescription {
        function formatDateForEPrescription(dateStr) {
            // dd.mm.yyyy -> yyyy-mm-dd
            var parts = dateStr.split(".");
            if (parts.length !== 3) return null;
            return parts[2] + "-" + parts[1] + "-" + parts[0];
        }

        var ePrescriptionObj = {
            Patient: {
                FName: prescription.patient.given_name,
                LName: prescription.patient.family_name,
                BDt: formatDateForEPrescription(
                    prescription.patient.birth_date,
                ),
                Gender:
                    prescription.patient.gender === "m" ||
                    prescription.patient.gender === "man"
                        ? 1
                        : 2,
                Street: prescription.patient.postal_address,
                Zip: prescription.patient.zip_code,
                City: prescription.patient.city,
                Lng:
                    String(localStorage.getItem("language")) == "fr"
                        ? "fr"
                        : "de",
                Phone: prescription.patient.phone_number,
                Email: prescription.patient.email_address,
                Rcv: prescription.patient.insurance_gln,
            },
            Medicaments: prescription.medications.map(function (m) {
                return {
                    Id: m.eancode,
                    IdType: 2, // GTIN
                };
            }),
            MedType: 3, // Prescription
            Id: crypto.randomUUID ? crypto.randomUUID() : String(Math.random()),
            Auth: prescription.operator.gln || "",
            Dt: new Date().toISOString(),
        };
        return new EPrescription(ePrescriptionObj);
    }

    constructor(json: object) {
        this.auth = json["Auth"];
        this.date = parseDateString(json["Dt"]);
        this.prescriptionId = json["Id"];
        this.medType = json["MedType"];
        this.zsr = json["Zsr"];
        this.rmk = json["Rmk"];

        this.PFields = (json["PFields"] || []).map((pfield) => {
            const pf = new PField();
            pf.nm = pfield["Nm"];
            pf.value = pfield["Val"];
            return pf;
        });

        const patient = json["Patient"];
        this.patientBirthdate = parseDateString(patient["BDt"]);
        this.patientCity = patient["City"];
        this.patientFirstName = patient["FName"];
        this.patientLastName = patient["LName"];
        this.patientGender = patient["Gender"];
        this.patientPhone = patient["Phone"];
        this.patientStreet = patient["Street"];
        this.patientZip = patient["Zip"];
        this.patientEmail = patient["Email"];
        this.patientReceiverGLN = patient["Rcv"];
        this.patientLang = patient["Lng"];

        this.patientIds = (patient["Ids"] || []).map((patientIdDict) => {
            const pid = new PatientId();
            pid.value = patientIdDict["Val"];
            pid.type = patientIdDict["Type"];
            return pid;
        });

        this.patientPFields = (patient["PFields"] || []).map(
            (patientPField) => {
                const pf = new PField();
                pf.nm = patientPField["Nm"];
                pf.value = patientPField["Val"];
                return pf;
            },
        );

        this.medicaments = (json["Medicaments"] || []).map((medicament) => {
            const m = new Medicament();
            m.appInstr = medicament["AppInstr"];
            m.medicamentId = medicament["Id"];
            m.idType = medicament["IdType"];
            m.unit = medicament["Unit"];
            m.rep = medicament["rep"];
            m.nbPack = medicament["NbPack"];
            m.subs = medicament["Subs"];

            m.pos = (medicament["Pos"] || []).map((posDict) => {
                const p = new Posology();
                p.dtFrom = parseDateString(posDict["DtFrom"]);
                p.dtTo = parseDateString(posDict["DtTo"]);
                p.cyDu = posDict["CyDu"];
                p.inRes = posDict["InRes"];

                p.d = posDict["D"];
                p.tt = (posDict["TT"] || []).map((ttDict) => {
                    const tt = new TakingTime();
                    tt.off = ttDict["Off"];
                    tt.du = ttDict["Du"];
                    tt.doFrom = ttDict["DoFrom"];
                    tt.doTo = ttDict["DoTo"];
                    tt.a = ttDict["A"];
                    tt.ma = ttDict["MA"];
                    return tt;
                });
                return p;
            });
            return m;
        });
    }

    async toAmkDict(): Promise<AMKPrescription> {
        var date = this.date || new Date();
        // Normally place_date is composed with doctor's name or city,
        // however it's not available in ePrescription, instead we put the ZSR nummber here
        var placeDate =
            (this.zsr || "") +
            "," +
            // dd.MM.yyyy (HH:mm:ss)
            ("0" + date.getDate()).slice(-2) +
            "." +
            ("0" + (date.getMonth() + 1)).slice(-2) +
            "." +
            date.getFullYear() +
            " (" +
            ("0" + date.getHours()).slice(-2) +
            ":" +
            ("0" + date.getMinutes()).slice(-2) +
            ":" +
            ("0" + date.getSeconds()).slice(-2) +
            ")";
        // dd.MM.yyyy
        var birthdateString = this.patientBirthdate
            ? String(this.patientBirthdate.getDate()).padStart(2, "0") +
              "." +
              String(this.patientBirthdate.getMonth()).padStart(2, "0") +
              "." +
              this.patientBirthdate.getFullYear()
            : "";
        var patientIds = this.patientIds || [];
        var patientId =
            patientIds.length && patientIds[0].type === 1
                ? this.patientIds[0].value
                : null;
        var healthCardNumber = null,
            insuranceGln = null;

        if (!patientId) {
            patientId = this.patientReceiverGLN;
        }
        if (patientId) {
            if (patientId.includes(".") || patientId.length === 20) {
                healthCardNumber = patientId;
            } else if (patientId.length === 13) {
                insuranceGln = patientId;
            }
        }
        var patientWithoutId: Omit<AMKPatient, "patient_id"> = {
            // "patient_id": // To be filled below
            given_name: this.patientFirstName,
            family_name: this.patientLastName,
            birth_date: birthdateString,
            gender: this.patientGender === 1 ? "man" : "woman",
            email_address: this.patientEmail || "",
            phone_number: this.patientPhone || "",
            postal_address: this.patientStreet || "",
            city: this.patientCity || "",
            zip_code: this.patientZip || "",
            insurance_gln: insuranceGln || "",
            country: "",
            weight_kg: "",
            height_cm: "",
            bag_number: "",
            health_card_number: healthCardNumber || "",
            health_card_expiry: "",
        };
        const amkPatientId =
            await Patient.generateAMKPatientId(patientWithoutId);

        var patient = Object.assign({}, patientWithoutId, {
            patient_id: amkPatientId,
        });
        return {
            prescription_hash: crypto.randomUUID
                ? crypto.randomUUID()
                : String(Math.random()),
            place_date: placeDate,
            operator: {
                gln: this.auth || "",
                zsr_number: this.zsr || "",
                title: "",
                given_name: "",
                family_name: "",
                postal_address: "",
                city: "",
                country: "",
                zip_code: "",
                phone_number: "",
                email_address: "",
                iban: "",
                vat_number: "",
                signature: "",
            },
            patient: patient,
            medications: this.medicaments.map((m) => ({
                eancode: m.medicamentId,
                title: "",
                owner: "",
                regnrs: "",
                atccode: "",
                product_name: "",
                package: "",
                comment: "",
            })),
        };
    }

    toJSON() {
        function formatDateForEPrescription(dateStr) {
            // dd.mm.yyyy -> yyyy-mm-dd
            var parts = dateStr.split(".");
            if (parts.length !== 3) return null;
            return parts[2] + "-" + parts[1] + "-" + parts[0];
        }
        return {
            Patient: {
                FName: this.patientFirstName || "",
                LName: this.patientLastName || "",
                BDt: formatDateForEPrescription(this.patientBirthdate) || "",
                Gender: this.patientGender === 1 ? "man" : "woman",
                Street: this.patientStreet || "",
                Zip: this.patientZip || "",
                City: this.patientCity || "",
                Lng: this.patientLang || "",
                Phone: this.patientPhone || "",
                Email: this.patientEmail || "",
            },
            Medicaments: (this.medicaments || []).map((item) => ({
                Id: item.medicamentId,
                IdType: 2, // GTIN
            })),
            MedType: 3, // Prescription
            Id: crypto.randomUUID ? crypto.randomUUID() : String(Math.random()),
            Auth: this.auth || "", // GLN of doctor
            Dt: new Date().toISOString(),
        };
    }
}
