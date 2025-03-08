import { UI, Patient, Prescription, AMKPrescription, AMKPatient } from './amiko.prescriptions.js';

declare var QrcodeDecoder: any;
declare var pdfjsLib: any;

export var EPrescription = {
    scanQRCodeWithCamera: function() {
        var videoElem = document.querySelector('#qrcode-scanner-video') as HTMLVideoElement;
        return ((window as any).QrcodeDecoder ? Promise.resolve() : Promise.resolve($.getScript('/assets/javascripts/qrcode-decoder.min.js')))
        .then(function() {
            var modal = document.querySelector('dialog#qrcode-scanner') as HTMLDialogElement;
            modal.showModal();

            videoElem.disablePictureInPicture = true;
            videoElem.playsInline = true;
            videoElem.muted = true;
            videoElem.hidden = false;
            var qrScanner = EPrescription.qrScanner || new QrcodeDecoder.default();
            EPrescription.qrScanner = qrScanner;
            const promise = qrScanner.decodeFromCamera(videoElem).then(function(result) {
                qrScanner.stop();
                EPrescription.qrScanner = null;
                EPrescription.importFromString(result.data)
                    .finally(function() {
                        modal.close();
                    });
            });
            videoElem.play();
            return promise;
        }).catch(()=> {
            videoElem.hidden = true;
        });
    },
    scanAndImportQRCodeFromFile: function(file) {
        if (file.type === 'application/pdf') {
            return EPrescription.findImagesFromPDF(file).then(async (images) => {
                for (const image of images) {
                    try {
                        await EPrescription.scanAndImportQRCodeImage(image);
                        return;
                    } catch (e) {
                        console.error(e);
                        // noop, try the next image
                    }
                }
                throw new Error('No QR Code found');
            });
        }
        return new Promise((res, rej)=> {
            var image = new Image();
            image.onload = function() {
                EPrescription.scanAndImportQRCodeImage(image).then(res).catch(rej);
            };
            image.src = URL.createObjectURL(file);
        });
    },
    findImagesFromPDF: async function(file: File): Promise<HTMLImageElement[]> {
        const typedArray = await new Promise((res)=> {
            var fileReader = new FileReader();
            fileReader.onload = function() {
                var typedArray = new Uint8Array(this.result as ArrayBuffer);
                res(typedArray);
            };
            fileReader.readAsArrayBuffer(file);
        });
        pdfjsLib.GlobalWorkerOptions.workerSrc = '/assets/javascripts/pdf.worker.mjs';
        const pdf = await pdfjsLib.getDocument(typedArray).promise;
        (window as any).pdf = pdf;


        const page1 = await pdf.getPage(1);
        const ops = await page1.getOperatorList();
        await pdf.loadingTask.promise;
        const imageArgs = ops.fnArray.map((fn, i)=> (fn === pdfjsLib.OPS.paintImageXObject) ? ops.argsArray[i][0] : null).filter(a => a);
        const bitmaps = (await Promise.all(imageArgs.map(async (arg)=> {
            try {
                const bitmapObj = await new Promise(res=> {
                    page1.objs.get(arg, res)
                }) as any;
                // Sometimes it might me a video frame, need to convert it:
                return await createImageBitmap(bitmapObj?.bitmap);
            } catch (_e) {
                return null;
            }
        }))).filter(a => a);
        const canvas = document.createElement('canvas');
        const ctx = canvas.getContext('2d');
        const result = [];
        for (const bitmap of bitmaps) {
            canvas.width = bitmap.width;
            canvas.height = bitmap.height;
            ctx.clearRect(0, 0, canvas.width, canvas.height);
            ctx.drawImage(bitmap, 0, 0, bitmap.width, bitmap.height);
            const url = canvas.toDataURL();
            const image = new Image();
            await new Promise(res => {
                image.onload = ()=> res(image);
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
    },
    scanAndImportQRCodeImage: function(imageElement: HTMLImageElement) {
        // I cannot find a reliable QRCode scanner library,
        // so here is it using multiple libraries.
        return Promise.any([
            EPrescription.scanImageWithDecoder(imageElement),
            EPrescription.scanImageWithZXing(imageElement)
        ])
            .catch(()=> {
                // Sometimes it returns null even if the entire image (600x600) is a QRCode
                // However It somehow works when the image is reduced to 300x300, not sure why.
                console.log('Try again with resized image');
                const smallerCanvas = document.createElement('canvas');
                smallerCanvas.width = imageElement.width / 2;
                smallerCanvas.height = imageElement.height / 2;
                const ctx = smallerCanvas.getContext('2d');
                ctx.drawImage(imageElement, 0, 0, imageElement.width / 2, imageElement.height / 2);
                return new Promise<HTMLImageElement>(res => {
                    const smallerImage = new Image();
                    smallerImage.onload = ()=> res(smallerImage);
                    smallerImage.src = smallerCanvas.toDataURL();
                })
                .then((smallerImage)=>
                    Promise.any([
                        EPrescription.scanImageWithDecoder(smallerImage),
                        EPrescription.scanImageWithZXing(smallerImage)
                    ])
                );
            })
            .then(EPrescription.importFromString);
    },
    scanImageWithDecoder: function(image: HTMLImageElement): Promise<string> {
        return ((window as any).QrcodeDecoder ? Promise.resolve() : Promise.resolve($.getScript('/assets/javascripts/qrcode-decoder.min.js')))
        .then(()=> {
            var qrScanner = EPrescription.qrScanner || new QrcodeDecoder.default();
            return qrScanner.decodeFromImage(image);
        }).then(function(result) {
            if (!result) {
                console.log('No QRCode found with decoder');
                return Promise.reject(null);
            }
            EPrescription.qrScanner = null;
            console.log('QRCode found with decoder');
            return result.data;
        });
    },
    scanImageWithZXing: function(image: HTMLImageElement): Promise<string> {
        return ((window as any).ZXing ? Promise.resolve() : Promise.resolve($.getScript('/assets/javascripts/zxing-library-0.21.3.js')))
        .then(()=>
            Promise.race([
                // For some reason, this library sometimes doesn't response, we add a 1s timeout just in case
                new Promise((_res, rej)=> {
                    setTimeout(rej, 1000);
                }),
                new (window as any).ZXing.BrowserQRCodeReader().decodeFromImage(image)
            ])
        ).then((result)=> {
            if (result && result.text) {
                console.log('found with zxing');
                return result.text;
            } else {
                console.log('No QRCode found with zxing');
                return Promise.reject(null);
            }
        }).catch((e)=> {
            console.log('No QRCode found with zxing');
            return Promise.reject(e);
        });
    },
    qrScanner: null,
    stopScanningQRCode: function() {
        if (EPrescription.qrScanner) {
            EPrescription.qrScanner.stop();
            EPrescription.qrScanner = null;
        }
    },
    importFromString: function(data) {
        return EPrescription.fromString(data).then(function(ep) {
            return EPrescription.toAMKPrescription(ep);
        }).then(function (amk) {
            var now = new Date();
            var filename = 'RZ_' +
                now.getFullYear() +
                ('0' + (now.getMonth() + 1)).slice(-2) +
                ('0' + now.getDate()).slice(-2) +
                ('0' + now.getHours()).slice(-2) +
                ('0' + now.getMinutes()).slice(-2) +
                ('0' + now.getSeconds()).slice(-2) +
                '.amk';
            (amk as AMKPrescription & {filename: string}).filename = filename;
            return Prescription.importAMKObjects([amk]);
        })
        .then(function(saveResults) {
            return Prescription.readComplete(saveResults[0]);
        })
        .then(UI.Prescription.show);
    },
    fromString: function(string) {
        var prefix = 'https://eprescription.hin.ch';
        if (string.startsWith(prefix)) {
            var sharpIndex = string.indexOf('#');
            string = string.substring(sharpIndex === -1 ? prefix.length : sharpIndex + 1);
            var andIndex = string.indexOf('&');
            if (andIndex !== -1) {
                string = string.substring(0, andIndex);
            }
        }
        prefix = 'CHMED16A0';
        if (string.startsWith(prefix)) {
            string = string.substring(prefix.length);
            return Promise.resolve(JSON.parse(string));
        }
        prefix = 'CHMED16A1';
        if (string.startsWith(prefix)) {
            string = string.substring(prefix.length);

            var byteCharacters = atob(string);
            var byteArrays = [];
            for (let i = 0; i < byteCharacters.length; i++) {
                byteArrays.push(byteCharacters.charCodeAt(i));
            }
            var byteArray = new Uint8Array(byteArrays);
            var blob = new Blob([byteArray], { type: 'text/plain' });

            var ds = new DecompressionStream("gzip");
            var decompressedStream = blob.stream().pipeThrough(ds);
            return new Response(decompressedStream).json().then(function(a) {
                // console.log('json', a);
                return a;
            });
        }
        return Promise.reject(new Error('Invalid prefix'));
    },
    fromPrescription: function(prescription) {
        function formatDateForEPrescription(dateStr) {
            // dd.mm.yyyy -> yyyy-mm-dd
            var parts = dateStr.split('.');
            if (parts.length !== 3) return null;
            return parts[2] + '-' + parts[1] + '-' + parts[0];
        }

        var ePrescriptionObj = {
            'Patient': {
                'FName': prescription.patient.given_name,
                'LName': prescription.patient.family_name,
                'BDt': formatDateForEPrescription(prescription.patient.birth_date),
                'Gender': prescription.patient.gender === 'm' ? 1 : 2,
                'Street': prescription.patient.postal_address,
                'Zip' : prescription.patient.zip_code,
                'City' : prescription.patient.city,
                'Lng': String(localStorage.getItem('language')) == 'fr' ? 'fr' : 'de',
                'Phone' : prescription.patient.phone_number,
                'Email' : prescription.patient.email_address,
                'Rcv' : prescription.patient.insurance_gln,
            },
            'Medicaments': prescription.medications.map(function(m){
                return {
                    'Id': m.eancode,
                    'IdType': 2 // GTIN
                };
            }),
            'MedType': 3, // Prescription
            'Id': crypto.randomUUID ? crypto.randomUUID() : String(Math.random()),
            'Auth': prescription.operator.gln || '',
            'Dt': new Date().toISOString()
        };
        return ePrescriptionObj;
    },
    parseDateString: function(dateString) {
        if (!dateString) return null;
        var date = new Date(dateString);
        if (date.getTime()) return date;
        // Sometimes we get invalid date string like: 2024-11-1300:00:00+2:00
        var matches = dateString.match(/([0-9]{4})-([0-9]{2})-([0-9]{2})([0-9]{2}):([0-9]{2}):([0-9]{2})([\\+|\\-])([0-9]{1,2}):?([0-9]{1,2})$/);
        if (!matches) return null;
        dateString = matches[1] + '-' +
            matches[2].padStart(2,'0') + '-' +
            matches[3].padStart(2,'0') + 'T' +
            matches[4].padStart(2,'0') + ':' +
            matches[5].padStart(2,'0') + ':' +
            matches[6].padStart(2,'0') +
            matches[7] +
            matches[8].padStart(2,'0') + ':' +
            matches[9].padStart(2,'0');
        date = new Date(dateString);
        if (!date.getTime()) return null;
        return date;
    },
    toAMKPrescription: function(ePrescriptionObj): Promise<AMKPrescription> {
        var date = EPrescription.parseDateString(ePrescriptionObj['Dt']) || new Date();
        // Normally place_date is composed with doctor's name or city,
        // however it's not available in ePrescription, instead we put the ZSR nummber here
        var placeDate = (ePrescriptionObj['Zsr'] || '') + ',' +
                        // dd.MM.yyyy (HH:mm:ss)
                        ('0' + date.getDate()).slice(-2) + '.' +
                        ('0' + (date.getMonth() + 1)).slice(-2) + '.' +
                        date.getFullYear() +
                        ' (' +
                        ('0' + date.getHours()).slice(-2) + ':' +
                        ('0' + date.getMinutes()).slice(-2) + ':' +
                        ('0' + date.getSeconds()).slice(-2) +
                        ')';
        var birthdate = EPrescription.parseDateString(ePrescriptionObj['Patient']['BDt']);
        // dd.MM.yyyy
        var birthdateString = birthdate ?
            String(birthdate.getDate()).padStart(2,'0') + '.' + String(birthdate.getMonth()).padStart(2,'0') + '.' + birthdate.getFullYear() :
            '';
        var patientIds = ePrescriptionObj['Patient']['Ids'] || [];
        var patientId = patientIds.length && patientIds[0]['Type'] === 1 ? patientIds[0]['Val'] : null;
        var healthCardNumber = null, insuranceGln = null;

        if (!patientId) {
            patientId = ePrescriptionObj['Patient']['Rcv'];
        }
        if (patientId) {
            if (patientId.includes('.') || patientId.length === 20) {
                healthCardNumber = patientId;
            } else if (patientId.length === 13) {
                insuranceGln = patientId;
            }
        }
        var patientWithoutId: Omit<AMKPatient, 'patient_id'> = {
            // "patient_id": // To be filled below
            "given_name": ePrescriptionObj['Patient']['FName'] || "",
            "family_name": ePrescriptionObj['Patient']['LName'] || "",
            "birth_date": birthdateString,
            "gender": ePrescriptionObj['Patient']['Gender'] === 1 ? "man" : "woman",
            "email_address": ePrescriptionObj['Patient']['Email'] || "",
            "phone_number": ePrescriptionObj['Patient']['Phone'] || "",
            "postal_address": ePrescriptionObj['Patient']['Street'] || "",
            "city": ePrescriptionObj['Patient']['City'] || "",
            "zip_code": ePrescriptionObj['Patient']['Zip'] || "",
            "insurance_gln": insuranceGln || "",
            "country": "",
            "weight_kg": "",
            "height_cm": "",
            "bag_number": "",
            "health_card_number": healthCardNumber || "",
            "health_card_expiry": "",
        };
        return Patient.generateAMKPatientId(patientWithoutId).then(function (patientId) {
            var patient = Object.assign({}, patientWithoutId, { patient_id: patientId });
            return {
                "prescription_hash": crypto.randomUUID ? crypto.randomUUID() : String(Math.random()),
                "place_date": placeDate,
                "operator": {
                    "gln": ePrescriptionObj['Auth'] || "",
                    "zsr_number": ePrescriptionObj['Zsr'] || "",
                    "title": "",
                    "given_name": "",
                    "family_name": "",
                    "postal_address": "",
                    "city": "",
                    "country": "",
                    "zip_code": "",
                    "phone_number": "",
                    "email_address": "",
                    "iban": "",
                    "vat_number": "",
                    "signature": "",
                },
                "patient": patient,
                "medications": ePrescriptionObj['Medicaments'].map(function(m) {
                    return {
                        "eancode": m["Id"]
                    };
                })
            };
        });
    }
};

// $('.prescription-actions').append($('<input type="file">').on('change', (event)=> {
//     const file = (event.target as HTMLInputElement).files[0];
//     if (!file) {
//         return;
//     }
//     EPrescription.scanImage(file);
// }));
