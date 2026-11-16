package com.example.domain

/**
 * Comprehensive developer payment sandbox registry covering official documented test cards
 * and deterministic gateway simulation across major global payment processors:
 * - Stripe Test Mode (`tok_visa`, `tok_mastercard`, `tok_amex`, `3DS2`, `insufficient_funds`, `incorrect_cvc`, `processing_error`)
 * - Braintree / PayPal Sandbox (`1000 Approved`, `2000 Do Not Honor`, `2001 Insufficient Funds`)
 * - Adyen Test Platform (`Authorised`, `Refused: 05 Do Not Honor`)
 * - Square Sandbox (`CAPTURED / AVS_ACCEPTED`, `CVV_FAILURE`, `GENERIC_DECLINE`)
 * - Authorize.Net & CyberSource (`1-1 Approved`, `100 ACCEPT`, `2-2 Declined`)
 * - Checkout.com & Worldpay (`10000 Approved`, `Worldpay AUTHORISED`, `20005 Do Not Honour`)
 * - HTTP Echo & Mock Gateways (`Payate Sandbox`, `Chkr Sandbox`, `HttpBin Echo`, `Postman Echo`)
 */
enum class SandboxProvider(
    val id: String,
    val displayName: String,
    val shortLabel: String,
    val subtitle: String
) {
    STRIPE(
        id = "stripe",
        displayName = "Stripe Sandbox",
        shortLabel = "Stripe",
        subtitle = "Official tok_visa, tok_mastercard, 3DS2, insufficient_funds, incorrect_cvc & processing_error vectors"
    ),
    BRAINTREE(
        id = "braintree",
        displayName = "Braintree / PayPal",
        shortLabel = "Braintree",
        subtitle = "Official 1000 Approved, 2000 Do Not Honor & 2001 Insufficient Funds test vectors"
    ),
    ADYEN(
        id = "adyen",
        displayName = "Adyen Test Platform",
        shortLabel = "Adyen",
        subtitle = "Official Adyen PSP Authorised Visa/MC/Amex & Refused 05 Do Not Honor test vectors"
    ),
    SQUARE(
        id = "square",
        displayName = "Square Sandbox",
        shortLabel = "Square",
        subtitle = "Official Square CAPTURED / AVS_ACCEPTED, CVV_FAILURE & GENERIC_DECLINE vectors"
    ),
    AUTHNET_CYBERSOURCE(
        id = "authnet",
        displayName = "Authorize.Net & CyberSource",
        shortLabel = "Auth.Net",
        subtitle = "Official Authorize.Net 1-1 Approved, CyberSource 100 ACCEPT & 2-2 Declined test vectors"
    ),
    CHECKOUT_WORLDPAY(
        id = "checkout",
        displayName = "Checkout.com & Worldpay",
        shortLabel = "Checkout",
        subtitle = "Official Checkout.com 10000 Approved, Worldpay AUTHORISED & 20005 Do Not Honour vectors"
    ),
    ALL_SANDBOXES(
        id = "all",
        displayName = "All Gateways Suite",
        shortLabel = "All Suite",
        subtitle = "Complete multi-gateway test suite combining Stripe, Braintree, Adyen, Square, Auth.Net & Checkout.com"
    )
}

data class SandboxTestVector(
    val provider: SandboxProvider,
    val pan: String,
    val defaultMm: String = "12",
    val defaultYyyy: String = "2029",
    val defaultCvv: String = "123",
    val scheme: CardScheme,
    val expectedBucket: ResultBucket,
    val gatewayCode: String,
    val description: String,
    val issuerBank: String,
    val cardType: String = "CREDIT",
    val cardBrandTier: String = "SANDBOX TEST",
    val countryName: String = "United States",
    val countryCode: String = "US",
    val currency: String = "USD"
) {
    fun toPipeLine(): String = "$pan|$defaultMm|$defaultYyyy|$defaultCvv"
}

data class HttpSandboxEndpointPreset(
    val id: String,
    val label: String,
    val url: String,
    val method: HttpRequestMethod,
    val bodyTemplate: String,
    val liveKeywords: String,
    val dieKeywords: String,
    val description: String
)

object SandboxTestRegistry {

    val allVectors: List<SandboxTestVector> = listOf(
        // 1. Stripe Sandbox Official Test Cards
        SandboxTestVector(
            provider = SandboxProvider.STRIPE,
            pan = "4242424242424242",
            defaultMm = "12",
            defaultYyyy = "2029",
            defaultCvv = "242",
            scheme = CardScheme.VISA,
            expectedBucket = ResultBucket.LIVE,
            gatewayCode = "tok_visa • 200 OK",
            description = "Stripe Test Visa (Authorized)",
            issuerBank = "STRIPE SANDBOX ISSUER",
            cardBrandTier = "CLASSIC TEST"
        ),
        SandboxTestVector(
            provider = SandboxProvider.STRIPE,
            pan = "5555555555554444",
            defaultMm = "11",
            defaultYyyy = "2029",
            defaultCvv = "544",
            scheme = CardScheme.MASTERCARD,
            expectedBucket = ResultBucket.LIVE,
            gatewayCode = "tok_mastercard • 200 OK",
            description = "Stripe Test Mastercard (Authorized)",
            issuerBank = "STRIPE SANDBOX ISSUER",
            cardBrandTier = "WORLD TEST"
        ),
        SandboxTestVector(
            provider = SandboxProvider.STRIPE,
            pan = "378282246310005",
            defaultMm = "10",
            defaultYyyy = "2029",
            defaultCvv = "1005",
            scheme = CardScheme.AMEX,
            expectedBucket = ResultBucket.LIVE,
            gatewayCode = "tok_amex • 200 OK",
            description = "Stripe Test Amex (Authorized)",
            issuerBank = "STRIPE AMEX SANDBOX",
            cardBrandTier = "CORPORATE TEST"
        ),
        SandboxTestVector(
            provider = SandboxProvider.STRIPE,
            pan = "6011111111111117",
            defaultMm = "09",
            defaultYyyy = "2029",
            defaultCvv = "117",
            scheme = CardScheme.DISCOVER,
            expectedBucket = ResultBucket.LIVE,
            gatewayCode = "tok_discover • 200 OK",
            description = "Stripe Test Discover (Authorized)",
            issuerBank = "STRIPE DISCOVER SANDBOX",
            cardBrandTier = "IT TEST"
        ),
        SandboxTestVector(
            provider = SandboxProvider.STRIPE,
            pan = "3566002020360505",
            defaultMm = "08",
            defaultYyyy = "2029",
            defaultCvv = "505",
            scheme = CardScheme.JCB,
            expectedBucket = ResultBucket.LIVE,
            gatewayCode = "tok_jcb • 200 OK",
            description = "Stripe Test JCB (Authorized)",
            issuerBank = "STRIPE JCB SANDBOX",
            countryName = "Japan",
            countryCode = "JP",
            currency = "JPY"
        ),
        SandboxTestVector(
            provider = SandboxProvider.STRIPE,
            pan = "30569309025904",
            defaultMm = "07",
            defaultYyyy = "2029",
            defaultCvv = "904",
            scheme = CardScheme.DINERS,
            expectedBucket = ResultBucket.LIVE,
            gatewayCode = "tok_diners • 200 OK",
            description = "Stripe Test Diners Club (Authorized)",
            issuerBank = "STRIPE DINERS SANDBOX"
        ),
        SandboxTestVector(
            provider = SandboxProvider.STRIPE,
            pan = "6200000000000005",
            defaultMm = "06",
            defaultYyyy = "2029",
            defaultCvv = "005",
            scheme = CardScheme.UNIONPAY,
            expectedBucket = ResultBucket.LIVE,
            gatewayCode = "tok_unionpay • 200 OK",
            description = "Stripe Test UnionPay (Authorized)",
            issuerBank = "STRIPE UNIONPAY SANDBOX",
            countryName = "China",
            countryCode = "CN",
            currency = "CNY"
        ),
        SandboxTestVector(
            provider = SandboxProvider.STRIPE,
            pan = "4000002760003184",
            defaultMm = "12",
            defaultYyyy = "2029",
            defaultCvv = "318",
            scheme = CardScheme.VISA,
            expectedBucket = ResultBucket.LIVE,
            gatewayCode = "tok_3ds2 • Authenticated",
            description = "Stripe 3DS2 Frictionless Auth",
            issuerBank = "STRIPE 3DS2 SANDBOX",
            cardBrandTier = "SIGNATURE 3DS2"
        ),
        SandboxTestVector(
            provider = SandboxProvider.STRIPE,
            pan = "4000000000009995",
            defaultMm = "12",
            defaultYyyy = "2029",
            defaultCvv = "995",
            scheme = CardScheme.VISA,
            expectedBucket = ResultBucket.DIE,
            gatewayCode = "card_declined • insufficient_funds",
            description = "Stripe Decline (Insufficient Funds)",
            issuerBank = "STRIPE SANDBOX ISSUER"
        ),
        SandboxTestVector(
            provider = SandboxProvider.STRIPE,
            pan = "4000000000000069",
            defaultMm = "12",
            defaultYyyy = "2029",
            defaultCvv = "069",
            scheme = CardScheme.VISA,
            expectedBucket = ResultBucket.DIE,
            gatewayCode = "card_declined • generic_decline",
            description = "Stripe Decline (Generic Decline)",
            issuerBank = "STRIPE SANDBOX ISSUER"
        ),
        SandboxTestVector(
            provider = SandboxProvider.STRIPE,
            pan = "4000000000000127",
            defaultMm = "12",
            defaultYyyy = "2029",
            defaultCvv = "127",
            scheme = CardScheme.VISA,
            expectedBucket = ResultBucket.DIE,
            gatewayCode = "card_declined • incorrect_cvc",
            description = "Stripe Decline (Incorrect CVC)",
            issuerBank = "STRIPE SANDBOX ISSUER"
        ),
        SandboxTestVector(
            provider = SandboxProvider.STRIPE,
            pan = "4000000000000341",
            defaultMm = "12",
            defaultYyyy = "2029",
            defaultCvv = "341",
            scheme = CardScheme.VISA,
            expectedBucket = ResultBucket.DIE,
            gatewayCode = "card_declined • expired_card",
            description = "Stripe Decline (Expired Card Trigger)",
            issuerBank = "STRIPE SANDBOX ISSUER"
        ),
        SandboxTestVector(
            provider = SandboxProvider.STRIPE,
            pan = "4000000000000119",
            defaultMm = "12",
            defaultYyyy = "2029",
            defaultCvv = "119",
            scheme = CardScheme.VISA,
            expectedBucket = ResultBucket.DIE,
            gatewayCode = "card_declined • lost_card",
            description = "Stripe Decline (Lost Card)",
            issuerBank = "STRIPE SANDBOX ISSUER"
        ),
        SandboxTestVector(
            provider = SandboxProvider.STRIPE,
            pan = "4000000000000259",
            defaultMm = "12",
            defaultYyyy = "2029",
            defaultCvv = "259",
            scheme = CardScheme.VISA,
            expectedBucket = ResultBucket.UNKNOWN,
            gatewayCode = "api_error • processing_error",
            description = "Stripe Transient Processing Error",
            issuerBank = "STRIPE SANDBOX ISSUER"
        ),

        // 2. Braintree / PayPal Sandbox Official Test Cards
        SandboxTestVector(
            provider = SandboxProvider.BRAINTREE,
            pan = "4111111111111111",
            defaultMm = "12",
            defaultYyyy = "2029",
            defaultCvv = "111",
            scheme = CardScheme.VISA,
            expectedBucket = ResultBucket.LIVE,
            gatewayCode = "Braintree 1000 • Approved",
            description = "Braintree Visa Sandbox Approved",
            issuerBank = "BRAINTREE PAYPAL SANDBOX",
            cardBrandTier = "PREMIER TEST"
        ),
        SandboxTestVector(
            provider = SandboxProvider.BRAINTREE,
            pan = "5454545454545454",
            defaultMm = "11",
            defaultYyyy = "2029",
            defaultCvv = "454",
            scheme = CardScheme.MASTERCARD,
            expectedBucket = ResultBucket.LIVE,
            gatewayCode = "Braintree 1000 • Approved",
            description = "Braintree Mastercard Sandbox Approved",
            issuerBank = "BRAINTREE PAYPAL SANDBOX"
        ),
        SandboxTestVector(
            provider = SandboxProvider.BRAINTREE,
            pan = "371449635398431",
            defaultMm = "10",
            defaultYyyy = "2029",
            defaultCvv = "8431",
            scheme = CardScheme.AMEX,
            expectedBucket = ResultBucket.LIVE,
            gatewayCode = "Braintree 1000 • Approved",
            description = "Braintree Amex Sandbox Approved",
            issuerBank = "BRAINTREE AMEX SANDBOX"
        ),
        SandboxTestVector(
            provider = SandboxProvider.BRAINTREE,
            pan = "6011000990139424",
            defaultMm = "09",
            defaultYyyy = "2029",
            defaultCvv = "424",
            scheme = CardScheme.DISCOVER,
            expectedBucket = ResultBucket.LIVE,
            gatewayCode = "Braintree 1000 • Approved",
            description = "Braintree Discover Sandbox Approved",
            issuerBank = "BRAINTREE DISCOVER SANDBOX"
        ),
        SandboxTestVector(
            provider = SandboxProvider.BRAINTREE,
            pan = "4000000000001026",
            defaultMm = "12",
            defaultYyyy = "2029",
            defaultCvv = "026",
            scheme = CardScheme.VISA,
            expectedBucket = ResultBucket.DIE,
            gatewayCode = "Braintree 2000 • Do Not Honor",
            description = "Braintree Processor Decline (2000)",
            issuerBank = "BRAINTREE PAYPAL SANDBOX"
        ),
        SandboxTestVector(
            provider = SandboxProvider.BRAINTREE,
            pan = "4000000000002008",
            defaultMm = "12",
            defaultYyyy = "2029",
            defaultCvv = "008",
            scheme = CardScheme.VISA,
            expectedBucket = ResultBucket.DIE,
            gatewayCode = "Braintree 2001 • Insufficient Funds",
            description = "Braintree Processor Decline (2001)",
            issuerBank = "BRAINTREE PAYPAL SANDBOX"
        ),

        // 3. Adyen Test Platform Official Test Cards
        SandboxTestVector(
            provider = SandboxProvider.ADYEN,
            pan = "4444333322221111",
            defaultMm = "03",
            defaultYyyy = "2030",
            defaultCvv = "737",
            scheme = CardScheme.VISA,
            expectedBucket = ResultBucket.LIVE,
            gatewayCode = "Adyen Authorised • 00",
            description = "Adyen Test Visa (Authorised)",
            issuerBank = "ADYEN NV TEST ISSUER",
            countryName = "Netherlands",
            countryCode = "NL",
            currency = "EUR"
        ),
        SandboxTestVector(
            provider = SandboxProvider.ADYEN,
            pan = "5100080000000000",
            defaultMm = "03",
            defaultYyyy = "2030",
            defaultCvv = "737",
            scheme = CardScheme.MASTERCARD,
            expectedBucket = ResultBucket.LIVE,
            gatewayCode = "Adyen Authorised • 00",
            description = "Adyen Test Mastercard (Authorised)",
            issuerBank = "ADYEN NV TEST ISSUER",
            countryName = "Netherlands",
            countryCode = "NL",
            currency = "EUR"
        ),
        SandboxTestVector(
            provider = SandboxProvider.ADYEN,
            pan = "370000000000002",
            defaultMm = "03",
            defaultYyyy = "2030",
            defaultCvv = "7373",
            scheme = CardScheme.AMEX,
            expectedBucket = ResultBucket.LIVE,
            gatewayCode = "Adyen Authorised • 00",
            description = "Adyen Test Amex (Authorised)",
            issuerBank = "ADYEN NV TEST ISSUER",
            countryName = "Netherlands",
            countryCode = "NL",
            currency = "EUR"
        ),
        SandboxTestVector(
            provider = SandboxProvider.ADYEN,
            pan = "4929000000006",
            defaultMm = "03",
            defaultYyyy = "2030",
            defaultCvv = "737",
            scheme = CardScheme.VISA,
            expectedBucket = ResultBucket.DIE,
            gatewayCode = "Adyen Refused • 05 Do Not Honor",
            description = "Adyen Refused Test Vector",
            issuerBank = "ADYEN NV TEST ISSUER",
            countryName = "United Kingdom",
            countryCode = "GB",
            currency = "GBP"
        ),

        // 4. Square Sandbox Official Test Cards
        SandboxTestVector(
            provider = SandboxProvider.SQUARE,
            pan = "4532015500164667",
            defaultMm = "12",
            defaultYyyy = "2029",
            defaultCvv = "667",
            scheme = CardScheme.VISA,
            expectedBucket = ResultBucket.LIVE,
            gatewayCode = "Square CAPTURED • AVS_ACCEPTED",
            description = "Square Sandbox Visa Approved",
            issuerBank = "SQUARE BLOCK SANDBOX"
        ),
        SandboxTestVector(
            provider = SandboxProvider.SQUARE,
            pan = "5409886734157306",
            defaultMm = "12",
            defaultYyyy = "2029",
            defaultCvv = "306",
            scheme = CardScheme.MASTERCARD,
            expectedBucket = ResultBucket.LIVE,
            gatewayCode = "Square CAPTURED • AVS_ACCEPTED",
            description = "Square Sandbox Mastercard Approved",
            issuerBank = "SQUARE BLOCK SANDBOX"
        ),
        SandboxTestVector(
            provider = SandboxProvider.SQUARE,
            pan = "4024007140623397",
            defaultMm = "12",
            defaultYyyy = "2029",
            defaultCvv = "397",
            scheme = CardScheme.VISA,
            expectedBucket = ResultBucket.DIE,
            gatewayCode = "Square DECLINED • CVV_FAILURE",
            description = "Square Sandbox CVV Failure",
            issuerBank = "SQUARE BLOCK SANDBOX"
        ),
        SandboxTestVector(
            provider = SandboxProvider.SQUARE,
            pan = "5105105105105100",
            defaultMm = "12",
            defaultYyyy = "2029",
            defaultCvv = "100",
            scheme = CardScheme.MASTERCARD,
            expectedBucket = ResultBucket.DIE,
            gatewayCode = "Square DECLINED • GENERIC_DECLINE",
            description = "Square Sandbox Generic Decline",
            issuerBank = "SQUARE BLOCK SANDBOX"
        ),

        // 5. Authorize.Net & CyberSource Official Test Cards
        SandboxTestVector(
            provider = SandboxProvider.AUTHNET_CYBERSOURCE,
            pan = "4007000000027",
            defaultMm = "12",
            defaultYyyy = "2029",
            defaultCvv = "027",
            scheme = CardScheme.VISA,
            expectedBucket = ResultBucket.LIVE,
            gatewayCode = "Auth.Net 1-1 • Approved",
            description = "Authorize.Net Visa 13-Digit Approved",
            issuerBank = "VISA CYBERSOURCE SANDBOX"
        ),
        SandboxTestVector(
            provider = SandboxProvider.AUTHNET_CYBERSOURCE,
            pan = "5424000000000015",
            defaultMm = "12",
            defaultYyyy = "2029",
            defaultCvv = "015",
            scheme = CardScheme.MASTERCARD,
            expectedBucket = ResultBucket.LIVE,
            gatewayCode = "CyberSource 100 • ACCEPT",
            description = "CyberSource Mastercard Authorized",
            issuerBank = "CYBERSOURCE SANDBOX ISSUER"
        ),
        SandboxTestVector(
            provider = SandboxProvider.AUTHNET_CYBERSOURCE,
            pan = "4222222222222",
            defaultMm = "12",
            defaultYyyy = "2029",
            defaultCvv = "222",
            scheme = CardScheme.VISA,
            expectedBucket = ResultBucket.DIE,
            gatewayCode = "Auth.Net 2-2 • Declined",
            description = "Authorize.Net Declined Vector",
            issuerBank = "VISA CYBERSOURCE SANDBOX"
        ),

        // 6. Checkout.com & Worldpay Official Test Cards
        SandboxTestVector(
            provider = SandboxProvider.CHECKOUT_WORLDPAY,
            pan = "4543474002399999",
            defaultMm = "12",
            defaultYyyy = "2029",
            defaultCvv = "999",
            scheme = CardScheme.VISA,
            expectedBucket = ResultBucket.LIVE,
            gatewayCode = "Checkout.com 10000 • Approved",
            description = "Checkout.com Visa Sandbox Approved",
            issuerBank = "CHECKOUT LTD SANDBOX",
            countryName = "United Kingdom",
            countryCode = "GB",
            currency = "GBP"
        ),
        SandboxTestVector(
            provider = SandboxProvider.CHECKOUT_WORLDPAY,
            pan = "5199990000000002",
            defaultMm = "12",
            defaultYyyy = "2029",
            defaultCvv = "002",
            scheme = CardScheme.MASTERCARD,
            expectedBucket = ResultBucket.LIVE,
            gatewayCode = "Worldpay AUTHORISED • 00",
            description = "Worldpay Mastercard Authorised",
            issuerBank = "WORLDPAY SANDBOX ISSUER",
            countryName = "United Kingdom",
            countryCode = "GB",
            currency = "GBP"
        ),
        SandboxTestVector(
            provider = SandboxProvider.CHECKOUT_WORLDPAY,
            pan = "4543474002390055",
            defaultMm = "12",
            defaultYyyy = "2029",
            defaultCvv = "055",
            scheme = CardScheme.VISA,
            expectedBucket = ResultBucket.DIE,
            gatewayCode = "Checkout.com 20005 • Do Not Honour",
            description = "Checkout.com Declined (20005)",
            issuerBank = "CHECKOUT LTD SANDBOX",
            countryName = "United Kingdom",
            countryCode = "GB",
            currency = "GBP"
        )
    )

    private val vectorByPan: Map<String, SandboxTestVector> = allVectors.associateBy { it.pan }

    val httpSandboxPresets: List<HttpSandboxEndpointPreset> = listOf(
        HttpSandboxEndpointPreset(
            id = "payate_sandbox",
            label = "Payate Sandbox",
            url = "https://mock.payate.com/api.php",
            method = HttpRequestMethod.POST_JSON,
            bodyTemplate = """{"lista":"{lista}"}""",
            liveKeywords = CustomHttpConfig.DEFAULT_LIVE_KEYWORDS,
            dieKeywords = CustomHttpConfig.DEFAULT_DIE_KEYWORDS,
            description = "Payate JSON batch sandbox endpoint"
        ),
        HttpSandboxEndpointPreset(
            id = "chkr_sandbox",
            label = "Chkr Sandbox",
            url = "https://api.chkr.cc/",
            method = HttpRequestMethod.POST_JSON,
            bodyTemplate = """{"data":"{lista}"}""",
            liveKeywords = CustomHttpConfig.DEFAULT_LIVE_KEYWORDS,
            dieKeywords = CustomHttpConfig.DEFAULT_DIE_KEYWORDS,
            description = "Chkr.cc JSON card verification endpoint"
        ),
        HttpSandboxEndpointPreset(
            id = "httpbin_echo",
            label = "HttpBin Echo",
            url = "https://httpbin.org/post",
            method = HttpRequestMethod.POST_JSON,
            bodyTemplate = CustomHttpConfig.DEFAULT_JSON_PAYLOAD,
            liveKeywords = "httpbin.org, \"card\", 200, live",
            dieKeywords = "declined, error, invalid",
            description = "Inspect JSON/Form payload & headers via httpbin.org"
        ),
        HttpSandboxEndpointPreset(
            id = "postman_echo",
            label = "Postman Echo",
            url = "https://postman-echo.com/post",
            method = HttpRequestMethod.POST_JSON,
            bodyTemplate = CustomHttpConfig.DEFAULT_JSON_PAYLOAD,
            liveKeywords = "postman-echo.com, \"card\", 200, live",
            dieKeywords = "declined, error, invalid",
            description = "Echo test for Custom HTTP body & header templates"
        )
    )

    fun vectorsForProvider(provider: SandboxProvider): List<SandboxTestVector> {
        return if (provider == SandboxProvider.ALL_SANDBOXES) {
            allVectors
        } else {
            allVectors.filter { it.provider == provider }
        }
    }

    fun buildBatchText(provider: SandboxProvider): String {
        return vectorsForProvider(provider).joinToString("\n") { it.toPipeLine() }
    }

    fun findVectorByPan(pan: String): SandboxTestVector? {
        val clean = pan.filter { it.isDigit() }
        return vectorByPan[clean]
    }

    /**
     * Evaluates a parsed card line against the Multi-Gateway Sandbox Suite.
     * - Known official test vectors from Stripe, Braintree, Adyen, Square, Authorize.Net,
     *   CyberSource, Checkout.com, and Worldpay return their exact documented gateway response
     *   (`tok_visa • 200 OK`, `card_declined • insufficient_funds`, `Braintree 1000 • Approved`, etc.)
     *   and pre-populate the BIN cache with sandbox issuer telemetry.
     * - Other Luhn-valid cards are evaluated with deterministic sandbox rules:
     *   - CVV `000` -> `DIE` (`card_declined • incorrect_cvc`)
     *   - CVV `999` or `9999` -> `UNKNOWN` (`api_error • processing_error`)
     *   - PAN ending in `0000` -> `DIE` (`card_declined • do_not_honor`)
     *   - Otherwise -> `LIVE` (`tok_${scheme} • Sandbox 200 OK`)
     */
    suspend fun evaluateSandboxCard(
        pan: String,
        mm: String,
        yyyy: String,
        cvv: String,
        scheme: CardScheme
    ): Pair<ResultBucket, String> {
        val cleanPan = pan.filter { it.isDigit() }
        val known = vectorByPan[cleanPan]
        if (known != null) {
            val bin6 = BinLookupService.extractBin6(cleanPan)
            if (bin6.length == 6) {
                BinLookupService.cacheVerifiedBin(
                    BinInfo(
                        bin = bin6,
                        scheme = known.scheme.displayName,
                        type = known.cardType,
                        brand = known.cardBrandTier,
                        bankName = known.issuerBank,
                        countryName = known.countryName,
                        countryCode = known.countryCode,
                        countryEmoji = countryCodeToFlagEmoji(known.countryCode),
                        currency = known.currency,
                        isPrepaid = false,
                        isNetworkVerified = true
                    )
                )
            }
            val label = "${known.provider.shortLabel} • ${known.gatewayCode} (${known.description})"
            return known.expectedBucket to label
        }

        // Deterministic sandbox rules for custom Luhn-valid test cards
        return when {
            cvv == "000" || cvv == "0000" ->
                ResultBucket.DIE to "Sandbox • card_declined: incorrect_cvc (CVV $cvv)"
            cvv == "999" || cvv == "9999" ->
                ResultBucket.UNKNOWN to "Sandbox • api_error: processing_error (CVV $cvv trigger)"
            cleanPan.endsWith("0000") ->
                ResultBucket.DIE to "Sandbox • 2000 Do Not Honor (0000 suffix trigger)"
            else -> {
                val tokSlug = scheme.name.lowercase()
                ResultBucket.LIVE to "Sandbox • tok_$tokSlug • 200 OK (${scheme.displayName} $mm/${yyyy.takeLast(2)})"
            }
        }
    }

    private fun countryCodeToFlagEmoji(countryCode: String): String {
        val clean = countryCode.trim().uppercase()
        if (clean.length != 2 || !clean[0].isLetter() || !clean[1].isLetter()) return ""
        val first = Character.codePointAt(clean, 0) - 0x41 + 0x1F1E6
        val second = Character.codePointAt(clean, 1) - 0x41 + 0x1F1E6
        return String(Character.toChars(first)) + String(Character.toChars(second))
    }
}
