var Favourites = {
    getDB: function() {
        return new Promise(function(resolve, reject) {
            var request = window.indexedDB.open("favourites", 1);
            request.onerror = function(event) {
                console.error('Cannot open database', request.errorCode);
                reject(event);
            };
            request.onsuccess = function(event) {
                var db = request.result;
                Favourites.getDB = function() { return Promise.resolve(db); };
                resolve(db);
            };
            request.onupgradeneeded = function(event) {
                var db = event.target.result;
                if (event.oldVersion <= 0) {
                    db.createObjectStore("favourites", { keyPath: "regnrs", autoIncrement: false });
                    db.createObjectStore("ft_favourites", { keyPath: "hashId", autoIncrement: false }); // full text
                }
            };
        });
    },
    getRegNrs: function() {
        return Favourites.getDB().then(function(db) {
            return new Promise(function(resolve, reject) {
                var objectStore = db.transaction("favourites").objectStore("favourites");
                var request = objectStore.getAll();
                request.onsuccess = function(event) {
                    var r = event.target.result.map(function(obj) {
                        return obj.regnrs;
                    });
                    resolve(r);
                };
                request.onerror = reject;
            });
        });
    },
    addRegNrs: function(regnrs) {
        return Favourites.getDB().then(function(db) {
            return new Promise(function(resolve, reject) {
                var req = db.transaction("favourites", "readwrite")
                    .objectStore("favourites")
                    .put({ regnrs: regnrs });
                req.onsuccess = function(res) {
                    Favourites.reloadCached();
                    resolve(res);
                };
                req.onerror = reject;
            });
        });
    },
    removeRegNrs: function(regnrs) {
        return Favourites.getDB().then(function(db) {
            return new Promise(function(resolve, reject) {
                var req = db
                    .transaction("favourites", "readwrite")
                    .objectStore("favourites")
                    .delete(regnrs);
                req.onsuccess = function(res) {
                    Favourites.reloadCached();
                    resolve(res);
                };
                req.onerror = reject;
            });
        });
    },
    removeAllRegNrs: function() {
        return Favourites.getDB().then(function(db) {
            return new Promise(function(resolve, reject) {
                var req = db
                    .transaction("favourites", "readwrite")
                    .objectStore("favourites")
                    .clear();
                req.onsuccess = resolve;
                req.onerror = reject;
            });
        });
    },
    getFullTextHashes: function() {
        return Favourites.getDB().then(function(db) {
            return new Promise(function(resolve, reject) {
                var objectStore = db.transaction("ft_favourites").objectStore("ft_favourites");
                request = objectStore.getAll();
                request.onsuccess = function(event) {
                    var r = event.target.result.map(function(obj) {
                        return obj.hashId;
                    });
                    resolve(r);
                };
                request.onerror = reject;
            });
        });
    },
    addFullTextHash: function(hashId) {
        return Favourites.getDB().then(function(db) {
            return new Promise(function(resolve, reject) {
                var req = db.transaction("ft_favourites", "readwrite")
                    .objectStore("ft_favourites")
                    .put({ hashId: hashId });
                req.onsuccess = function(res) {
                    Favourites.reloadCached();
                    resolve(res);
                };
                req.onerror = reject;
            });
        });
    },
    removeFullTextHash: function(hashId) {
        return Favourites.getDB().then(function(db) {
            return new Promise(function(resolve, reject) {
                var req = db
                    .transaction("ft_favourites", "readwrite")
                    .objectStore("ft_favourites")
                    .delete(hashId);
                req.onsuccess = function(res) {
                    Favourites.reloadCached();
                    resolve(res);
                };
                req.onerror = reject;
            });
        });
    },
    removeAllFullTextHash: function() {
        return Favourites.getDB().then(function(db) {
            return new Promise(function(resolve, reject) {
                var req = db
                    .transaction("ft_favourites", "readwrite")
                    .objectStore("ft_favourites")
                    .clear();
                req.onsuccess = resolve;
                req.onerror = reject;
            });
        });
    },
    reloadCached: function() {
        Favourites.getRegNrs().then(function(favs) {
            Favourites.cached = new Set(favs);
        });
        Favourites.getFullTextHashes().then(function(favs){
            Favourites.cachedFullText = new Set(favs);
        });
    },
    cached: new Set(), // cachedFavourites
    cachedFullText: new Set(),
};

Favourites.reloadCached();
