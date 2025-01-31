var EPrescription = {
    fromString: function(string) {
        var prefix = 'https://eprescription.hin.ch';
        if (string.startsWith(prefix)) {
            string = string.substring(prefix.length);
        }
        prefix = 'CHMED16A1';
        if (!string.startsWith(prefix)) {
            return Promise.reject(new Error('Invalid prefix'));
        }
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
        return ePrescriptionObj;
    },
    parseDateString: function(dateString) {
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
    toAMKPrescription: function(ePrescriptionObj) {
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
        var patientIds = ePrescriptionObj['Patient']['Ids'];
        var insuranceGln = patientIds.length && patientIds[0]['Type'] === 1 ? patientIds[0]['Val'] : ePrescriptionObj['Patient']['Rcv'];
        var patientWithoutId = {
            // "patient_id": To be filled below
            "given_name": ePrescriptionObj['Patient']['FName'] || "",
            "family_name": ePrescriptionObj['Patient']['LName'] || "",
            "birth_date": birthdateString,
            "gender": ePrescriptionObj['Patient']['Gender'] === 1 ? "man" : "woman",
            "email_address": ePrescriptionObj['Patient']['Email'] || "",
            "phone_number": ePrescriptionObj['Patient']['Phone'] || "",
            "postal_address": ePrescriptionObj['Patient']['Street'] || "",
            "city": ePrescriptionObj['Patient']['City'] || "",
            "zip_code": ePrescriptionObj['Patient']['Zip'] || "",
            "insurance_gln": insuranceGln || ""
        };
        return Patient.generateAMKPatientId(patientWithoutId).then(function (patientId) {
            var patient = Object.assign({}, patientWithoutId, { patient_id: patientId });
            return {
                "prescription_hash": crypto.randomUUID(),
                "place_date": placeDate,
                "operator": {
                    "gln": ePrescriptionObj['Auth'] || "",
                    "zsr_number": ePrescriptionObj['Zsr'] || "",
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
