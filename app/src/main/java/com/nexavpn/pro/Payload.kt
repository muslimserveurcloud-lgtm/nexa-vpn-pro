package com.nexavpn.pro

/** Remplace les balises de payload façon HTTP Custom. */
fun expandPayload(p: String, host: String, port: Int): String = p
    .replace("[crlf]", "\r\n")
    .replace("[cr]", "\r")
    .replace("[lf]", "\n")
    .replace("[host_port]", "$host:$port")
    .replace("[host]", host)
    .replace("[port]", port.toString())
    .replace("[protocol]", "HTTP/1.1")
    .replace("[ua]", "Mozilla/5.0 (Linux; Android 13)")
