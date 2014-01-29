package org.fedoraproject.mobile

import Implicits._

import android.os.Bundle
import android.preference.PreferenceManager
import android.view.View
import android.widget.Toast

import com.google.android.gms.gcm.GoogleCloudMessaging
import com.google.common.io.CharStreams

import scalaz._, Scalaz._
import scalaz.concurrent.Promise
import scalaz.concurrent.Promise._
import scalaz.effect._

import java.io.{ DataOutputStream, InputStreamReader }
import java.net.{ HttpURLConnection, URL, URLEncoder }

/** This is where the user actually registers for FMN notifications.
  *
  * They hit a button which tells FMN "This is my API key, my oauth information,
  * and my GCM registration ID." Once FMN has this, it sends a notification
  * to us, which gets gets routed to FedmsgConfirmationActivity, which is where
  * we tell FMN whether or not they accepted.
  */
class FedmsgRegisterActivity extends NavDrawerActivity {
  override def onPostCreate(bundle: Bundle) {
    super.onPostCreate(bundle)
    setUpNav(R.layout.fmn_register_activity)
  }

  def register(view: View): Unit = {
    val sharedPref = PreferenceManager.getDefaultSharedPreferences(this)
    val username = Option(sharedPref.getString("pref_fas_username", null))
    val apiKey = Option(sharedPref.getString("pref_fmn_apikey", null))

    val sendFMN =
      if (username.isEmpty || apiKey.isEmpty)
        IO {
          Toast.makeText(
            this,
            R.string.fmn_no_username_or_api_key,
            Toast.LENGTH_LONG)
          .show
        }
      else
        IO {
          val registrationID: Promise[String] = getRegistrationID
          val fmnResponse = registrationID map {
            case id => sendIDToFMN(username.get, apiKey.get, id)
          }
          fmnResponse map {
            case r => // TODO: Parse JSON response and do something with it.
          }
        }
    sendFMN.unsafePerformIO
  }

  private def sendIDToFMN(
    username: String,
    apiKey: String,
    id: String): Promise[String] = promise {
    val openid = username + ".id.fedoraproject.org"
    val connection =
      new URL(
        "https://apps.fedoraproject.org/notifications/link-fedora-mobile/" ++
          openid ++ "/" ++ apiKey ++ "/" ++ id)
        .openConnection
        .asInstanceOf[HttpURLConnection]
    connection setDoOutput true
    connection setRequestMethod "GET"
    CharStreams.toString(new InputStreamReader(connection.getInputStream, "utf8"))
  }

  def getRegistrationID: Promise[String] = promise {
    val gcm = GoogleCloudMessaging.getInstance(this)
    gcm.register(getString(R.string.fmn_sender_id))
  }
}