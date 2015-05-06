package controllers

import controllers.Application._
import play.api.Play.current
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

  val sqlQueryBegin = """select word, score from (select widfrom, sum(weight) as score from ( select wid from Words where word in ("""
  val sqlQueryEnd = """)) Inputs inner join Neighbors on wid = widto group by widfrom order by score desc ) Outputs inner join Words on wid = widfrom"""
  val qqq = """select word, score from (select widfrom, sum(weight) as score from (select wid from Words where word in ('E')) Inputs inner join Neighbors on wid = widto group by widfrom order by score desc) Outputs inner join Words on wid = widfrom"""
  val numberOfResults = 20

  def getWordsFromDB(word: String): List[String] = {
    if (word.isEmpty) {
      List()
    } else {
      DB.withConnection { connection =>
        val statement = connection.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)

        val result = statement.executeQuery(sqlQueryBegin + """"""" + word + """"""" + sqlQueryEnd)
        var resultWords: List[String] = List()
        while (result.next()) {
          val word = result.getString("word")
          resultWords = word :: resultWords
        }

        resultWords
      }
    }
  }
  
  def retrieveWordFromPattern(pattern: String): List[String] = {
	if (pattern.isEmpty) {
      List()
    } else {
      DB.withConnection { connection =>
        val statement = connection.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)

        val result = statement.executeQuery("""select words.word from words where words.word like '""" + pattern + """'""")
        var resultWords: List[String] = List()
        while (result.next()) {
          val word = result.getString("word")
          resultWords = word :: resultWords
        }

        resultWords
      }
    }
  }

  def getCluesFromWords(words: List[String]): List[String] = {
    if (words.isEmpty) {
      List()
    } else {
      DB.withConnection { connection =>

        words.map(word => {

          var statement = connection.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)

          val result = statement.executeQuery( """select clue from words, items where words.word = '""" + word + """' and words.wid = items.wid)""")
          if (result.next()) {
            result.getString("clue")
          }
          "No clue found"
        }
        )
      }
    }
  }

  def getWordsFromDB(stems: Seq[String]): String = {
    if (stems.isEmpty) {
      ""
    } else {
      DB.withConnection { connection =>
        val statement = connection.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)
		
		val words = stems.map(word => """'""" + word + """'""")
		var wordsSearched = "";
		if (stems.size == 1) {
			wordsSearched = words(0)
		} else {
			wordsSearched = words.reduce(_ + """, """ + _)
		}
  
        val result = statement.executeQuery(sqlQueryBegin + wordsSearched + sqlQueryEnd)
        var resultWords: List[(String, Float)] = List()
        while (result.next()) {
			val word = result.getString("word")
			val score = result.getFloat("score")
          resultWords = (word, score) :: resultWords
        }
		
		if (resultWords.size == 0) {
			""
		} else if (resultWords.size == 1) {
			resultWords(0)._1
		} else {
			resultWords.sortBy(_._2).reverse.map(_._1).take(numberOfResults).reduce(_ + "<br>" + _)
		}
      }
    }
  }

  def getRandomWordsFromDB(numberWords: Int): List[(Int, String)] = {
    DB.withConnection { connection =>
      val statement = connection.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)

      val result = statement.executeQuery("""SELECT * FROM words order by RAND() LIMIT """ + numberWords)
      var resultWords: List[(Int, String)] = List()
      while (result.next()) {
        val word = result.getString("word")
        val wid = result.getInt("wid")

        if (word.matches("^[a-zA-Z]*$") && (word.length > 0)) {
          resultWords = (wid, word) :: resultWords
        }
      }

      resultWords
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

	getWordsFromDB(stems)
}

def searchWord(searchWord: String) = {
val searchClean = searchWord.toUpperCase().replaceAll("""[^A-Z\*\?]""", "").replaceAll("""\*""", """.*""").replaceAll("""\?""", """.""")
println(searchClean)
val listMatched = retrieveWordFromPattern(searchClean.replaceAll("""\.\*""", """%""").replaceAll("""\.""", """\_""")).map(_.toUpperCase()).filter(_.matches(searchClean))

if (listMatched.size != 0) {
  listMatched.take(numberOfResults).reduce(_ + "<br>" + _)
} else {
  "No matching words!"
}
}

def searchWords(searchText: String, word: String) = Action {
var result = ""

result += "Result similiarity: <br>"
result += searching(searchText)
result += "<br>Result matching: <br>"
result += searchWord(word)

Ok(result)
}

def javascriptRoutes = Action { implicit request =>
Ok(Routes.javascriptRouter("jsRoutes")(routes.javascript.Search.searchWords)).as("text/javascript")
}

}
