/*
 * Pin arrastrable para fijar dónde está un centro (fragments/location-picker.html).
 * La dirección se busca en Nominatim (OpenStreetMap) al pulsar el botón y, al revés, al tocar el mapa o mover
 * el pin se escribe la dirección de ese punto en el campo. Las coordenadas van a los campos ocultos latitude y longitude.
 */
document.addEventListener('DOMContentLoaded', () => {
    'use strict';
    const SPAIN = [40.4168, -3.7038];

    document.querySelectorAll('[data-location-picker]').forEach(root => {
        const status = root.querySelector('[data-location-status]');
        const latInput = root.querySelector('[data-location-lat]');
        const lonInput = root.querySelector('[data-location-lon]');
        const address = document.getElementById(root.dataset.addressInput);
        const searchBtn = root.querySelector('[data-location-search]');
        const hereBtn = root.querySelector('[data-location-here]');

        if (!window.L) {
            status.textContent = 'No se ha podido cargar el mapa. Revisa tu conexión y recarga la página.';
            searchBtn.disabled = true;
            hereBtn.disabled = true;
            return;
        }

        const hasPoint = latInput.value !== '' && lonInput.value !== '';
        const start = hasPoint ? [Number(latInput.value), Number(lonInput.value)] : SPAIN;
        // En pantallas táctiles un dedo mueve la página, no el mapa: se acerca con los botones + y -
        const map = L.map(root.querySelector('[data-location-map]'), {
            scrollWheelZoom: false,
            dragging: !L.Browser.mobile
        }).setView(start, hasPoint ? 17 : 6);
        L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
            maxZoom: 19,
            attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
        }).addTo(map);

        let marker = null;
        let lookup = 0;

        /** Dirección legible a partir de los datos de Nominatim: «Calle Mayor 12, 28013 Madrid». */
        function formatAddress(result) {
            const a = result.address || {};
            const street = a.road || a.pedestrian || a.footway || a.square || a.neighbourhood;
            const city = a.city || a.town || a.village || a.municipality;
            const town = [a.postcode, city].filter(Boolean).join(' ');
            if (!street) return { text: town, exact: false };
            const first = a.house_number ? `${street} ${a.house_number}` : street;
            return { text: town ? `${first}, ${town}` : first, exact: true };
        }

        /** Al poner o mover el pin, escribe la dirección de ese punto (solo vale la respuesta del último). */
        async function fillAddress(latlng) {
            if (!address) return;
            const id = ++lookup;
            status.textContent = 'Buscando la dirección de ese punto…';
            try {
                const url = 'https://nominatim.openstreetmap.org/reverse?format=jsonv2&zoom=18&addressdetails=1&accept-language=es'
                    + `&lat=${latlng.lat}&lon=${latlng.lng}`;
                const result = await fetch(url, { credentials: 'omit' }).then(r => r.ok ? r.json() : null);
                if (id !== lookup) return;
                const found = result && !result.error ? formatAddress(result) : { text: '' };
                if (!found.text) {
                    status.textContent = 'Pin colocado. No sabemos la dirección de ese punto: escríbela tú.';
                    return;
                }
                address.value = found.text;
                address.dispatchEvent(new Event('input', { bubbles: true }));
                status.textContent = found.exact
                    ? 'Dirección escrita a partir del pin. Revísala y añade el piso o el local si hace falta.'
                    : 'Solo sabemos la localidad: acerca el mapa y toca la puerta del centro, o añade tú la calle.';
            } catch (e) {
                if (id === lookup) status.textContent = 'Pin colocado. No hemos podido buscar la dirección: escríbela tú.';
            }
        }

        function save(latlng) {
            latInput.value = latlng.lat.toFixed(6);
            lonInput.value = latlng.lng.toFixed(6);
            latInput.dispatchEvent(new Event('change', { bubbles: true }));
        }

        function place(latlng, zoom) {
            if (!marker) {
                marker = L.marker(latlng, { draggable: true, title: 'Ubicación del centro' }).addTo(map);
                marker.on('dragend', () => {
                    save(marker.getLatLng());
                    fillAddress(marker.getLatLng());
                });
            } else {
                marker.setLatLng(latlng);
            }
            save(marker.getLatLng());
            if (zoom) map.setView(latlng, zoom);
            status.textContent = 'Pin colocado. Arrástralo si no está justo en la puerta del centro.';
        }

        if (hasPoint) place(start);
        // Un toque con el mapa muy alejado no es preciso: se acerca a ese punto para poder ajustar el pin
        map.on('click', e => {
            place(e.latlng, map.getZoom() < 15 ? 16 : null);
            fillAddress(e.latlng);
        });

        searchBtn.addEventListener('click', async () => {
            const query = (address?.value || '').trim();
            if (!query) {
                status.textContent = 'Escribe primero la dirección.';
                address?.focus();
                return;
            }
            searchBtn.disabled = true;
            status.textContent = 'Buscando la dirección…';
            try {
                const url = 'https://nominatim.openstreetmap.org/search?format=jsonv2&limit=1&accept-language=es&q='
                    + encodeURIComponent(query);
                const results = await fetch(url, { credentials: 'omit' }).then(r => r.ok ? r.json() : []);
                if (!results.length) {
                    status.textContent = 'No encontramos esa dirección. Acerca el mapa a tu calle y toca donde está el centro.';
                    return;
                }
                place([Number(results[0].lat), Number(results[0].lon)], 18);
            } catch (e) {
                status.textContent = 'No se ha podido buscar ahora. Acerca el mapa a tu calle y toca donde está el centro.';
            } finally {
                searchBtn.disabled = false;
            }
        });

        hereBtn.addEventListener('click', () => {
            if (!navigator.geolocation) {
                status.textContent = 'Este navegador no permite saber tu ubicación. Toca el mapa donde está el centro.';
                return;
            }
            status.textContent = 'Buscando tu ubicación…';
            navigator.geolocation.getCurrentPosition(
                pos => {
                    const here = L.latLng(pos.coords.latitude, pos.coords.longitude);
                    place(here, 18);
                    fillAddress(here);
                },
                () => { status.textContent = 'No se ha podido saber tu ubicación. Toca el mapa donde está el centro.'; },
                { enableHighAccuracy: true, timeout: 10000 });
        });

        // Un mapa que empieza oculto o cambia de ancho necesita recalcular su tamaño
        new ResizeObserver(() => map.invalidateSize()).observe(root);
    });
});
