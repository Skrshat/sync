package com.offlinesync.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.content.ContentProviderOperation
import android.content.ContentValues
import java.util.ArrayList
import android.provider.ContactsContract
import android.telephony.SubscriptionManager
import android.telephony.SubscriptionInfo
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContactManager @Inject constructor(@ApplicationContext private val context: Context) {

    private val _contactsPermissionGranted = MutableStateFlow(false)
    val contactsPermissionGranted: StateFlow<Boolean> = _contactsPermissionGranted

    private val _phoneStatePermissionGranted = MutableStateFlow(false)
    val phoneStatePermissionGranted: StateFlow<Boolean> = _phoneStatePermissionGranted

    private val _writeContactsPermissionGranted = MutableStateFlow(false)
    val writeContactsPermissionGranted: StateFlow<Boolean> = _writeContactsPermissionGranted

    init {
        _contactsPermissionGranted.value = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
        _phoneStatePermissionGranted.value = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
        _writeContactsPermissionGranted.value = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }

    // This method is now used by ViewModel to tell ContactManager the permission result
    // The actual permission request is initiated by the UI (Composable)
    fun updatePermissionStatus(permission: String, isGranted: Boolean) {
        when (permission) {
            Manifest.permission.READ_CONTACTS -> _contactsPermissionGranted.value = isGranted
            Manifest.permission.READ_PHONE_STATE -> _phoneStatePermissionGranted.value = isGranted
            Manifest.permission.WRITE_CONTACTS -> _writeContactsPermissionGranted.value = isGranted
        }
    }

    fun hasContactsPermission(): Boolean {
        return _contactsPermissionGranted.value
    }

    fun hasPhoneStatePermission(): Boolean {
        return _phoneStatePermissionGranted.value
    }

    fun hasWriteContactsPermission(): Boolean {
        return _writeContactsPermissionGranted.value
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
            e.printStackTrace()
        } catch (e: SecurityException) {
            e.printStackTrace()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return contacts
    }

    fun getSimContacts(): List<Contact> {
        val simContacts = mutableListOf<Contact>()
        val contentResolver = context.contentResolver

        if (!hasPhoneStatePermission()) {
            return emptyList()
        }

        val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
        val activeSubscriptions: List<SubscriptionInfo>? = if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            subscriptionManager.activeSubscriptionInfoList
        } else {
            null
        }

        if (activeSubscriptions != null && activeSubscriptions.isNotEmpty()) {
            for (subscriptionInfo in activeSubscriptions) {
                val subId = subscriptionInfo.subscriptionId
                val simUri = Uri.parse("content://icc/adn/subId/$subId")
                val simOrigin = "SIM (${subscriptionInfo.displayName ?: "ID: $subId"})"

                try {
                    contentResolver.query(
                        simUri,
                        null,
                        null,
                        null,
                        null
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

    fun getSimSubscriptions(): List<Pair<Int, String>> {
        val subscriptions = mutableListOf<Pair<Int, String>>()
        if (!hasPhoneStatePermission()) {
            return subscriptions
        }

        val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
        val activeSubscriptions = if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            subscriptionManager.activeSubscriptionInfoList
        } else {
            null
        }

        activeSubscriptions?.forEach { subInfo ->
            subscriptions.add(subInfo.subscriptionId to (subInfo.displayName?.toString() ?: "SIM ${subInfo.simSlotIndex + 1}"))
        }
        return subscriptions
    }

    fun writeContactsToDevice(contacts: List<Contact>): Int {
        if (!hasWriteContactsPermission()) {
            return 0 // Permission not granted
        }

        val ops = ArrayList<ContentProviderOperation>()
        var insertedCount = 0

        for (contact in contacts) {
            val rawContactId = ops.size // Index for the new raw contact

            // Add new raw contact
            ops.add(ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                .build())

            // Add display name
            ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, contact.name)
                .build())

            // Add phone numbers
            for (phoneNumber in contact.phoneNumbers) {
                if (phoneNumber.isNotBlank()) {
                    ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                        .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, phoneNumber)
                        .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
                        .build())
                }
            }

            // Add email addresses
            for (email in contact.emails) {
                if (email.isNotBlank()) {
                    ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE)
                        .withValue(ContactsContract.CommonDataKinds.Email.ADDRESS, email)
                        .withValue(ContactsContract.CommonDataKinds.Email.TYPE, ContactsContract.CommonDataKinds.Email.TYPE_HOME)
                        .build())
                }
            }
        }

        try {
            val results = context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            insertedCount = results.count { it?.uri != null }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return insertedCount
    }

    fun writeContactToSim(contact: Contact, subId: Int?): Boolean {
        if (!hasWriteContactsPermission()) {
            return false // Permission not granted
        }

        val contentResolver = context.contentResolver
        var simUri = Uri.parse("content://icc/adn") // Default SIM URI

        if (subId != null && hasPhoneStatePermission()) {
            // If subId is provided and we have permission, construct specific SIM URI
            simUri = Uri.parse("content://icc/adn/subId/$subId")
        }

        val values = ContentValues().apply {
            put("tag", contact.name.take(14)) // SIM contact name typically has char limit (e.g., 14)
            put("number", contact.phoneNumbers.firstOrNull()?.take(20)) // SIM contact number limit (e.g., 20)
        }

        return try {
            val resultUri = contentResolver.insert(simUri, values)
            resultUri != null
        } catch (e: SecurityException) {
            e.printStackTrace()
            false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}

@Serializable // Added for JSON serialization
data class Contact(
    val name: String,
    val phoneNumbers: List<String>,
    val emails: List<String>,
    val origin: String // "SIM" or "DEVICE"
)