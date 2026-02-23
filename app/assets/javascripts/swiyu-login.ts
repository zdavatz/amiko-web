/**
 * swiyu-login.ts
 *
 * swiyu Wallet login widget for amiko-web.
 * Uses jQuery (already available via @types/jquery in amiko-web).
 *
 * Build: tsc --target ES5 swiyu-login.ts  →  swiyu-login.js
 * Place compiled JS: public/javascripts/swiyu-login.js
 */

// No "declare var $" – jQuery types already provided by @types/jquery

interface SwiyuClaims {
    firstName: string;
    lastName:  string;
    gln:       string;
}

// Use different name to avoid collision with DOM's ErrorCallback
type SwiyuSuccessCallback = (claims: SwiyuClaims) => void;
type SwiyuErrorCallback   = (error: string)       => void;

class SwiyuLogin {
    private container:      HTMLElement;
    private pollInterval:   number | null = null;
    private verificationId: string | null = null;
    private onSuccess:      SwiyuSuccessCallback;
    private onError:        SwiyuErrorCallback;

    constructor(
        containerSelector: string,
        onSuccess: SwiyuSuccessCallback,
        onError:   SwiyuErrorCallback = (e: string) => console.error(e)
    ) {
        const el = document.querySelector<HTMLElement>(containerSelector);
        if (!el) throw new Error('swiyu container not found: ' + containerSelector);
        this.container = el;
        this.onSuccess = onSuccess;
        this.onError   = onError;
    }

    start(): void {
        this.render('loading');
        $.ajax({
            url:      '/swiyu/login',
            method:   'GET',
            dataType: 'json',
            success:  (data: any) => {
                this.verificationId = data.verification_id;
                this.showQRCode(data.deeplink);
                this.startPolling();
            },
            error: (xhr: JQueryXHR) => {
                const msg = 'Verifier nicht erreichbar (' + xhr.status + ')';
                this.render('error', msg);
                this.onError(msg);
            }
        });
    }

    stop(): void {
        this.stopPolling();
    }

    private showQRCode(deeplink: string): void {
        this.render('qr', deeplink);
        const QRCode = (window as any).QRCode;
        if (QRCode) {
            const canvas = document.getElementById('swiyu-qr-canvas') as HTMLCanvasElement;
            if (canvas) {
                QRCode.toCanvas(canvas, deeplink, {
                    width: 256,
                    color: { dark: '#003d73', light: '#ffffff' }
                });
            }
        }
        const link = document.getElementById('swiyu-deeplink') as HTMLAnchorElement;
        if (link) link.href = deeplink;
    }

    private startPolling(): void {
        if (!this.verificationId) return;
        const id = this.verificationId;
        this.pollInterval = window.setInterval(() => {
            $.ajax({
                url:      '/swiyu/status/' + id,
                method:   'GET',
                dataType: 'json',
                success:  (data: any) => {
                    if (data.state === 'SUCCESS' && data.authenticated && data.claims) {
                        this.stopPolling();
                        this.render('success', data.claims);
                        this.onSuccess(data.claims);
                    } else if (data.state === 'FAILED' || data.state === 'EXPIRED') {
                        this.stopPolling();
                        this.render('error', 'Verifikation fehlgeschlagen oder abgelaufen.');
                        this.onError(data.state);
                    }
                },
                error: () => { /* network hiccup – keep polling */ }
            });
        }, 2000);
    }

    private stopPolling(): void {
        if (this.pollInterval !== null) {
            clearInterval(this.pollInterval);
            this.pollInterval = null;
        }
    }

    private render(state: string, data?: any): void {
        switch (state) {
            case 'loading':
                this.container.innerHTML =
                    '<div class="swiyu-login">' +
                    '<div class="swiyu-spinner"></div>' +
                    '<p>Verbindung wird hergestellt...</p>' +
                    '</div>';
                break;
            case 'qr':
                this.container.innerHTML =
                    '<div class="swiyu-login">' +
                    '<div class="swiyu-header"><h3>Mit swiyu Wallet anmelden</h3></div>' +
                    '<p class="swiyu-instructions">Scannen Sie den QR-Code mit Ihrer <strong>swiyu Wallet</strong> App und bestätigen Sie Ihren Arztausweis.</p>' +
                    '<div class="swiyu-qr-wrapper"><canvas id="swiyu-qr-canvas"></canvas></div>' +
                    '<a id="swiyu-deeplink" href="#" class="swiyu-deeplink-btn">Auf Mobilgerät öffnen</a>' +
                    '<div class="swiyu-polling"><span class="swiyu-dot"></span>Warte auf Bestätigung...</div>' +
                    '</div>';
                break;
            case 'success':
                const c: SwiyuClaims = data;
                this.container.innerHTML =
                    '<div class="swiyu-login swiyu-success">' +
                    '<div class="swiyu-checkmark">✓</div>' +
                    '<h3>Erfolgreich angemeldet</h3>' +
                    '<p><strong>Dr. ' + c.firstName + ' ' + c.lastName + '</strong></p>' +
                    '<p class="swiyu-gln">GLN: ' + c.gln + '</p>' +
                    '</div>';
                break;
            case 'error':
                this.container.innerHTML =
                    '<div class="swiyu-login swiyu-error">' +
                    '<p>⚠ ' + (data || 'Fehler bei der Anmeldung') + '</p>' +
                    '<button id="swiyu-retry">Erneut versuchen</button>' +
                    '</div>';
                const btn = document.getElementById('swiyu-retry');
                if (btn) btn.addEventListener('click', () => this.start());
                break;
        }
    }
}
