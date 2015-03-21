package crosswords.data.mine

import java.io.{FileReader, FileWriter, IOException, File}
import java.util._

import crosswords.util.{Vec, Direction, Text}
import play.api.libs.json.{JsObject, Json}

import scala.io.Source

/**
 * Tools used to automatically data mine from <a href="http://www.mirror.co.uk/play/crosswords">Mirror</a>.
 *
 * @author Timothee Emery (mainly inspired by Johan's script)
 */
object Mirror {

  /*
  The crossword HTML page has the following format:
  (ref view-source:http://s3.mirror.co.uk/mirror/crosswords/mir_straight_20140401.html)

   <div class="row"><div id='cell-1-1' class='cell blank'></div><div id='cell-2-1' class='cell blank'></div><div id='cell-3-1' class='cell ' data-clueid='12' data-reverted='0'>
                    <span class='cell-number'>1</span>
                    <div class='cell-input' data-solution='E'></div>
                </div><div id='cell-4-1' class='cell blank'></div><div id='cell-5-1' class='cell ' data-clueid='13' data-reverted='0'>
                    <span class='cell-number'>2</span>
                    <div class='cell-input' data-solution='T'></div>
                </div><div id='cell-6-1' class='cell blank'></div><div id='cell-7-1' class='cell ' data-clueid='14' data-reverted='0'>
                    <span class='cell-number'>3</span>
                    <div class='cell-input' data-solution='S'></div>

    (...)

     <div class="clues">
                    <h2>ACROSS</h2>
                    <div class="clues-list across">
                        <ul class="clues-across">
                            <li id='clue-1' data-direction='across' data-x1='1' data-x2='12' data-y1='2' data-y2='2'
                    data-islink='0' data-linkid='clue-' data-cluenum='6'> <b>6</b> Quarrelsome

 (12) </li><li id='clue-2' data-direction='across' data-x1='1' data-x2='7' data-y1='4' data-y2='4'
                    data-islink='0' data-linkid='clue-' data-cluenum='8'> <b>8</b> Ask to marry

 (7) </li><li id='clue-3' data-direction='across' data-x1='9' data-x2='13' data-y1='4' data-y2='4'
                    data-islink='0' data-linkid='clue-' data-cluenum='9'> <b>9</b> Order given to dogs

 (5) </li><li id='clue-4' data-direction='across' data-x1='1' data-x2='4' data-y1='6' data-y2='6'
                    data-islink='0' data-linkid='clue-' data-cluenum='10'> <b>10</b> Chuck


   (...)

   <div class="clues">
                    <h2>DOWN</h2>
                    <div class="clues-list down">
                        <ul class="clues-down">
                            <li id='clue-12' data-direction='down' data-x1='3' data-x2='3' data-y1='1' data-y2='8'
                    data-islink='0' data-linkid='clue-' data-cluenum='1'> <b>1</b> Gives approval to

 (8) </li><li id='clue-13' data-direction='down' data-x1='5' data-x2='5' data-y1='1' data-y2='5'
                    data-islink='0' data-linkid='clue-' data-cluenum='2'> <b>2</b> Hooked claw

 (5) </li><li id='clue-14' data-direction='down' data-x1='7' data-x2='7' data-y1='1' data-y2='5'
                    data-islink='0' data-linkid='clue-' data-cluenum='3'> <b>3</b> Winter sportsman

 (5) </li><li id='clue-15' data-direction='down' data-x1='9' data-x2='9' data-y1='1' data-y2='7'
                    data-islink='0' data-linkid='clue-' data-cluenum='4'> <b>4</b> Aimless or rootless person

 */


  private val format = new java.text.SimpleDateFormat("yyyyMMdd")
  private val formatDateForJson = new java.text.SimpleDateFormat("yyyy-MM-dd")
  private val _root = "http://s3.mirror.co.uk/mirror/crosswords/mir_"

  private val _folder = "../data/mirror/"

  private val _begin = """<div class="puzzle-wrapper right-clue center">"""
  private val _end = """<script type='text/javascript' src='http://s3.mirror.co.uk/production/crosswords/files/jquery.1.8.min.js'>"""

  private val _titlePuzId = """var puzzleId = "([0-9]+)";""".r
  private val _category = """/mir_([a-z]+)_20""".r
  private val _date = """_(20[0-9]+).html""".r

  // "http://s3.mirror.co.uk/mirror/crosswords/mir_straight_20140402.html"
  //private val _url = """var path = "([^"]*)"""".r


  /**
   * Parse crosswords from HTML text.
   * @param source source to parse
   * @param url the url from where the html come from
   * @return a JSON representation of the crossword
   */
  def parse(source: Source, url: String): JsObject = {

    // Prepare interesting data
    val content = Text.bound(source.getLines().mkString, _begin, _end)
    var json = Json.obj(
      "source" -> "Mirror",
      "language" -> "eng"
    )

    //Adding URL
    json += "url" -> Json.toJson(url)

    //Find and add category
    val category = _category.findFirstMatchIn(url).map(_.group(1))
    json += "categories" -> Json.toJson(Seq(category))

    //Find, format and add date
    val date = _date.findFirstMatchIn(url).map(_.group(1))
    json += "date" -> Json.toJson(formatDateForJson.format(format.parse(date.get)))

    //Adding title
    val titlePuzId = _titlePuzId.findFirstMatchIn(content).map(_.group(1))
    if (titlePuzId.isDefined) {
      json += "title" -> Json.toJson(category.get + " " + titlePuzId.get)
    }

    //Adding source
    json += "source" -> Json.toJson("Wired Puzzles - Crossword")

    //private val _location = """<span style="left: (\d+)px; top: (\d+)px;" class="(across|down)">(\d+)</span>""".r
/*
    // Get word locations
    val locations = _location.findAllMatchIn(content).map(m => (
      (m.group(4).toInt, Direction(m.group(3))),
      Vec(m.group(1).toInt / 29, m.group(2).toInt / 29)
      )).toMap
*/
    println(json.toString())
    /*
        // Get word locations
        val locations = _location.findAllMatchIn(content).map(m => (
          (m.group(4).toInt, Direction(m.group(3))),
          Vec(m.group(1).toInt / 29, m.group(2).toInt / 29)
          )).toMap

        // Get and rebuild solutions
        val solutions = _solution.findAllMatchIn(content).map(m => (
          (m.group(1).toInt, Direction(m.group(2))),
          (m.group(3).toInt, m.group(4).head)
          )).toVector.groupBy(_._1).mapValues(_.sortBy(_._2._1).map(_._2._2).mkString)

        // Get clues for words
        val clues = _clue.findAllMatchIn(content).map(m => (
          (m.group(1).toInt, Direction(m.group(2))),
          m.group(3).trim
          )).toMap


        // Join locations, solutions and clues
        val words = locations.map { case (k, Vec(x, y)) => Json.obj(
          "word" -> solutions(k),
          "clue" -> clues(k),
          "x" -> x,
          "y" -> y,
          "dir" -> k._2.toString
        )}.toVector
        if (words.isEmpty)
          throw new NoSuchElementException("empty crossword")
        json += "words" -> Json.toJson(words)
    */
    // Done
    json

  }






  private def ensure(category: String, date: String) {

    // Do nothing if there already is the JSON file
    val jsonFile = new File(_folder + category + "/" + date + ".json")
    if (jsonFile.exists())
      return

    // Check if the HTML file exists
    val htmlFile = new File(_folder + category + "/" + date + ".html")
    if (!htmlFile.exists()) {

      // Guarantee that folder is here
      if (!htmlFile.getParentFile.isDirectory && !htmlFile.getParentFile.mkdirs())
        throw new IOException("failed to create directory for Mirror")

      // Download HTML file
      println("downloading " + category + "/" + date + "...")
      Thread.sleep(1000) // let the server breath a bit ;)
      val html = try Source.fromURL(_root + category + date + ".html").mkString catch { case e: Exception =>
        System.err.println("failed to get " + category + "/" + date + " (" + e.getMessage + ")")
        ""
      }

      // Save content
      val out = new FileWriter(htmlFile)
      out.write(html)
      out.close()

    }


    // Convert to JSON
    val json = try Json.prettyPrint(parse(Source.fromFile(htmlFile), _root + category + date + ".html")) catch { case e: Exception =>
      System.err.println("failed to parse " + category + "/" + date + " (" + e.getMessage + ")")
      ""
    }

    // Save content
    val out = new FileWriter(jsonFile)
    out.write(json)
    out.close()


    println(category + "/" + date + " successfully exported!")

  }

  private def query(category: String, last: Date, first: Date = new Date()) {
    val c = Calendar.getInstance()
    var currentDate = first

    while (currentDate.after(last)) {
      ensure(category, format.format(currentDate))
      c.add(Calendar.DATE, -1)
      currentDate = c.getTime()
    }

  }

  def main(args: Array[String]) {

    val today =  new Date()
    val last = format.parse("20140401") // 1st of April

    // Testing parsing
    val htmlFile = new File(_folder + "2s_cryptic_" + "/" + 20140402 + ".html")
    val url = "http://s3.mirror.co.uk/mirror/crosswords/mir_straight_20140402.html"
    parse(Source.fromFile(htmlFile), url)



    /*
     val category = title.get.split(" ").head.toLowerCase
     if (category == "cryptic" || category == "quiptic")
       json += "categories" -> Json.toJson(Seq("cryptic"))
    */

    /*
    query("straight_", last, today)
    query("2s_straight_", last, today)
    query("2s_cryptic_", last, today)
    query("quizword_", last, today)
    */
  }

}
