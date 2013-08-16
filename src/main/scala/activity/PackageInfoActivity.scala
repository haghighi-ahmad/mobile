package org.fedoraproject.mobile

import Implicits._

import Pkgwat._
import Pkgwat.JSONParsing._

import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.view.View
import android.widget.{ TableRow, TextView, Toast }

import spray.json._

import scala.concurrent.future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.io.Source
import scala.util.{ Failure, Try, Success }

import com.google.common.hash.Hashing

class PackageInfoActivity extends NavDrawerActivity {
  override def onPostCreate(bundle: Bundle) {
    super.onPostCreate(bundle)
    setUpNav(R.layout.package_info_activity)
    val pkg = getIntent.getSerializableExtra("package").asInstanceOf[Package]

    val actionbar = getActionBar

    lazy val iconView = findView(TR.icon)

    Cache.getPackageIcon(this, pkg.icon) onComplete { result =>
      result match {
        case Success(icon) => {
          runOnUiThread {
            actionbar.setIcon(new BitmapDrawable(getResources, icon))
          }
        }
        case Failure(error) => {
          runOnUiThread {
            iconView.setImageResource(R.drawable.ic_search)
          }
        }
      }
    }
    actionbar.setTitle(pkg.name)
    actionbar.setSubtitle(pkg.summary)

    findView(TR.description).setText(pkg.description.replaceAll("\n", " "))

    pkg.develOwner match {
      case Some(owner) => {
        val ownerView = findView(TR.owner)
        ownerView.setText(owner)
        Cache.getGravatar(
          this,
          Hashing.md5.hashBytes(s"$owner@fedoraproject.org".getBytes("utf8")).toString).onComplete { result =>
            result match {
              case Success(gravatar) => {
                runOnUiThread {
                  ownerView.setCompoundDrawablesWithIntrinsicBounds(new BitmapDrawable(getResources, gravatar), null, null, null)
                }
              }
              case _ =>
            }
          }
      }
      case None =>
    }

    val jsonURL = constructURL(
      "bodhi/query/query_active_releases",
      FilteredQuery(
        20,
        0,
        Map("package" -> pkg.name)))

    future {
      Source.fromURL(jsonURL).mkString
    } onComplete { result =>

      runOnUiThread {
        Option(findView(TR.progress)).map(_.setVisibility(View.GONE))
      }

      result match {
        case Success(content) => {
          // This is *really* hacky, but blocked on
          // https://github.com/fedora-infra/fedora-packages/issues/24.
          // The issue is that right now Fedora Packages (the app)'s API
          // returns strings of HTML in some of its responses. We have to
          // strip out the HTML in most cases, but in one case here, we
          // want to use the HTML to split on, so that we can nuke the karma
          // that we also get back in testing_version, since we only care
          // about the version number. Ideally we'd actually get an object
          // back in JSON, and we could split that into a Version object
          // locally, here in Scala-land. This object would have: version,
          // karma, and karma_icon. But for now, life isn't ideal.
          def stripHTML(s: String) = s.replaceAll("""<\/?.*?>""", "")

          val result = JsonParser(content).convertTo[Pkgwat.APIResults[Release]]

          val releasesTable = Option(findView(TR.releases))

          val header = new TableRow(this)

          header.addView(
            new TextView(this).tap { obj =>
              obj.setText(R.string.release)
              obj.setTypeface(null, Typeface.BOLD)
            })

          header.addView(
            new TextView(this).tap { obj =>
              obj.setText(R.string.stable)
              obj.setTypeface(null, Typeface.BOLD)
            })

          header.addView(
            new TextView(this).tap { obj =>
              obj.setText(R.string.testing)
              obj.setTypeface(null, Typeface.BOLD)
            })

          runOnUiThread {
            releasesTable.map(_.addView(header))
          }

          result.rows.foreach { release =>
            val row = new TableRow(this)
            row.addView(new TextView(this).tap(_.setText(stripHTML(release.release))))
            row.addView(new TextView(this).tap(_.setText(stripHTML(release.stableVersion))))
            row.addView(new TextView(this).tap(_.setText(stripHTML(release.testingVersion.split("<div").head)))) // HACK
            runOnUiThread {
              releasesTable.map(_.addView(row))
            }
          }
        }
        case Failure(error) => Toast.makeText(this, R.string.packages_release_failure, Toast.LENGTH_LONG).show
      }
    }
  }
}