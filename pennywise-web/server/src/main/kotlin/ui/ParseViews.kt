package com.example.ui

import com.pennywiseai.parser.core.ParsedTransaction
import com.example.ui.SharedComponents.commonHead
import com.example.ui.SharedComponents.commonStyles
import com.example.ui.SharedComponents.siteHeader
import com.example.ui.SharedComponents.proBanner
import com.example.ui.SharedComponents.siteFooter
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.html.*
import io.ktor.server.response.*
import kotlinx.html.*
import kotlinx.serialization.json.*
import kotlinx.serialization.encodeToString

object ParseViews {
    suspend fun ApplicationCall.respondParsePage() {
        respondHtml(HttpStatusCode.OK) {
            lang = "en"
            head {
                // Shares the site chrome rather than duplicating it — this page used to
                // carry its own copy of the header markup and ~50 lines of the same CSS,
                // which is why nav additions never reached the site's most-visited page.
                commonHead(
                    "Bank SMS parser — see what PennyWise reads",
                    "Paste any bank SMS and see precisely which transaction details " +
                        "PennyWise extracts from it. Runs the same parser that ships in the app.",
                    "/tools/parse",
                    htmx = true,
                )
                style {
                    unsafe {
                        +commonStyles
                        +"""
                        .tool-head { display: flex; align-items: center; justify-content: space-between; gap: 24px; margin: 8px 0 28px; }
                        .tool-head h1 { font-size: clamp(34px, 6vw, 52px); line-height: 1.06; margin: 6px 0 12px; }
                        .tool-head .lede { margin: 0; }
                        .tool-badge { flex: none; width: 112px; height: 112px; display: grid; place-items: center; background: var(--primary-container); }
                        .tool-badge img { width: 72px; height: 72px; display: block; }

                        .tool-layout { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); gap: 20px; align-items: start; }
                        .tool-form { background: var(--surface-low); border-radius: var(--r-2xl); padding: 24px; }
                        .tool-form h2 { font-size: 22px; font-weight: 700; margin: 0 0 6px; }
                        .tool-form label:first-of-type { margin-top: 18px; }
                        /* M3 filled text field: surface-container-highest on the card's low surface. */
                        .tool-form input[type=text], .tool-form textarea { background: var(--surface-highest); border-radius: var(--r-md); }
                        .tool-form input[type=text]:focus, .tool-form textarea:focus { background: var(--surface-highest); border-color: var(--primary); }
                        .tool-form textarea { min-height: 180px; line-height: 1.6; }
                        .form-actions { display: flex; align-items: center; gap: 12px; margin-top: 20px; }
                        .form-actions .parse-btn { min-width: 160px; }
                        /* hx-indicator puts .htmx-request on the spinner itself, not an ancestor. */
                        .spinner.htmx-request { display: inline-block; }

                        .result-pane { position: sticky; top: 88px; }
                        .result-empty { border: 2px dashed var(--outline-variant); border-radius: var(--r-2xl); padding: 48px 24px; text-align: center; color: var(--on-variant); }
                        .result-empty p { margin: 0; }
                        .result-none { background: var(--surface-container); border-radius: var(--r-2xl); padding: 28px 24px; color: var(--on-bg); }
                        .result-none p { margin: 0; font-weight: 600; }

                        .result-hero { border-radius: var(--r-2xl) var(--r-2xl) var(--r-xs) var(--r-xs); padding: 24px; }
                        .result-hero .parsed-top { align-items: center; }
                        .result-hero h2 { color: inherit; font-size: 15px; font-weight: 600; letter-spacing: 0; margin: 0; opacity: .9; }
                        .result-hero .parsed-amount { margin-top: 10px; font-size: clamp(36px, 5vw, 48px); line-height: 1.1; word-break: break-word; }
                        .result-hero .parsed-amount .cur { font-size: .5em; font-weight: 700; margin-right: 6px; opacity: .85; }
                        .result-hero .tag.tag-income { background: var(--success-container); color: var(--on-success-container); }

                        .raw { margin-top: 4px; background: var(--surface-low); border-radius: var(--r-xs) var(--r-xs) var(--r-lg) var(--r-lg); padding: 14px 18px; }
                        .raw summary { cursor: pointer; color: var(--on-bg); font-size: 15px; font-weight: 600; }
                        .raw pre { background: var(--surface-container); color: var(--on-bg); border-radius: var(--r-md); padding: 14px 16px; margin: 12px 0 2px; overflow-x: auto; white-space: pre-wrap; word-wrap: break-word; font-size: 12.5px; line-height: 1.7; font-family: var(--mono); }
                        .result-actions { display: flex; justify-content: flex-end; margin-top: 12px; }
                        button.report-btn { height: 40px; padding: 0 20px; background: transparent; color: var(--error); border: 1px solid var(--outline-variant); border-radius: var(--r-full); font-size: 14px; }
                        button.report-btn:hover { background: var(--error-container); color: var(--on-error-container); border-color: transparent; filter: none; }

                        /* Report dialog: M3 basic dialog over a scrim. The scrim role is black in both themes. */
                        .modal { display: none; position: fixed; inset: 0; background: rgb(0 0 0 / .45); z-index: 1000; align-items: center; justify-content: center; padding: 16px; }
                        .modal.show { display: flex; }
                        .modal-content { background: var(--surface-high); color: var(--on-bg); border-radius: var(--r-xl); padding: 24px; width: 100%; max-width: 520px; max-height: calc(100vh - 32px); overflow-y: auto; }
                        .modal h3 { margin: 0 0 16px 0; font-size: 24px; font-weight: 700; }
                        .modal h4 { margin: 0 0 4px; font-size: 16px; font-weight: 600; color: var(--on-bg); }
                        .modal label { margin-top: 14px; }
                        .modal input[type=text], .modal input[type=number], .modal select, .modal textarea { background: var(--surface-lowest); border: 1px solid var(--outline-variant); border-radius: var(--r-sm); color: var(--on-bg); font-size: 15px; }
                        .modal input:focus, .modal select:focus, .modal textarea:focus { background: var(--surface-lowest); border-color: var(--primary); }
                        .modal textarea { min-height: 88px; font-family: var(--body); font-size: 15px; }
                        .modal-buttons { display: flex; justify-content: flex-end; gap: 8px; margin-top: 24px; }
                        .modal-buttons button.btn-text { background: transparent; color: var(--primary); padding: 0 16px; }
                        .modal-buttons button.btn-text:hover { background: color-mix(in srgb, var(--primary) 10%, transparent); filter: none; }
                        .success-msg { background: var(--success-container); color: var(--on-success-container); padding: 12px 16px; border-radius: var(--r-md); margin-top: 16px; font-size: 14px; font-weight: 500; }
                        .error-msg { background: var(--error-container); color: var(--on-error-container); padding: 12px 16px; border-radius: var(--r-md); margin-top: 16px; font-size: 14px; font-weight: 500; }
                        .parsed-info { background: var(--primary-container); color: var(--on-primary-container); border-radius: var(--r-lg); padding: 16px 18px; margin-bottom: 20px; }
                        .parsed-info h4 { margin: 0 0 8px 0; color: inherit; font-size: 14px; font-weight: 600; opacity: .9; }
                        .parsed-info .info-row { display: flex; justify-content: space-between; gap: 12px; padding: 6px 0; font-size: 14px; }
                        .parsed-info .info-row + .info-row { border-top: 1px solid color-mix(in srgb, currentColor 16%, transparent); }
                        .parsed-info .info-label { opacity: .85; }
                        .parsed-info .info-value { font-weight: 600; font-family: var(--mono); text-align: right; word-break: break-word; }
                        .parsed-info .no-transaction { margin: 0; font-style: italic; opacity: .9; }

                        @media (max-width: 900px) {
                          .tool-layout { grid-template-columns: 1fr; }
                          .result-pane { position: static; }
                        }
                        @media (max-width: 640px) {
                          .tool-head { margin-bottom: 20px; }
                          .tool-badge { width: 72px; height: 72px; }
                          .tool-badge img { width: 46px; height: 46px; }
                          .tool-form { padding: 20px 16px; border-radius: var(--r-xl); }
                          .form-actions .parse-btn { flex: 1; }
                          .result-hero { padding: 20px; border-radius: var(--r-xl) var(--r-xl) var(--r-xs) var(--r-xs); }
                          .result-empty { padding: 32px 20px; border-radius: var(--r-xl); }
                        }
                        """
                    }
                }
            }
            body {
                siteHeader(currentPage = "parse")

                div(classes = "container") {
                    proBanner()
                    div(classes = "tool-head") {
                        div {
                            span(classes = "eyebrow") { +"Free tool" }
                            h1 { +"Bank SMS parser" }
                            p(classes = "lede") {
                                +"Paste a bank SMS and see exactly what PennyWise reads from it, with the same parser that ships in the app. "
                                +"Spot a mistake? Report it to help improve accuracy."
                            }
                        }
                        div(classes = "tool-badge shape-cookie") {
                            attributes["aria-hidden"] = "true"
                            picture {
                                source {
                                    attributes["srcset"] = "/static/owl-dark.svg"
                                    attributes["media"] = "(prefers-color-scheme: dark)"
                                }
                                img(src = "/static/owl.svg", alt = "")
                            }
                        }
                    }

                    div(classes = "tool-layout") {
                        form(classes = "tool-form") {
                            attributes["hx-post"] = "/htmx/parse"
                            attributes["hx-target"] = "#result"
                            attributes["hx-swap"] = "innerHTML"
                            attributes["hx-indicator"] = "#indicator"

                            h2 { +"Your SMS" }
                            p(classes = "muted") { +"Enter the sender ID exactly as it appears on your phone." }

                            label { htmlFor = "sender"; +"Sender ID" }
                            input(type = InputType.text, name = "sender") {
                                id = "sender"; placeholder = "e.g., VM-HDFC, AD-SBIBK-S"; required = true
                            }

                            label { htmlFor = "smsBody"; +"Message Body" }
                            textArea {
                                id = "smsBody"; name = "smsBody"; placeholder = "Paste SMS body here"; required = true
                            }

                            input(type = InputType.hidden, name = "timestamp") { id = "timestamp" }

                            div(classes = "form-actions") {
                                button(type = ButtonType.submit, classes = "btn btn-filled btn-lg parse-btn") { +"Parse" }
                                span("spinner") { id = "indicator"; attributes["aria-hidden"] = "true" }
                            }
                        }

                        div(classes = "result-pane") {
                            id = "result"
                            attributes["aria-live"] = "polite"
                            div(classes = "result-empty") { p { +"Result will appear here." } }
                        }
                    }

                    // Report Modal
                    div(classes = "modal") {
                        id = "reportModal"
                        attributes["role"] = "dialog"
                        attributes["aria-modal"] = "true"
                        attributes["aria-labelledby"] = "reportTitle"
                        div(classes = "modal-content") {
                            h3 { id = "reportTitle"; +"Report Parsing Issue" }

                            // Display what was parsed
                            div(classes = "parsed-info") {
                                id = "parsedInfo"
                                h4 { +"What we detected:" }
                                div { id = "parsedDetails" }
                            }

                            form {
                                id = "reportForm"
                                h4 { +"What did you expect?" }

                                label { htmlFor = "expected_amount"; +"Expected Amount" }
                                input(type = InputType.number, name = "expected_amount") {
                                    id = "expected_amount"
                                    placeholder = "Enter amount (leave empty if not a transaction)"
                                    step = "0.01"
                                }

                                label { htmlFor = "expected_type"; +"Expected Type" }
                                select {
                                    id = "expected_type"
                                    name = "expected_type"
                                    option { value = ""; +"Select type..." }
                                    option { value = "INCOME"; +"Income" }
                                    option { value = "EXPENSE"; +"Expense" }
                                }

                                label { htmlFor = "expected_merchant"; +"Expected Merchant" }
                                input(type = InputType.text, name = "expected_merchant") {
                                    id = "expected_merchant"
                                    placeholder = "Enter merchant name"
                                }

                                label { htmlFor = "user_note"; +"Additional Notes (Optional)" }
                                textArea {
                                    id = "user_note"
                                    name = "user_note"
                                    placeholder = "Any additional information that might help..."
                                    rows = "3"
                                }

                                div(classes = "modal-buttons") {
                                    button(type = ButtonType.button, classes = "btn btn-text") {
                                        attributes["onclick"] = "hideReportModal()"
                                        +"Cancel"
                                    }
                                    button(type = ButtonType.button, classes = "btn btn-filled") {
                                        attributes["onclick"] = "submitReport()"
                                        +"Submit Report"
                                    }
                                }
                            }

                            div { id = "reportStatus" }
                        }
                    }

                    script {
                        unsafe { +"""
                            (function(){
                              var ts = document.getElementById('timestamp');
                              function setTs(){ ts && (ts.value = Date.now()); }
                              setTs();
                              document.addEventListener('htmx:configRequest', function(){ setTs(); });

                              // Parse URL parameters (supports both hash and query params)
                              function parseUrlParams() {
                                const params = {};

                                // Parse hash parameters
                                if (window.location.hash) {
                                  const hashParams = window.location.hash.substring(1);
                                  const pairs = hashParams.split('&');
                                  pairs.forEach(pair => {
                                    const [key, value] = pair.split('=');
                                    if (key && value) {
                                      params[key] = decodeURIComponent(value.replace(/\+/g, ' '));
                                    }
                                  });
                                }

                                // Parse query parameters (override hash params if both exist)
                                const searchParams = new URLSearchParams(window.location.search);
                                searchParams.forEach((value, key) => {
                                  params[key] = value;
                                });

                                return params;
                              }

                              // Auto-fill form and optionally parse on page load
                              window.addEventListener('DOMContentLoaded', function() {
                                const params = parseUrlParams();

                                // Fill sender field
                                if (params.sender) {
                                  const senderField = document.getElementById('sender');
                                  if (senderField) {
                                    senderField.value = params.sender;
                                  }
                                }

                                // Fill message field
                                if (params.message) {
                                  const messageField = document.getElementById('smsBody');
                                  if (messageField) {
                                    messageField.value = params.message;
                                  }
                                }

                                // Auto-parse if requested
                                if (params.autoparse === 'true' && params.sender && params.message) {
                                  // Small delay to ensure HTMX is ready
                                  setTimeout(function() {
                                    const form = document.querySelector('form[hx-post="/htmx/parse"]');
                                    if (form) {
                                      htmx.trigger(form, 'submit');
                                    }
                                  }, 100);
                                }

                                // Clear URL parameters after processing (cleaner URL)
                                if (Object.keys(params).length > 0) {
                                  const cleanUrl = window.location.pathname;
                                  window.history.replaceState({}, document.title, cleanUrl);
                                }
                              });
                            })();

                            function showReportModal() {
                                // Show the modal
                                document.getElementById('reportModal').classList.add('show');

                                // Display parsed data in the modal
                                const parsedDetails = document.getElementById('parsedDetails');

                                const raw = document.getElementById('parsed_result').value;
                                parsedDetails.textContent = '';
                                if (!raw) {
                                    const p = document.createElement('p');
                                    p.className = 'no-transaction';
                                    p.textContent = 'No transaction details detected';
                                    parsedDetails.appendChild(p);
                                    return;
                                }
                                let parsed;
                                try {
                                    parsed = JSON.parse(raw);
                                } catch (e) {
                                    const p = document.createElement('p');
                                    p.className = 'no-transaction';
                                    p.textContent = 'Error parsing transaction details';
                                    parsedDetails.appendChild(p);
                                    return;
                                }
                                if (!parsed || typeof parsed.amount !== 'number') {
                                    const p = document.createElement('p');
                                    p.className = 'no-transaction';
                                    p.textContent = 'No transaction details detected';
                                    parsedDetails.appendChild(p);
                                    return;
                                }

                                function addInfoRow(label, value) {
                                    const row = document.createElement('div');
                                    row.className = 'info-row';
                                    const labelEl = document.createElement('span');
                                    labelEl.className = 'info-label';
                                    labelEl.textContent = label;
                                    const valueEl = document.createElement('span');
                                    valueEl.className = 'info-value';
                                    valueEl.textContent = value;
                                    row.appendChild(labelEl);
                                    row.appendChild(valueEl);
                                    parsedDetails.appendChild(row);
                                }

                                addInfoRow('Amount:',
                                    parsed.currency === 'INR' || !parsed.currency
                                        ? '₹' + parsed.amount.toLocaleString('en-IN')
                                        : parsed.currency + ' ' + parsed.amount.toLocaleString());
                                if (parsed.type) {
                                    addInfoRow('Type:', parsed.type);
                                }
                                if (parsed.merchant) {
                                    addInfoRow('Merchant:', parsed.merchant);
                                }
                            }

                            function hideReportModal() {
                                document.getElementById('reportModal').classList.remove('show');
                                document.getElementById('reportForm').reset();
                                document.getElementById('reportStatus').innerHTML = '';
                                document.getElementById('parsedDetails').innerHTML = '';
                            }

                            async function submitReport() {
                                const senderId = document.getElementById('parsed_sender').value;
                                const message = document.getElementById('parsed_message').value;
                                const parsedResultStr = document.getElementById('parsed_result').value;

                                const amount = document.getElementById('expected_amount').value;
                                const type = document.getElementById('expected_type').value;
                                const merchant = document.getElementById('expected_merchant').value;
                                const userNote = document.getElementById('user_note').value;

                                const userExpected = {
                                    amount: amount ? parseFloat(amount) : null,
                                    type: type || null,
                                    merchant: merchant || null,
                                    isTransaction: !!(amount || type || merchant)
                                };

                                const requestBody = {
                                    senderId: senderId,
                                    message: message,
                                    parsedResult: parsedResultStr ? JSON.parse(parsedResultStr) : null,
                                    userExpected: userExpected,
                                    userNote: userNote || null
                                };

                                // Same wrapper structure the old innerHTML strings
                                // produced, rebuilt as text nodes (SEC-001).
                                function showStatus(text, success) {
                                    const status = document.getElementById('reportStatus');
                                    status.textContent = '';
                                    const el = document.createElement('div');
                                    el.className = success ? 'success-msg' : 'error-msg';
                                    el.textContent = text;
                                    status.appendChild(el);
                                }

                                try {
                                    const response = await fetch('/api/report', {
                                        method: 'POST',
                                        headers: { 'Content-Type': 'application/json' },
                                        body: JSON.stringify(requestBody)
                                    });

                                    const result = await response.json();

                                    if (result.success) {
                                        showStatus('Report submitted successfully! Thank you for your feedback.', true);
                                        setTimeout(hideReportModal, 2000);
                                    } else {
                                        showStatus('Error: ' + result.message, false);
                                    }
                                } catch (error) {
                                    showStatus('Failed to submit report. Please try again.', false);
                                }
                            }
                        """ }
                    }
                }
                siteFooter()
            }
        }
    }

    fun FlowContent.renderParseResult(parsed: ParsedTransaction?, senderId: String = "", message: String = "") {
        if (parsed == null) {
            div(classes = "result-none") { p { +"No transaction detected." } }
            return
        }
        div(classes = "result") {
            div(classes = "parsed result-hero") {
                div(classes = "parsed-top") {
                    h2 { +"Parsed Transaction" }
                    span(classes = if (parsed.type.name == "INCOME") "tag tag-income" else "tag") { +parsed.type.name }
                }
                // Currency-tagged, never a bare number: the registry now parses
                // non-INR banks (LKR since #658), so the code must travel with the
                // amount.
                div(classes = "parsed-amount") {
                    span(classes = "cur") { +parsed.currency }
                    +" ${parsed.amount.toPlainString()}"
                }
                div(classes = "fields") {
                    fun field(label: String, value: String) {
                        div { div(classes = "field-k") { +label }; div(classes = "field-v") { +value } }
                    }
                    field("Bank", parsed.bankName)
                    parsed.merchant?.let { field("Merchant", it) }
                    parsed.reference?.let { field("Reference", it) }
                    parsed.accountLast4?.let { field("Account last 4", it) }
                    parsed.balance?.let { field("Balance", "${parsed.currency} ${it.toPlainString()}") }
                    parsed.creditLimit?.let { field("Available limit", "${parsed.currency} ${it.toPlainString()}") }
                    field("From card", if (parsed.isFromCard) "Yes" else "No")
                }
            }
            details(classes = "raw") {
                summary { +"Raw SMS" }
                pre { +parsed.smsBody }
            }

            // Report Issue button
            div(classes = "result-actions") {
                button(type = ButtonType.button, classes = "report-btn") {
                    attributes["onclick"] = "showReportModal()"
                    +"Report Issue"
                }
            }
        }

        // Hidden data for the report form
        input(type = InputType.hidden) { id = "parsed_sender"; value = senderId }
        input(type = InputType.hidden) { id = "parsed_message"; value = message }
        input(type = InputType.hidden) { id = "parsed_result"; value = if (parsed != null) Json.encodeToString(JsonObject.serializer(), buildJsonObject {
            put("amount", parsed.amount.toDouble())
            put("currency", parsed.currency)
            put("type", parsed.type.name)
            parsed.merchant?.let { put("merchant", it) }
        }) else "" }
    }
}


