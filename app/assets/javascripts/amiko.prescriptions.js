(function() {
var Doctor = {
    toAMKObject: function(profile, doctorSignData) {
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
            "signature": doctorSignData,
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
                    setCurrentPatientId(patientId);
                    resolve(patientId);
                };
                req.onerror = reject;
            });
        });
    },
    deleteAll: function() {
        return getPrescriptionDatabase().then(function(db) {
            return db.transaction("patients", "readwrite")
                .objectStore("patients")
                .clear();
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
    }
};

var Prescription = {
    toAMKObject: function() {},
    fromAMKObject: function() {},
    fromCurrentUIState: function() {},
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
    deleteAll: function() {
        return getPrescriptionDatabase().then(function(db) {
            return db.transaction("prescriptions", "readwrite")
                .objectStore("prescriptions")
                .clear();
        });
    },
    // Import prescriptions and _new_ patients
    importAMKObjects: function(amkObjs) {
        return Patient.importFromAMKPrescriptions(amkObjs).then(function(oldPatientIdToNewPatientId) {
            // TODO, sort by place date?
            return Promise.all(amkObjs.map(function(amkObj) {
                amkObj.patient_id = amkObj.patient.patient_id = oldPatientIdToNewPatientId[amkObj.patient.patient_id];
                return Prescription.save(amkObj);
            }));
        });
    }
};

function reloadPrescriptionInfo() {
    readDoctor().then(function(profile) {
        var div = document.getElementsByClassName('prescription-doctor-info')[0];
        if (!profile) {
            div.innerText = '';
        } else {
            div.innerText = profile.title + ' ' + profile.name + ' ' + profile.surname;
        }
    });
    var patientInfo = document.getElementsByClassName('prescription-patent-info')[0];
    var patientId = getCurrentPatientId();
    if (patientId === null) {
        patientInfo.innerText = '';
    } else {
        readPatient(patientId).then(function(patient) {
            if (patient) {
                patientInfo.innerText = patient.name + ' ' + patient.surname;
            } else {
                // Cannot find patient, remove id
                setCurrentPatientId(null);
            }
        });
    }
    displayPrescriptionItems();
    displaySavedPrescriptions();
}

function showDoctorModal() {
    var modal = document.querySelector('dialog.prescriptions-doctor');
    modal.showModal();
    readAndFillDoctorModal();
}

function closeDoctorModal() {
    var modal = document.querySelector('dialog.prescriptions-doctor');
    modal.close();
}

function showPatientsModal() {
    var modal = document.querySelector('dialog.prescriptions-address-book');
    listPatients();
    modal.showModal();
}

function closePatientsModal() {
    var modal = document.querySelector('dialog.prescriptions-address-book');
    modal.close();
}

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

function saveDoctor() {
    var profile = {
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
    return getPrescriptionDatabase().then(function (db) {
        return new Promise(function(resolve, reject) {
            var req = db.transaction("doctor", "readwrite")
                .objectStore("doctor")
                .put(profile, "doctor-profile");
            req.onsuccess = resolve;
            req.onerror = reject;
        });
    })
    .then(reloadPrescriptionInfo);
}

function readDoctor() {
    return getPrescriptionDatabase().then(function (db) {
        return new Promise(function(resolve, reject) {
            var req = db.transaction("doctor")
                .objectStore("doctor")
                .get("doctor-profile");
            req.onsuccess = function(event) {
                resolve(event.target.result);
            };
            req.onerror = reject;
        });
    });
}

function readAndFillDoctorModal() {
    return readDoctor().then(function (profile) {
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
        document.getElementById('doctor-sign-image').src = localStorage.doctorSignImage || '';
    });
}

function getCurrentPatientId() {
    try {
        var id = localStorage.currentPatientId;
        return parseInt(id) || null;
    } catch (_e) {
        return null;
    }
}
function setCurrentPatientId(id) {
    if (id === null) {
        localStorage.removeItem('currentPatientId');
    } else {
        localStorage.currentPatientId = id;
    }
}
function savePatient() {
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
    var patientId = getCurrentPatientId();
    if (patientId !== null) {
        patient.id = patientId;
    }
    return Patient.upsert(patient).then(reloadPrescriptionInfo);
}

function listPatients() {
    return getPrescriptionDatabase().then(function(db) {
        return new Promise(function(resolve, reject) {
            var objectStore = db.transaction("patients").objectStore("patients");
            var request = objectStore.getAll();
            request.onsuccess = function(event) {
                resolve(event.target.result);
            };
            request.onerror = reject;
        });
    }).then(function(patients) {
        var container = document.getElementsByClassName('prescriptions-address-book-patients')[0];
        container.innerHTML = '';
        patients.forEach(function (patient) {
            var id = patient.id;
            var div = document.createElement('div');
            div.className = 'prescriptions-address-book-patient ' + (id === getCurrentPatientId() ? '--selected' : '');
            div.innerText = patient.name + ' ' + patient.surname;
            div.onclick = function () {
                readAndFillPatientModal(id);
            };
            var deleteButton = document.createElement('div');
            deleteButton.className = 'prescriptions-address-book-patient-delete';
            div.appendChild(deleteButton);
            deleteButton.onclick = function(e) {
                e.stopPropagation();
                deletePatient(id);
            };
            container.appendChild(div);
        });
    });
}

function readPatient(id) {
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
}

function readAndFillPatientModal(id) {
    return readPatient(id)
    .then(function (patient) {
        var birthdayString = patient.birthday || '';
        var birthdayParts = (patient.birthday || '').split('.');
        if (birthdayParts.length === 3) {
            var year = birthdayParts[2];
            var month = birthdayParts[1];
            var date = birthdayParts[0];
            birthdayString = year + '-' + month + '-' + date;
        }
        setCurrentPatientId(patient.id);
        setCurrentPrescriptionId(null);
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
        return listPatients();
    })
    .then(function () {
        reloadPrescriptionInfo();
    });
}

function newPatient() {
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
    setCurrentPatientId(null);
    reloadPrescriptionInfo();
}

function deletePatient(id) {
    return getPrescriptionDatabase().then(function(db) {
        return new Promise(function(resolve, reject) {
            var req = db
                .transaction("patients", "readwrite")
                .objectStore("patients")
                .delete(id);
            req.onsuccess = resolve;
            req.onerror = reject;
        });
    })
    .then(listPatients)
    .then(newPatient);
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
            localStorage.doctorSignImage = url;
            console.log('sign url', url);
            var signDisplay = document.getElementById('doctor-sign-image');
            signDisplay.src = url;
        };
        image.src = dataURL;
    };
    reader.readAsDataURL(file);
}

function savePrescription(prescriptionObj, optionalPrescriptionId) {
    // The saved object is
    // amk prescription object with
    // + patient_id: number <- refers to a patient in the patient store
    // + filename: string
    // + (automatically generated) id: number
    // - operator.signature <- to save data size

    // if optionalPrescriptionId is present, it updates existing prescription
    var now = new Date();
    var currentDateStr = '' +
        now.getFullYear() +
        ('0' + (now.getMonth() + 1)).slice(-2) +
        ('0' + now.getDate()).slice(-2) +
        ('0' + now.getHours()).slice(-2) +
        ('0' + now.getMinutes()).slice(-2) +
        ('0' + now.getSeconds()).slice(-2);

    // yyyy-MM-dd'T'HH:mm.ss
    var filenamePromise = optionalPrescriptionId ? getFullSavedPrescription(optionalPrescriptionId).then(p => p.filename) : Promise.resolve(null);

    return filenamePromise.then(function(filename) {
        var prescription = Object.assign({}, prescriptionObj, {
            patient_id: Number(prescriptionObj.patient.patient_id),
            filename: filename || "RZ_"+currentDateStr+".amk",
            operator: Object.assign({}, prescriptionObj.operator, {signature: null}),
        },
        optionalPrescriptionId ? {id: optionalPrescriptionId} : {});
        return getPrescriptionDatabase().then(function (db) {
            return new Promise(function(resolve, reject) {
                var req = db.transaction("prescriptions", "readwrite")
                    .objectStore("prescriptions")
                    .put(prescription);
                req.onsuccess = function(e) {
                    var prescriptionId = e.target.result;
                    setCurrentPrescriptionId(prescriptionId);
                    resolve(prescriptionId);
                };
                req.onerror = reject;
            });
        });
    });
}

function listSimplifiedPrescriptions(patientId) {
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
}

function makeFullPrescription(simplifiedPrescription) {
    // We do not save the doctor's signature in the DB to save space,
    // this function add the signature back to a simplified prescription
    var doctorSignData = localStorage.doctorSignImage;
    if (doctorSignData) {
        var index = doctorSignData.indexOf(',');
        doctorSignData = doctorSignData.slice(index + 1);
        simplifiedPrescription.operator.signature = doctorSignData;
    }
    return simplifiedPrescription;
}

function getFullSavedPrescription(prescriptionId) {
    return getPrescriptionDatabase().then(function (db) {
        return new Promise(function(resolve, reject) {
            var req = db.transaction("prescriptions")
                .objectStore("prescriptions")
                .get(prescriptionId);
            req.onsuccess = function(event) {
                var p = event.target.result;
                var full = makeFullPrescription(p);
                resolve(full);
            };
            req.onerror = reject;
        });
    });
}

function deletePrescription(prescriptionId) {
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
        if (prescriptionId === getCurrentPrescriptionId()) {
            setCurrentPrescriptionId(null);
        }
    });
}

function getCurrentPrescriptionId() {
    if (!localStorage.currentPrescriptionId) return null;
    return Number(localStorage.currentPrescriptionId);
}

function setCurrentPrescriptionId(prescriptionId) {
    if (!prescriptionId) {
        localStorage.removeItem('currentPrescriptionId');
    } else {
        localStorage.currentPrescriptionId = prescriptionId;
    }
}

function displaySavedPrescriptions() {
    var list = $('#prescriptions-right-list');
    list.html('');
    return listSimplifiedPrescriptions(getCurrentPatientId()).then(function(prescriptions) {
        prescriptions.forEach(function(prescription) {
            list.append(
                $('<div>')
                .text(prescription.filename)
                .addClass('prescriptions-right-list-item')
                .on('click', function() {
                    localStorage.prescriptionBasket = '[]';
                    prescription.medications.forEach(function(m) {
                        addToPrescriptionBasket({
                            title: m.title,
                            author: m.owner,
                            regnrs: m.regnrs,
                            atccode: m.atccode,
                            package: m.package,
                            eancode: m.eancode,
                            note: m.comment || '',
                        });
                    });
                    displayPrescriptionItems();
                    setCurrentPatientId(prescription.patient_id);
                    setCurrentPrescriptionId(prescription.id);
                    var patientInfo = document.getElementsByClassName('prescription-patent-info')[0];
                    patientInfo.innerText = prescription.patient.given_name + ' ' + prescription.patient.family_name;
                })
                .append(
                    $('<button>').addClass('download-button').on('click', function (e) {
                        e.stopPropagation();
                        getFullSavedPrescription(prescription.id).then(function(obj) {
                            var blob = prescriptionToAMK(obj);
                            downloadBlob(blob, prescription.filename);
                        });
                    })
                )
                .append(
                    $('<button>').addClass('delete-button').on('click', function (e) {
                        e.stopPropagation();
                        deletePrescription(prescription.id).then(displaySavedPrescriptions);
                    })
                )
            );
        });
    });
}

document.addEventListener('DOMContentLoaded', function() {
    if (!document.URL.endsWith('/prescriptions')) {
        return;
    }
    document.getElementById('prescription-choose-patient-button').addEventListener('click', function() {
        showPatientsModal();
    });
    document.getElementById('prescription-edit-doctor-button').addEventListener('click', function() {
        showDoctorModal();
    });
    document.getElementById('doctor-save').addEventListener('click', function() {
        saveDoctor();
        closeDoctorModal();
    });
    document.getElementById('doctor-sign-input').addEventListener('change', function(e) {
        console.log(e);
        if (!e.target.files.length) return;
        didPickDoctorSignatureImage(e.target.files[0]);
    });
    document.getElementById('patient-save').addEventListener('click', function() {
        savePatient();
        listPatients();
    });
    document.getElementById('patient-create').addEventListener('click', function() {
        newPatient();
    });
    document.getElementById('prescription-save').addEventListener('click', function() {
        if (getCurrentPatientId() === null) {
            alert(PrescriptionLocalization.prescription_please_choose_patient);
            return;
        }
        if (getCurrentPrescriptionId()) {
            // Ask if save new prescription
            var modal = document.getElementById('prescriptions-save-confirm');
            modal.showModal();
        } else {
            // Just save new prescription
            encodeCurrentPrescriptionToJSON()
                .then(savePrescription)
                .then(displaySavedPrescriptions);
        }
    });
    document.getElementById('prescription-save-new').addEventListener('click', function() {
        encodeCurrentPrescriptionToJSON()
            .then(savePrescription)
            .then(displaySavedPrescriptions);
        var modal = document.getElementById('prescriptions-save-confirm');
        modal.close();
    });
    document.getElementById('prescription-save-overwrite').addEventListener('click', function() {
        encodeCurrentPrescriptionToJSON()
            .then(function(obj) {
                return savePrescription(obj, getCurrentPrescriptionId());
            })
            .then(displaySavedPrescriptions);
        var modal = document.getElementById('prescriptions-save-confirm');
        modal.close();
    });
    document.getElementById('prescription-create').addEventListener('click', function() {
        localStorage.prescriptionBasket = '[]';
        setCurrentPrescriptionId(null);
        displayPrescriptionItems();
    });
    document.getElementById('prescription-export-all').addEventListener('click', function() {
        exportEverything();
    });
    document.getElementById('prescription-import-all').addEventListener('change', function(e) {
        console.log(e);
        if (!e.currentTarget.files.length) return;
        importFromZip(e.currentTarget.files[0]);
    });
    reloadPrescriptionInfo();
});

function addToPrescriptionBasket(data) {
// title: String
// eancode: String
    var basket = listPrescriptionBasket();
    data.note = '';
    basket.push(data);
    savePrescriptionBasket(basket);
}

function savePrescriptionBasket(basket) {
    localStorage.prescriptionBasket = JSON.stringify(basket);
    displayPrescriptionItems();
}

function deleteFromPrescriptionBasket(index) {
    var basket = listPrescriptionBasket();
    basket.splice(index, 1);
    localStorage.prescriptionBasket = JSON.stringify(basket);
    displayPrescriptionItems();
}

function listPrescriptionBasket() {
    return JSON.parse(localStorage.prescriptionBasket || "[]");
}

function encodeCurrentPrescriptionToJSON() {
    return Promise.all([readDoctor(), readPatient(getCurrentPatientId())]).then(function(result) {
        var profile = result[0];
        var patient = result[1];
        var doctorSignData = localStorage.doctorSignImage;
        if (doctorSignData) {
            var index = doctorSignData.indexOf(',');
            doctorSignData = doctorSignData.slice(index + 1);
        }
        var now = new Date();

        return {
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
            operator: Doctor.toAMKObject(profile, doctorSignData),
            patient: Patient.toAMKObject(patient),
            medications: listPrescriptionBasket().map(item => {
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
                    comment: item.note || '',
                };
            })
        };
    });
}

function prescriptionToAMK(obj) {
    obj = Object.assign({}, obj); // Shallow clone so we can
    // Remove the extra fields, see savePrescription
    delete obj.patient_id;
    delete obj.id;
    delete obj.filename;
    var json = JSON.stringify(obj);
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
}

function amkToPrescription(amkStr) {
    var utf8 = atob(amkStr);
    var charCodes = [];
    for (var i = 0; i < utf8.length; i++) {
        charCodes.push(utf8.charCodeAt(i));
    }
    var decoder = new TextDecoder('utf-8');
    var utf16 = decoder.decode(new Uint8Array(charCodes));
    return JSON.parse(utf16);
}

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

function displayPrescriptionItems() {
    $('.prescription-items').empty();
    listPrescriptionBasket().forEach(function (item, i) {
        $('.prescription-items').append(
            $('<div>')
                .addClass('prescription-item')
                .append(
                    $('<div>')
                        .addClass('prescription-item-actions')
                        .append(
                            $('<button>').addClass('delete-button').on('click', function (e) {
                                deleteFromPrescriptionBasket(i);
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
                        .addClass('prescription-item-note')
                        .data('prescription-item-index', i)
                        .attr('value', item.note || '')
                )
        );
    });
}

$(document).on('change', 'input.prescription-item-note', function(e) {
    var index = $(e.target).data('prescription-item-index');
    var items = listPrescriptionBasket();
    items[index].note = e.target.value;
    savePrescriptionBasket(items);
});

$(document).on('click', 'p.article-packinfo', function(e) {
    var data = $(e.currentTarget).data('prescription');
    addToPrescriptionBasket(data);
    if (!document.URL.endsWith('/prescriptions')) {
        $('button.state-button.--prescription').addClass('shake');
    }
});

function exportEverything() {
    (window.JSZip ? Promise.resolve() : Promise.resolve(
        $.getScript('/assets/javascripts/jszip.min.js')
    ))
    .then(getPrescriptionDatabase)
    .then(function(db) {
        return new Promise(function (res, rej) {
            var store = db.transaction("prescriptions").objectStore("prescriptions");
            var getAllRequest = store.getAll();
            getAllRequest.onsuccess = function() {
              res(getAllRequest.result);
            };
            getAllRequest.onerror = rej;
        });
    })
    .then(function(simplifiedPrescriptions) {
        var zip = new JSZip();
        simplifiedPrescriptions.forEach(function(prescription) {
            var obj = makeFullPrescription(prescription);
            var blob = prescriptionToAMK(obj);
            zip.file(prescription.filename, blob);
        });
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

function sequencePromise(promises) {
    var results = [];
    return promises.reduce(function(prev, curr) {
        return prev.then(function() {
            return curr.then(function(val) {
                results.push(val);
            });
        });
    }, Promise.resolve())
    .then(function() {
        return results;
    });
}

function importFromZip(file) {
    var amksPromise;
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
                        if (!file.name.endsWith('.amk') || file.dir) {
                            return null;
                        }
                        return file.async('text')
                            .then(amkToPrescription)
                            .then(function(prescription) {
                                prescription.filename = file.name;
                                return prescription;
                            });
                    })
                    .filter(function(x){ return x !== null; })
            );
        });
    } else if (file.name.endsWith('.amk')) {
        amksPromise = file.text()
            .then(amkToPrescription)
            .then(function(prescription) {
                prescription.filename = file.name;
                return [prescription];
            });
    } else {
        amksPromise = Promise.reject(new Error('Unknown file type'));
    }
    return amksPromise.then(function(amks) {
        window.amks = amks;
        // TODO: confirm delete all
        var clear = amks.length > 1 ?
            Promise.all([
                Prescription.deleteAll(),
                Patient.deleteAll()
            ]) :
            Promise.resolve();
        return clear
            .then(function(){ return Prescription.importAMKObjects(amks); })
            .then(function() { return amks; });
    })
    .then(function(amks) {
        var amkDoctor = amks[0].operator;
        if (amkDoctor.signature) {
            localStorage.doctorSignImage = 'data:image/png;base64,' + amkDoctor.signature;
        }
        var doctor = Doctor.fromAMKObject(amkDoctor);
        return Doctor.save(doctor);
    })
    // TODO: alert "imported n files"
    .catch(function(e) {
        alert(e.toString());
    });
}

})();
