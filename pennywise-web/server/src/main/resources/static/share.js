// Report button on /s/{id}. Without JS the form still posts (204 keeps the page in place);
// with it, the visitor gets a confirmation and a visible acknowledgement.
(function () {
  var form = document.getElementById('share-report');
  if (!form) return;
  form.addEventListener('submit', function (event) {
    event.preventDefault();
    if (!window.confirm(form.getAttribute('data-confirm'))) return;
    var button = form.querySelector('button');
    button.disabled = true;
    fetch(form.action, { method: 'POST' }).then(function (response) {
      if (!response.ok) throw new Error(String(response.status));
      button.textContent = form.getAttribute('data-done');
    }).catch(function () {
      button.disabled = false;
    });
  });
})();
