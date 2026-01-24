package com.example.ui

import com.pennywiseai.parser.core.ParsedTransaction
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
            head {
                title { +"PennyWise AI - SMS Parser Tool" }
                meta { charset = "utf-8" }
                meta { name = "viewport"; content = "width=device-width, initial-scale=1" }
                link { rel = "icon"; href = "/static/logo.png" }
                script { src = "https://unpkg.com/htmx.org@1.9.12" }
                style {
                    unsafe {
                        +"""
                        :root { color-scheme: dark; }
                        * { box-sizing: border-box; }
                        body { background: #000; color: #fff; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, Cantarell, 'Fira Sans', 'Droid Sans', 'Helvetica Neue', Arial, sans-serif; margin: 0; line-height: 1.5; }
                        .header { background: #0a0a0a; border-bottom: 1px solid #222; padding: 16px 0; margin-bottom: 24px; }
                        .header-content { max-width: 860px; margin: 0 auto; padding: 0 24px; display: flex; align-items: center; justify-content: space-between; }
                        .logo-section { display: flex; align-items: center; gap: 12px; }
                        .logo-section a { display: flex; align-items: center; gap: 12px; text-decoration: none; color: inherit; }
                        .logo { width: 40px; height: 40px; border-radius: 8px; }
                        .logo-text { font-size: 20px; font-weight: 600; }
                        .nav-links { display: flex; gap: 20px; align-items: center; }
                        .nav-links a { color: #9ca3af; text-decoration: none; display: flex; align-items: center; gap: 6px; transition: color 0.2s; font-size: 14px; }
                        .nav-links a:hover { color: #fff; }
                        .nav-links a.active { color: #fff; font-weight: 500; }
                        .nav-links svg { width: 20px; height: 20px; }
                        .container { max-width: 860px; margin: 0 auto; padding: 24px; }
                        h1 { margin: 0 0 6px 0; font-size: 22px; }
                        label { display: block; font-weight: 600; margin: 12px 0 6px; color: #fff; }
                        input[type=text], textarea { width: 100%; padding: 12px 12px; background: #0a0a0a; border: 1px solid #333; border-radius: 8px; font-size: 14px; color: #fff; }
                        input[type=text]::placeholder, textarea::placeholder { color: #9ca3af; }
                        input[type=text]:focus, textarea:focus { outline: none; border-color: #555; box-shadow: 0 0 0 3px rgba(255,255,255,0.06); }
                        textarea { min-height: 160px; resize: vertical; }
                        button { margin-top: 12px; padding: 10px 16px; background: #fff; color: #000; border: 1px solid #fff; border-radius: 8px; cursor: pointer; font-weight: 600; }
                        button:hover { background: #000; color: #fff; }
                        button:disabled { opacity: .6; cursor: not-allowed; }
                        .card { background: #0a0a0a; border: 1px solid #222; border-radius: 10px; padding: 16px; margin-top: 16px; }
                        .muted { color: #9ca3af; font-size: 12px; }
                        .row { display: grid; grid-template-columns: 160px 1fr; gap: 8px; margin: 6px 0; }
                        .row > div:first-child { color: #9ca3af; }
                        .row > div:last-child { word-break: break-word; }
                        details { margin-top: 12px; }
                        details summary { cursor: pointer; color: #9ca3af; font-size: 14px; }
                        details pre { background: #000; border: 1px solid #333; border-radius: 6px; padding: 12px; margin: 8px 0 0 0; overflow-x: auto; white-space: pre-wrap; word-wrap: break-word; font-size: 12px; }
                        .spinner { display:none; width:16px; height:16px; border:2px solid #333; border-top-color:#fff; border-radius:50%; animation: spin 1s linear infinite; margin-left: 8px; vertical-align: middle; }
                        .htmx-request .spinner { display:inline-block; }
                        @keyframes spin { to { transform: rotate(360deg) } }
                        .report-btn { margin-top: 12px; padding: 8px 14px; background: #ef4444; color: #fff; border: 1px solid #ef4444; border-radius: 6px; cursor: pointer; font-size: 14px; }
                        .report-btn:hover { background: #dc2626; border-color: #dc2626; }
                        .modal { display: none; position: fixed; top: 0; left: 0; width: 100%; height: 100%; background: rgba(0,0,0,0.8); z-index: 1000; align-items: center; justify-content: center; }
                        .modal.show { display: flex; }
                        .modal-content { background: #0a0a0a; border: 1px solid #333; border-radius: 12px; padding: 24px; width: 90%; max-width: 500px; max-height: 90vh; overflow-y: auto; }
                        .modal h3 { margin: 0 0 16px 0; }
                        .modal label { margin-top: 16px; }
                        .modal input[type=number] { width: 100%; }
                        .modal select { width: 100%; padding: 12px; background: #0a0a0a; border: 1px solid #333; border-radius: 8px; color: #fff; font-size: 14px; }
                        .modal-buttons { display: flex; gap: 12px; margin-top: 20px; }
                        .modal-buttons button { flex: 1; }
                        .success-msg { background: #065f46; border: 1px solid #10b981; color: #10b981; padding: 12px; border-radius: 8px; margin-top: 12px; }
                        .parsed-info { background: #1a1a1a; border: 1px solid #333; border-radius: 8px; padding: 16px; margin-bottom: 20px; }
                        .parsed-info h4 { margin: 0 0 12px 0; color: #9ca3af; font-size: 14px; font-weight: 600; }
                        .parsed-info .info-row { display: flex; justify-content: space-between; padding: 6px 0; font-size: 14px; }
                        .parsed-info .info-label { color: #9ca3af; }
                        .parsed-info .info-value { color: #fff; font-weight: 500; }
                        .parsed-info .no-transaction { color: #9ca3af; font-style: italic; }
                        @media (max-width: 640px) {
                          .header-content { flex-direction: column; gap: 16px; }
                          .container { padding: 16px; }
                          .row { grid-template-columns: 1fr; }
                          h1 { font-size: 18px; }
                          .logo-text { font-size: 18px; }
                          .nav-links { width: 100%; justify-content: center; }
                        }
                        """
                    }
                }
            }
            body {
                // Header with logo and links
                div(classes = "header") {
                    div(classes = "header-content") {
                        div(classes = "logo-section") {
                            a(href = "/") {
                                img(src = "/static/logo.png", alt = "PennyWise AI", classes = "logo")
                                span(classes = "logo-text") { +"PennyWise AI" }
                            }
                        }
                        nav(classes = "nav-links") {
                            a(href = "/", classes = "active") { +"Parser" }
                            a(href = "/feedback") { +"Feedback" }
                            a(href = "/roadmap") { +"Roadmap" }
                            a(href = "https://github.com/sarim2000/pennywiseai-tracker", target = "_blank") {
                                unsafe {
                                    +"""<svg fill="currentColor" viewBox="0 0 24 24"><path d="M12 2C6.477 2 2 6.484 2 12.017c0 4.425 2.865 8.18 6.839 9.504.5.092.682-.217.682-.483 0-.237-.008-.868-.013-1.703-2.782.605-3.369-1.343-3.369-1.343-.454-1.158-1.11-1.466-1.11-1.466-.908-.62.069-.608.069-.608 1.003.07 1.531 1.032 1.531 1.032.892 1.53 2.341 1.088 2.91.832.092-.647.35-1.088.636-1.338-2.22-.253-4.555-1.113-4.555-4.951 0-1.093.39-1.988 1.029-2.688-.103-.253-.446-1.272.098-2.65 0 0 .84-.27 2.75 1.026A9.564 9.564 0 0112 6.844c.85.004 1.705.115 2.504.337 1.909-1.296 2.747-1.027 2.747-1.027.546 1.379.202 2.398.1 2.651.64.7 1.028 1.595 1.028 2.688 0 3.848-2.339 4.695-4.566 4.943.359.309.678.92.678 1.855 0 1.338-.012 2.419-.012 2.747 0 .268.18.58.688.482A10.019 10.019 0 0022 12.017C22 6.484 17.522 2 12 2z"/></svg>"""
                                }
                            }
                            a(href = "https://discord.gg/H3xWeMWjKQ", target = "_blank") {
                                unsafe {
                                    +"""<svg fill="currentColor" viewBox="0 0 24 24"><path d="M20.317 4.37a19.791 19.791 0 0 0-4.885-1.515a.074.074 0 0 0-.079.037c-.21.375-.444.864-.608 1.25a18.27 18.27 0 0 0-5.487 0a12.64 12.64 0 0 0-.617-1.25a.077.077 0 0 0-.079-.037A19.736 19.736 0 0 0 3.677 4.37a.07.07 0 0 0-.032.027C.533 9.046-.32 13.58.099 18.057a.082.082 0 0 0 .031.057a19.9 19.9 0 0 0 5.993 3.03a.078.078 0 0 0 .084-.028a14.09 14.09 0 0 0 1.226-1.994a.076.076 0 0 0-.041-.106a13.107 13.107 0 0 1-1.872-.892a.077.077 0 0 1-.008-.128a10.2 10.2 0 0 0 .372-.292a.074.074 0 0 1 .077-.01c3.928 1.793 8.18 1.793 12.062 0a.074.074 0 0 1 .078.01c.12.098.246.198.373.292a.077.077 0 0 1-.006.127a12.299 12.299 0 0 1-1.873.892a.077.077 0 0 0-.041.107c.36.698.772 1.362 1.225 1.993a.076.076 0 0 0 .084.028a19.839 19.839 0 0 0 6.002-3.03a.077.077 0 0 0 .032-.054c.5-5.177-.838-9.674-3.549-13.66a.061.061 0 0 0-.031-.03zM8.02 15.33c-1.183 0-2.157-1.085-2.157-2.419c0-1.333.956-2.419 2.157-2.419c1.21 0 2.176 1.096 2.157 2.42c0 1.333-.956 2.418-2.157 2.418zm7.975 0c-1.183 0-2.157-1.085-2.157-2.419c0-1.333.955-2.419 2.157-2.419c1.21 0 2.176 1.096 2.157 2.42c0 1.333-.946 2.418-2.157 2.418z"/></svg>"""
                                }
                            }
                        }
                    }
                }

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
                                            html += '<div class="info-row"><span class="info-label">Amount:</span><span class="info-value">₹' + parsed.amount.toLocaleString('en-IN') + '</span></div>';
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
            div(classes = "row") { div { +"Amount" }; div { +parsed.amount.toPlainString() } }
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
            put("type", parsed.type.name)
            parsed.merchant?.let { put("merchant", it) }
        }) else "" }
    }
}


