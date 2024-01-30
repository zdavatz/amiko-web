function showDoctorModal() {
    var modal = document.querySelector('dialog.prescriptions-doctor');
    modal.showModal();
    readDoctor();
}

function closeDoctorModal() {
    var modal = document.querySelector('dialog.prescriptions-doctor');
    modal.close();
}

function showPatientsModal() {
    var modal = document.querySelector('dialog.prescriptions-address-book');
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
        var request = window.indexedDB.open("prescriptions", 1);
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
            var _doctorStore = db.createObjectStore("doctor");
            var _patientStore = db.createObjectStore("patients", { keyPath: "id", autoIncrement: true });
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
    });
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
    }).then(function (profile) {
        document.getElementsByName('doctor-field-title')[0].value = profile.title;
        document.getElementsByName('doctor-field-zsrnumber')[0].value = profile.zsrnumber;
        document.getElementsByName('doctor-field-gln')[0].value = profile.gln;
        document.getElementsByName('doctor-field-surname')[0].value = profile.surname;
        document.getElementsByName('doctor-field-name')[0].value = profile.name;
        document.getElementsByName('doctor-field-street')[0].value = profile.street;
        document.getElementsByName('doctor-field-city')[0].value = profile.city;
        document.getElementsByName('doctor-field-zip')[0].value = profile.zip;
        document.getElementsByName('doctor-field-phone')[0].value = profile.phone;
        document.getElementsByName('doctor-field-email')[0].value = profile.email;
        document.getElementsByName('doctor-field-iban')[0].value = profile.iban;
        document.getElementsByName('doctor-field-vat')[0].value = profile.vat;
    });
}

document.addEventListener('DOMContentLoaded', function() {
    document.getElementById('doctor-save').addEventListener('click', function() {
        saveDoctor();
        closeDoctorModal();
    });
});
