package com.offlinesync.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri // Added for SIM contacts
import android.provider.ContactsContract
import android.telephony.SubscriptionManager // Added for dual SIM
import android.telephony.SubscriptionInfo // Added for dual SIM
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable // Added for JSON serialization
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContactManager @Inject constructor(@ApplicationContext private val context: Context) {

    private val _contactsPermissionGranted = MutableStateFlow(false)
    val contactsPermissionGranted: StateFlow<Boolean> = _contactsPermissionGranted

    private val _phoneStatePermissionGranted = MutableStateFlow(false)
    val phoneStatePermissionGranted: StateFlow<Boolean> = _phoneStatePermissionGranted

    init {
        _contactsPermissionGranted.value = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
        _phoneStatePermissionGranted.value = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
    }


    fun hasContactsPermission(): Boolean {
        return _contactsPermissionGranted.value
    }

    fun hasPhoneStatePermission(): Boolean {
        return _phoneStatePermissionGranted.value
    }

    fun getDeviceContacts(): List<Contact> {
        val contacts = mutableListOf<Contact>()
        val contentResolver = context.contentResolver

        val projection = arrayOf(
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY
        )

        try {
            contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                projection,
                null,
                null,
                null
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndex(ContactsContract.Contacts._ID)
                val nameColumn = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)

                while (cursor.moveToNext()) {
                    val contactId = cursor.getString(idColumn)
                    val contactName = cursor.getString(nameColumn)

                    val phoneNumbers = mutableListOf<String>()
                    val emailAddresses = mutableListOf<String>()

                    // Get phone numbers
                    contentResolver.query(
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        null,
                        ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                        arrayOf(contactId),
                        null
                    )?.use { phoneCursor ->
                        val phoneColumn = phoneCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                        while (phoneCursor.moveToNext()) {
                            phoneNumbers.add(phoneCursor.getString(phoneColumn))
                        }
                    }

                    // Get email addresses
                    contentResolver.query(
                        ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                        null,
                        ContactsContract.CommonDataKinds.Email.CONTACT_ID + " = ?",
                        arrayOf(contactId),
                        null
                    )?.use { emailCursor ->
                        val emailColumn = emailCursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.ADDRESS)
                        while (emailCursor.moveToNext()) {
                            emailAddresses.add(emailCursor.getString(emailColumn))
                        }
                    }
                    contacts.add(Contact(contactName, phoneNumbers, emailAddresses, "DEVICE"))
                }
            }
        } catch (e: IllegalArgumentException) {
            // This can happen if an account is in an "UNSPECIFIED" state, as seen in the crash log
            e.printStackTrace()
        } catch (e: SecurityException) {
            // Handle cases where READ_CONTACTS permission might not be enough or other security restrictions
            e.printStackTrace()
        } catch (e: Exception) {
            // Catch any other unexpected exceptions
            e.printStackTrace()
        }
        return contacts
    }

    fun getSimContacts(): List<Contact> {
        val simContacts = mutableListOf<Contact>()
        val contentResolver = context.contentResolver

        if (!hasPhoneStatePermission()) {
            // READ_PHONE_STATE permission not granted, cannot access SubscriptionManager
            return emptyList()
        }

        val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
        val activeSubscriptions: List<SubscriptionInfo>? = if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            subscriptionManager.activeSubscriptionInfoList
        } else {
            null // Handle the case where permission is not granted
        }

        if (activeSubscriptions != null && activeSubscriptions.isNotEmpty()) {
            for (subscriptionInfo in activeSubscriptions) {
                val subId = subscriptionInfo.subscriptionId
                val simUri = Uri.parse("content://icc/adn/subId/$subId")
                val simOrigin = "SIM (${subscriptionInfo.displayName ?: "ID: $subId"})"

                try {
                    contentResolver.query(
                        simUri,
                        null, // Projection: null to get all available columns
                        null, // Selection: null to get all rows
                        null, // Selection arguments
                        null  // Sort order
                    )?.use { cursor ->
                        val nameColumnIndex = cursor.getColumnIndex("name")
                        val numberColumnIndex = cursor.getColumnIndex("number")

                        while (cursor.moveToNext()) {
                            val name = if (nameColumnIndex != -1) cursor.getString(nameColumnIndex) else "Unknown SIM Contact"
                            val number = if (numberColumnIndex != -1) cursor.getString(numberColumnIndex) else ""
                            simContacts.add(Contact(name, listOf(number), emptyList(), simOrigin))
                        }
                    }
                } catch (e: SecurityException) {
                    e.printStackTrace()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        return simContacts
    }
}

@Serializable // Added for JSON serialization
data class Contact(
    val name: String,
    val phoneNumbers: List<String>,
    val emails: List<String>,
    val origin: String // "SIM" or "DEVICE"
)

