package com.migrantbank.security

import java.security.MessageDigest

object TokenHash {
  def sha256Hex(s: String): String = {
    val md = MessageDigest.getInstance("SHA-256")
    md.digest(s.getBytes("UTF-8")).map("%02x".format(_)).mkString
  }
}
