
package crosswords.data.mine

import java.io.{FileWriter, IOException, File}
import java.text.SimpleDateFormat
import java.util.TimeZone
import crosswords.util._
import play.api.libs.json.{Json, JsObject}
import scala.io.Source

/**
 * Tools used to automatically data mine from <a href="http://www.theguardian.com/crosswords">The Guardian</a>.
 *
 * @author Johan Berdat
 */
object TheGuardian {

  /*
  The crossword HTML page has the following format:
  (ref http://www.theguardian.com/crosswords/cryptic/26482)

  ...
  <div class="crossword">
  ...
  <script>
  ...
  var crossword_identifier = 'crossword-ag5zfngtcHV6emxlLWhyZHIWCxIJQ3Jvc3N3b3JkGICAgIDR3ZMJDA';
  ...
  intersections["10-across-8"] = "8-down-3";
  ...
  words_for_clue["12-down"] = ['12-down'];
  ...
  solutions["4-across-1"] = "M";
  ...
  var path = "http://www.theguardian.com/crosswords/cryptic/26482";
  ...
  <h1>Quick crossword No 13,988</h1>
  ...
  <ul class="article-attributes">
    <li class="byline">Set by <a href="http://www.theguardian.com/profile/pasquale">Pasquale</a></li>
    <li class="publication"><a href="http://www.theguardian.com/theguardian">The Guardian</a>, Friday 30 January 2015 00.00 GMT</li>
  </ul>
  ...
  <li><label id="4-across-clue" for="4-across">
            <span class="clue-number">4</span>
            Pasquale given less than full assent? That's mean! (6)</label></li>
  ...
  <li><label id="1-down-clue" for="1-down">
            <span class="clue-number">1</span>
            Fuss about drink — situation causing disapproval? (1,3,2)</label></li>
  ...
  </div>
  ...
  <span style="left: 0px; top: 203px;" class="across">15</span>
  ...
  <span style="left: 174px; top: 145px;" class="down">12</span>
  ...
  <div id="crossword-help">
  ...

 */

  private val _begin = """<div class="crossword">"""
  private val _end = """<div id="crossword-help">"""

  private val _title = """<h1>([^<]*)</h1>""".r
  private val _url = """var path = "([^"]*)"""".r
  private val _author = """<ul class="article-attributes">\s*<li class="byline">Set by <a href="[^"]*">([^<]*)</a></li>""".r
  private val _date = """<li class="publication"><a href="[^"]*">[^<]*</a>, ([^<]+)</li>""".r

  private val _solution = """solutions\["(\d+)-(across|down)-(\d+)"] = "([A-Z])";""".r
  private val _location = """<span style="left: (\d+)px; top: (\d+)px;" class="(across|down)">(\d+)</span>""".r
  private val _clue = """for="(\d+)-(across|down)">\s*<span class="clue-number">\d+</span>([^<]*)</label></li>""".r

  private val _dateformatin = new SimpleDateFormat("EEEE d MMMM y HH.mm z")
  private val _dateformatout = new SimpleDateFormat("yyyy-MM-dd")
  _dateformatout.setTimeZone(TimeZone.getTimeZone("GMT"))

  /**
   * Parse crosswords from HTML text.
   * @param source source to parse
   * @return a JSON representation of the crossword
   */
  def parse(source: Source): JsObject = {

    // Prepare interesting data
    val content = Text.bound(source.getLines().mkString, _begin, _end)
    var json = Json.obj(
      "source" -> "The Guardian",
      "language" -> "eng"
    )

    // Try to get title
    val title = _title.findFirstMatchIn(content).map(_.group(1))
    if (title.isDefined) {
      json += "title" -> Json.toJson(title.get)
      val category = title.get.split(" ").head.toLowerCase
      if (category == "cryptic" || category == "quiptic")
        json += "categories" -> Json.toJson(Seq("cryptic"))
    }

    // Try to get url
    val url = _url.findFirstMatchIn(content).map(_.group(1))
    if (url.isDefined)
      json += "url" -> Json.toJson(url.get)

    // Try to get author
    val author = _author.findFirstMatchIn(content).map(_.group(1))
    if (author.isDefined)
      json += "author" -> Json.toJson(author.get)

    // Try to get date
    val date = _date.findFirstMatchIn(content).map(m => _dateformatin.parse(m.group(1)))
    if (date.isDefined)
      json += "date" -> Json.toJson(_dateformatout.format(date.get))

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

    // TODO extract word size(s) from definition, and split words

    // TODO support multicolumn words?

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

    // Done
    json

  }

  /*
    Each crossword has an id and a category.
   */

  private val _root = "http://www.theguardian.com/crosswords/"
  private val _folder = "../data/guardian/"

  private def ensure(category: String, id: Int) {

    // Do nothing if there already is the JSON file
    val jsonFile = new File(_folder + category + "/" + id + ".json")
    if (jsonFile.exists())
      return

    // Check if the HTML file exists
    val htmlFile = new File(_folder + category + "/" + id + ".html")
    if (!htmlFile.exists()) {

      // Guarantee that folder is here
      if (!htmlFile.getParentFile.isDirectory && !htmlFile.getParentFile.mkdirs())
        throw new IOException("failed to create directory for The Guardian")

      // Download HTML file
      println("downloading " + category + "/" + id + "...")
      //Thread.sleep(1000) // let the server breath a bit ;)
      val html = try Source.fromURL(_root + category + "/" + id).mkString catch { case e: Exception =>
        System.err.println("failed to wget " + category + "/" + id + " (" + e.getMessage + ")")
        ""
      }

      // Save content
      val out = new FileWriter(htmlFile)
      out.write(html)
      out.close()

    }

    // Convert to JSON
    val json = try Json.prettyPrint(parse(Source.fromFile(htmlFile))) catch { case e: Exception =>
      System.err.println("failed to parse " + category + "/" + id + " (" + e.getMessage + ")")
      ""
    }

    // Save content
    val out = new FileWriter(jsonFile)
    out.write(json)
    out.close()

    println(category + "/" + id + " successfully exported!")

  }

  private def query(category: String, last: Int, first: Int = 0) {
    var id = last
    while (id >= first) {
      ensure(category, id)
      id -= 1
    }
  }

  def main(args: Array[String]) {

    query("quick", 13989, 9093)
    query("cryptic", 26517, 21620)
    query("quiptic", 799, 1)
    query("speedy", 1015, 410)
    query("everyman", 3570, 2965)

    // "prize" do not have solutions
    // "genius" and "azed" are not in new format yet

  }

}
