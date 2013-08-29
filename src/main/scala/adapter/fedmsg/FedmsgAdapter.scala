package org.fedoraproject.mobile

import Implicits._

import android.app.Activity
import android.content.{ Context, Intent }
import android.net.Uri
import android.view.{ LayoutInflater, View, ViewGroup }
import android.view.View.OnClickListener
import android.widget.{ ArrayAdapter, ImageView, LinearLayout, ListView, TextView, Toast }

import com.google.common.hash.Hashing

import scala.concurrent.{ future, Future }
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{ Failure, Try, Success }

import java.util.ArrayList // TODO: Do something about this.

class FedmsgAdapter(
  context: Context,
  resource: Int,
  items: ArrayList[HRF.Result])
  extends ArrayAdapter[HRF.Result](context, resource, items) {

  val activity = context.asInstanceOf[Activity]

  override def getView(position: Int, convertView: View, parent: ViewGroup): View = {
    val item = getItem(position)

    val layout = LayoutInflater.from(context)
      .inflate(R.layout.fedmsg_list_item, parent, false)
      .asInstanceOf[LinearLayout]

    val iconView = layout
      .findViewById(R.id.icon)
      .asInstanceOf[ImageView]

    // If there's a user associated with it, pull from gravatar.
    // Otherwise, just use the icon if it exists.
    if (item.usernames.length > 0) {
      val bytes = s"${item.usernames.head}@fedoraproject.org".getBytes("utf8")
      Cache.getGravatar(
        context,
        Hashing.md5.hashBytes(bytes).toString).onComplete { result =>
          result match {
            case Success(gravatar) => {
              activity.runOnUiThread {
                iconView.setImageBitmap(gravatar)
              }
            }
            case _ =>
          }
        }
    } else {
      item.icon match {
        case Some(icon) => {
          Cache.getServiceIcon(context, icon, item.title) onSuccess {
            case icon =>
              activity.runOnUiThread {
                iconView.setImageBitmap(icon)
              }
          }
        }
        case None =>
      }
    }

    layout
      .findViewById(R.id.title)
      .asInstanceOf[TextView]
      .setText(item.title)

    layout
      .findViewById(R.id.subtitle)
      .asInstanceOf[TextView]
      .setText(item.subtitle)

    layout
      .findViewById(R.id.timestamp)
      .asInstanceOf[TextView]
      .setText(item.timestamp("ago"))

    layout.setOnClickListener(new OnClickListener() {
      override def onClick(view: View): Unit = {
        item.link.map { link =>
          val intent = new Intent(Intent.ACTION_VIEW, Uri.parse(link))
          activity.startActivity(intent)
        }
      }
    })

    layout
  }
}