import { Prescription } from './amiko.prescriptions.js';

function main() {
    var statusElement = document.querySelector('.status');

    window.addEventListener('message', async (event) => {
        console.log('got event', event);
        if (event.origin !== acceptedParentOrigin) return;
        var data = event.data;
        if (data.type === 'INSERT_PRESCRIPTION') {
            if (!data.data) {
                statusElement.textContent = 'data is needed in message';
                window.parent.postMessage(
                    {type:'INSERT_PRESCRIPTION', status: 'error'},
                    acceptedParentOrigin
                );
                return;
            }
            data.data.filename = Prescription.generateAMKFileName();
            var importResult = await Prescription.importAMKObjects([data.data]);
            console.log('Imported prescription id', importResult)
            statusElement.textContent = 'Imported AMK: ' + data.data.filename;
            window.parent.postMessage(
                {type:'INSERT_PRESCRIPTION', status: 'success'},
                acceptedParentOrigin
            );
        }
      },
      false,
    );

    statusElement.textContent = 'Ready for message';
    console.log('Ready for message');
}

window.addEventListener('load', main);

declare var acceptedParentOrigin: string;
