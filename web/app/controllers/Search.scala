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
    "Jaba",
    "JavaScript",
    "Lisp",
    "Perl",
    "PHP",
    "Python",
    "Ruby",
    "Ruba",
    "Scala",
    "Scila",
    "Scheme")

  val numberOfResults = 3

  def searching(searchText: String) = {
    val stems = Stem.clean(searchText)

    if (stems.size != 0) {
      val stemsString = stems.reduce(_ + " " + _)
      stemsString
    } else {
      "&nbsp"
    }
  }

  def searchWord(searchWord: String) = {
    val searchClean = searchWord.toUpperCase().replaceAll("""[^A-Z\*\?]""", "").replaceAll("""\*""", """.*""").replaceAll("""\?""", """.""")
    val listMatched = words.map(_.toUpperCase()).filter(_.matches(searchClean))

    if (listMatched.size != 0) {
      listMatched.take(numberOfResults).reduce(_ + "<br>" + _)
    } else {
      "No matching words!"
    }
  }

  def searchWords(searchText: String, word: String) = Action {
    var result = ""

    result += searching(searchText)
    result += "<br>"
    result += searchWord(word)

    Ok(result)
  }

  def javascriptRoutes = Action { implicit request =>
    Ok(Routes.javascriptRouter("jsRoutes")(routes.javascript.Search.searchWords)).as("text/javascript")
  }

}
