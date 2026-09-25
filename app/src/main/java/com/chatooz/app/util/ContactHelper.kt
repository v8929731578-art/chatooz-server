package com.chatooz.app.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import com.chatooz.app.model.User
import kotlinx.serialization.Serializable

@Serializable
data class DeviceContact(
    val id: String,
    val name: String,
    val rawPhone: String,
    val normalizedPhone: String,
    var isOnChatooz: Boolean = false,
    var matchedUser: User? = null
)

object ContactHelper {

    val DEFAULT_INVITE_URL: String get() = "${com.chatooz.app.data.AppConfig.apiBaseUrl}/download"

    fun normalizePhoneNumber(phone: String): String {
        val digitsOnly = phone.replace(Regex("[^0-9+]"), "")
        return when {
            digitsOnly.startsWith("+91") && digitsOnly.length == 13 -> digitsOnly.substring(3)
            digitsOnly.startsWith("91") && digitsOnly.length == 12 -> digitsOnly.substring(2)
            digitsOnly.startsWith("0") && digitsOnly.length == 11 -> digitsOnly.substring(1)
            else -> digitsOnly
        }
    }

    fun fetchDeviceContacts(context: Context): List<DeviceContact> {
        val contacts = mutableListOf<DeviceContact>()
        val seenNumbers = mutableSetOf<String>()

        try {
            val contentResolver = context.contentResolver
            val cursor = contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                null,
                null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
            )

            cursor?.use { c ->
                val idIndex = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val nameIndex = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                while (c.moveToNext()) {
                    val id = if (idIndex >= 0) c.getString(idIndex) ?: "" else ""
                    val name = if (nameIndex >= 0) c.getString(nameIndex) ?: "Contact" else "Contact"
                    val number = if (numberIndex >= 0) c.getString(numberIndex) ?: "" else ""

                    if (number.isNotBlank()) {
                        val normalized = normalizePhoneNumber(number)
                        if (normalized.isNotBlank() && !seenNumbers.contains(normalized)) {
                            seenNumbers.add(normalized)
                            contacts.add(
                                DeviceContact(
                                    id = id,
                                    name = name,
                                    rawPhone = number,
                                    normalizedPhone = normalized
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return contacts.sortedBy { it.name.lowercase() }
    }

    fun shareApp(context: Context, customMessage: String? = null) {
        val msg = customMessage ?: "Hey! I'm using Chatooz — Fast, secure messaging with HD Voice & Video calls! 🚀\n\nDownload the app here: $DEFAULT_INVITE_URL"
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, msg)
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, "Invite friends to Chatooz")
        shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(shareIntent)
    }

    fun inviteSingleContact(context: Context, contact: DeviceContact) {
        val msg = "Hey ${contact.name}! Join me on Chatooz — Fast & secure chat with free HD calls! 🚀\n\nDownload link: $DEFAULT_INVITE_URL"
        try {
            val smsIntent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("sms:${contact.rawPhone}")
                putExtra("sms_body", msg)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(smsIntent)
        } catch (_: Exception) {
            // Fallback to general share
            shareApp(context, msg)
        }
    }
}
