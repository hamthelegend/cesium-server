package com.thebrownfoxx.totp.logic

import com.thebrownfoxx.models.totp.Base32

fun generateTotpSecret(): Base32 {
    val allowedCharacters = ('a'..'z') + ('A'..'Z') + (0..9)
    val plainText = Array(10) { allowedCharacters.random() }.toString()
    return plainText.toBase32()
}