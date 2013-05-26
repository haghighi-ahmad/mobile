package org.fedoraproject.mobile

import Implicits._

import android.app.Activity
import android.content.Context
import android.graphics.{ Color, PorterDuff }
import android.os.Bundle
import android.view.{ LayoutInflater, Menu, MenuItem, View, ViewGroup }
import android.widget.AdapterView.OnItemClickListener
import android.widget.{ AdapterView, ArrayAdapter, LinearLayout, TextView, Toast }

import spray.json._

import scala.concurrent.future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.io.Source
import scala.util.{ Failure, Try, Success }

case class StatusesResponse(
  global_info: String,
  global_status: String,
  global_verbose_status: String,
  services: Map[String, Map[String, String]])

object MyJsonProtocol extends DefaultJsonProtocol {
  implicit val f = jsonFormat4(StatusesResponse.apply)
}

import MyJsonProtocol._

class StatusActivity extends NavDrawerActivity {
  private def updateStatuses() {
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
                .setBackgroundResource(service("status") match {
                  case "good" => R.drawable.status_good
                  case "minor" => R.drawable.status_minor
                  case "major" => R.drawable.status_major
                })

              layout
                .findViewById(R.id.servicename)
                .asInstanceOf[TextView]
                .setText(service("name"))

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

          val orderedStatuses = parsed.services.toArray.sortBy(_._2("name")).map(_._1)

          val adapter = new StatusAdapter(
            this,
            android.R.layout.simple_list_item_1,
            orderedStatuses)

          runOnUiThread {
            findView(TR.statuses).tap { obj =>
              obj.setAdapter(adapter)
              obj.setOnItemClickListener(new OnItemClickListener {
                def onItemClick(parent: AdapterView[_], view: View, position: Int, id: Long) {
                  val shortname = orderedStatuses(position)
                  val service = parsed.services(shortname)
                  view
                    .findViewById(R.id.servicemessage)
                    .asInstanceOf[TextView].tap { obj =>
                      obj.setText(service("message"))
                      obj.setVisibility(obj.getVisibility match {
                        case View.GONE => View.VISIBLE
                        case View.VISIBLE => View.GONE
                      })
                    }
                }
              })
            }
          }

          runOnUiThread {
            findView(TR.globalinfo).tap { obj =>
              obj.setText(parsed.global_verbose_status)
              obj.setBackgroundColor(parsed.global_status match {
                case "good" => Color.parseColor("#009900")
                case "minor" => Color.parseColor("#ffcc00")
                case "major" => Color.parseColor("#990000")
              })
            }
          }

        }
        case Failure(e) => Toast.makeText(this, R.string.status_failure, Toast.LENGTH_LONG).show
      }
    }
  }

  override def onCreate(bundle: Bundle) {
    super.onCreate(bundle)
    setUpNav(R.layout.statuses)
    updateStatuses()
  }

  override def onCreateOptionsMenu(menu: Menu) = {
    super.onCreateOptionsMenu(menu)
    val inflater = getMenuInflater
    inflater.inflate(R.menu.status, menu);
    true
  }

  override def onOptionsItemSelected(item: MenuItem) = {
    super.onOptionsItemSelected(item)
    item.getItemId match {
      case R.id.menu_refresh => {
        findView(TR.globalinfo).setText(R.string.loading)
        updateStatuses()
      }
      case _ =>
    }
    true
  }

}