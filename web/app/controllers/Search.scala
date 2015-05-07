package controllers

import controllers.Application._
import play.api.Play.current
import play.api.Routes
import play.api.mvc._
import routes.javascript._
import play.api._
import play.api.db._
import java.sql.{DriverManager, ResultSet}

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

  def getWordsFromDB(stems: Seq[String]): List[(String, Float)] = {
    if (stems.isEmpty) {
      List()
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
		
		
		resultWords.sortBy(_._2).reverse.take(numberOfResults)
      }
    }
  }

  def getRandomWordsFromDB(numberWords: Int): List[(Int, String)] = {
    /*
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
    */

    val dbc = "jdbc:mysql://localhost:3306/testDatabase?user=root&password=Root2015"//"jdbc:mysql://192.168.56.1:3306/testDatabase?user=root&password=vm" // observe that we specify the database name this time
    var conn = DriverManager.getConnection(dbc)
    var statement = conn.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)
    var resultWords: List[(Int, String)] = List()

    try {
      // select the tuples (7,14) and (8,16)
      val rs = statement.executeQuery("""SELECT * FROM words order by RAND() LIMIT """ + numberWords)

      // Iterate Over ResultSet
      while (rs.next) {
        val word = rs.getString("word")
        val wid = rs.getInt("wid")

        if (word.matches("^[a-zA-Z]*$") && (word.length > 0)) {
          resultWords = (wid, word) :: resultWords
        }
      }
    }
    finally {
      conn.close
    }
    resultWords
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

  val wordsWeight = getWordsFromDB(stems)
  
  if (wordsWeight.isEmpty) {
	"No associate words found"
  } else {
	"Similar results with " + searchText + ": <br><ul>" + wordsWeight.map(w => "<li>" + w._1 + " with a weight of " + w._2 +
        """ (see in <a href="https://en.wiktionary.org/wiki/""" + w._1.toLowerCase() + """" target="_blank">wiktionary</a>)</li>""")
        .reduce(_ + "<br>" + _) + "</ul>"
  }
}

def searchWord(searchWord: String) = {
  val searchClean = searchWord.toUpperCase().replaceAll("""[^A-Z\*\?]""", "").replaceAll("""\*""", """.*""").replaceAll("""\?""", """.""")
  val listMatched = retrieveWordFromPattern(searchClean.replaceAll("""\.\*""", """%""").replaceAll("""\.""", """\_""")).map(_.toUpperCase()).filter(_.matches(searchClean))

  if (listMatched.size != 0) {
    "Matched results with " + searchWord + ":" + "<ul>" + listMatched.take(numberOfResults).map(w => "<li>" + w + """ (see in <a href="https://en.wiktionary.org/wiki/""" + w.toLowerCase() + """" target="_blank">wiktionary</a>)</li>""").reduce(_ + "<br>" + _) + "</ul>"
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

  def searchMatching(searchText: String) = Action {
    Ok(searchWord(searchText))
  }

  def searchAssociate(searchText: String) = Action {
    Ok(searching(searchText))
  }

def javascriptRoutes = Action { implicit request =>
  Ok(Routes.javascriptRouter("jsRoutes")(routes.javascript.Search.searchMatching, routes.javascript.Search.searchAssociate)).as("text/javascript")
}

}
