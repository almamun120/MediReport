package com.medireport.portal.util

import com.medireport.portal.data.SmtpConfig
import java.io.File
import java.util.Properties
import javax.activation.DataHandler
import javax.activation.FileDataSource
import javax.mail.Authenticator
import javax.mail.Message
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart

object Mailer {
    /** Blocking – call from a background thread. Throws with a readable message on failure. */
    fun send(
        cfg: SmtpConfig,
        to: List<String>,
        subject: String,
        body: String,
        attachment: File? = null,
        attachmentName: String? = null,
        attachmentMime: String = "application/pdf"
    ) {
        require(cfg.isComplete) { "Mail server is not configured" }
        require(to.isNotEmpty()) { "No recipient" }

        // JavaMail looks up its handlers through the context class loader
        Thread.currentThread().contextClassLoader = Session::class.java.classLoader

        val props = Properties()
        props["mail.smtp.host"] = cfg.host
        props["mail.smtp.port"] = cfg.port.toString()
        props["mail.smtp.connectiontimeout"] = "20000"
        props["mail.smtp.timeout"] = "30000"
        props["mail.smtp.writetimeout"] = "30000"
        when (cfg.security) {
            "SSL" -> props["mail.smtp.ssl.enable"] = "true"
            "STARTTLS" -> {
                props["mail.smtp.starttls.enable"] = "true"
                props["mail.smtp.starttls.required"] = "true"
            }
        }
        val useAuth = cfg.username.isNotBlank()
        props["mail.smtp.auth"] = useAuth.toString()

        val session = if (useAuth) {
            Session.getInstance(props, object : Authenticator() {
                override fun getPasswordAuthentication() = PasswordAuthentication(cfg.username, cfg.password)
            })
        } else Session.getInstance(props)

        val msg = MimeMessage(session)
        msg.setFrom(InternetAddress(cfg.fromEmail, cfg.fromName.ifBlank { null }, "UTF-8"))
        msg.setRecipients(Message.RecipientType.TO, to.map { InternetAddress(it.trim()) }.toTypedArray())
        msg.setSubject(subject, "UTF-8")

        val multipart = MimeMultipart()
        val text = MimeBodyPart()
        text.setText(body, "UTF-8")
        multipart.addBodyPart(text)
        if (attachment != null && attachment.exists()) {
            val part = MimeBodyPart()
            val ds = object : FileDataSource(attachment) {
                override fun getContentType(): String = attachmentMime
            }
            part.dataHandler = DataHandler(ds)
            part.fileName = attachmentName ?: attachment.name
            multipart.addBodyPart(part)
        }
        msg.setContent(multipart)
        Transport.send(msg)
    }

    fun isValidEmail(s: String) = android.util.Patterns.EMAIL_ADDRESS.matcher(s.trim()).matches()

    /** Replaces {patient}, {clinic}, {lab_id}, {report} in a template. */
    fun fill(template: String, vars: Map<String, String>): String {
        var out = template
        vars.forEach { (k, v) -> out = out.replace("{$k}", v) }
        return out
    }
}
