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
    })
    .then(reloadPrescriptionInfo);
}

function listPatients() {
    return getPrescriptionDatabase().then(function(db) {
        return new Promise(function(resolve, reject) {
            var objectStore = db.transaction("patients").objectStore("patients");
            var cursor = objectStore.openCursor();
            var results = [];
            cursor.onsuccess = function(event) {
              var cursor = event.target.result;
              if (cursor) {
                cursor.continue();
                results.push(cursor.value);
              } else {
                console.log("No more entries!");
                resolve(results);
              }
            };
            cursor.onerror = reject;
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
    // prescription object with
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
              res(getAllRequest.result);
            };
            getAllRequest.onerror = rej;
        });
    });
}

function getFullSavedPrescription(prescriptionId) {
    return getPrescriptionDatabase().then(function (db) {
        return new Promise(function(resolve, reject) {
            var req = db.transaction("prescriptions")
                .objectStore("prescriptions")
                .get(prescriptionId);
            req.onsuccess = function(event) {
                var p = event.target.result;
                var doctorSignData = localStorage.doctorSignImage;
                if (doctorSignData) {
                    var index = doctorSignData.indexOf(',');
                    doctorSignData = doctorSignData.slice(index + 1);
                    p.operator.signature = doctorSignData;
                }
                resolve(p);
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
                            const url = prescriptionToAMK(obj);
                            var element = window.document.createElement('a');
                            element.href = url;
                            element.download = prescription.filename;
                            element.style.display = 'none';
                            document.body.appendChild(element);
                            element.click();
                            document.body.removeChild(element);
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
            $('#prescriptions-save-no-patient')[0].showModal();
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
            operator: {
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
            },
            patient: {
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
            },
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
    var encoder = new TextEncoder();
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
    var url = URL.createObjectURL(blob);
    return url;
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
