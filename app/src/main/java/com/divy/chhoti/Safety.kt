package com.divy.chhoti

object Safety {
    // Verification codes, OTPs and password resets are hidden before anything reaches the AI,
    // so an instruction hidden in some message can't make Chhoti read out your login codes.
    private val sensitive = Regex(
        "(verification code|verify your|one[- ]time (password|code|passcode)|\\bOTP\\b|security code|" +
            "sign[- ]?in code|log[- ]?in code|login attempt|password reset|reset your password|" +
            "2-step|two[- ]factor|\\b2FA\\b|do not share (this|the) code|is your .{0,20}code)",
        RegexOption.IGNORE_CASE
    )

    fun isSensitive(text: String) = sensitive.containsMatchIn(text)

    fun redact(text: String): String = if (isSensitive(text)) "[security / verification message hidden]" else text
}
