/* Turnos Odontología — mejora progresiva (C-11 D-05). Sin inline-JS (CSP).
 * Todo funciona sin JS: el autocomplete degrada a <select> server-side. */
(function () {
  "use strict";

  // Logout HTML: el endpoint responde 204; redirigir al login.
  document.querySelectorAll("form[data-logout]").forEach(function (form) {
    form.addEventListener("submit", function (ev) {
      ev.preventDefault();
      var token = form.querySelector("input[type=hidden]");
      fetch(form.action, {
        method: "POST",
        credentials: "same-origin",
        headers: token ? { "X-CSRF-TOKEN": token.value } : {}
      }).then(function () {
        window.location.href = "/login";
      }).catch(function () {
        window.location.href = "/login";
      });
    });
  });

  // Autocomplete de pacientes con degradación al <select> si falla el fetch.
  var input = document.querySelector("input[data-autocomplete-pacientes]");
  if (input) {
    var box = document.createElement("ul");
    box.className = "sugeridos";
    box.setAttribute("aria-live", "polite");
    input.after(box);
    var timer = null;
    input.addEventListener("input", function () {
      window.clearTimeout(timer);
      var q = input.value.trim();
      box.innerHTML = "";
      if (q.length < 2) {
        return;
      }
      timer = window.setTimeout(function () {
        var esDni = /^[0-9]+$/.test(q);
        var url = "/api/pacientes?" + (esDni
          ? "dni=" + encodeURIComponent(q)
          : "apellido=" + encodeURIComponent(q));
        fetch(url, { credentials: "same-origin", headers: { "Accept": "application/json" } })
          .then(function (res) { return res.ok ? res.json() : null; })
          .then(function (page) {
            box.innerHTML = "";
            if (!page || !page.content) {
              return;
            }
            page.content.slice(0, 5).forEach(function (p) {
              var li = document.createElement("li");
              var btn = document.createElement("button");
              btn.type = "button";
              btn.className = "btn-secundario";
              btn.textContent = p.apellido + ", " + p.nombre + " (" + p.dni + ")";
              btn.setAttribute("data-paciente-id", p.id);
              btn.addEventListener("click", function () {
                var sel = document.querySelector("select[name=pacienteId]");
                if (sel) {
                  var opt = document.createElement("option");
                  opt.value = p.id;
                  opt.textContent = p.apellido + ", " + p.nombre + " (" + p.dni + ")";
                  opt.selected = true;
                  sel.appendChild(opt);
                }
                box.innerHTML = "";
              });
              li.appendChild(btn);
              box.appendChild(li);
            });
          })
          .catch(function () { /* sin JS de red: queda el select server-side */ });
      }, 250);
    });
  }
})();
