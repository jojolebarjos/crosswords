package controllers

import controllers.Application._
import play.api.Routes
import play.api.mvc._
import routes.javascript._
import routes.javascript._

object Search extends Controller{
  //TODO
  val words = List("ActionScript",
    "AppleScript",
    "Asp",
    "BASIC",
    "C",
    "C++",
    "Clojure",
    "COBOL",
    "ColdFusion",
    "Erlang",
    "Fortran",
    "Groovy",
    "Haskell",
    "Java",
    "JavaScript",
    "Lisp",
    "Perl",
    "PHP",
    "Python",
    "Ruby",
    "Scala",
    "Scheme")

  def searching(searchText: String) = Action {
    val stems = Stem.clean(searchText)

    if (stems.size != 0) {
      val stemsString = stems.reduce(_ + " " + _)
      Ok(stemsString)
    } else {
      Ok("&nbsp")
    }
  }

  def javascriptRoutes = Action { implicit request =>
    Ok(Routes.javascriptRouter("jsRoutes")(routes.javascript.Search.searching)).as("text/javascript")
  }

}
