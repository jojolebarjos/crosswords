package controllers

import controllers.Application._
import play.api.Play.current
import play.api.Routes
import play.api.mvc._
import routes.javascript._
import play.api._
import play.api.db._
import java.sql.{DriverManager, ResultSet}

/**
 * Represent a Search abstraction the will search data into the database
 */
object Search extends Controller {

  val sqlQueryBegin = """select word, score from (select widfrom, sum(weight) as score from ( select wid from Words where word in ("""
  val sqlQueryEnd = """)) Inputs inner join Neighbors on wid = widto group by widfrom order by score desc ) Outputs inner join Words on wid = widfrom"""
  val numberOfResults = 20

  /**
   * Given a word, retrieves a list of word associate with this word
   * @param word the word
   * @return a list of word
   */
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

  /**
   * Given a pattern, retrieves a list of word that match this pattern
   * @param pattern the pattern
   * @return the list of word
   */
  def retrieveWordFromPattern(pattern: String): List[String] = {
    if (pattern.isEmpty) {
      List()
    } else {
      DB.withConnection { connection =>
        val statement = connection.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)

        val result = statement.executeQuery( """select words.word from words where words.word like '""" + pattern + """'""")
        var resultWords: List[String] = List()
        while (result.next()) {
          val word = result.getString("word")
          resultWords = word :: resultWords
        }

        resultWords
      }
    }
  }

  /**
   * Given a list of word, retrieves a list of word that are the clues of the words in input (each clues are at the index of the corresponding word)
   * @param words the list of word
   * @return the list of clues
   */
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

  /**
   * Given a list of word, retrieves a list of (word, score) where word is a word associate with the input word and
   * the score is the similarity score
   * @param stems the list of word stemmed
   * @return the list of (word, score)
   */
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


        resultWords.sortBy(_._2).reverse
      }
    }
  }

  /**
   * Given a number of word, retrieves a list of word of this size the the words are only composed of letters
   * @param numberWords the max number of words
   * @return the list of word
   */
  def getRandomWordsFromDB(numberWords: Int): List[(Int, String)] = {

    val dbc = "jdbc:mysql://localhost:3306/testDatabase?user=root&password=Root2015" //"jdbc:mysql://192.168.56.1:3306/testDatabase?user=root&password=vm" // observe that we specify the database name this time
    var conn = DriverManager.getConnection(dbc)
    var statement = conn.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)
    var resultWords: List[(Int, String)] = List()

    try {
      // select the tuples (7,14) and (8,16)
      val rs = statement.executeQuery( """SELECT * FROM words order by RAND() LIMIT """ + numberWords)

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


  /**
   * Search for similarity words
   * @param searchText the word searched
   * @return the string representation of the html returned
   */
  def searching(searchText: String) = {
    val stems = Stem.clean(searchText)

    val wordsWeight = getWordsFromDB(stems).take(numberOfResults)

    if (wordsWeight.isEmpty) {
      "No associate words found"
    } else {
      "Similar results with " + searchText + ": <br><ul>" + wordsWeight.map(w => "<li>" + w._1 + " with a weight of " + w._2 +
        """ (see in <a href="https://en.wiktionary.org/wiki/""" + w._1.toLowerCase() + """" target="_blank">wiktionary</a>)</li>""")
        .reduce(_ + "<br>" + _) + "</ul>"
    }
  }

  /**
   * Search for matched words
   * @param searchWord the matching searched
   * @return the string representation of the htm of matched words
   */
  def searchWord(searchWord: String) = {
    val searchClean = searchWord.toUpperCase().replaceAll( """[^A-Z\*\?]""", "").replaceAll( """\*""", """.*""").replaceAll( """\?""", """.""")
    val listMatched = retrieveWordFromPattern(searchClean.replaceAll( """\.\*""", """%""").replaceAll( """\.""", """\_""")).map(_.toUpperCase()).filter(_.matches(searchClean))

    if (listMatched.size != 0) {
      "Matched results with " + searchWord + ":" + "<ul>" + listMatched.take(numberOfResults).map(w => "<li>" + w + """ (see in <a href="https://en.wiktionary.org/wiki/""" + w.toLowerCase() + """" target="_blank">wiktionary</a>)</li>""").reduce(_ + "<br>" + _) + "</ul>"
    } else {
      "No matching words!"
    }
  }

  /**
   * Used with ajax if we want to print all similarities with a pattern
   * @param searchText the word for similarity test
   * @param word the word for matching test
   * @return an action
   */
  def searchAssociateMatching(words: String, matching: String) = Action {
	var result = ""
    val stems = Stem.clean(words)
    val wordsWeight = getWordsFromDB(stems)
	
	val matchClean = matching.toUpperCase().replaceAll( """[^A-Z\*\?]""", "").replaceAll( """\*""", """.*""").replaceAll( """\?""", """.""")
	
	val filteredWordsWeight = wordsWeight.map(t => (t._1.toUpperCase(), t._2)).filter(_._1.matches(matchClean)).take(numberOfResults)

    if (filteredWordsWeight.isEmpty) {
      result = "No associate words found"
    } else {
      result = "Similar results with " + words + " and matched " + matching + ": <br><ul>" + filteredWordsWeight.map(w => "<li>" + w._1 + " with a weight of " + w._2 +
        """ (see in <a href="https://en.wiktionary.org/wiki/""" + w._1.toLowerCase() + """" target="_blank">wiktionary</a>)</li>""")
        .reduce(_ + "<br>" + _) + "</ul>"
    }

    Ok(result)
  }

  /**
   * Used with ajax to return the matching words
   * @param searchText the word to match
   * @return an action
   */
  def searchMatching(searchText: String) = Action {
    Ok(searchWord(searchText))
  }

  /**
   * Used with ajax to return the similarity words
   * @param searchText the word to find similarities
   * @return an action
   */
  def searchAssociate(searchText: String) = Action {
    Ok(searching(searchText))
  }

  def searchSolver(crosswordEntry: String) = Action {

    val positionWordClue = crosswordEntry.split(";").map(w => {
      val l = w.split("-")
	  if (l.size == 0) {
		("", "", "")
	  } else if (l.size == 2){
		(l(0), l(1), "")
	  } else {
		(l(0), l(1), l(2))
	  }
    }).map(t => {
	  var result = (t._1, "")
	  if ((t._1 == "") && (t._2 == "") && (t._3 == "")) {
		result = ("", "")
	  } else {
		  if (t._3 == "") {
			val searchClean = t._2.toUpperCase().replaceAll( """[^A-Z\*\?]""", "").replaceAll( """\*""", """.*""").replaceAll( """\?""", """.""")
			val listMatched = retrieveWordFromPattern(searchClean.replaceAll( """\.\*""", """%""").replaceAll( """\.""", """\_""")).map(_.toUpperCase()).filter(_.matches(searchClean))

			if (!listMatched.isEmpty) {
			  result = (t._1, listMatched.take(numberOfResults).reduce(_ + "," + _))
			}
		  } else {
			  val stems = Stem.clean(t._3)
			  val wordsWeight = getWordsFromDB(stems)

			  val matchClean = t._2.toUpperCase().replaceAll( """[^A-Z\*\?]""", "").replaceAll( """\*""", """.*""").replaceAll( """\?""", """.""")

			  val filteredWordsWeight = wordsWeight.map(_._1.toUpperCase()).filter(_.matches(matchClean)).take(numberOfResults)

			  if (!filteredWordsWeight.isEmpty) {
				result = (t._1, filteredWordsWeight.reduce(_ + "," + _))
			  }
		  }
	  }
      

      result
    }).filter(_._2 != "")

    var result = ""
    if (!positionWordClue.isEmpty) {
      result = positionWordClue.map(t => t._1 + "-" + t._2).reduce(_ + ";" + _)
    }

    Ok(result)
  }

  /**
   * Enable to use ajax
   * @return an action
   */
  def javascriptRoutes = Action { implicit request =>
    Ok(Routes.javascriptRouter("jsRoutes")(routes.javascript.Search.searchMatching, routes.javascript.Search.searchAssociate, routes.javascript.Search.searchAssociateMatching,
      routes.javascript.Search.searchSolver)).as("text/javascript")
  }

}
