package controllers

import javax.swing.text.html.HTML

import play.api._
import play.api.db._
import play.api.mvc._
import play.api.libs.json.{Json, JsObject, JsValue}
import java.sql.ResultSet
import play.api.Play.current
import play.twirl.api.Html

/**
 * The application that serves the page when a request is send.
 */
object Application extends Controller {

  /**
   * Serves the home page
   * @return the home page
   */
  def index = Action {
    Ok(views.html.index())
  }

  /**
   * Retrieves a random crossword from the database
   * @return a random crossword
   */
  def getRandomCrossword(): Crossword = {
    val crossword = DB.withConnection { connection =>
      val statement = connection.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)
      val result = statement.executeQuery("""SELECT * FROM crosswords c ORDER BY RANDOM() LIMIT 1""")

      var generalFormat = """{ """

      var id = 0;

      while (result.next()) {
        id = result.getInt("cwid")
        val source = result.getString("source")
        val language = result.getString("lang")
        val title = result.getString("title")
        val url = result.getString("url")
        val date = """'2015/05/13'"""

        generalFormat += """"source" : """" + source + """", "language" : """" + language + """", "title" : """" +
          title + """", "url" : """" + url + """", "date" : """" + date + """", """
      }

	  val statement2 = connection.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)
      val words = statement2.executeQuery("""SELECT xcoord, ycoord, clue, direction, word FROM items, words WHERE items.cwid=""" + id + """ AND items.wid=words.wid""")
      var wordsFormat = """ "words" : ["""
      while (words.next()) {
        val word = words.getString("word")
        val clue = words.getString("clue")
        val x = words.getInt("xcoord")
        val y = words.getInt("ycoord")
        val dir = words.getString("direction")


        wordsFormat += """ { "word" : """" + word + """", "clue" : """" + clue + """", "x" : """ + x +
          """, "y" : """ + y + """, "dir" : """" + dir + """"}, """
        }

      wordsFormat = wordsFormat.substring(0, wordsFormat.length - 2) + """ ] """

      generalFormat += wordsFormat + """ }""";
      generalFormat

    }

    new Crossword(Json.parse(crossword))
  }

  /**
   * Serves the crossword page
   * @return the crossword page
   */
  def crosswordPage = Action {
    Ok(views.html.crossword(getRandomCrossword()))
  }

  /**
   * Serves the search page
   * @return the search page
   */
  def search = Action {
    Ok(views.html.search())
  }

  /**
   * Serves the contact page
   * @return the contact page
   */
  def contact = Action {
    Ok(views.html.contact())
  }
  
  /**
   * Serves the solver page
   * @return the contact page
   */
  def solver = Action {
    Ok(views.html.solver())
  }
}