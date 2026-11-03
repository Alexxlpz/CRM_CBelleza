/*
 * Alta de centro (/register-center): validación de los campos y filas de trabajadores que se añaden y quitan.
 * El servidor vuelve a validarlo todo.
 */
document.addEventListener('DOMContentLoaded', () => {
    'use strict';
    const form = document.getElementById('registerCenterForm');
    if (!form) return;

    const V = Cuquora.validators;
    const submitBtn = document.getElementById('submitCertBtn');
    const timeZone = form.elements.timeZone;
    // Igual que en el servidor: un centro en España necesita CIF, NIF o NIE español con su letra o dígito de control
    const inSpain = () => !timeZone || ['Europe/Madrid', 'Atlantic/Canary'].includes(timeZone.value);
    const validator = Cuquora.validateForm(form, {
        centerName: [V.required('Escribe el nombre comercial del centro.'),
            V.minLength(2, 'El nombre del centro tiene que tener al menos 2 caracteres.')],
        cif: [V.required('Indica el CIF o NIF de la empresa o autónomo.'), value => taxIdProblem(value, inSpain())],
        phone: [V.required('Escribe un teléfono de contacto del centro.'), V.phoneNumber()],
        cabins: [V.required('Indica cuántas cabinas o puestos tenéis.'), (value, el) => {
            const n = Number(value);
            return Number.isInteger(n) && n >= Number(el.min || 1) && n <= Number(el.max || 50) ? ''
                : `Las cabinas o puestos tienen que ser un número entre ${el.min || 1} y ${el.max || 50}.`;
        }],
        address: [V.required('Escribe la dirección del centro.'), value => inSpain() ? postcodeProblem(value) : ''],
        latitude: [V.required('Marca en el mapa dónde está el centro.')],
        contactName: [V.required('Escribe tu nombre.'), V.minLength(2, 'Tu nombre tiene que tener al menos 2 caracteres.')],
        email: [V.required('Escribe tu correo.'), V.email()],
        terms: [V.required('Confirma la actividad legal del centro y acepta los términos para continuar.')]
    }, {
        submitButton: submitBtn,
        onSubmit: () => {
            submitBtn.classList.add('is-loading');
            submitBtn.setAttribute('aria-busy', 'true');
            submitBtn.querySelector('span').textContent = 'Registrando el centro…';
        }
    });

    // El CIF/NIF no deja pasar de 9 letras y números en España (20 fuera); los puntos, guiones y espacios no cuentan
    const cifInput = form.elements.cif;
    const cifMax = () => inSpain() ? 9 : 20;
    const cifLength = value => value.replace(/[\s.\-/]/g, '').length;
    cifInput.addEventListener('beforeinput', e => {
        if (!e.inputType.startsWith('insert') || e.inputType === 'insertFromPaste') return;
        const next = cifInput.value.slice(0, cifInput.selectionStart) + (e.data || '') + cifInput.value.slice(cifInput.selectionEnd);
        if (cifLength(next) > cifMax()) e.preventDefault();
    });
    cifInput.addEventListener('input', () => {
        // Lo pegado se recorta
        if (cifLength(cifInput.value) <= cifMax()) return;
        let keep = cifMax();
        cifInput.value = [...cifInput.value].filter(c => /[\s.\-/]/.test(c) || keep-- > 0).join('');
    });

    // Al cambiar de país (zona horaria) cambian las reglas del CIF y del código postal
    timeZone?.addEventListener('change', () => {
        ['cif', 'address'].forEach(name => form.elements[name].dispatchEvent(new Event('input', { bubbles: true })));
    });

    // ---- Más trabajadores ----
    const rows = document.getElementById('teamRows');
    const template = document.getElementById('workerRowTemplate');
    const addBtn = document.getElementById('addWorkerBtn');
    const limitHelp = document.getElementById('teamLimitHelp');
    const max = Number(form.dataset.maxWorkers) || 20;
    let nextId = rows.children.length;

    function refreshLimit() {
        const full = rows.children.length >= max;
        addBtn.hidden = full;
        limitHelp.hidden = !full;
    }

    addBtn.addEventListener('click', () => {
        const row = template.content.firstElementChild.cloneNode(true);
        row.querySelectorAll('input').forEach(input => {
            const id = `${input.name === 'workerNames' ? 'workerName' : 'workerEmail'}${nextId}`;
            input.id = id;
            input.previousElementSibling.htmlFor = id;
        });
        nextId++;
        rows.appendChild(row);
        row.querySelector('input').focus();
        refreshLimit();
    });

    rows.addEventListener('click', e => {
        const remove = e.target.closest('[data-remove-worker]');
        if (!remove) return;
        const row = remove.closest('.team-row');
        // La última fila se vacía en lugar de desaparecer, para no perder el sitio donde escribir
        if (rows.children.length === 1) {
            row.querySelectorAll('input').forEach(input => { input.value = ''; });
            row.querySelector('input').focus();
        } else {
            (row.nextElementSibling || row.previousElementSibling).querySelector('input').focus();
            row.remove();
        }
        refreshLimit();
    });

    refreshLimit();

    // ---- Asistente: un bloque cada vez ----
    const steps = [...form.querySelectorAll(':scope > fieldset.registration-section')];
    const stepper = document.getElementById('wizardSteps');
    const stepButtons = [...stepper.querySelectorAll('[data-go-step]')];
    const backBtn = document.getElementById('wizardBack');
    const nextBtn = document.getElementById('wizardNext');
    let current = 0;
    let reached = 0;

    // En móvil la vista previa no cabe al lado: antes de registrar se enseña como paso de confirmación
    const preview = document.getElementById('registrationPreview');
    const previewHome = preview.parentElement;
    const confirmIntro = preview.querySelector('.registration-confirm-intro');
    const wizardNav = form.querySelector('.wizard-nav');
    const submitLabel = submitBtn.querySelector('span');
    const submitText = submitLabel.textContent;
    const submitIcon = submitBtn.querySelector('i');
    const narrow = window.matchMedia('(max-width: 1099px)');
    let confirming = false;

    // En móvil el botón del último paso no registra todavía: lleva a la vista previa
    function refreshSubmit() {
        const toPreview = narrow.matches && !confirming;
        submitLabel.textContent = confirming ? 'Sí, registrar' : toPreview ? 'Ver vista previa' : submitText;
        submitIcon.className = toPreview ? 'lucide-eye' : 'lucide-send';
    }

    function showConfirm() {
        // Se confirma desde el último paso (también si se envía con Intro en otro): «Atrás» vuelve a él
        showStep(steps.length - 1);
        confirming = true;
        steps.forEach(step => { step.hidden = true; });
        form.insertBefore(preview, wizardNav);
        preview.classList.add('is-confirming');
        confirmIntro.hidden = false;
        backBtn.hidden = false;
        refreshSubmit();
        stepper.scrollIntoView({ behavior: Cuquora.scrollBehavior(), block: 'nearest' });
        confirmIntro.querySelector('h2').focus({ preventScroll: true });
    }

    function leaveConfirm() {
        confirming = false;
        previewHome.appendChild(preview);
        preview.classList.remove('is-confirming');
        confirmIntro.hidden = true;
        refreshSubmit();
    }

    function showStep(index) {
        if (confirming) leaveConfirm();
        current = index;
        reached = Math.max(reached, index);
        steps.forEach((step, i) => { step.hidden = i !== index; });
        stepButtons.forEach((btn, i) => {
            btn.classList.toggle('is-current', i === index);
            btn.classList.toggle('is-done', i < reached && i !== index);
            btn.disabled = i > reached;
            if (i === index) btn.setAttribute('aria-current', 'step');
            else btn.removeAttribute('aria-current');
        });
        const last = index === steps.length - 1;
        backBtn.hidden = index === 0;
        nextBtn.hidden = last;
        submitBtn.hidden = !last;
        // El mapa de la ubicación se crea oculto: Leaflet recalcula su tamaño con el evento resize
        window.dispatchEvent(new Event('resize'));
    }

    // El horario lo comprueba su propio editor (opening-hours.js): días abiertos y tramos que no se pisan
    const hoursEditor = form.querySelector('[data-hours-editor]');
    const stepIsValid = index => validator.check(el => steps[index].contains(el))
        && !(hoursEditor && steps[index].contains(hoursEditor) && window.OpeningHoursEditor.problem(hoursEditor))
        && !(steps[index].contains(rows) && !teamIsValid());

    /** Filas del equipo: nombre y correo juntos, correos bien escritos y sin repetir (también el tuyo). Marca los errores. */
    function teamIsValid() {
        const owner = form.elements.email.value.trim().toLowerCase();
        const seen = new Set(owner ? [owner] : []);
        let ok = true;
        [...rows.querySelectorAll('.team-row')].forEach(row => {
            const name = row.querySelector('input[name="workerNames"]');
            const email = row.querySelector('input[name="workerEmails"]');
            const address = email.value.trim().toLowerCase();
            Cuquora.fieldError.clear(name);
            Cuquora.fieldError.clear(email);
            if (!name.value.trim() && !address) return;
            let problem = null;
            if (name.value.trim().length < 2) {
                problem = [name, name.value.trim() ? 'El nombre tiene que tener al menos 2 caracteres.' : 'Falta el nombre (o quita la fila).'];
            } else if (!address) {
                problem = [email, 'Falta el correo (o quita la fila).'];
            } else if (V.email()(address)) {
                problem = [email, V.email()(address)];
            } else if (seen.has(address)) {
                problem = [email, 'Este correo está repetido: cada persona necesita el suyo.'];
            }
            seen.add(address);
            if (problem) {
                Cuquora.fieldError.show(...problem);
                ok = false;
            }
        });
        return ok;
    }
    rows.addEventListener('input', e => {
        if (e.target.classList.contains('has-error')) Cuquora.fieldError.clear(e.target);
    });

    function goTo(index) {
        // Hacia delante solo si los pasos que se dejan atrás están bien
        for (let i = current; i < index; i++) {
            if (!stepIsValid(i)) {
                showStep(i);
                return;
            }
        }
        showStep(index);
        stepper.scrollIntoView({ behavior: Cuquora.scrollBehavior(), block: 'nearest' });
        steps[index].querySelector('input, select, textarea')?.focus({ preventScroll: true });
    }

    nextBtn.addEventListener('click', () => goTo(current + 1));
    // Desde la confirmación, «Atrás» vuelve al último paso
    backBtn.addEventListener('click', () => goTo(confirming ? current : current - 1));
    stepButtons.forEach((btn, i) => btn.addEventListener('click', () => goTo(i)));

    // Si al enviar falta algo, se vuelve al primer paso con errores (antes que la validación general).
    // Si está todo bien, en móvil se pide confirmar con la vista previa antes de registrar
    form.addEventListener('submit', e => {
        const wrong = steps.findIndex((step, i) => !stepIsValid(i));
        if (wrong >= 0) {
            showStep(wrong);
        } else if (narrow.matches && !confirming) {
            e.preventDefault();
            e.stopImmediatePropagation();
            showConfirm();
        }
    }, { capture: true });

    // Si la ventana se ensancha durante la confirmación, la vista previa vuelve a su sitio al lado
    narrow.addEventListener('change', () => {
        if (confirming && !narrow.matches) showStep(current);
        refreshSubmit();
    });
    refreshSubmit();

    stepper.hidden = false;
    showStep(0);

    // ---- Vista previa ----
    const field = name => form.elements[name];

    function setPreview(key, text) {
        preview.querySelectorAll(`[data-preview="${key}"]`).forEach(el => {
            el.textContent = text || el.dataset.empty || '';
            el.classList.toggle('is-empty', !text);
        });
    }

    function refreshPreview() {
        ['centerName', 'address', 'email', 'cabins'].forEach(name => setPreview(name, field(name).value.trim()));
        const phone = field('phone').value.trim();
        setPreview('phone', phone && !phone.startsWith('+') ? `+${field('phoneCountry').value} ${phone}` : phone);
        const initials = Cuquora.initials(field('centerName').value);
        document.querySelectorAll('[data-initials]').forEach(el => { el.textContent = initials; });
        const specialties = [...form.querySelectorAll('input[name="specialties"]:checked')].map(c => c.value);
        setPreview('specialties', specialties.join(', '));
        const extra = [...form.querySelectorAll('input[name="workerEmails"]')].filter(i => i.value.trim()).length;
        setPreview('team', extra === 0 ? 'Solo tú' : `Tú y ${extra} ${extra === 1 ? 'persona más' : 'personas más'}`);
        const openDays = [...form.querySelectorAll('[data-hours-open]')].filter(box => box.checked).length;
        const split = [...form.querySelectorAll('[data-hours-period="2"]')].some(period => !period.hidden);
        setPreview('hours', openDays === 0 ? '' : `${openDays} ${openDays === 1 ? 'día' : 'días'} a la semana${split ? ', con jornada partida' : ''}`);
        const mode = field('mode');
        setPreview('loyalty', mode.value === 'OFF' ? 'No' : mode.selectedOptions[0].textContent.replace(/^Sí: /, 'Sí, '));
    }

    form.addEventListener('input', refreshPreview);
    form.addEventListener('change', refreshPreview);
    rows.addEventListener('click', () => setTimeout(refreshPreview));
    form.querySelector('[data-hours-editor]')?.addEventListener('click', () => setTimeout(refreshPreview));
    preview.hidden = false;
    refreshPreview();
});

/** Mismas reglas que TaxIds.java: mensaje de error del CIF, NIF o NIE, o '' si vale. */
function taxIdProblem(value, spanish) {
    const id = value.replace(/[\s.\-/]/g, '').toUpperCase();
    if (!id) return '';
    if (!/^[A-Z0-9]{5,20}$/.test(id)) return 'Revisa el CIF o NIF: solo letras y números, por ejemplo B12345674.';
    // Fuera de España cada país tiene el suyo (un RUT chileno como 76123456K parece un NIF y no lo es)
    if (!spanish) return '';
    const dni = digits => 'TRWAGMYFPDXBNJZSQVHLCKE'[Number(digits) % 23];
    const last = id.slice(-1);
    let valid = null;
    if (/^\d{8}[A-Z]$/.test(id)) {
        valid = dni(id.slice(0, 8)) === last;
    } else if (/^[XYZ]\d{7}[A-Z]$/.test(id)) {
        valid = dni('XYZ'.indexOf(id[0]) + id.slice(1, 8)) === last;
    } else if (/^[KLM]\d{7}[A-Z]$/.test(id)) {
        valid = dni(id.slice(1, 8)) === last;
    } else if (/^[ABCDEFGHJNPQRSUVW]\d{7}[0-9A-J]$/.test(id)) {
        let sum = 0;
        [...id.slice(1, 8)].forEach((c, i) => {
            const d = Number(c);
            sum += i % 2 === 0 ? Math.floor(d * 2 / 10) + (d * 2) % 10 : d;
        });
        const control = (10 - sum % 10) % 10;
        const digit = String(control);
        const letter = 'JABCDEFGHI'[control];
        if ('PQRSNW'.includes(id[0])) valid = last === letter;
        else if ('ABEH'.includes(id[0])) valid = last === digit;
        else valid = last === digit || last === letter;
    }
    if (valid === false) return `La letra o el último dígito del CIF o NIF ${id} no cuadra: revisa que esté bien copiado.`;
    if (valid === null) {
        return 'Revisa el CIF o NIF: un NIF son 8 números y una letra (12345678Z), un NIE empieza por X, Y o Z (X1234567L) '
            + 'y un CIF es una letra y 8 caracteres (B12345674).';
    }
    return '';
}

/** Igual que CenterRegistrationService.checkSpanishPostcode: 5 cifras y provincia de 01 a 52. */
function postcodeProblem(address) {
    if (!address.trim()) return '';
    const codes = [...address.matchAll(/(?<!\d)(\d{5})(?!\d)/g)].map(m => m[1]);
    if (codes.some(code => Number(code.slice(0, 2)) >= 1 && Number(code.slice(0, 2)) <= 52)) return '';
    return codes.length
        ? 'Revisa el código postal de la dirección: en España empieza por 01 a 52 (ej.: Calle Mayor 12, 28013 Madrid).'
        : 'Añade el código postal a la dirección (5 cifras, ej.: Calle Mayor 12, 28013 Madrid).';
}
