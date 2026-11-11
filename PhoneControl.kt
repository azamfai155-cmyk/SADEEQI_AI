package com.azzam.sadeeqi.phone

import android.Manifest
import android.app.AlarmClock
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.provider.Settings
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager

/** Only uses public Android intents/APIs; operations that have side effects require explicit UI confirmation. */
class PhoneControl(private val context:Context){
 fun launchApp(packageName:String):Boolean {val i=context.packageManager.getLaunchIntentForPackage(packageName)?:return false;i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);context.startActivity(i);return true}
 fun openSettings(action:String=Settings.ACTION_SETTINGS){val allowed=setOf(Settings.ACTION_SETTINGS,Settings.ACTION_WIFI_SETTINGS,Settings.ACTION_BLUETOOTH_SETTINGS,Settings.ACTION_SOUND_SETTINGS,Settings.ACTION_APPLICATION_DETAILS_SETTINGS);require(action in allowed);context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))}
 fun setMusicVolume(value:Int){val a=context.getSystemService(Context.AUDIO_SERVICE) as AudioManager;a.setStreamVolume(AudioManager.STREAM_MUSIC,value.coerceIn(0,a.getStreamMaxVolume(AudioManager.STREAM_MUSIC)),0)}
 fun openCamera(){context.startActivity(Intent("android.media.action.IMAGE_CAPTURE").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))}
 fun createAlarm(hour:Int,minute:Int,label:String){require(hour in 0..23&&minute in 0..59);context.startActivity(Intent(AlarmClock.ACTION_SET_ALARM).putExtra(AlarmClock.EXTRA_HOUR,hour).putExtra(AlarmClock.EXTRA_MINUTES,minute).putExtra(AlarmClock.EXTRA_MESSAGE,label).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))}
 fun pickFile(){context.startActivity(Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))}
 fun dial(number:String){context.startActivity(Intent(Intent.ACTION_DIAL,Uri.parse("tel:${Uri.encode(number)}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))}
 fun composeSms(number:String,body:String){context.startActivity(Intent(Intent.ACTION_SENDTO,Uri.parse("smsto:${Uri.encode(number)}")).putExtra("sms_body",body).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))}
 fun hasContactsPermission()=ContextCompat.checkSelfPermission(context,Manifest.permission.READ_CONTACTS)==PackageManager.PERMISSION_GRANTED
 fun searchContacts(name:String):List<Pair<String,String>>{if(!hasContactsPermission())return emptyList();val out=mutableListOf<Pair<String,String>>();context.contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,ContactsContract.CommonDataKinds.Phone.NUMBER),"${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",arrayOf("%$name%"),null)?.use{c->while(c.moveToNext())out+=c.getString(0).orEmpty() to c.getString(1).orEmpty()};return out}
}
