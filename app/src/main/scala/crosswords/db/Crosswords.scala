
package crosswords.db

import java.sql.{Connection, Date}
import crosswords.util.Grid
import play.api.libs.json.{Json, JsObject}
import Helper._

import scala.io.Source

/**
 * Crossword database tools.
 *
 * @author Utku Sirin
 * @author Johan Berdat
 */
object Crosswords {

  /**
   * Drop DatabaseDB
   */
  def drop() {

    // Open connection, with no specific database
    using(open()) { connection =>

      // Drop the table
      update(connection, "drop database if exists CrosswordDB")
    }

  }

  /**
   * Create DatabaseDB.
   */
  def create() {

    // TODO use getSchema to check if database exists

    // Open connection, with no specific database
    using(open()) { connection =>

      // Create the table
      update(connection, "create database CrosswordDB")
    }

    // Reopen database, with the new database
    using(connection()) { connection =>

      // Create crosswords attributes table
      update(connection, """
        |create table CrosswordAttributes (
        |    cwid int,
        |    source varchar(50),
        |    language varchar(20),
        |    title varchar(50),
        |    url varchar(100),
        |    author varchar(30),
        |    cwdate date,
        |    difficulty int,
        |    primary key (cwid)
        |)
      """.stripMargin)

      // Create words table
      update(connection, """
        |create table Words (
        |    wid int,
        |    word varchar(50),
        |    primary key (wid)
        |)
      """.stripMargin)

      // Create crosswords table
      update(connection, """
        |create table Crosswords (
        |    cwid int,
        |    wid int,
        |    xcoord int,
        |    ycoord int,
        |    clue varchar(100),
        |    direction varchar(10),
        |    primary key (cwid,wid),
        |    foreign key (cwid)
        |        references CrosswordAttributes(cwid)
        |        on update cascade on delete cascade,
        |    foreign key (wid)
        |        references Words(wid)
        |        on update cascade on delete cascade
        |)
      """.stripMargin)
    }

  }

  /**
   * Open database.
   */
  def connection(): Connection =
    open("CrosswordDB")

  /**
   * Upload a word to database. No duplicate is added.
   * @return word ID
   */
  def uploadWord(connection: Connection, word: String): Int = {

    // Check if word exists
    val existing = downloadWord(connection, word)
    if (existing.isDefined)
      return existing.get

    // Find next available ID
    val id = max(connection, "words", "wid") + 1

    // Insert value
    update(connection, "insert into words (wid, word) values (" + id + ", \"" + escape(word.toUpperCase) + "\")")
    id

  }

  /**
   * Upload a word to database. No duplicate is added.
   * @return word ID
   */
  def uploadWord(word: String): Int =
    using(connection())(c => uploadWord(c, word))

  /**
   * Get word for specified ID.
   */
  def downloadWord(connection: Connection, wid: Int): Option[String] =
    query(connection, "select word from words where wid = " + wid).as1[String].toList.headOption

  /**
   * Get word for specified ID.
   */
  def downloadWord(connection: Connection, word: String): Option[Int] =
    // TODO "=" or "like", to compare string in a case insensitive manner
    query(connection, "select wid from words where word = \"" + escape(word) + "\"").as1[Int].toList.headOption


  /**
   * Upload crosswords in JSON format.
   * @return crossword ID
   */
  def uploadCrossword(connection: Connection, crossword: JsObject): Int = {

    // Extract data
    val source = (crossword \ "source").asOpt[String].orNull
    val language = (crossword \ "language").asOpt[String].orNull
    val title = (crossword \ "title").asOpt[String].orNull
    val url = (crossword \ "url").asOpt[String].orNull
    val author = (crossword \ "author").asOpt[String].orNull
    val date = (crossword \ "date").asOpt[Date].orNull
    val difficulty = (crossword \ "difficulty").asOpt[Int].getOrElse(0)
    val words = Grid.toWords(crossword)

    // Find next available ID
    val cwid = max(connection, "crosswordattributes", "cwid") + 1

    // Add attributes
    val attributes_s = connection.prepareStatement(
      "insert into crosswordattributes (cwid, source, language, title, url, author, cwdate, difficulty) " +
        "values (?, ?, ?, ?, ?, ?, ?, ?)")
    attributes_s.setInt(1, cwid)
    attributes_s.setString(2, source)
    attributes_s.setString(3, language)
    attributes_s.setString(4, title)
    attributes_s.setString(5, url)
    attributes_s.setString(6, author)
    attributes_s.setDate(7, date)
    attributes_s.setInt(8, difficulty)
    attributes_s.executeUpdate()

    // Add words
    val word_s = connection.prepareStatement(
      "insert into crosswords (cwid, wid, xcoord, ycoord, clue, direction) " +
        "values (?, ?, ?, ?, ?, ?)")
    word_s.setInt(1, cwid)
    for ((word, clue, origin, direction) <- words) {
      val wid = uploadWord(connection, word)
      word_s.setInt(2, wid)
      word_s.setInt(3, origin.x)
      word_s.setInt(4, origin.y)
      word_s.setString(5, clue)
      word_s.setString(6, direction.toString)
      word_s.executeUpdate()
    }

    cwid

  }

  /**
   * Upload crosswords in JSON format.
   * @return crossword ID
   */
  def uploadCrossword(crossword: JsObject): Int =
    using(connection())(c => uploadCrossword(c, crossword))

  /**
   * Get specified crossword in JSON format.
   */
  def download(connection: Connection, cwid: Int): JsObject = {

    // TODO download JSON
    ???

  }

  /**
   * Get specified crossword in JSON format.
   */
  def download(cwid: Int): JsObject =
    using(connection())(c => download(c, cwid))


  def main(args: Array[String]) {

    //drop()
    //create()

    val json = Json.parse(Source.fromFile("../data/sample/rankfile.json").mkString).as[JsObject]

    val cwid = uploadCrossword(json)
    println(cwid)

  }

}
