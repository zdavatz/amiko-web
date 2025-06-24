import { Prescription } from './amiko.prescriptions.js';

async function main() {
    var statusElement = document.querySelector('.status');

    if ((window as any).prescriptions && typeof Array.isArray(prescriptions)) {
        var totalCount = prescriptions.length;
        for (var prescription of prescriptions) {
            var placeDateStr = prescription['place_date'];
            if (!placeDateStr) continue;
            var date = Prescription.placeDateToDate(placeDateStr);
            if (!date) continue;
            prescription.filename = Prescription.generateAMKFileName(date);
        }
        var importResult = await Prescription.importAMKObjects(prescriptions);
        console.log('Imported prescription id', importResult)
        statusElement.textContent = 'Imported AMK: ' + prescriptions.map(p => p.filename).join(', ');

        location.href = redirectDest;
    }
}

window.addEventListener('load', main);

declare var prescriptions: Prescription[];
declare var redirectDest: string;
