package com.example.ui

import com.pennywiseai.parser.core.ParsedTransaction
import com.example.ui.SharedComponents.commonHead
import com.example.ui.SharedComponents.commonStyles
import com.example.ui.SharedComponents.siteHeader
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
                    "/",
                    htmx = true,
                )
                style {
                    unsafe {
                        +commonStyles
                        +"""
                        .header-content, .container { max-width: 860px; }
                        .row { display: grid; grid-template-columns: 160px 1fr; gap: 8px; margin: 6px 0; }
                        .row > div:first-child { color: var(--muted); font-family: var(--mono); font-size: 12px; }
                        .row > div:last-child { word-break: break-word; font-family: var(--mono); font-size: 13.5px; }
                        details { margin-top: 12px; }
                        details summary { cursor: pointer; color: var(--muted); font-size: 14px; }
                        details pre { background: var(--ink); border: 1px solid var(--line); border-radius: 8px; padding: 12px; margin: 8px 0 0 0; overflow-x: auto; white-space: pre-wrap; word-wrap: break-word; font-size: 12px; font-family: var(--mono); }
                        .report-btn { margin-top: 12px; padding: 8px 14px; background: transparent; color: #FF8A8A; border: 1px solid rgba(255,138,138,.4); border-radius: 8px; cursor: pointer; font-size: 14px; }
                        .report-btn:hover { border-color: #FF8A8A; background: rgba(255,138,138,.08); }
                        .modal { display: none; position: fixed; top: 0; left: 0; width: 100%; height: 100%; background: rgba(0,0,0,0.8); z-index: 1000; align-items: center; justify-content: center; }
                        .modal.show { display: flex; }
                        .modal-content { background: var(--surface); border: 1px solid var(--line); border-radius: 14px; padding: 24px; width: 90%; max-width: 500px; max-height: 90vh; overflow-y: auto; }
                        .modal h3 { margin: 0 0 16px 0; }
                        .modal label { margin-top: 16px; }
                        .modal input[type=number] { width: 100%; padding: 12px; background: var(--surface); border: 1px solid var(--line); border-radius: 10px; color: var(--text); font-size: 14px; }
                        .modal select { width: 100%; padding: 12px; background: var(--surface); border: 1px solid var(--line); border-radius: 10px; color: var(--text); font-size: 14px; }
                        .modal-buttons { display: flex; gap: 12px; margin-top: 20px; }
                        .modal-buttons button { flex: 1; }
                        .success-msg { background: rgba(91,217,165,.1); border: 1px solid var(--mint); color: var(--mint); padding: 12px; border-radius: 10px; margin-top: 12px; }
                        .parsed-info { background: var(--surface-2); border: 1px solid var(--line); border-radius: 10px; padding: 16px; margin-bottom: 20px; }
                        .parsed-info h4 { margin: 0 0 12px 0; color: var(--muted); font-size: 14px; font-weight: 600; }
                        .parsed-info .info-row { display: flex; justify-content: space-between; padding: 6px 0; font-size: 14px; }
                        .parsed-info .info-label { color: var(--muted); }
                        .parsed-info .info-value { color: var(--text); font-weight: 500; }
                        .parsed-info .no-transaction { color: var(--muted); font-style: italic; }
                        @media (max-width: 640px) {
                          .row { grid-template-columns: 1fr; }
                        }
                        """
                    }
                }
            }
            body {
                siteHeader(currentPage = "parse")

                div(classes = "container") {
                    h1 { +"SMS Parser Tool" }
                    p(classes = "muted") { +"Test bank SMS parsing with instant feedback. Report issues to help improve accuracy." }

                    form {
                        attributes["hx-post"] = "/htmx/parse"
                        attributes["hx-target"] = "#result"
                        attributes["hx-swap"] = "innerHTML"
                        attributes["hx-indicator"] = "#indicator"

                        label { htmlFor = "sender"; +"Sender ID" }
                        input(type = InputType.text, name = "sender") {
                            id = "sender"; placeholder = "e.g., VM-HDFC, AD-SBIBK-S"; required = true
                        }

                        label { htmlFor = "smsBody"; +"Message Body" }
                        textArea {
                            id = "smsBody"; name = "smsBody"; placeholder = "Paste SMS body here"; required = true
                        }

                        input(type = InputType.hidden, name = "timestamp") { id = "timestamp" }

                        button(type = ButtonType.submit) { +"Parse" }
                        span("spinner") { id = "indicator" }
                    }

                    div(classes = "card") { id = "result"; p { +"Result will appear here." } }

                    // Report Modal
                    div(classes = "modal") {
                        id = "reportModal"
                        div(classes = "modal-content") {
                            h3 { +"Report Parsing Issue" }

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
                                    button(type = ButtonType.button) {
                                        attributes["onclick"] = "hideReportModal()"
                                        +"Cancel"
                                    }
                                    button(type = ButtonType.button) {
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
                                const parsedResultStr = document.getElementById('parsed_result').value;
                                const parsedDetails = document.getElementById('parsedDetails');

                                if (parsedResultStr) {
                                    try {
                                        const parsed = JSON.parse(parsedResultStr);
                                        let html = '';

                                        if (parsed.amount !== undefined) {
                                            html += '<div class="info-row"><span class="info-label">Amount:</span><span class="info-value">' + (parsed.currency === 'INR' || !parsed.currency ? '₹' + parsed.amount.toLocaleString('en-IN') : parsed.currency + ' ' + parsed.amount.toLocaleString()) + '</span></div>';
                                        }
                                        if (parsed.type) {
                                            html += '<div class="info-row"><span class="info-label">Type:</span><span class="info-value">' + parsed.type + '</span></div>';
                                        }
                                        if (parsed.merchant) {
                                            html += '<div class="info-row"><span class="info-label">Merchant:</span><span class="info-value">' + parsed.merchant + '</span></div>';
                                        }

                                        if (html === '') {
                                            html = '<p class="no-transaction">No transaction details detected</p>';
                                        }

                                        parsedDetails.innerHTML = html;
                                    } catch (e) {
                                        parsedDetails.innerHTML = '<p class="no-transaction">No transaction detected</p>';
                                    }
                                } else {
                                    parsedDetails.innerHTML = '<p class="no-transaction">No transaction detected</p>';
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

                                try {
                                    const response = await fetch('/api/report', {
                                        method: 'POST',
                                        headers: { 'Content-Type': 'application/json' },
                                        body: JSON.stringify(requestBody)
                                    });

                                    const result = await response.json();

                                    if (result.success) {
                                        document.getElementById('reportStatus').innerHTML =
                                            '<div class="success-msg">Report submitted successfully! Thank you for your feedback.</div>';
                                        setTimeout(hideReportModal, 2000);
                                    } else {
                                        document.getElementById('reportStatus').innerHTML =
                                            '<div style="color: #ef4444;">Error: ' + result.message + '</div>';
                                    }
                                } catch (error) {
                                    document.getElementById('reportStatus').innerHTML =
                                        '<div style="color: #ef4444;">Failed to submit report. Please try again.</div>';
                                }
                            }
                        """ }
                    }
                }
            }
        }
    }

    fun FlowContent.renderParseResult(parsed: ParsedTransaction?, senderId: String = "", message: String = "") {
        if (parsed == null) {
            div { p { +"No transaction detected." } }
            return
        }
        div {
            h3 { +"Parsed Transaction" }
            div(classes = "row") { div { +"Bank" }; div { +parsed.bankName } }
            div(classes = "row") { div { +"Type" }; div { +parsed.type.name } }
            // Currency-tagged, never a bare number: the registry now parses
            // non-INR banks (LKR since #658), so the code must travel with the
            // amount.
            div(classes = "row") { div { +"Amount" }; div { +"${parsed.currency} ${parsed.amount.toPlainString()}" } }
            if (parsed.merchant != null) {
                div(classes = "row") { div { +"Merchant" }; div { +parsed.merchant!! } }
            }
            if (parsed.reference != null) {
                div(classes = "row") { div { +"Reference" }; div { +parsed.reference!! } }
            }
            if (parsed.accountLast4 != null) {
                div(classes = "row") { div { +"Account Last 4" }; div { +parsed.accountLast4!! } }
            }
            if (parsed.balance != null) {
                div(classes = "row") { div { +"Balance" }; div { +parsed.balance!!.toPlainString() } }
            }
            if (parsed.creditLimit != null) {
                div(classes = "row") { div { +"Available Limit" }; div { +parsed.creditLimit!!.toPlainString() } }
            }
            div(classes = "row") { div { +"From Card" }; div { +(if (parsed.isFromCard) "Yes" else "No") } }
            details {
                summary { +"Raw SMS" }
                pre { +parsed.smsBody }
            }

            // Report Issue button
            button(classes = "report-btn") {
                attributes["onclick"] = "showReportModal()"
                +"Report Issue"
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


