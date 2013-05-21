package org.fedoraproject.mobile

import Implicits._

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.{ LayoutInflater, View, ViewGroup }
import android.widget.AdapterView.OnItemClickListener
import android.widget.{ AdapterView, ArrayAdapter, LinearLayout, TextView }

import spray.json._

import scala.concurrent.future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.io.Source
import scala.util.{ Failure, Try, Success }

case class StatusesResponse(global_info: String, services: Map[String, Map[String, String]])

object MyJsonProtocol extends DefaultJsonProtocol {
  implicit val f = jsonFormat2(StatusesResponse.apply)
}

import MyJsonProtocol._

class StatusActivity extends NavDrawerActivity {
  override def onCreate(bundle: Bundle) {
    super.onCreate(bundle)
    setUpNav(R.layout.statuses)

    future {
      Source.fromURL("http://status.fedoraproject.org/statuses.json").mkString
    }.onComplete { result =>
      result match {
        case Success(e) => {
          val parsed = JsonParser(e).convertTo[StatusesResponse]

          class StatusAdapter(
            context: Context,
            resource: Int,
            items: Array[String])
          extends ArrayAdapter[String](context, resource, items) {
            override def getView(position: Int, convertView: View, parent: ViewGroup): View = {
              val shortname = getItem(position)
              val service = parsed.services(shortname)

              val layout = LayoutInflater.from(context)
                .inflate(R.layout.status_list_item, parent, false)
                .asInstanceOf[LinearLayout]

              layout
                .findViewById(R.id.servicename)
                .asInstanceOf[TextView]
                .setText(service("name"))

              layout
                .findViewById(R.id.serviceshortname)
                .asInstanceOf[TextView]
                .setText(shortname)

              layout
                .findViewById(R.id.servicestatus)
                .asInstanceOf[TextView]
                .tap { obj =>
                  obj.setText(service("status") match {
                    case "good" => R.string.status_good
                    case "minor" => R.string.status_minor
                    case "major" => R.string.status_major
                  })
                  obj.setTextColor(service("status") match {
                    case "good" => Color.parseColor("#009900")
                    case "minor" => Color.parseColor("#ffcc00")
                    case "major" => Color.parseColor("#990000")
                  })
                }

              layout
            }
          }

          val adapter = new StatusAdapter(
            this,
            android.R.layout.simple_list_item_1,
            parsed.services.toArray.sortBy(_._2("name")).map(_._1))

          runOnUiThread {
            findView(TR.statuses).setAdapter(adapter)
          }
        }
        case Failure(e) =>
      }
    }
  }
}