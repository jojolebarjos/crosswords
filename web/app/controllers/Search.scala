package controllers

import controllers.Application._
import play.api.Routes
import play.api.mvc._
import routes.javascript._
import play.api._
import play.api.db._
import java.sql.ResultSet

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

  val sqlQueryBegin = "select word, score from (select widfrom, sum(weight) as score from (select wid from Words where word in ("
  val sqlQueryEnd = ") Inputs inner join Neighbors on wid = widto group by widfrom order by score desc) Outputs inner join Words on wid = widfrom"
  val numberOfResults = 3

  def getWordsFromDB(stems: Seq[String]): Unit = {
    if (stems.isEmpty) {
      ""
    } else {
      val foo = DB.withConnection { connection =>
        val statement = connection.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)
        val result = statement.executeQuery(sqlQueryBegin + stems + sqlQueryEnd)
        var resultWords: List[String] = List()
        while (result.next()) {
          resultWords = "word:%s score:%f".format(result.getString("word"), result.getFloat("score")) :: resultWords
        }
        resultWords.reduce(_ + ", " + _)
      }
    }
}

def searching(searchText: String) = {
val stems = Stem.clean(searchText)
/*
if (stems.size != 0) {
  val stemsString = stems.reduce(_ + " " + _)
  stemsString
} else {
  "&nbsp"
}*/

  getWordsFromDB(stems.map(word =>
    """'""" + word + """'"""
  ))
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
