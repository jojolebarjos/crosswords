package controllers

import play.api._
import play.api.db._
import play.api.mvc._
import play.api.libs.json.{Json, JsObject, JsValue}
import java.sql.ResultSet
import play.api.Play.current

object Application extends Controller {

  def index = Action {

    /*val foo = DB.withConnection { connection =>
        val statement = connection.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)
        val result = statement.executeQuery("select * from testTable where id > 6")
        var text = ""
        while (result.next()) {
          text += "id:%d val:%d".format(result.getInt("id"), result.getInt("val"))
        }
        text
    }

    Ok(views.html.index(foo))*/

    Ok(views.html.index("This is a temporary msg to avoid database settings!"))
  }

  def getRandomCrossword(): Crossword = {
    val crossword = DB.withConnection { connection =>
      val statement = connection.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)
      val result = statement.executeQuery("""SELECT * FROM crosswords.crosswords c ORDER BY RAND() LIMIT 1""")

      var generalFormat = """["""

      var id = 0;

      while (result.next()) {
        id = result.getInt("cwid")
        val source = result.getString("source")
        val language = result.getString("lang")
        val title = result.getString("title")
        val url = result.getString("url")
        val date = result.getDate("cwdate")

        generalFormat += """"source" : """" + source + """", "language" : """" + language + """", "title" : """" +
          title + """", "url" : """" + url + """", "date" : """" + date + """", """
      }

      val words = statement.executeQuery("""SELECT * FROM items, words WHERE items.cwid=""" + id + """ AND items.wid=words.wid""")
      var wordsFormat = """ "words" : ["""
      while (words.next()) {
        val word = result.getString("word")
        val clue = result.getString("clue")
        val x = result.getInt("xcoord")
        val y = result.getInt("ycoord")
        val dir = result.getString("direction")


        wordsFormat += """ { "word" : """" + word + """", "clue" : """" + clue + """", "x" : """" + x +
          """", "y" : """" + y + """", "dir" : """" + dir + """"},"""
        }

      wordsFormat = wordsFormat.substring(0, wordsFormat.length - 1) + """]"""

      generalFormat += wordsFormat + """]""";
      generalFormat

    }

    new Crossword(Json.parse(crossword))
  }

  def crosswordPage = Action {
    Ok(views.html.crossword(getRandomCrossword()))//crossword(0)))
  }

  def search = Action {
    Ok(views.html.search())
  }

  def contact = Action {
    Ok(views.html.contact())
  }

  def crossword(id: Int): Crossword = {

      val text = """{
          "source" : "The Guardian",
          "language" : "eng",
          "title" : "Quick crossword No 13,988",
          "url" : "http://www.theguardian.com/crosswords/quick/13988",
          "date" : "2015-03-10",
          "words" : [ {
            "word" : "NARCOSIS",
            "clue" : "Unconsciousness induced by drugs (8)",
            "x" : 0,
            "y" : 8,
            "dir" : "East"
          }, {
            "word" : "MINDFUL",
            "clue" : "Taking care (7)",
            "x" : 2,
            "y" : 0,
            "dir" : "South"
          }, {
            "word" : "REBEL",
            "clue" : "Kick over the traces (5)",
            "x" : 2,
            "y" : 8,
            "dir" : "South"
          }, {
            "word" : "CANE",
            "clue" : "Walking stick (4)",
            "x" : 8,
            "y" : 9,
            "dir" : "South"
          }, {
            "word" : "LOLLIPOPS",
            "clue" : "Sweets — popular works of classical music (9)",
            "x" : 12,
            "y" : 0,
            "dir" : "South"
          }, {
            "word" : "CONFETTI",
            "clue" : "Bits of paper scattered at festive occasions (8)",
            "x" : 5,
            "y" : 4,
            "dir" : "East"
          }, {
            "word" : "BOLERO",
            "clue" : "Dance — work by 9 (6)",
            "x" : 0,
            "y" : 6,
            "dir" : "East"
          }, {
            "word" : "RAVEL",
            "clue" : "Make more complicated — French composer, d. 1937 (5)",
            "x" : 8,
            "y" : 2,
            "dir" : "East"
          }, {
            "word" : "SEES",
            "clue" : "Notices (4)",
            "x" : 9,
            "y" : 8,
            "dir" : "East"
          }, {
            "word" : "RABBI",
            "clue" : "Jewish minister (5)",
            "x" : 0,
            "y" : 10,
            "dir" : "East"
          }, {
            "word" : "HID",
            "clue" : "Concealed oneself (3)",
            "x" : 12,
            "y" : 10,
            "dir" : "South"
          }, {
            "word" : "LETSGO",
            "clue" : "Liberates (4,2)",
            "x" : 7,
            "y" : 6,
            "dir" : "East"
          }, {
            "word" : "CABINCREW",
            "clue" : "Flight attendants (5,4)",
            "x" : 0,
            "y" : 4,
            "dir" : "South"
          }, {
            "word" : "WELLTRAVELLED",
            "clue" : "Having seen a lot of the world (4,9)",
            "x" : 0,
            "y" : 12,
            "dir" : "East"
          }, {
            "word" : "COVET",
            "clue" : "Wish for something (5)",
            "x" : 10,
            "y" : 0,
            "dir" : "South"
          }, {
            "word" : "MIASMA",
            "clue" : "Unwholesome atmosphere (6)",
            "x" : 6,
            "y" : 7,
            "dir" : "South"
          }, {
            "word" : "HEMISPHERICAL",
            "clue" : "Domed (13)",
            "x" : 0,
            "y" : 0,
            "dir" : "East"
          }, {
            "word" : "HARLOW",
            "clue" : "Essex town — American film actress, d. 1937 (6)",
            "x" : 6,
            "y" : 0,
            "dir" : "South"
          }, {
            "word" : "HUM",
            "clue" : "Smell badly (slang) (3)",
            "x" : 0,
            "y" : 0,
            "dir" : "South"
          }, {
            "word" : "CAFE",
            "clue" : "Greasy spoon (4)",
            "x" : 0,
            "y" : 4,
            "dir" : "East"
          }, {
            "word" : "RAREFIED",
            "clue" : "Grand — with low density (8)",
            "x" : 8,
            "y" : 0,
            "dir" : "South"
          }, {
            "word" : "SLAVISH",
            "clue" : "Abjectly submissive (7)",
            "x" : 6,
            "y" : 10,
            "dir" : "East"
          }, {
            "word" : "SITE",
            "clue" : "Position (4)",
            "x" : 4,
            "y" : 0,
            "dir" : "South"
          }, {
            "word" : "SPECIAL",
            "clue" : "Important (7)",
            "x" : 10,
            "y" : 6,
            "dir" : "South"
          }, {
            "word" : "MONITOR",
            "clue" : "School pupil assigned responsibilities (7)",
            "x" : 0,
            "y" : 2,
            "dir" : "East"
          }, {
            "word" : "ARSONIST",
            "clue" : "Pyromaniac (8)",
            "x" : 4,
            "y" : 5,
            "dir" : "South"
          } ]

        }"""

    new Crossword(Json.parse(text))

  }


}