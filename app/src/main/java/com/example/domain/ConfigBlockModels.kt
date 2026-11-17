package com.example.domain

import java.util.Locale
import java.util.UUID

/**
 * The 9 block categories in Config Maker / Custom Config Studio.
 */
enum class BlockCategory(
    val displayName: String,
    val description: String,
    val shortBadge: String
) {
    BROWSER("Browser", "Blocks for browser automation", "BRW"),
    CAPTCHAS("Captchas", "Blocks for solving captchas", "CAP"),
    CONDITIONS("Conditions", "Blocks that have to do with checking conditions", "CND"),
    FUNCTIONS("Functions", "General purpose functions", "FN"),
    INTEROP("Interop", "Blocks for interoperability with other programs", "IOP"),
    PARSING("Parsing", "Blocks for extracting data from strings", "PRS"),
    REQUESTS("Requests", "Blocks for performing network requests", "REQ"),
    SELENIUM("Selenium", "Blocks for browser automation with Selenium", "SEL"),
    UTILITY("Utility", "Utility blocks for miscellaneous purposes", "UTL")
}

/**
 * Concrete block definitions grouped under the 9 [BlockCategory] entries.
 */
enum class BlockType(
    val category: BlockCategory,
    val displayName: String,
    val summary: String,
    val primaryLabel: String,
    val primaryPlaceholder: String,
    val secondaryLabel: String = "",
    val secondaryPlaceholder: String = "",
    val tertiaryLabel: String = "",
    val tertiaryPlaceholder: String = "",
    val defaultOutputVar: String = ""
) {
    // 1. Browser (Blocks for browser automation)
    BROWSER_OPEN(
        category = BlockCategory.BROWSER,
        displayName = "Open Browser",
        summary = "Initialize browser automation context with User-Agent & viewport",
        primaryLabel = "Engine / Mode",
        primaryPlaceholder = "Headless Chromium",
        secondaryLabel = "User-Agent",
        secondaryPlaceholder = "{random_ua}",
        tertiaryLabel = "Extra Flags / Viewport",
        tertiaryPlaceholder = "1280x720 --no-sandbox",
        defaultOutputVar = "browser_session"
    ),
    BROWSER_NAVIGATE(
        category = BlockCategory.BROWSER,
        displayName = "Navigate URL",
        summary = "Navigate browser page to target URL (supports fetch HTML mode)",
        primaryLabel = "Target URL",
        primaryPlaceholder = "https://example.com/checkout",
        secondaryLabel = "Wait Until",
        secondaryPlaceholder = "DOMContentLoaded",
        tertiaryLabel = "Timeout (ms)",
        tertiaryPlaceholder = "15000",
        defaultOutputVar = "SOURCE"
    ),
    BROWSER_CLICK(
        category = BlockCategory.BROWSER,
        displayName = "Click Element",
        summary = "Click DOM element matching CSS or XPath selector",
        primaryLabel = "CSS / XPath Selector",
        primaryPlaceholder = "button[type=submit]",
        secondaryLabel = "Selector Type",
        secondaryPlaceholder = "CSS",
        tertiaryLabel = "Wait After Click (ms)",
        tertiaryPlaceholder = "500"
    ),
    BROWSER_TYPE(
        category = BlockCategory.BROWSER,
        displayName = "Type Text",
        summary = "Type text or card/combo tokens into a form input element",
        primaryLabel = "CSS / XPath Selector",
        primaryPlaceholder = "input[name=cardnumber]",
        secondaryLabel = "Text / Tokens to Type",
        secondaryPlaceholder = "{card}",
        tertiaryLabel = "Delay Between Keys (ms)",
        tertiaryPlaceholder = "25"
    ),
    BROWSER_WAIT_ELEMENT(
        category = BlockCategory.BROWSER,
        displayName = "Wait For Element",
        summary = "Wait until selector becomes visible or attached in DOM",
        primaryLabel = "CSS / XPath Selector",
        primaryPlaceholder = ".payment-status",
        secondaryLabel = "Condition",
        secondaryPlaceholder = "Visible",
        tertiaryLabel = "Timeout (ms)",
        tertiaryPlaceholder = "10000"
    ),
    BROWSER_EXEC_JS(
        category = BlockCategory.BROWSER,
        displayName = "Execute JS",
        summary = "Evaluate JavaScript in page context and store return value",
        primaryLabel = "JavaScript Snippet",
        primaryPlaceholder = "return document.title;",
        secondaryLabel = "Scope / Frame",
        secondaryPlaceholder = "main",
        defaultOutputVar = "js_result"
    ),
    BROWSER_GET_COOKIES(
        category = BlockCategory.BROWSER,
        displayName = "Get Cookies",
        summary = "Export browser session cookies into variable & HTTP cookie jar",
        primaryLabel = "Cookie Name (* for all)",
        primaryPlaceholder = "*",
        secondaryLabel = "Domain Filter (Opt)",
        secondaryPlaceholder = "",
        defaultOutputVar = "browser_cookies"
    ),
    BROWSER_SCREENSHOT(
        category = BlockCategory.BROWSER,
        displayName = "Capture DOM / Shot",
        summary = "Capture rendered page DOM or screenshot reference",
        primaryLabel = "Capture Mode",
        primaryPlaceholder = "DOM_HTML",
        secondaryLabel = "Selector (Optional)",
        secondaryPlaceholder = "body",
        defaultOutputVar = "dom_snapshot"
    ),
    BROWSER_CLOSE(
        category = BlockCategory.BROWSER,
        displayName = "Close Browser",
        summary = "Terminate active browser context and release session",
        primaryLabel = "Clear Cookies on Close",
        primaryPlaceholder = "true"
    ),

    // 2. Captchas (Blocks for solving captchas)
    CAPTCHA_RECAPTCHA_V2(
        category = BlockCategory.CAPTCHAS,
        displayName = "ReCaptcha v2",
        summary = "Solve Google ReCaptcha v2 using sitekey & page URL",
        primaryLabel = "Site Key",
        primaryPlaceholder = "6Le-wvkSAAAAAPBMRTvw0Q4Muexq9bi0DJwx_mJ-",
        secondaryLabel = "Solver Provider (2Captcha / CapSolver / Anti-Captcha)",
        secondaryPlaceholder = "2Captcha",
        tertiaryLabel = "Page URL | API Key (url|key)",
        tertiaryPlaceholder = "https://example.com|{cap:solver_key}",
        defaultOutputVar = "g_recaptcha_response"
    ),
    CAPTCHA_RECAPTCHA_V3(
        category = BlockCategory.CAPTCHAS,
        displayName = "ReCaptcha v3",
        summary = "Solve score-based Google ReCaptcha v3 with action & min score",
        primaryLabel = "Site Key",
        primaryPlaceholder = "6Lc...",
        secondaryLabel = "Action & MinScore (e.g. checkout:0.7)",
        secondaryPlaceholder = "checkout:0.7",
        tertiaryLabel = "Page URL | API Key",
        tertiaryPlaceholder = "https://example.com|{cap:solver_key}",
        defaultOutputVar = "recaptcha_v3_token"
    ),
    CAPTCHA_HCAPTCHA(
        category = BlockCategory.CAPTCHAS,
        displayName = "hCaptcha",
        summary = "Solve hCaptcha challenge token via solver service",
        primaryLabel = "Site Key",
        primaryPlaceholder = "10000000-ffff-ffff-ffff-000000000001",
        secondaryLabel = "Solver Provider",
        secondaryPlaceholder = "CapSolver",
        tertiaryLabel = "Page URL | API Key",
        tertiaryPlaceholder = "https://example.com|{cap:solver_key}",
        defaultOutputVar = "h_captcha_response"
    ),
    CAPTCHA_TURNSTILE(
        category = BlockCategory.CAPTCHAS,
        displayName = "Cloudflare Turnstile",
        summary = "Solve Cloudflare Turnstile challenge token",
        primaryLabel = "Site Key",
        primaryPlaceholder = "0x4AAAAAAA...",
        secondaryLabel = "Solver Provider",
        secondaryPlaceholder = "2Captcha",
        tertiaryLabel = "Page URL | API Key",
        tertiaryPlaceholder = "https://example.com|{cap:solver_key}",
        defaultOutputVar = "cf_turnstile_response"
    ),
    CAPTCHA_IMAGE(
        category = BlockCategory.CAPTCHAS,
        displayName = "Image Captcha",
        summary = "Solve base64 image captcha via OCR / solver endpoint",
        primaryLabel = "Base64 Image / Variable",
        primaryPlaceholder = "{var:captcha_b64}",
        secondaryLabel = "Solver Provider",
        secondaryPlaceholder = "2Captcha",
        tertiaryLabel = "API Key",
        tertiaryPlaceholder = "{cap:solver_key}",
        defaultOutputVar = "captcha_text"
    ),
    CAPTCHA_FUNCAPTCHA(
        category = BlockCategory.CAPTCHAS,
        displayName = "FunCaptcha / Arkose",
        summary = "Solve Arkose Labs FunCaptcha public key challenge",
        primaryLabel = "Public Key",
        primaryPlaceholder = "476068BF-9607-4799-B53D-966BE98E2B81",
        secondaryLabel = "Service Subdomain",
        secondaryPlaceholder = "client-api.arkoselabs.com",
        tertiaryLabel = "Page URL | API Key",
        tertiaryPlaceholder = "https://example.com|{cap:solver_key}",
        defaultOutputVar = "arkose_token"
    ),
    CAPTCHA_BALANCE(
        category = BlockCategory.CAPTCHAS,
        displayName = "Solver Balance",
        summary = "Query live account balance from 2Captcha or CapSolver API",
        primaryLabel = "Solver Provider (2Captcha / CapSolver)",
        primaryPlaceholder = "2Captcha",
        secondaryLabel = "API Key",
        secondaryPlaceholder = "{cap:solver_key}",
        defaultOutputVar = "captcha_balance"
    ),

    // 3. Conditions (Blocks that have to do with checking conditions)
    CONDITION_KEYCHECK(
        category = BlockCategory.CONDITIONS,
        displayName = "Keycheck",
        summary = "Check response/source keywords and assign LIVE / DIE / UNKNOWN verdict",
        primaryLabel = "Target Source / Var (<SOURCE> or var)",
        primaryPlaceholder = "<SOURCE>",
        secondaryLabel = "Operator (CONTAINS / CONTAINS_ANY / CONTAINS_ALL / EQUALS / REGEX)",
        secondaryPlaceholder = "CONTAINS_ANY",
        tertiaryLabel = "Expected Value(s) (comma-separated)",
        tertiaryPlaceholder = "succeeded,approved,authorized",
        defaultOutputVar = "keycheck_match"
    ),
    CONDITION_IF_STRING(
        category = BlockCategory.CONDITIONS,
        displayName = "If String",
        summary = "Compare two strings (Contains, Equals, NotContains, StartsWith, Regex)",
        primaryLabel = "Left Value / Token",
        primaryPlaceholder = "{var:status}",
        secondaryLabel = "Operator (EQUALS / CONTAINS / NOT_CONTAINS / REGEX / STARTS_WITH)",
        secondaryPlaceholder = "EQUALS",
        tertiaryLabel = "Right Value",
        tertiaryPlaceholder = "succeeded",
        defaultOutputVar = "cond_result"
    ),
    CONDITION_IF_NUMBER(
        category = BlockCategory.CONDITIONS,
        displayName = "If Number",
        summary = "Numeric comparison (==, !=, >, <, >=, <=)",
        primaryLabel = "Left Number / Token",
        primaryPlaceholder = "{var:STATUS_CODE}",
        secondaryLabel = "Operator (== / != / > / < / >= / <=)",
        secondaryPlaceholder = "==",
        tertiaryLabel = "Right Number",
        tertiaryPlaceholder = "200",
        defaultOutputVar = "num_cond_result"
    ),
    CONDITION_IF_STATUS(
        category = BlockCategory.CONDITIONS,
        displayName = "If HTTP Status",
        summary = "Match last HTTP response status code or comma-separated list",
        primaryLabel = "Allowed Status Code(s)",
        primaryPlaceholder = "200,201,204",
        secondaryLabel = "Negate (false/true)",
        secondaryPlaceholder = "false",
        defaultOutputVar = "status_match"
    ),
    CONDITION_IF_EXISTS(
        category = BlockCategory.CONDITIONS,
        displayName = "If Var Exists",
        summary = "Check whether a captured variable exists and is non-empty",
        primaryLabel = "Variable Name",
        primaryPlaceholder = "payment_token",
        secondaryLabel = "Must Be Non-Blank (true/false)",
        secondaryPlaceholder = "true",
        defaultOutputVar = "var_exists"
    ),

    // 4. Functions (General purpose functions)
    FUNC_HASH(
        category = BlockCategory.FUNCTIONS,
        displayName = "Hash (MD5/SHA)",
        summary = "Compute MD5, SHA-1, SHA-256, or SHA-512 lowercase hex digest",
        primaryLabel = "Input String / Tokens",
        primaryPlaceholder = "{card}|{mm}|{yyyy}",
        secondaryLabel = "Algorithm (MD5 / SHA-1 / SHA-256 / SHA-512)",
        secondaryPlaceholder = "SHA-256",
        defaultOutputVar = "hash_hex"
    ),
    FUNC_HMAC(
        category = BlockCategory.FUNCTIONS,
        displayName = "HMAC Signature",
        summary = "Compute HMAC-SHA256 or HMAC-SHA512 digest with secret key",
        primaryLabel = "Message / Payload",
        primaryPlaceholder = "{timestamp}.{card}",
        secondaryLabel = "Secret Key",
        secondaryPlaceholder = "my_webhook_secret",
        tertiaryLabel = "Algorithm (HmacSHA256 / HmacSHA512)",
        tertiaryPlaceholder = "HmacSHA256",
        defaultOutputVar = "hmac_sig"
    ),
    FUNC_BASE64_ENCODE(
        category = BlockCategory.FUNCTIONS,
        displayName = "Base64 Encode",
        summary = "Encode UTF-8 text or combo string to Base64",
        primaryLabel = "Input Text / Tokens",
        primaryPlaceholder = "{email}:{pass}",
        defaultOutputVar = "b64_encoded"
    ),
    FUNC_BASE64_DECODE(
        category = BlockCategory.FUNCTIONS,
        displayName = "Base64 Decode",
        summary = "Decode a Base64 string back to UTF-8 text",
        primaryLabel = "Base64 Input / Var",
        primaryPlaceholder = "{var:b64_encoded}",
        defaultOutputVar = "b64_decoded"
    ),
    FUNC_URL_ENCODE(
        category = BlockCategory.FUNCTIONS,
        displayName = "URL Encode / Decode",
        summary = "URL-encode or URL-decode a string (UTF-8)",
        primaryLabel = "Input Text / Tokens",
        primaryPlaceholder = "{lista}",
        secondaryLabel = "Mode (ENCODE / DECODE)",
        secondaryPlaceholder = "ENCODE",
        defaultOutputVar = "url_text"
    ),
    FUNC_RANDOM_STRING(
        category = BlockCategory.FUNCTIONS,
        displayName = "Random String / UUID",
        summary = "Generate random UUID, hex, digits, or alphanumeric string",
        primaryLabel = "Mode (UUID / HEX / DIGITS / ALNUM)",
        primaryPlaceholder = "HEX",
        secondaryLabel = "Length (for HEX/DIGITS/ALNUM)",
        secondaryPlaceholder = "16",
        defaultOutputVar = "rand_val"
    ),
    FUNC_TIMESTAMP(
        category = BlockCategory.FUNCTIONS,
        displayName = "Current Time / ISO",
        summary = "Generate Unix seconds, milliseconds, or ISO-8601 UTC timestamp",
        primaryLabel = "Format (UNIX_S / UNIX_MS / ISO_UTC)",
        primaryPlaceholder = "UNIX_S",
        defaultOutputVar = "now_ts"
    ),
    FUNC_STRING_REPLACE(
        category = BlockCategory.FUNCTIONS,
        displayName = "Replace / Regex Sub",
        summary = "Replace literal text or regex matches inside a string",
        primaryLabel = "Input Text / Var",
        primaryPlaceholder = "{card_spaced}",
        secondaryLabel = "Find (or Regex)",
        secondaryPlaceholder = " ",
        tertiaryLabel = "Replace With",
        tertiaryPlaceholder = "",
        defaultOutputVar = "replaced_str"
    ),
    FUNC_STRING_CASE(
        category = BlockCategory.FUNCTIONS,
        displayName = "Case / Trim / Length",
        summary = "Transform string: UPPER, LOWER, TRIM, or LENGTH",
        primaryLabel = "Input Text / Var",
        primaryPlaceholder = "{scheme}",
        secondaryLabel = "Operation (UPPER / LOWER / TRIM / LENGTH)",
        secondaryPlaceholder = "LOWER",
        defaultOutputVar = "str_out"
    ),
    FUNC_MATH_EVAL(
        category = BlockCategory.FUNCTIONS,
        displayName = "Math / Compute",
        summary = "Evaluate arithmetic (+, -, *, /, %) on numeric values",
        primaryLabel = "Expression (e.g. 100 + 25 * 2)",
        primaryPlaceholder = "{var:amount} * 100",
        secondaryLabel = "Decimal Places (0..6)",
        secondaryPlaceholder = "0",
        defaultOutputVar = "math_result"
    ),
    FUNC_CONSTANT(
        category = BlockCategory.FUNCTIONS,
        displayName = "Constant / Format",
        summary = "Assign a formatted template string with tokens to a variable",
        primaryLabel = "Template Value",
        primaryPlaceholder = "{card}|{mm}|{yyyy}|{cvv}",
        defaultOutputVar = "formatted_val"
    ),

    // 5. Interop (Blocks for interoperability with other programs)
    INTEROP_SHELL(
        category = BlockCategory.INTEROP,
        displayName = "Shell Command",
        summary = "Define external CLI / process command with arguments & timeout",
        primaryLabel = "Executable / Command",
        primaryPlaceholder = "python3 solver.py",
        secondaryLabel = "Arguments",
        secondaryPlaceholder = "--pan {card} --bin {bin}",
        tertiaryLabel = "Timeout (ms)",
        tertiaryPlaceholder = "5000",
        defaultOutputVar = "shell_stdout"
    ),
    INTEROP_SCRIPT(
        category = BlockCategory.INTEROP,
        displayName = "External Script / Eval",
        summary = "Evaluate inline script or variable transform expression",
        primaryLabel = "Script / Expression",
        primaryPlaceholder = "concat({bin}, '-', {pan4})",
        secondaryLabel = "Runtime (JS / Python / Expression)",
        secondaryPlaceholder = "Expression",
        defaultOutputVar = "script_out"
    ),
    INTEROP_WEBHOOK_POST(
        category = BlockCategory.INTEROP,
        displayName = "Webhook / IPC Push",
        summary = "Send JSON payload to an external webhook or companion service",
        primaryLabel = "Webhook URL",
        primaryPlaceholder = "https://hooks.example.com/ingest",
        secondaryLabel = "JSON Payload",
        secondaryPlaceholder = """{"card":"{card}","bin":"{bin}","status":"{var:status}"}""",
        tertiaryLabel = "Headers (Key: Value)",
        tertiaryPlaceholder = "Content-Type: application/json",
        defaultOutputVar = "webhook_resp"
    ),
    INTEROP_TCP_SOCKET(
        category = BlockCategory.INTEROP,
        displayName = "TCP / Socket Exchange",
        summary = "Connect to TCP host:port to send payload or probe service availability",
        primaryLabel = "Host:Port",
        primaryPlaceholder = "127.0.0.1:9000",
        secondaryLabel = "Payload to Send (Opt)",
        secondaryPlaceholder = "PING {card}",
        tertiaryLabel = "Timeout (ms)",
        tertiaryPlaceholder = "3000",
        defaultOutputVar = "tcp_reply"
    ),
    INTEROP_INTENT_EXPORT(
        category = BlockCategory.INTEROP,
        displayName = "Android Intent / URI",
        summary = "Prepare deep-link URI or Android Intent action for external app handoff",
        primaryLabel = "Action / URI Scheme",
        primaryPlaceholder = "https://t.me/share/url?url={lista}",
        secondaryLabel = "Target Package (Opt)",
        secondaryPlaceholder = "org.telegram.messenger",
        defaultOutputVar = "intent_uri"
    ),

    // 6. Parsing (Blocks for extracting data from strings)
    PARSE_LR(
        category = BlockCategory.PARSING,
        displayName = "Parse Left / Right",
        summary = "Extract substring between Left and Right delimiter strings",
        primaryLabel = "Left Delimiter",
        primaryPlaceholder = "\"token\":\"",
        secondaryLabel = "Right Delimiter",
        secondaryPlaceholder = "\"",
        tertiaryLabel = "Input Var (<SOURCE> default) | Fallback",
        tertiaryPlaceholder = "<SOURCE>",
        defaultOutputVar = "parsed_lr"
    ),
    PARSE_JSON(
        category = BlockCategory.PARSING,
        displayName = "Parse JSON Path",
        summary = "Extract value using dot-path & array index (e.g. errors[0].code)",
        primaryLabel = "JSON Dot-Path",
        primaryPlaceholder = "data.token",
        secondaryLabel = "Input Var (<SOURCE> default)",
        secondaryPlaceholder = "<SOURCE>",
        tertiaryLabel = "Fallback Default",
        tertiaryPlaceholder = "",
        defaultOutputVar = "parsed_json"
    ),
    PARSE_REGEX(
        category = BlockCategory.PARSING,
        displayName = "Parse Regex",
        summary = "Extract regex capture group(s) from source string",
        primaryLabel = "Regex Pattern",
        primaryPlaceholder = "\"id\"\\s*:\\s*\"([^\"]+)\"",
        secondaryLabel = "Input Var (<SOURCE> default)",
        secondaryPlaceholder = "<SOURCE>",
        tertiaryLabel = "Fallback Default",
        tertiaryPlaceholder = "",
        defaultOutputVar = "parsed_rx"
    ),
    PARSE_CSS(
        category = BlockCategory.PARSING,
        displayName = "Parse CSS / HTML",
        summary = "Extract HTML element text or @attribute (e.g. input[name=csrf]@value)",
        primaryLabel = "CSS Selector[@attr]",
        primaryPlaceholder = "input[name=csrf_token]@value",
        secondaryLabel = "Input Var (<SOURCE> default)",
        secondaryPlaceholder = "<SOURCE>",
        tertiaryLabel = "Fallback Default",
        tertiaryPlaceholder = "",
        defaultOutputVar = "parsed_css"
    ),
    PARSE_HEADER(
        category = BlockCategory.PARSING,
        displayName = "Parse HTTP Header",
        summary = "Extract response HTTP header value by header name",
        primaryLabel = "Header Name",
        primaryPlaceholder = "x-request-id",
        tertiaryLabel = "Fallback Default",
        tertiaryPlaceholder = "",
        defaultOutputVar = "parsed_hdr"
    ),
    PARSE_COOKIE(
        category = BlockCategory.PARSING,
        displayName = "Parse Set-Cookie",
        summary = "Extract specific cookie value or all cookies (*) from response",
        primaryLabel = "Cookie Name (or *)",
        primaryPlaceholder = "session_id",
        tertiaryLabel = "Fallback Default",
        tertiaryPlaceholder = "",
        defaultOutputVar = "parsed_cookie"
    ),

    // 7. Requests (Blocks for performing network requests)
    REQUEST_HTTP(
        category = BlockCategory.REQUESTS,
        displayName = "HTTP Request",
        summary = "Perform HTTP GET / POST / PUT / PATCH / DELETE network request",
        primaryLabel = "Request URL",
        primaryPlaceholder = "https://api.stripe.com/v1/tokens",
        secondaryLabel = "Method (GET / POST_JSON / POST_FORM / PUT / DELETE)",
        secondaryPlaceholder = "POST_FORM",
        tertiaryLabel = "Body Template",
        tertiaryPlaceholder = "card[number]={card}&card[cvc]={cvv}",
        defaultOutputVar = "SOURCE"
    ),
    REQUEST_SET_COOKIE(
        category = BlockCategory.REQUESTS,
        displayName = "Set Cookie",
        summary = "Set or override a session cookie (name=value) for subsequent requests",
        primaryLabel = "Cookie Name",
        primaryPlaceholder = "session_token",
        secondaryLabel = "Cookie Value",
        secondaryPlaceholder = "{cap:sid}"
    ),
    REQUEST_CLEAR_COOKIES(
        category = BlockCategory.REQUESTS,
        displayName = "Clear Cookies",
        summary = "Clear all accumulated cookies in the current session jar",
        primaryLabel = "Scope",
        primaryPlaceholder = "ALL"
    ),
    REQUEST_MULTIPART(
        category = BlockCategory.REQUESTS,
        displayName = "Multipart Request",
        summary = "Send multipart/form-data key=value fields over HTTP POST",
        primaryLabel = "Request URL",
        primaryPlaceholder = "https://example.com/upload",
        secondaryLabel = "Form Parts (key=value per line)",
        secondaryPlaceholder = "card={card}\nmm={mm}\nyyyy={yyyy}",
        defaultOutputVar = "SOURCE"
    ),

    // 8. Selenium (Blocks for browser automation with Selenium)
    SELENIUM_OPEN(
        category = BlockCategory.SELENIUM,
        displayName = "Selenium Open Browser",
        summary = "Open Selenium WebDriver instance (Chrome / Firefox / Edge / Headless)",
        primaryLabel = "Driver Type",
        primaryPlaceholder = "ChromeHeadless",
        secondaryLabel = "Driver Arguments",
        secondaryPlaceholder = "--disable-gpu --window-size=1280,720",
        tertiaryLabel = "Proxy Override (Opt)",
        tertiaryPlaceholder = "",
        defaultOutputVar = "webdriver_id"
    ),
    SELENIUM_NAVIGATE(
        category = BlockCategory.SELENIUM,
        displayName = "Selenium Navigate",
        summary = "Call WebDriver driver.get(url) to load target page",
        primaryLabel = "Target URL",
        primaryPlaceholder = "https://example.com/login",
        secondaryLabel = "Page Load Timeout (s)",
        secondaryPlaceholder = "20",
        defaultOutputVar = "SOURCE"
    ),
    SELENIUM_FIND_ELEMENT(
        category = BlockCategory.SELENIUM,
        displayName = "Find WebElement",
        summary = "Locate DOM element using By.CssSelector, By.XPath, By.Id, or By.Name",
        primaryLabel = "Locator Value",
        primaryPlaceholder = "//input[@id='card-number']",
        secondaryLabel = "Locator Strategy (CSS / XPATH / ID / NAME)",
        secondaryPlaceholder = "XPATH",
        tertiaryLabel = "Attribute to Read (Opt, e.g. value/text)",
        tertiaryPlaceholder = "text",
        defaultOutputVar = "element_val"
    ),
    SELENIUM_SEND_KEYS(
        category = BlockCategory.SELENIUM,
        displayName = "Send Keys",
        summary = "Type text or card/combo tokens into a Selenium WebElement",
        primaryLabel = "Locator (CSS or XPath)",
        primaryPlaceholder = "#card-number",
        secondaryLabel = "Keys / Text",
        secondaryPlaceholder = "{card}",
        tertiaryLabel = "Clear Before Typing (true/false)",
        tertiaryPlaceholder = "true"
    ),
    SELENIUM_CLICK(
        category = BlockCategory.SELENIUM,
        displayName = "Element Click",
        summary = "Click a Selenium WebElement matched by locator",
        primaryLabel = "Locator (CSS or XPath)",
        primaryPlaceholder = "button#submit-pay",
        secondaryLabel = "Strategy (CSS / XPATH / ID)",
        secondaryPlaceholder = "CSS"
    ),
    SELENIUM_WAIT(
        category = BlockCategory.SELENIUM,
        displayName = "Explicit Wait",
        summary = "WebDriverWait until condition (Visible / Clickable / Present)",
        primaryLabel = "Locator (CSS or XPath)",
        primaryPlaceholder = ".receipt-success",
        secondaryLabel = "Wait Condition",
        secondaryPlaceholder = "ElementIsVisible",
        tertiaryLabel = "Timeout (s)",
        tertiaryPlaceholder = "15"
    ),
    SELENIUM_EXEC_SCRIPT(
        category = BlockCategory.SELENIUM,
        displayName = "Execute WebDriver JS",
        summary = "Execute JavascriptExecutor script inside Selenium browser",
        primaryLabel = "JavaScript Code",
        primaryPlaceholder = "return window.localStorage.getItem('token');",
        defaultOutputVar = "sel_js_out"
    ),
    SELENIUM_GET_COOKIES(
        category = BlockCategory.SELENIUM,
        displayName = "Get WebDriver Cookies",
        summary = "Extract WebDriver cookies into session jar & variable",
        primaryLabel = "Cookie Name (* for all)",
        primaryPlaceholder = "*",
        defaultOutputVar = "sel_cookies"
    ),
    SELENIUM_QUIT(
        category = BlockCategory.SELENIUM,
        displayName = "Selenium Quit",
        summary = "Close browser tabs and dispose WebDriver session",
        primaryLabel = "Close Mode",
        primaryPlaceholder = "QUIT"
    ),

    // 9. Utility (Utility blocks for miscellaneous purposes)
    UTIL_DELAY(
        category = BlockCategory.UTILITY,
        displayName = "Delay / Sleep",
        summary = "Pause pipeline execution for specified milliseconds",
        primaryLabel = "Duration (ms)",
        primaryPlaceholder = "500"
    ),
    UTIL_LOG(
        category = BlockCategory.UTILITY,
        displayName = "Log Message",
        summary = "Write a timestamped diagnostic message into execution trace",
        primaryLabel = "Message Template",
        primaryPlaceholder = "Checked {pan4} -> status={var:status}"
    ),
    UTIL_SET_VAR(
        category = BlockCategory.UTILITY,
        displayName = "Set Variable",
        summary = "Assign or update a named pipeline variable",
        primaryLabel = "Value / Token Template",
        primaryPlaceholder = "{card}|{mm}|{yyyy}|{cvv}",
        defaultOutputVar = "custom_var"
    ),
    UTIL_CLEAR_VAR(
        category = BlockCategory.UTILITY,
        displayName = "Clear Variable",
        summary = "Remove a variable from pipeline memory (or * to clear all)",
        primaryLabel = "Variable Name (or *)",
        primaryPlaceholder = "temp_token"
    ),
    UTIL_LIST_OP(
        category = BlockCategory.UTILITY,
        displayName = "List Operation",
        summary = "Split, Count, First, Last, or Get Index from a delimited string",
        primaryLabel = "Input String / Var",
        primaryPlaceholder = "{lista}",
        secondaryLabel = "Operation (COUNT / FIRST / LAST / GET_INDEX / JOIN)",
        secondaryPlaceholder = "COUNT",
        tertiaryLabel = "Delimiter | Index (e.g. | or ,|0)",
        tertiaryPlaceholder = "|",
        defaultOutputVar = "list_out"
    ),
    UTIL_FILE_APPEND(
        category = BlockCategory.UTILITY,
        displayName = "Output / Note Append",
        summary = "Append formatted result line into pipeline output notes",
        primaryLabel = "Output Line Template",
        primaryPlaceholder = "{lista} => {var:status}",
        defaultOutputVar = "output_note"
    );

    companion object {
        fun forCategory(category: BlockCategory): List<BlockType> =
            entries.filter { it.category == category }
    }
}

/**
 * A single configurable block instance inside a Config Maker block pipeline.
 */
data class ConfigBlock(
    val id: String = UUID.randomUUID().toString().take(8),
    val type: BlockType,
    val label: String = type.displayName,
    val enabled: Boolean = true,
    val inputTarget: String = "",
    val secondaryParam: String = "",
    val tertiaryParam: String = "",
    val outputVar: String = type.defaultOutputVar,
    val captureToOutput: Boolean = false,
    val verdictOnMatch: ResultBucket = ResultBucket.LIVE,
    val extraParams: Map<String, String> = emptyMap()
) {
    val category: BlockCategory
        get() = type.category

    fun compactSummary(): String {
        val target = inputTarget.trim().ifBlank { secondaryParam.trim() }.take(38)
        val out = outputVar.trim()
        return buildString {
            if (target.isNotEmpty()) append(target) else append(type.summary.take(38))
            if (out.isNotEmpty()) {
                append(" -> ")
                append(if (captureToOutput) "CAP " else "VAR ")
                append(out)
            }
            if (type.category == BlockCategory.CONDITIONS && type == BlockType.CONDITION_KEYCHECK) {
                append(" [").append(verdictOnMatch.name).append(']')
            }
        }
    }
}

enum class BlockStepStatus {
    SUCCESS,
    MATCHED,
    SIMULATED,
    SKIPPED,
    FAILED
}

data class BlockExecutionStep(
    val index: Int,
    val block: ConfigBlock,
    val status: BlockStepStatus,
    val detail: String,
    val durationMs: Long,
    val outputPreview: String = ""
)

data class BlockPipelineResult(
    val steps: List<BlockExecutionStep>,
    val variables: Map<String, String>,
    val captures: Map<String, String>,
    val cookies: Map<String, String>,
    val logs: List<String>,
    val verdict: Pair<ResultBucket, String>?,
    val lastStatusCode: Int?,
    val lastSourceBody: String,
    val lastHeaders: Map<String, String>,
    val totalDurationMs: Long
)

data class BlockRecipeTemplate(
    val id: String,
    val name: String,
    val description: String,
    val blocks: List<ConfigBlock>
)

object ConfigBlockSerialization {
    private const val BLOCK_LINE_SEP = "\n"
    private const val FIELD_SEP = "\u001F"
    private const val EXTRA_SEP = "\u001D"
    private const val EXTRA_KV_SEP = "\u001C"

    private fun packText(value: String): String =
        value
            .replace("\\", "\\\\")
            .replace("\r", "\\r")
            .replace("\n", "\\n")
            .replace(FIELD_SEP, " ")
            .replace(EXTRA_SEP, " ")
            .replace(EXTRA_KV_SEP, " ")

    private fun unpackText(packed: String): String {
        if (!packed.contains('\\')) return packed
        val sb = StringBuilder(packed.length)
        var i = 0
        while (i < packed.length) {
            val ch = packed[i]
            if (ch == '\\' && i + 1 < packed.length) {
                when (val next = packed[i + 1]) {
                    '\\' -> { sb.append('\\'); i += 2 }
                    'n' -> { sb.append('\n'); i += 2 }
                    'r' -> { sb.append('\r'); i += 2 }
                    else -> { sb.append(ch).append(next); i += 2 }
                }
            } else {
                sb.append(ch)
                i++
            }
        }
        return sb.toString()
    }

    fun serializeBlocks(blocks: List<ConfigBlock>): String {
        if (blocks.isEmpty()) return ""
        return blocks.joinToString(BLOCK_LINE_SEP) { b ->
            val extras = b.extraParams.entries.joinToString(EXTRA_SEP) { (k, v) ->
                "${packText(k)}$EXTRA_KV_SEP${packText(v)}"
            }
            listOf(
                packText(b.id),
                b.type.name,
                packText(b.label),
                if (b.enabled) "1" else "0",
                packText(b.inputTarget),
                packText(b.secondaryParam),
                packText(b.tertiaryParam),
                packText(b.outputVar),
                if (b.captureToOutput) "1" else "0",
                b.verdictOnMatch.name,
                extras
            ).joinToString(FIELD_SEP)
        }
    }

    fun parseBlocks(spec: String): List<ConfigBlock> {
        if (spec.isBlank()) return emptyList()
        return spec.lines().mapNotNull { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty()) return@mapNotNull null
            val parts = line.split(FIELD_SEP)
            if (parts.size < 2) return@mapNotNull null
            val type = runCatching { BlockType.valueOf(parts[1].trim().uppercase(Locale.ROOT)) }
                .getOrNull() ?: return@mapNotNull null
            val id = unpackText(parts[0]).ifBlank { UUID.randomUUID().toString().take(8) }
            val label = unpackText(parts.getOrElse(2) { type.displayName }).ifBlank { type.displayName }
            val enabled = parts.getOrElse(3) { "1" } != "0"
            val inputTarget = unpackText(parts.getOrElse(4) { "" })
            val secondaryParam = unpackText(parts.getOrElse(5) { "" })
            val tertiaryParam = unpackText(parts.getOrElse(6) { "" })
            val outputVar = unpackText(parts.getOrElse(7) { type.defaultOutputVar })
            val captureToOutput = parts.getOrElse(8) { "0" } == "1"
            val verdict = runCatching {
                ResultBucket.valueOf(parts.getOrElse(9) { ResultBucket.LIVE.name }.trim().uppercase(Locale.ROOT))
            }.getOrDefault(ResultBucket.LIVE)
            val extrasRaw = parts.getOrElse(10) { "" }
            val extraMap = LinkedHashMap<String, String>()
            if (extrasRaw.isNotEmpty()) {
                extrasRaw.split(EXTRA_SEP).forEach { pair ->
                    val sepIdx = pair.indexOf(EXTRA_KV_SEP)
                    if (sepIdx > 0) {
                        val k = unpackText(pair.substring(0, sepIdx))
                        val v = unpackText(pair.substring(sepIdx + 1))
                        if (k.isNotBlank()) extraMap[k] = v
                    }
                }
            }
            ConfigBlock(
                id = id,
                type = type,
                label = label,
                enabled = enabled,
                inputTarget = inputTarget,
                secondaryParam = secondaryParam,
                tertiaryParam = tertiaryParam,
                outputVar = outputVar,
                captureToOutput = captureToOutput,
                verdictOnMatch = verdict,
                extraParams = extraMap
            )
        }
    }

    val BUILT_IN_RECIPES: List<BlockRecipeTemplate> by lazy {
        listOf(
            BlockRecipeTemplate(
                id = "recipe_hash_parse_keycheck",
                name = "Token + Hash + Parse + Keycheck",
                description = "Computes SHA-256 signature, parses JSON status & code, and checks LIVE/DIE conditions",
                blocks = listOf(
                    ConfigBlock(
                        type = BlockType.FUNC_HASH,
                        label = "SHA-256 Card Sig",
                        inputTarget = "{card}|{mm}|{yyyy}",
                        secondaryParam = "SHA-256",
                        outputVar = "card_sig",
                        captureToOutput = true
                    ),
                    ConfigBlock(
                        type = BlockType.PARSE_JSON,
                        label = "Extract Status",
                        inputTarget = "status",
                        secondaryParam = "<SOURCE>",
                        outputVar = "status",
                        captureToOutput = true
                    ),
                    ConfigBlock(
                        type = BlockType.CONDITION_KEYCHECK,
                        label = "Check Approved",
                        inputTarget = "<SOURCE>",
                        secondaryParam = "CONTAINS_ANY",
                        tertiaryParam = "succeeded,approved,authorized",
                        outputVar = "keycheck_match",
                        verdictOnMatch = ResultBucket.LIVE
                    ),
                    ConfigBlock(
                        type = BlockType.UTIL_LOG,
                        label = "Log Verdict",
                        inputTarget = "Card {pan4} sig={var:card_sig} status={var:status}"
                    )
                )
            ),
            BlockRecipeTemplate(
                id = "recipe_browser_captcha_post",
                name = "Browser + Captcha + HTTP POST",
                description = "Opens browser context, solves Turnstile/ReCaptcha, submits POST request & checks response",
                blocks = listOf(
                    ConfigBlock(
                        type = BlockType.BROWSER_OPEN,
                        label = "Launch Headless",
                        inputTarget = "Headless Chromium",
                        secondaryParam = "{random_ua}",
                        outputVar = "browser_session"
                    ),
                    ConfigBlock(
                        type = BlockType.BROWSER_NAVIGATE,
                        label = "Load Checkout",
                        inputTarget = "https://example.com/checkout",
                        secondaryParam = "DOMContentLoaded",
                        tertiaryParam = "15000",
                        outputVar = "SOURCE"
                    ),
                    ConfigBlock(
                        type = BlockType.CAPTCHA_TURNSTILE,
                        label = "Solve Turnstile",
                        inputTarget = "0x4AAAAAAA_SITEKEY",
                        secondaryParam = "2Captcha",
                        tertiaryParam = "https://example.com/checkout|{cap:solver_key}",
                        outputVar = "cf_token"
                    ),
                    ConfigBlock(
                        type = BlockType.REQUEST_HTTP,
                        label = "Submit Tokenized Card",
                        inputTarget = "https://example.com/api/verify",
                        secondaryParam = "POST_JSON",
                        tertiaryParam = """{"card":"{card}","mm":"{mm}","yyyy":"{yyyy}","cvv":"{cvv}","cf":"{var:cf_token}"}""",
                        outputVar = "SOURCE"
                    ),
                    ConfigBlock(
                        type = BlockType.CONDITION_KEYCHECK,
                        label = "Verify Success",
                        inputTarget = "<SOURCE>",
                        secondaryParam = "CONTAINS_ANY",
                        tertiaryParam = "approved,succeeded",
                        verdictOnMatch = ResultBucket.LIVE
                    )
                )
            ),
            BlockRecipeTemplate(
                id = "recipe_selenium_flow",
                name = "Selenium WebDriver Flow",
                description = "Automates Chrome WebDriver: navigate, type card fields, click submit, extract cookies",
                blocks = listOf(
                    ConfigBlock(
                        type = BlockType.SELENIUM_OPEN,
                        label = "Open ChromeDriver",
                        inputTarget = "ChromeHeadless",
                        secondaryParam = "--disable-gpu --window-size=1280,720",
                        outputVar = "webdriver_id"
                    ),
                    ConfigBlock(
                        type = BlockType.SELENIUM_NAVIGATE,
                        label = "Open Portal",
                        inputTarget = "https://example.com/portal",
                        secondaryParam = "20",
                        outputVar = "SOURCE"
                    ),
                    ConfigBlock(
                        type = BlockType.SELENIUM_SEND_KEYS,
                        label = "Type Card PAN",
                        inputTarget = "#card-number",
                        secondaryParam = "{card}",
                        tertiaryParam = "true"
                    ),
                    ConfigBlock(
                        type = BlockType.SELENIUM_CLICK,
                        label = "Click Submit",
                        inputTarget = "button[type=submit]",
                        secondaryParam = "CSS"
                    ),
                    ConfigBlock(
                        type = BlockType.SELENIUM_GET_COOKIES,
                        label = "Capture Cookies",
                        inputTarget = "*",
                        outputVar = "sel_cookies",
                        captureToOutput = true
                    ),
                    ConfigBlock(
                        type = BlockType.SELENIUM_QUIT,
                        label = "Quit Driver",
                        inputTarget = "QUIT"
                    )
                )
            ),
            BlockRecipeTemplate(
                id = "recipe_interop_webhook",
                name = "Interop Webhook + Script",
                description = "Formats output via script expression, dispatches webhook push, and appends log note",
                blocks = listOf(
                    ConfigBlock(
                        type = BlockType.FUNC_BASE64_ENCODE,
                        label = "Encode Lista",
                        inputTarget = "{lista}",
                        outputVar = "b64_lista"
                    ),
                    ConfigBlock(
                        type = BlockType.INTEROP_SCRIPT,
                        label = "Build Tag",
                        inputTarget = "{scheme_lower}:{bin}:{pan4}",
                        secondaryParam = "Expression",
                        outputVar = "card_tag",
                        captureToOutput = true
                    ),
                    ConfigBlock(
                        type = BlockType.INTEROP_WEBHOOK_POST,
                        label = "Push to Companion API",
                        inputTarget = "https://httpbin.org/post",
                        secondaryParam = """{"tag":"{var:card_tag}","b64":"{var:b64_lista}"}""",
                        outputVar = "webhook_resp"
                    ),
                    ConfigBlock(
                        type = BlockType.UTIL_FILE_APPEND,
                        label = "Record Note",
                        inputTarget = "{card_tag} -> {var:webhook_resp}",
                        outputVar = "output_note",
                        captureToOutput = true
                    )
                )
            )
        )
    }
}
