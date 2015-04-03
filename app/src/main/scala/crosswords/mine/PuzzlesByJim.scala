
package crosswords.mine

import java.io.{FileWriter, IOException, File}

import crosswords.util.{South, East, Direction, Vec}
import play.api.libs.json.{Json, JsObject}

import scala.io.Source
import scala.xml.{NodeSeq, Elem, XML}

/**
 * Data mine helper for <a href="http://www.puzzlesbyjim.com/">Puzzles by Jim</a>
 *
 * @author Johan Berdat
 */
object PuzzlesByJim {

  val website = "http://www.puzzlesbyjim.com/"

  private val _begin = "var CrosswordPuzzleData = \""
  private val _end = "\";"

  /**
   * Parse crossword in <a href="http://www.crossword-compiler.com/">Crossword Compiler</a> format.
   * Returned JSON crossword does only have the <code>words</code> attribute.
   *
   * @param js JS file content
   * @return a JSON representation of the crossword
   */
  def parse(js: String): JsObject = {

    // TODO is it really the Crossword Compiler format?

    // Extract XML code
    val begin = js.indexOf(_begin)
    val end = js.lastIndexOf(_end)
    val content = js.substring(begin + _begin.size, end)
    val xml = XML.loadString(content.replace("\\\"", "\""))
    val crossword = xml \\ "crossword"

    // Get words
    val width = (crossword \ "grid" \ "@width").text.toInt
    val height = (crossword \ "grid" \ "@height").text.toInt
    val cells = (crossword \ "grid" \ "cell").map(c => (
      (c \ "@number").text,
      (c \ "@solution").text,
      Vec((c \ "@x").text.toInt, (c \ "@y").text.toInt)
      )).toSeq
    val grid = cells.map(c => c._3 -> (if (c._1.isEmpty) None else Some(c._1.toInt), c._2.headOption)).
      toMap.withDefaultValue((None, None))
    val locations = grid.filter(_._2._1.isDefined).map(_._1).flatMap(v =>
      List(East, South).filter(d => !grid(v - d.toVec)._2.isDefined && grid(v + d.toVec)._2.isDefined).map(_ -> v)
    ).toSeq
    val words = locations.map{case (dir, v) =>
      (dir, grid(v)._1.get) -> ((0 to width + height).map(i => grid(v + dir.toVec * i)).takeWhile(_._2.isDefined).map(_._2.get).mkString, v)
    }

    // Get clues
    val list = (crossword \ "clues").flatMap{
      case e: Elem => e.child
      case _ => NodeSeq.Empty
    }.tail
    val split = list.indexWhere(_.text == "Down")
    def extract(dir: Direction, seq: NodeSeq) = seq.map(c => (
      (dir, (c \ "@number").text.toInt),
      ((c \ "@word").text.toInt, c.text)
      )).toMap
    val clues = extract(East, list.take(split)) ++ extract(South, list.drop(split + 1))

    // Build JSON
    Json.obj(
      "words" -> words.map{case (k@(d, _), (w, v)) => Json.obj(
        "word" -> w,
        "clue" -> clues(k)._2,
        "x" -> (v.x - 1),
        "y" -> (v.y - 1),
        "dir" -> d.toString
      )}
    )

  }

  private def enumerate() = {

    // Get Puzzle by Jim homepage
    val content = Source.fromURL(website).getLines().mkString

    // Find all entries
    val regex = """<a href="([^"]*)">\s*<span class="imMnMnBorder">\s*<span class="imMnMnTxt"><span class="imMnMnImg"></span>([^<]*)</span>\s*</span>\s*</a>""".r
    regex.findAllMatchIn(content).map(m => (website + m.group(1), m.group(2))).toVector
    // TODO unescape HTML in title

  }

  private def getJsUrl(url: String) = {

    // Get puzzle HTML file
    val content = Source.fromURL(url).getLines().mkString

    // Find JS url
    val regex = """src="CrosswordCompilerApp/crosswordCompiler.js"></script><script src="([^"]*)"></script>""".r
    website + regex.findFirstMatchIn(content).map(_.group(1)).get

  }

  private def write(file: File, content: String) {
    val out = new FileWriter(file)
    out.write(content)
    out.close()
  }

  private val _folder = "../data/jim/"

  private def query(url: String, title: String) {

    // Get safe name
    val name = title.replaceAll("[^A-Za-z0-9 ]", "").trim

    // Check if JSON file already exists
    val jsonFile = new File(_folder + name + ".json")
    if (jsonFile.exists())
      return

    // Check if the JS file exists
    val jsFile = new File(_folder + name + ".js")
    if (!jsFile.exists()) {

      // Guarantee that folder is here
      if (!jsFile.getParentFile.isDirectory && !jsFile.getParentFile.mkdirs())
        throw new IOException("failed to create directory for Puzzles by Jim")

      // Download HTML file
      println("downloading " + title + "...")
      val js = try Source.fromURL(getJsUrl(url)).mkString catch { case e: Exception =>
        System.err.println("failed to wget " + title + " (" + e.getMessage + ")")
        ""
      }

      // Save content
      write(jsFile, js)

    }

    // Convert to JSON
    val json = try {
      var json = Json.obj()
      json += "source" -> Json.toJson("Puzzles by Jim")
      json += "url" -> Json.toJson(url)
      json += "title" -> Json.toJson(title)
      json += "author" -> Json.toJson("Jim")
      json += "language" -> Json.toJson("eng")
      json ++= parse(Source.fromFile(jsFile).mkString)
      Json.prettyPrint(json)
    } catch { case e: Exception =>
      System.err.println("failed to parse " + title + " (" + e.getMessage + ")")
      ""
    }

    // Save content
    write(jsonFile, json)

    // Success
    println("Challenge " + title + " successfully exported!")

  }

  def main(args: Array[String]) {

    val items = enumerate()
    for ((url, title) <- items)
      query(url, title)

  }

}
