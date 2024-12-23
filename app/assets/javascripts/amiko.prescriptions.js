(function() {
var Doctor = {
    toAMKObjectWithoutSign: function(profile) {
        return {
            "title": profile.title,
            "gln": profile.gln,
            "given_name": profile.name,
            "family_name": profile.surname,
            "postal_address": profile.street,
            "city": profile.city,
            "country": profile.country,
            "zip_code": profile.zip,
            "phone_number": profile.phone,
            "email_address": profile.email,
            "iban": profile.iban,
            "vat_number": profile.vat,
            "zsr_number": profile.zsrnumber,
        };
    },
    fromAMKObject: function(obj) {
        return {
            title: obj.title,
            gln: obj.gln,
            name: obj.given_name,
            surname: obj.family_name,
            street: obj.postal_address,
            city: obj.city,
            country: obj.country,
            zip: obj.zip_code,
            phone: obj.phone_number,
            email: obj.email_address,
            iban: obj.iban,
            vat: obj.vat_number,
            zsrnumber: obj.zsr_number,
        };
    },
    fromCurrentUIState: function() {
        return {
            title: document.getElementsByName('doctor-field-title')[0].value,
            zsrnumber: document.getElementsByName('doctor-field-zsrnumber')[0].value,
            gln: document.getElementsByName('doctor-field-gln')[0].value,
            surname: document.getElementsByName('doctor-field-surname')[0].value,
            name: document.getElementsByName('doctor-field-name')[0].value,
            street: document.getElementsByName('doctor-field-street')[0].value,
            city: document.getElementsByName('doctor-field-city')[0].value,
            country: document.getElementsByName('doctor-field-country')[0].value,
            zip: document.getElementsByName('doctor-field-zip')[0].value,
            phone: document.getElementsByName('doctor-field-phone')[0].value,
            email: document.getElementsByName('doctor-field-email')[0].value,
            iban: document.getElementsByName('doctor-field-iban')[0].value,
            vat: document.getElementsByName('doctor-field-vat')[0].value,
        };
    },
    saveFromCurrentUIState: function() {
        var profile = Doctor.fromCurrentUIState();
        return Doctor.save(profile).then(UI.Prescription.reloadInfo);
    },
    save: function(profile) {
        return getPrescriptionDatabase().then(function (db) {
            return new Promise(function(resolve, reject) {
                var req = db.transaction("doctor", "readwrite")
                    .objectStore("doctor")
                    .put(profile, "doctor-profile");
                req.onsuccess = resolve;
                req.onerror = reject;
            });
        });
    },
    read: function() {
        return getPrescriptionDatabase().then(function (db) {
            return new Promise(function(resolve, reject) {
                var req = db.transaction("doctor")
                    .objectStore("doctor")
                    .get("doctor-profile");
                req.onsuccess = function(event) {
                    resolve(event.target.result || Doctor.fromCurrentUIState() /* Default empty when there isn't anything */);
                };
                req.onerror = reject;
            });
        });
    },
    getSignatureURL: function() {
        return localStorage.doctorSignImage;
    },
    getSignatureBase64: function() {
        var doctorSignData = localStorage.doctorSignImage;
        if (doctorSignData) {
            var index = doctorSignData.indexOf(',');
            return doctorSignData.slice(index + 1);
        }
        return undefined;
    },
    getSignatureImage: function() {
        var url = Doctor.getSignatureURL();
        if (!url) {
            return Promise.resolve(null);
        }
        return new Promise(function(res) {
            var img = new Image();
            img.onload = function() { res(img); };
            img.src = url;
        });
    },
    setSignatureWithURL: function(url) {
        localStorage.doctorSignImage = url;
    },
    setSignatureWithBase64: function(base64Str) {
        localStorage.doctorSignImage = 'data:image/png;base64,' + base64Str;
    },
    stringForPrescriptionPrinting: function(profile) {
        var s = "";
        if (profile.title) {
            s += profile.title + " ";
        }
        s += profile.name + " " + profile.surname;
        s += "\n" + profile.street;
        s += "\n" + profile.zip + " " + profile.city;
        if (profile.email) s += "\n" + profile.email;
        return s;
    }
};

var Patient = {
    toAMKObject: function(patient) {
        return {
            "patient_id": String(patient.id),
            "given_name": patient.name,
            "family_name": patient.surname,
            "birth_date": patient.birthday,
            "weight_kg": patient.weight,
            "height_cm": patient.height,
            "gender": patient.sex,
            "postal_address": patient.street,
            "zip_code": patient.zip,
            "city": patient.city,
            "country": patient.country,
            "phone_number": patient.phone,
            "email_address": patient.email,
            "bag_number": patient.bagnumber,
            "health_card_number": patient.cardnumber,
            "health_card_expiry": patient.cardexpiry,
            "insurance_gln": patient.gln,
        };
    },
    fromAMKObject: function(amkPatient) {
        return {
            id: parseInt(amkPatient.patient_id),
            name: amkPatient.given_name,
            surname: amkPatient.family_name,
            birthday: amkPatient.birth_date,
            weight: amkPatient.weight_kg,
            height: amkPatient.height_cm,
            sex: amkPatient.gender,
            street: amkPatient.postal_address,
            zip: amkPatient.zip_code,
            city: amkPatient.city,
            country: amkPatient.country,
            phone: amkPatient.phone_number,
            email: amkPatient.email_address,
            bagnumber: amkPatient.bag_number,
            cardnumber: amkPatient.health_card_number,
            cardexpiry: amkPatient.health_card_expiry,
            gln: amkPatient.insurance_gln,
        };
    },
    fromCurrentUIState: function() {
        var sexCheckbox = document.querySelector('input[name=address-book-field-sex]:checked');
        var birthdayString = document.getElementsByName('address-book-field-birthday')[0].value || '';
        var birthdayParts = birthdayString.split('-');
        if (birthdayParts.length === 3) {
            var year = birthdayParts[0];
            var month = birthdayParts[1];
            var date = birthdayParts[2];
            birthdayString = date + '.' + month + '.' + year;
        }
        var patient = {
            surname: document.getElementsByName('address-book-field-surname')[0].value,
            name: document.getElementsByName('address-book-field-name')[0].value,
            street: document.getElementsByName('address-book-field-street')[0].value,
            city: document.getElementsByName('address-book-field-city')[0].value,
            zip: document.getElementsByName('address-book-field-zip')[0].value,
            country: document.getElementsByName('address-book-field-country')[0].value,
            birthday: birthdayString,
            sex: sexCheckbox ? sexCheckbox.value : "",
            weight: document.getElementsByName('address-book-field-weight')[0].value,
            height: document.getElementsByName('address-book-field-height')[0].value,
            phone: document.getElementsByName('address-book-field-phone')[0].value,
            email: document.getElementsByName('address-book-field-email')[0].value,
            bagnumber: document.getElementsByName('address-book-field-bagnumber')[0].value,
            cardnumber: document.getElementsByName('address-book-field-cardnumber')[0].value,
            cardexpiry: document.getElementsByName('address-book-field-cardexpiry')[0].value,
            gln: document.getElementsByName('address-book-field-gln')[0].value,
        };
        var patientId = Patient.getCurrentId();
        if (patientId !== null) {
            patient.id = patientId;
        }
        return patient;
    },
    saveFromCurrentUIState: function() {
        var patient = Patient.fromCurrentUIState();
        return Patient.upsert(patient).then(UI.Prescription.reloadInfo);
    },
    getCurrentId: function() {
        try {
            var id = localStorage.currentPatientId;
            return parseInt(id) || null;
        } catch (_e) {
            return null;
        }
    },
    setCurrentId: function(id) {
        if (id === null) {
            localStorage.removeItem('currentPatientId');
        } else {
            localStorage.currentPatientId = id;
        }
    },
    // If the patient object has an `id` value, it updates existing patient
    // @return Promise<patientId>
    upsert: function(patient) {
        return getPrescriptionDatabase().then(function (db) {
            return new Promise(function(resolve, reject) {
                var req = db.transaction("patients", "readwrite")
                    .objectStore("patients")
                    .put(patient);
                req.onsuccess = function(event) {
                    var patientId = event.target.result;
                    Patient.setCurrentId(patientId);
                    resolve(patientId);
                };
                req.onerror = reject;
            });
        });
    },
    delete: function(id) {
        return getPrescriptionDatabase().then(function(db) {
            return new Promise(function(resolve, reject) {
                var req = db
                    .transaction("patients", "readwrite")
                    .objectStore("patients")
                    .delete(id);
                req.onsuccess = resolve;
                req.onerror = reject;
            })
            .then(function(){
                return Prescription.listSimplified(id);
            })
            .then(function(prescriptions) {
                return Promise.all(prescriptions.map(function(p) {
                    return Prescription.delete(p.id);
                }));
            });
        });
    },
    deleteAll: function() {
        return getPrescriptionDatabase().then(function(db) {
            return new Promise(function(resolve, reject){
                var req = db.transaction("patients", "readwrite")
                    .objectStore("patients")
                    .clear();
                req.onsuccess = resolve;
                req.onerror = reject;
            });
        });
    },
    list: function() {
        return getPrescriptionDatabase().then(function(db) {
            return new Promise(function(resolve, reject) {
                var objectStore = db.transaction("patients").objectStore("patients");
                var request = objectStore.getAll();
                request.onsuccess = function(event) {
                    resolve(event.target.result);
                };
                request.onerror = reject;
            });
        });
    },
    read: function(id) {
        return getPrescriptionDatabase().then(function (db) {
            return new Promise(function(resolve, reject) {
                var req = db.transaction("patients")
                    .objectStore("patients")
                    .get(id);
                req.onsuccess = function(event) {
                    resolve(event.target.result);
                };
                req.onerror = reject;
            });
        });
    },
    // Returns a map of old patient id -> new patient id
    importFromAMKPrescriptions: function (prescriptions) {
        var patientById = {};
        var oldPatientIdToNewPatientId = {};
        prescriptions.forEach(function(prescription) {
            var patient = prescription.patient;
            patientById[patient.patient_id] = patient;
        });
        return Promise.all(Object.keys(patientById).map(function(patientId) {
            var amkPatient = patientById[patientId];
            var patient = Patient.fromAMKObject(amkPatient);
            delete patient.id; // make it insert new patient
            return Patient.upsert(patient).then(function(newPatientId) {
                oldPatientIdToNewPatientId[patientId] = newPatientId;
            });
        })).then(function() {
            return oldPatientIdToNewPatientId;
        });
    },
    stringForPrescriptionPrinting: function(patient) {
        var s = "";
        s += patient.name + " " + patient.surname + "\n";
        s += patient.street + "\n";
        s += patient.zip + " " + patient.city + "\n";
        return s;
    }
};

var PrescriptionBasket = {
    add: function(data) {
        // data.title: String
        // data.eancode: String
        var basket = PrescriptionBasket.list();
        if (!data.comment) {
            data.comment = '';
        }
        basket.push(data);
        PrescriptionBasket.save(basket);
    },
    save: function(basket) {
        localStorage.prescriptionBasket = JSON.stringify(basket);
        UI.PrescriptionBasket.reloadList();
    },
    delete: function(index) {
        var basket = PrescriptionBasket.list();
        basket.splice(index, 1);
        localStorage.prescriptionBasket = JSON.stringify(basket);
        UI.PrescriptionBasket.reloadList();
    },
    clear: function() {
        localStorage.prescriptionBasket = '[]';
    },
    list: function() {
        return JSON.parse(localStorage.prescriptionBasket || "[]");
    }
};

var Prescription = {
    toAMKBlob: function(prescriptionObj) {
        prescriptionObj = Object.assign({}, prescriptionObj); // Shallow clone so we can
        // Remove the extra fields, see Prescription.fromCurrentUIState
        delete prescriptionObj.patient_id;
        delete prescriptionObj.id;
        delete prescriptionObj.filename;
        var json = JSON.stringify(prescriptionObj);
        var encoder = new TextEncoder('utf-8');
        var bytes = encoder.encode(json);
        var binary = '';
        var len = bytes.byteLength;
        for (var i = 0; i < len; i++) {
            binary += String.fromCharCode( bytes[i] );
        }
        var str = btoa(binary);
        var blob = new Blob([str], {
            type: 'document/amk'
        });
        return blob;
    },
    fromAMKString: function(amkStr) {
        var utf8 = atob(amkStr);
        var charCodes = [];
        for (var i = 0; i < utf8.length; i++) {
            charCodes.push(utf8.charCodeAt(i));
        }
        var decoder = new TextDecoder('utf-8');
        var utf16 = decoder.decode(new Uint8Array(charCodes));
        return JSON.parse(utf16);
    },
    fromCurrentUIState: function(overwriteCurrent) {
        // The saved object is
        // amk prescription object with
        // + patient_id: number <- refers to a patient in the patient store
        // + filename: string
        // + (automatically generated) id: number
        // - operator.signature <- to save data size
        var optionalPrescriptionId = overwriteCurrent ? Prescription.getCurrentId() : null;
        var pid = Patient.getCurrentId();
        if (pid === null) {
            alert('Please select a patient');
            return;
        }
        var now = new Date();
        var currentDateStr = '' +
            now.getFullYear() +
            ('0' + (now.getMonth() + 1)).slice(-2) +
            ('0' + now.getDate()).slice(-2) +
            ('0' + now.getHours()).slice(-2) +
            ('0' + now.getMinutes()).slice(-2) +
            ('0' + now.getSeconds()).slice(-2);
        var filenamePromise = optionalPrescriptionId ? Prescription.readComplete(optionalPrescriptionId).then(p => p.filename) : Promise.resolve(null);
        return Promise.all([
                Doctor.read(),
                Patient.read(pid),
                filenamePromise
            ])
            .then(function(result) {
                var profile = result[0];
                var patient = result[1];
                var filename = result[2];

                var doctorSignData = Doctor.getSignatureBase64();
                var now = new Date();

                var prescriptionObj = {
                    // Non-AMK extra fields
                    patient_id: Number(patient.id),
                    filename: filename || "RZ_"+currentDateStr+".amk",
                    // AMK fields
                    prescription_hash: crypto.randomUUID(),
                    place_date: profile.city + ', ' +
                        // dd.MM.yyyy (HH:mm:ss)
                        ('0' + now.getDate()).slice(-2) + '.' +
                        ('0' + (now.getMonth() + 1)).slice(-2) + '.' +
                        now.getFullYear() +
                        ' (' +
                        ('0' + now.getHours()).slice(-2) + ':' +
                        ('0' + now.getMinutes()).slice(-2) + ':' +
                        ('0' + now.getSeconds()).slice(-2) +
                        ')',
                    operator: Doctor.toAMKObjectWithoutSign(profile),
                    patient: Patient.toAMKObject(patient),
                    medications: PrescriptionBasket.list().map(item => {
                        var titleComponents = item.package.split('[');
                        titleComponents = titleComponents[0].split(',');
                        return {
                            title: item.title,
                            owner: item.author,
                            regnrs: item.regnrs,
                            atccode: item.atccode,
                            product_name: titleComponents[0],
                            package: item.package,
                            eancode: item.eancode,
                            // TODO: note is legacy, remove it later
                            comment: item.note || item.comment || '',
                        };
                    }),
                };
                // if optionalPrescriptionId is present, it updates existing prescription
                if (optionalPrescriptionId) {
                    prescriptionObj.id = optionalPrescriptionId;
                }
                return prescriptionObj;
            });
    },
    getCurrentId: function() {
        if (!localStorage.currentPrescriptionId) return null;
        return Number(localStorage.currentPrescriptionId);
    },
    setCurrentId: function(prescriptionId) {
        if (!prescriptionId) {
            localStorage.removeItem('currentPrescriptionId');
        } else {
            localStorage.currentPrescriptionId = prescriptionId;
        }
    },
    saveFromCurrentUIState: function(overwriteCurrent) {
        return Prescription.fromCurrentUIState(overwriteCurrent).then(Prescription.save).then(Prescription.setCurrentId);
    },
    save: function(prescription) {
        return getPrescriptionDatabase().then(function (db) {
            return new Promise(function(resolve, reject) {
                var req = db.transaction("prescriptions", "readwrite")
                    .objectStore("prescriptions")
                    .put(prescription);
                req.onsuccess = function(e) {
                    var prescriptionId = e.target.result;
                    resolve(prescriptionId);
                };
                req.onerror = reject;
            });
        });
    },
    delete: function(prescriptionId) {
        return getPrescriptionDatabase().then(function(db) {
            return new Promise(function(resolve, reject) {
                var req = db
                    .transaction("prescriptions", "readwrite")
                    .objectStore("prescriptions")
                    .delete(prescriptionId);
                req.onsuccess = resolve;
                req.onerror = reject;
            });
        }).then(function() {
            if (prescriptionId === Prescription.getCurrentId()) {
                Prescription.setCurrentId(null);
            }
        });
    },
    deleteAll: function() {
        return getPrescriptionDatabase().then(function(db) {
            return new Promise(function(resolve, reject){
                var req = db.transaction("prescriptions", "readwrite")
                    .objectStore("prescriptions")
                    .clear();
                req.onsuccess = resolve;
                req.onerror = reject;
            });
        });
    },
    listSimplified: function(patientId) {
        if (!patientId) {
            return Promise.resolve([]);
        }
        // This function returns the saved, simplified version of prescription,
        // which doesn't have the signature to save space
        return getPrescriptionDatabase().then(function (db) {
            return new Promise(function (res, rej) {
                var store = db.transaction("prescriptions").objectStore("prescriptions");
                var index = store.index('patient_id');
                var getAllRequest = index.getAll(patientId);
                getAllRequest.onsuccess = function() {
                  res(getAllRequest.result.reverse());
                };
                getAllRequest.onerror = rej;
            });
        });
    },
    makeComplete: function(simplifiedPrescription) {
        // We do not save the doctor's signature in the DB to save space,
        // this function add the signature back to a simplified prescription
        var doctorSignData = Doctor.getSignatureBase64();
        if (doctorSignData) {
            simplifiedPrescription.operator.signature = doctorSignData;
        }
        return simplifiedPrescription;
    },
    readComplete: function(prescriptionId) {
        console.log('Reading Precription: ', prescriptionId);
        return getPrescriptionDatabase().then(function (db) {
            return new Promise(function(resolve, reject) {
                var req = db.transaction("prescriptions")
                    .objectStore("prescriptions")
                    .get(prescriptionId);
                req.onsuccess = function(event) {
                    var p = event.target.result;
                    var full = Prescription.makeComplete(p);
                    resolve(full);
                };
                req.onerror = reject;
            });
        });
    },
    // Import prescriptions and _new_ patients
    importAMKObjects: function(amkObjs) {
        return Patient.importFromAMKPrescriptions(amkObjs).then(function(oldPatientIdToNewPatientId) {
            return sequencePromise(amkObjs.map(function(amkObj) {
                return function() {
                    amkObj.patient_id = amkObj.patient.patient_id = oldPatientIdToNewPatientId[amkObj.patient.patient_id];
                    return Prescription.save(amkObj);
                };
            }));
        });
    }
};
var UI = {
    Doctor: {
        showModal: function() {
            var modal = document.querySelector('dialog.prescriptions-doctor');
            modal.showModal();
            Doctor.read().then(UI.Doctor.applyToModal);
            UI.Doctor.reloadOAuthState();
        },
        closeModal: function() {
            var modal = document.querySelector('dialog.prescriptions-doctor');
            modal.close();
        },
        applyToModal: function(profile) {
            document.getElementsByName('doctor-field-title')[0].value = profile.title;
            document.getElementsByName('doctor-field-zsrnumber')[0].value = profile.zsrnumber;
            document.getElementsByName('doctor-field-gln')[0].value = profile.gln;
            document.getElementsByName('doctor-field-surname')[0].value = profile.surname;
            document.getElementsByName('doctor-field-name')[0].value = profile.name;
            document.getElementsByName('doctor-field-street')[0].value = profile.street;
            document.getElementsByName('doctor-field-city')[0].value = profile.city;
            document.getElementsByName('doctor-field-country')[0].value = profile.country;
            document.getElementsByName('doctor-field-zip')[0].value = profile.zip;
            document.getElementsByName('doctor-field-phone')[0].value = profile.phone;
            document.getElementsByName('doctor-field-email')[0].value = profile.email;
            document.getElementsByName('doctor-field-iban')[0].value = profile.iban;
            document.getElementsByName('doctor-field-vat')[0].value = profile.vat;
            document.getElementById('doctor-sign-image').src = Doctor.getSignatureURL() || '';
        },
        reloadOAuthState:function() {
            var oauthSDSContainer = $('#oauth-sds-status').empty();
            if (OAuth.SDS.isLoggedIn()) {
                oauthSDSContainer
                .append($('<span>').text('HIN ID: ' + OAuth.SDS.currentCredentials().hinId))
                .append(
                    $('<button>')
                        .attr('type', 'button')
                        .text(PrescriptionLocalization.import_profile)
                        .on('click', function() {
                            OAuth.SDS.importProfile(true).then(function() {
                                return Doctor.read().then(UI.Doctor.applyToModal);
                            });
                        })
                )
                .append(
                    $('<button>')
                        .attr('type', 'button')
                        .text(PrescriptionLocalization.logout_from_hin_sds)
                        .on('click', OAuth.SDS.logout)
                );
            } else {
                oauthSDSContainer.append(
                    $('<button>')
                        .attr('type', 'button')
                        .text(PrescriptionLocalization.login_with_hin_sds)
                        .on('click', OAuth.SDS.login)
                );
            }
            var oauthADSwissContainer = $('#oauth-adswiss-status').empty();

            if (OAuth.ADSwiss.isLoggedIn()) {
                oauthADSwissContainer
                .append($('<span>').text('HIN ID: ' + OAuth.ADSwiss.currentCredentials().hinId))
                .append(
                    $('<button>')
                        .attr('type', 'button')
                        .text(PrescriptionLocalization.logout_from_hin_adswiss)
                        .on('click', OAuth.ADSwiss.logout)
                );
            } else {
                oauthADSwissContainer.append(
                    $('<button>')
                        .attr('type', 'button')
                        .text(PrescriptionLocalization.login_with_hin_adswiss)
                        .on('click', OAuth.ADSwiss.login)
                );
            }
        }
    },
    Patient: {
        showModal: function() {
            var modal = document.querySelector('dialog.prescriptions-address-book');
            UI.Patient.fillModalPatientList();
            var pid = Patient.getCurrentId();
            if (pid) {
                UI.Patient.fillModal(pid);
            }
            modal.showModal();
        },
        closeModal: function() {
            var modal = document.querySelector('dialog.prescriptions-address-book');
            modal.close();
        },
        fillModal: function(id) {
            return Patient.read(id)
                .then(function (patient) {
                    UI.Patient.fillModalForm(patient);
                    return UI.Patient.fillModalPatientList();
                })
                .then(UI.Prescription.reloadInfo);
        },
        fillModalForm: function(patient) {
            var birthdayString = patient.birthday || '';
            var birthdayParts = (patient.birthday || '').split('.');
            if (birthdayParts.length === 3) {
                var year = birthdayParts[2];
                var month = birthdayParts[1];
                var date = birthdayParts[0];
                birthdayString = year + '-' + month + '-' + date;
            }
            Patient.setCurrentId(patient.id);
            Prescription.setCurrentId(null);
            document.getElementsByName('address-book-field-surname')[0].value = patient.surname;
            document.getElementsByName('address-book-field-name')[0].value = patient.name;
            document.getElementsByName('address-book-field-street')[0].value = patient.street;
            document.getElementsByName('address-book-field-city')[0].value = patient.city;
            document.getElementsByName('address-book-field-zip')[0].value = patient.zip;
            document.getElementsByName('address-book-field-country')[0].value = patient.country;
            document.getElementsByName('address-book-field-birthday')[0].value = birthdayString;
            document.querySelector('input[name=address-book-field-sex][value=m]').checked =
            document.querySelector('input[name=address-book-field-sex][value=f]').checked = false;
            if (patient.sex === 'f' || patient.sex === 'm') {
                document.querySelector('input[name=address-book-field-sex][value=' + patient.sex + ']').checked = true;
            }
            document.getElementsByName('address-book-field-weight')[0].value = patient.weight;
            document.getElementsByName('address-book-field-height')[0].value = patient.height;
            document.getElementsByName('address-book-field-phone')[0].value = patient.phone;
            document.getElementsByName('address-book-field-email')[0].value = patient.email;
            document.getElementsByName('address-book-field-bagnumber')[0].value = patient.bagnumber;
            document.getElementsByName('address-book-field-cardnumber')[0].value = patient.cardnumber;
            document.getElementsByName('address-book-field-cardexpiry')[0].value = patient.cardexpiry;
            document.getElementsByName('address-book-field-gln')[0].value = patient.gln;
        },
        fillModalPatientList: function() {
            return Patient.list().then(function(patients) {
                var container = document.getElementsByClassName('prescriptions-address-book-patients')[0];
                container.innerHTML = '';
                patients.forEach(function (patient) {
                    var id = patient.id;
                    var div = document.createElement('div');
                    div.className = 'prescriptions-address-book-patient ' + (id === Patient.getCurrentId() ? '--selected' : '');
                    div.innerText = patient.name + ' ' + patient.surname;
                    div.onclick = function () {
                        UI.Patient.fillModal(id);
                    };
                    div.ondblclick = UI.Patient.closeModal;
                    var deleteButton = document.createElement('div');
                    deleteButton.className = 'prescriptions-address-book-patient-delete';
                    div.appendChild(deleteButton);
                    deleteButton.onclick = function(e) {
                        e.stopPropagation();
                        UI.Patient.delete(id);
                    };
                    container.appendChild(div);
                });
            });
        },
        delete: function(id) {
            return Patient.delete(id)
                .then(UI.Patient.fillModalPatientList)
                .then(UI.Patient.reset);
        },
        reset: function() {
            Patient.setCurrentId(null);
            UI.Patient.resetForm();
            UI.Prescription.reloadInfo();
        },
        resetForm: function() {
            document.getElementsByName('address-book-field-surname')[0].value = '';
            document.getElementsByName('address-book-field-name')[0].value = '';
            document.getElementsByName('address-book-field-street')[0].value = '';
            document.getElementsByName('address-book-field-city')[0].value = '';
            document.getElementsByName('address-book-field-zip')[0].value = '';
            document.getElementsByName('address-book-field-country')[0].value = '';
            document.getElementsByName('address-book-field-birthday')[0].value = '';
            document.querySelector('input[name=address-book-field-sex][value=m]').checked =
            document.querySelector('input[name=address-book-field-sex][value=f]').checked = false;
            document.getElementsByName('address-book-field-weight')[0].value = '';
            document.getElementsByName('address-book-field-height')[0].value = '';
            document.getElementsByName('address-book-field-phone')[0].value = '';
            document.getElementsByName('address-book-field-email')[0].value = '';
            document.getElementsByName('address-book-field-bagnumber')[0].value = '';
            document.getElementsByName('address-book-field-cardnumber')[0].value = '';
            document.getElementsByName('address-book-field-cardexpiry')[0].value = '';
            document.getElementsByName('address-book-field-gln')[0].value = '';
        }
    },
    PrescriptionBasket: {
        reloadList: function() {
            $('.prescription-items').empty();
            PrescriptionBasket.list().forEach(function (item, i) {
                $('.prescription-items').append(
                    $('<div>')
                        .addClass('prescription-item')
                        .append(
                            $('<div>')
                                .addClass('prescription-item-actions')
                                .append(
                                    $('<button>').addClass('delete-button').on('click', function (e) {
                                        PrescriptionBasket.delete(i);
                                    })
                                )
                        )
                        .append(
                            $('<div>')
                            .addClass('prescription-item-name')
                            .text(item.package)
                        )
                        .append(
                            $('<input>')
                                .addClass('prescription-item-comment')
                                .data('prescription-item-index', i)
                                // TODO: note is legacy, remove it later
                                .attr('value', item.note || item.comment || '')
                        )
                );
            });
        },
    },
    Prescription: {
        reloadInfo: function() {
            Doctor.read().then(function(profile) {
                var div = document.getElementsByClassName('prescription-doctor-info')[0];
                if (!profile) {
                    div.innerText = '';
                } else {
                    div.innerText = profile.title + ' ' + profile.name + ' ' + profile.surname;
                }
            });
            var patientInfo = document.getElementsByClassName('prescription-patent-info')[0];
            var patientId = Patient.getCurrentId();
            if (patientId === null) {
                patientInfo.innerText = '';
            } else {
                Patient.read(patientId).then(function(patient) {
                    if (patient) {
                        patientInfo.innerText = patient.name + ' ' + patient.surname;
                    } else {
                        // Cannot find patient, remove id
                        Patient.setCurrentId(null);
                    }
                });
            }
            UI.PrescriptionBasket.reloadList();
            return UI.Prescription.reloadList();
        },
        reloadList: function() {
            var list = $('#prescriptions-right-list');
            list.html('');
            var pid = Patient.getCurrentId();

            return (pid === null ? Promise.resolve([]) : Prescription.listSimplified(pid))
                .then(function(prescriptions) {
                    prescriptions.forEach(function(prescription) {
                        list.append(
                            $('<div>')
                            .text(prescription.filename)
                            .addClass('prescriptions-right-list-item')
                            .on('click', function() {
                                PrescriptionBasket.clear();
                                prescription.medications.forEach(function(m) {
                                    PrescriptionBasket.add({
                                        title: m.title,
                                        author: m.owner,
                                        regnrs: m.regnrs,
                                        atccode: m.atccode,
                                        package: m.package,
                                        eancode: m.eancode,
                                        // TODO: note is legacy, remove it later
                                        comment: m.note || m.comment || '',
                                    });
                                });
                                UI.PrescriptionBasket.reloadList();
                                Patient.setCurrentId(prescription.patient_id);
                                Prescription.setCurrentId(prescription.id);
                                var patientInfo = document.getElementsByClassName('prescription-patent-info')[0];
                                patientInfo.innerText = prescription.patient.given_name + ' ' + prescription.patient.family_name;
                            })
                            .append(
                                $('<button>').addClass('download-button').on('click', function (e) {
                                    e.stopPropagation();
                                    Prescription.readComplete(prescription.id).then(function(obj) {
                                        var blob = Prescription.toAMKBlob(obj);
                                        downloadBlob(blob, prescription.filename);
                                    });
                                })
                            )
                            .append(
                                $('<button>').addClass('delete-button').on('click', function (e) {
                                    e.stopPropagation();
                                    Prescription.delete(prescription.id).then(UI.Prescription.reloadList);
                                })
                            )
                        );
                    });
                });
        }
    }
};
var OAuth = {
    // Return: {
    // accessToken
    // refreshToken
    // hinId
    // expiresAt
    // }
    getWithApplicationName: function(applicationName) {
        var str = localStorage['oauth-' + applicationName];
        if (!str) return null;
        var obj = JSON.parse(str);
        return Object.assign(obj, {
            expiresAt: new Date(obj.expiresAt)
        });
    },
    renewTokenIfNeeded: function(applicationName) {
        var oauthObj = OAuth.getWithApplicationName(applicationName);
        if (oauthObj.expiresAt > new Date()) {
            return Promise.resolve(oauthObj);
        }
        return Promise.resolve($.post('/oauth/renew_token', {
            refresh_token: oauthObj.refreshToken
        })).then(function(oauthResponse) {
            var newOAuthObj = {
                accessToken: oauthResponse.access_token,
                refreshToken: oauthResponse.refresh_token,
                hinId: oauthResponse.hin_id,
                expiresAt: new Date(new Date().getTime() + 1000 * oauthResponse.expires_in).toISOString()
            };
            localStorage['oauth-' + applicationName] = JSON.stringify(newOAuthObj);
            return OAuth.getWithApplicationName(applicationName);
        });
    },
    SDS: {
        currentCredentials: function() {
            return OAuth.getWithApplicationName('hin_sds');
        },
        isLoggedIn: function() {
            return !!OAuth.SDS.currentCredentials();
        },
        login: function() {
            location.href = '/oauth/sds';
        },
        logout: function() {
            localStorage.removeItem('oauth-hin_sds');
            UI.Doctor.reloadOAuthState();
        },
        fetchSelf: function() {
            if (!OAuth.SDS.isLoggedIn()) {
                return Promise.reject('Not logged in');
            }
            return OAuth.renewTokenIfNeeded('hin_sds')
            .then(function(oauth) {
                return fetch('/sds/profile?access_token=' + encodeURIComponent(oauth.accessToken));
            })
            .then(function(res) {
                return res.json();
            });
        },
        importProfile: function(fromUI) {
            return Promise.all([
                OAuth.SDS.fetchSelf(),
                fromUI ? Doctor.fromCurrentUIState() : Doctor.read(),
            ])
            .then(function(result) {
                var response = result[0];
                var doctor = result[1];
                if (!doctor.email) {
                    doctor.email = response.email;
                }
                if (!doctor.surname) {
                    doctor.surname = response.contactId.lastName;
                }
                if (!doctor.name) {
                    doctor.name = response.contactId.firstName;
                }
                if (!doctor.street) {
                    doctor.street = response.contactId.address;
                }
                if (!doctor.zip) {
                    doctor.zip = response.contactId.postalCode;
                }
                if (!doctor.city) {
                    doctor.city = response.contactId.city;
                }
                if (!doctor.country) {
                    doctor.country = response.contactId.countryCode;
                }
                if (!doctor.phone) {
                    doctor.phone = response.contactId.phoneNr;
                }
                if (!doctor.gln) {
                    doctor.gln = response.contactId.gln;
                }
                return Doctor.save(doctor);
            })
            .then(function() {
                return Doctor.read().then(UI.Doctor.applyToModal);
            });
        }
    },
    ADSwiss: {
        currentCredentials: function() {
            return OAuth.getWithApplicationName(adswissAppName);
        },
        isLoggedIn: function() {
            return !!OAuth.ADSwiss.currentCredentials();
        },
        login: function() {
            location.href = '/oauth/adswiss';
        },
        logout: function() {
            localStorage.removeItem('oauth-' + adswissAppName);
            localStorage.removeItem('auth-handle-' + adswissAppName);
            UI.Doctor.reloadOAuthState();
        },
        fetchSAML: function() {
            return OAuth.renewTokenIfNeeded(adswissAppName)
                .then(function(oauth) {
                    return Promise.resolve($.post('/adswiss/saml?access_token=' + encodeURIComponent(oauth.accessToken)));
                })
                .then(function(obj) {
                    return obj.epdAuthUrl;
                });
        },
        // @return {
        //     token: string,
        //     expiresAt: Date,
        //     lastUsedAt: Date
        // }
        getAuthHandle: function() {
            // if (1) return  {token:'aaaa', expiresAt: new Date("2024-08-16T13:17:51.979Z" ), lastUsedAt: new Date()};
            var str = localStorage['auth-handle-' + adswissAppName];
            if (!str) return null;
            var obj = JSON.parse(str);
            obj.expiresAt = new Date(obj.expiresAt);
            obj.lastUsedAt = new Date(obj.lastUsedAt);
            var now = new Date();
            if (now >= obj.expiresAt || now.getTime() >= obj.lastUsedAt + 2 * 60 * 60 * 1000) { // Expires after 2 hours of idle
                localStorage.removeItem('auth-handle-' + adswissAppName);
                return null;
            }
            return obj;
        },
        updateAuthHandleLastUsed: function() {
            var authHandle = OAuth.ADSwiss.getAuthHandle();
            if (!authHandle) {
                console.warn('Cannot update auth handle lastUsed');
                return;
            }
            localStorage['auth-handle-' + adswissAppName] = JSON.stringify({
                token: authHandle.token,
                expiresAt: authHandle.expiresAt.toISOString(),
                lastUsedAt: new Date().toISOString()
            });
        },
        makeQRCodeWithPrescription: function(prescription) {
            var authHandle = OAuth.ADSwiss.getAuthHandle();
            if (!authHandle) return Promise.reject();
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
                    'Gender': prescription.patient.gender == 'm' ? 1 : 2,
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
                'Id': crypto.randomUUID(),
                'Auth': prescription.operator.gln || '',
                'Dt': new Date().toISOString()
            };

            var encoder = new TextEncoder('utf-8');
            console.log('[PDF generation] Making EPrescription (1)');
            var bodyStream = new ReadableStream({
                start: function(controller) {
                    controller.enqueue(encoder.encode(JSON.stringify(ePrescriptionObj)));
                    controller.close();
                }
            })
                .pipeThrough(new CompressionStream("gzip"));
            return new Response(bodyStream).blob()
            .then(function(blob) {
                console.log('[PDF generation] Making EPrescription (2)');
               return new Promise(function(res) {
                    var reader = new FileReader();
                    reader.onload = function() {
                        var dataUrl = reader.result;
                        var base64 = dataUrl.split(',')[1];
                        res(base64);
                    };
                    reader.readAsDataURL(blob);
                });
            }).then(function(str) {
                console.log('[PDF generation] Making EPrescription (3)');
                return fetch('/adswiss/eprescription_qr?auth_handle=' + encodeURIComponent(authHandle.token), {
                    method: 'POST',
                    body: 'CHMED16A1' + str,
                    headers: {
                        'Content-Type': 'text/plain',
                        'Csrf-Token': XHRCSRFToken
                    }
                });
            }).then(function(res) {
                console.log('[PDF generation] Making Response status: ', res.status);
                if (res.status >= 300) {
                    return res.text().then(function(t) {
                        console.log('Unexpected status, body: ', t);
                        throw new Error('Unexpected QR Response');
                    });
                }
                OAuth.ADSwiss.updateAuthHandleLastUsed();
                return res.blob();
            }).then(function(blob) {
                console.log('[PDF generation] Making EPrescription (4)');
                var url = URL.createObjectURL(blob);
                return new Promise(function(res, rej) {
                    var img = new Image();
                    img.onload = function() { res(img); };
                    img.onerror = function (e) {
                        rej(new Error('Cannot generate image from response'));
                    };
                    img.src = url;
                });
            });
        }
    }
};

var db = null;
function getPrescriptionDatabase() {
    if (db !== null) {
        return Promise.resolve(db);
    }
    return new Promise(function(resolve, reject) {
        var request = window.indexedDB.open("prescriptions", 2);
        request.onerror = function(event) {
            console.error('Cannot open database', request.errorCode);
            reject(event);
        };
        request.onsuccess = function(event) {
            // Do something with request.result!
            db = request.result;
            resolve(db);
        };
        request.onupgradeneeded = function(event) {
            var db = event.target.result;
            // Version 1
            if (event.oldVersion <= 0) {
                var _doctorStore = db.createObjectStore("doctor");
                var _patientStore = db.createObjectStore("patients", { keyPath: "id", autoIncrement: true });
            }
            // Version 2
            if (event.oldVersion <= 1) {
                var prescriptionStore = db.createObjectStore("prescriptions", { keyPath: "id", autoIncrement: true });
                prescriptionStore.createIndex("patient_id", "patient_id");
            }
        };
    });
}

function didPickDoctorSignatureImage(file) {
    var minSignWidth = 90, minSignHeight = 45;
    var maxSignWidth = 500, maxSignHeight = 500; // We cannot store too much data in browser

    var reader = new FileReader();
    reader.onload = function() {
        var dataURL = reader.result;
        var image = new Image();
        image.onload = function() {
            var ratio;
            var url;
            var canvas = document.createElement('canvas');
            var context = canvas.getContext('2d');
            if (image.width < minSignWidth || image.height < minSignHeight) {
                ratio = Math.max(minSignWidth / image.width, minSignHeight / image.height);
                canvas.width = image.width * ratio;
                canvas.height = image.height * ratio;
                context.drawImage(image, 0, 0, canvas.width, canvas.height);
                url = canvas.toDataURL('image/png');
            } else if (image.width > maxSignWidth || image.height > maxSignHeight) {
                ratio = Math.min(maxSignWidth / image.width, maxSignHeight / image.height);
                canvas.width = image.width * ratio;
                canvas.height = image.height * ratio;
                context.drawImage(image, 0, 0, canvas.width, canvas.height);
                url = canvas.toDataURL('image/png');
            } else {
                url = dataURL;
            }
            Doctor.setSignatureWithURL(url);
            console.log('sign url', url);
            var signDisplay = document.getElementById('doctor-sign-image');
            signDisplay.src = url;
        };
        image.src = dataURL;
    };
    reader.readAsDataURL(file);
}

document.addEventListener('DOMContentLoaded', function() {
    if (!document.URL.endsWith('/prescription') && !document.URL.endsWith('/rezept')) {
        return;
    }
    document.getElementById('prescription-choose-patient-button').addEventListener('click',
        UI.Patient.showModal
    );
    document.getElementById('prescription-edit-doctor-button').addEventListener('click',
        UI.Doctor.showModal
    );
    document.getElementById('doctor-save').addEventListener('click', function() {
        Doctor.saveFromCurrentUIState();
        UI.Doctor.closeModal();
    });
    document.getElementById('doctor-sign-input').addEventListener('change', function(e) {
        console.log(e);
        if (!e.target.files.length) return;
        didPickDoctorSignatureImage(e.target.files[0]);
    });
    document.getElementById('patient-save').addEventListener('click', function() {
        Patient.saveFromCurrentUIState();
        UI.Patient.fillModalPatientList();
    });
    document.getElementById('patient-create').addEventListener('click',
        UI.Patient.reset
    );
    document.getElementById('prescription-save').addEventListener('click', function() {
        if (Patient.getCurrentId() === null) {
            alert(PrescriptionLocalization.prescription_please_choose_patient);
            return;
        }
        if (Prescription.getCurrentId()) {
            // Ask if save new prescription
            var modal = document.getElementById('prescriptions-save-confirm');
            modal.showModal();
        } else {
            // Just save new prescription
            Prescription.saveFromCurrentUIState(false)
                .then(UI.Prescription.reloadList);
        }
    });
    document.getElementById('prescription-save-new').addEventListener('click', function() {
        Prescription.saveFromCurrentUIState(false).then(UI.Prescription.reloadList);
        var modal = document.getElementById('prescriptions-save-confirm');
        modal.close();
    });
    document.getElementById('prescription-save-overwrite').addEventListener('click', function() {
        Prescription.saveFromCurrentUIState(true).then(UI.Prescription.reloadList);
        var modal = document.getElementById('prescriptions-save-confirm');
        modal.close();
    });
    document.getElementById('prescription-create').addEventListener('click', function() {
        PrescriptionBasket.clear();
        Prescription.setCurrentId(null);
        UI.PrescriptionBasket.reloadList();
    });
    document.getElementById('prescription-export-all').addEventListener('click', function() {
        exportEverything();
    });
    document.getElementById('prescription-import-all').addEventListener('change', function(e) {
        console.log(e);
        if (!e.currentTarget.files.length) return;
        importFromZip(e.currentTarget.files[0]);
    });
    document.getElementById('prescription-print').addEventListener('click',
        generatePDFWithEPrescriptionPrompt
    );
    $(document).on('change', 'input.prescription-item-comment', function(e) {
        var index = $(e.target).data('prescription-item-index');
        var items = PrescriptionBasket.list();
        items[index].comment = e.target.value;
        PrescriptionBasket.save(items);
    });

    $(document).on('click', 'p.article-packinfo', function(e) {
        var data = $(e.currentTarget).data('prescription');
        PrescriptionBasket.add(data);
    });
    UI.Prescription.reloadInfo();
    // Auto import doctor from SDS after OAuth
    if (localStorage['needs-import-sds']) {
        OAuth.SDS.importProfile(false);
        localStorage.removeItem('needs-import-sds');
    }
    if (localStorage['ePrescription-flow-next'] === 'login-saml') {
        OAuth.ADSwiss.fetchSAML().then(function(url) {
            localStorage['ePrescription-flow-next'] = 'make-pdf';
            document.location = url;
        });
    } else if (localStorage['ePrescription-flow-next'] === 'make-pdf') {
        localStorage.removeItem('ePrescription-flow-next');
        generatePDFWithEPrescriptionPrompt();
    }
});

function downloadBlob(blob, filename) {
    var url = URL.createObjectURL(blob);
    var element = window.document.createElement('a');
    element.href = url;
    element.download = filename;
    element.style.display = 'none';
    document.body.appendChild(element);
    element.click();
    document.body.removeChild(element);
}

function exportEverything() {
    (window.JSZip ? Promise.resolve() : Promise.resolve(
        $.getScript('/assets/javascripts/jszip.min.js')
    ))
    .then(getPrescriptionDatabase)
    .then(function(db) {
        return Promise.all([
            new Promise(function (res, rej) {
                var store = db.transaction("prescriptions").objectStore("prescriptions");
                var getAllRequest = store.getAll();
                getAllRequest.onsuccess = function() {
                  res(getAllRequest.result);
                };
                getAllRequest.onerror = rej;
            }),
            Favourites.getRegNrs(),
            Favourites.getFullTextHashes(),
        ]);
    })
    .then(function(results) {
        var simplifiedPrescriptions = results[0];
        var favRegnrs = results[1];
        var ftFavRegnrs = results[2];
        var zip = new JSZip();
        simplifiedPrescriptions.forEach(function(prescription) {
            var obj = Prescription.makeComplete(prescription);
            var blob = Prescription.toAMKBlob(obj);
            zip.file(prescription.filename, blob);
        });
        zip.file('favourites.json', JSON.stringify(favRegnrs));
        zip.file('ft-favourites.json', JSON.stringify(ftFavRegnrs));
        return zip.generateAsync({type:"blob"});
    })
    .then(function(blob) {
        // dd.mm.yyyy-hh.mm.ss.zip
        var now = new Date();
        var filename =
            ('0' + now.getDate()).slice(-2) + '.' +
            ('0' + (now.getMonth() + 1)).slice(-2) + '.' +
            now.getFullYear() +
            '-' +
            ('0' + now.getHours()).slice(-2) + '.' +
            ('0' + now.getMinutes()).slice(-2) + '.' +
            ('0' + now.getSeconds()).slice(-2) +
            '.zip';
        downloadBlob(blob, filename);
    });
}

function importFromZip(file) {
    var amksPromise;
    var otherPromiseFns = [];
    if (file.name.endsWith('.zip')) {
        amksPromise = (window.JSZip ? Promise.resolve() : Promise.resolve(
            $.getScript('/assets/javascripts/jszip.min.js')
        ))
        .then(function() {
            return JSZip.loadAsync(file);
        })
        .then(function(zip) {
            if (zip.files.length === 0) {
                return Promise.reject(new Error('Empty zip file'));
            } else if (zip.files)
            return Promise.all(
                Object.keys(zip.files)
                    .map(function(key) {
                        var file = zip.files[key];
                        if (file.dir) {
                            return null;
                        }
                        if (file.name.endsWith('.amk')) {
                            return file.async('text')
                                .then(Prescription.fromAMKString)
                                .then(function(prescription) {
                                    prescription.filename = file.name;
                                    return prescription;
                                });
                        } else if (file.name === 'favourites.json') {
                            return file.async('text')
                                    .then(function(str) {
                                        var regnrs = JSON.parse(str);
                                        otherPromiseFns.push(function() {
                                            return Promise.all(regnrs.map(Favourites.addRegNrs));
                                        });
                                    })
                                    .then(function() { return null; });
                        } else if (file.name === 'ft-favourites.json') {
                            return file.async('text')
                                .then(function(str) {
                                    var hashes = JSON.parse(str);
                                    otherPromiseFns.push(function() {
                                        return Promise.all(hashes.map(Favourites.addFullTextHash));
                                    });
                                })
                                .then(function() { return null; });
                        }
                        return null;
                    })
                    .filter(function(x){ return x !== null; })
            );
        });
    } else if (file.name.endsWith('.amk')) {
        amksPromise = file.text()
            .then(Prescription.fromAMKString)
            .then(function(prescription) {
                prescription.filename = file.name;
                return [prescription];
            });
    } else {
        amksPromise = Promise.reject(new Error('Unknown file type'));
    }
    return amksPromise.then(function(amks) {
        if (amks.length > 1 && !window.confirm(PrescriptionLocalization.prescription_confirm_clear)) {
            return [];
        }
        amks = amks.filter(a => a !== null);
        amks.sort(function(a, b) {
            return a.filename < b.filename ? -1 : 1;
        });
        window.amks = amks;
        var clear = amks.length > 1 ?
            Promise.all([
                Prescription.deleteAll(),
                Patient.deleteAll(),
                Favourites.removeAllRegNrs(),
                Favourites.removeAllFullTextHash(),
            ]) :
            Promise.resolve();
        return clear
            .then(function(){ return Prescription.importAMKObjects(amks); })
            .then(function() { return amks; });
    })
    .then(function(amks) {
        if (amks.length === 0) {
            return [];
        }
        var amkDoctor = amks[0].operator;
        if (amkDoctor.signature) {
            Doctor.setSignatureWithBase64(amkDoctor.signature);
        }
        var doctor = Doctor.fromAMKObject(amkDoctor);
        return Doctor.save(doctor)
            .then(function() { return amks; });
    })
    .then(function(amks) {
        if (amks.length) {
            alert(PrescriptionLocalization.prescription_imported.replace('%d', amks.length));
            Patient.setCurrentId(amks[0].patient_id);
        }
        UI.Prescription.reloadInfo();
    })
    .then(function() {
        return Promise.all(otherPromiseFns.map(function(fn){ return fn(); }));
    })
    .catch(function(e) {
        alert(e.toString());
    });
}

function generatePDFWithEPrescriptionPrompt() {
    var currentId = Prescription.getCurrentId();
    console.log('[PDF generation] currentId', currentId);
    return (currentId ? Prescription.readComplete(currentId) : Prescription.fromCurrentUIState())
        .then(function(prescription) {
            console.log('[PDF generation] got prescription', prescription);
            var authHandle = OAuth.ADSwiss.getAuthHandle();
            console.log('[PDF generation] auth handle', authHandle);
            if (authHandle) {
                console.log('[PDF generation] making QR Code1');
                return OAuth.ADSwiss.makeQRCodeWithPrescription(prescription)
                    .then(function(qrCode) {
                        return generatePDF(prescription, qrCode);
                    });
            }
            if (confirm(PrescriptionLocalization.sign_eprescription_confirm)) {
                if (OAuth.ADSwiss.isLoggedIn()) {
                    return OAuth.ADSwiss.fetchSAML().then(function(url) {
                        localStorage['ePrescription-flow-next'] = 'make-pdf';
                        document.location = url;
                    });
                } else {
                    localStorage['ePrescription-flow-next'] = 'login-saml';
                    OAuth.ADSwiss.login();
                }
            } else {
                generatePDF(prescription, null);
            }
        }).catch(function(e) {
            console.log(e);
            alert(e.message || e);
        });
}

function generatePDF(prescription, qrCodeImage) {
    if (!prescription) return Promise.reject('No prescription');
    console.log('[PDF generation] Loading JSPDF');
    var margin = 20;
    return (window.jspdf ? Promise.resolve() : Promise.resolve(
        $.getScript('/assets/javascripts/jspdf.umd.min.js')
    ))
    .then(function() {
        return qrCodeImage ? qrCodeImage : Doctor.getSignatureImage();
    })
    .then(function(signature) {
        console.log('[PDF generation] Got signature: ', signature);
        var doc = new jspdf.jsPDF();
        var fontSize = 11;
        doc.setFontSize(fontSize);

        var originY = 0;
        var didDrawnHeaderForCurrentPage = false;
        var pageNumber = 1;

        for (var medicationIndex = 0; medicationIndex < prescription.medications.length; medicationIndex++) {
            if (!didDrawnHeaderForCurrentPage) {
                drawPageNumber(doc, pageNumber);
                originY = drawPDFHeader(doc, originY, prescription, signature);
                didDrawnHeaderForCurrentPage = true;
            }

            originY = drawMedication(doc, originY, prescription.medications[medicationIndex]);

            var needNewPage = isNaN(originY);
            if (needNewPage) {
                doc.addPage();
                didDrawnHeaderForCurrentPage = false;
                originY = 0;
                pageNumber++;
            }
        }
        doc.save(prescription.filename + ".pdf");
    });

    function drawPageNumber(doc, num) {
        doc.text(
            PrescriptionLocalization.pdf_page_num.replace("%d", String(num)),
            doc.internal.pageSize.getWidth() - margin,
            50,
            {
                baseline: 'top',
                align: 'right'
            }
        );
    }

    function drawPDFHeader(doc, originY, prescription, signature) {
        var profile = Doctor.fromAMKObject(prescription.operator);
        var strDoctor = Doctor.stringForPrescriptionPrinting(profile);
        var doctorTextSize = measureText(doc, strDoctor);
        var docX = doc.internal.pageSize.getWidth() - margin;
        var docY = originY + 60;
        doc.text(strDoctor, docX, docY, {
            baseline: 'top',
            align: 'right'
        });

        var patient = Patient.fromAMKObject(prescription.patient);
        var patientStr = Patient.stringForPrescriptionPrinting(patient);
        var patientY = docY;
        doc.text(patientStr, margin, patientY, {baseline:"top"});

        var signatureY;
        var signatureHeight;
        if (signature) {
            var whRatio = 2; // In Amiko-iOS, the max signature size was 90x45;
            var signatureMaxWidth = 60, signatureMaxHeight = 30;
            var ratio = Math.min(signatureMaxWidth / signature.width, signatureMaxHeight / signature.height);
            var signatureWidth = signature.width * ratio;
            signatureHeight = signature.height * ratio;
            var signatureX = docX - (signatureMaxWidth - signatureWidth) / 2 - signatureWidth;
            signatureY = docY + doctorTextSize.h + (signatureMaxHeight - signatureHeight) / 2;

            doc.addImage(signature, "PNG", signatureX, signatureY, signatureWidth, signatureHeight);
        }

        var placeDateY = signature ? (signatureY + signatureHeight) : (docY + doctorTextSize.h);
        doc.text(prescription.place_date, margin, placeDateY, { baseline: 'top' });
        var placeDateSize = measureText(doc, prescription.place_date);
        return placeDateY + placeDateSize.h + 10;
    }

    function drawMedication(doc, originY, medication) {
        var maxWidth = doc.internal.pageSize.getWidth() - margin * 2;
        var packageSize = measureText(doc, medication.package, maxWidth);
        var eanSize = measureText(doc, medication.eancode, maxWidth);
        var commentSize = medication.comment ? measureText(doc, medication.comment, maxWidth) : {h:0, w:0};
        if (originY + packageSize.h + 5 + eanSize.h + 5 + (commentSize.h ? commentSize.h + 5 : 0) > doc.internal.pageSize.getHeight() - margin) {
            return NaN; // Need next page
        }
        doc.setTextColor("#0A60FE");
        doc.text(medication.package, margin, originY, {
            baseline: 'top',
            maxWidth: maxWidth
        });
        originY += packageSize.h + 2;

        doc.setTextColor("#666666");
        doc.text(medication.eancode, margin, originY, {
            baseline: 'top',
            maxWidth: maxWidth
        });
        originY += eanSize.h + 2;
        doc.setTextColor("#000000");
        if (medication.comment) {
            doc.text(medication.comment, margin, originY, {
                baseline: 'top',
                maxWidth: maxWidth
            });
            originY += commentSize.h + 2;
        }
        originY += 5;
        return originY;
    }

    function measureText(doc, str, width) {
        if (!width) width = 210;
        var fontSize = doc.getFontSize();
        var lineHeightFactor = doc.getLineHeightFactor();
        var lines = doc.splitTextToSize(str, width);
        var w = 0;
        lines.forEach((l)=> {
            var d = doc.getTextDimensions(l);
            w = Math.max(w, d.w);
        });
        var h = (fontSize * (lineHeightFactor * (lines.length - 1) + 1)) / doc.internal.scaleFactor;
        return {h: h, w : w};
    }
}

function sequencePromise(promiseFns) {
    var results = [];
    return promiseFns.reduce(function(prev, curr) {
        return function(){
            return prev().then(function() {
                return curr().then(function(val) {
                    results.push(val);
                });
            });
        };
    }, function() { return Promise.resolve(); })()
    .then(function() {
        return results;
    });
}

})();
