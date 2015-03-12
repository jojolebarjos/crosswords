
package crosswords.data.mine

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

  private val _begin = "var CrosswordPuzzleData = \""
  private val _end = "\";"

  private def parse(raw: String, id: Int): JsObject = {
    
    // TODO it seems that this is the format of http://www.crossword-compiler.com/

    // Extract XML code
    val begin = raw.indexOf(_begin)
    val end = raw.lastIndexOf(_end)
    val content = raw.substring(begin + _begin.size, end)
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
      "source" -> "Puzzles by Jim",
      "language" -> "eng",
      "url" -> ("http://www.puzzlesbyjim.com/challenge--" + id + ".html"),
      "title" -> ("Challenge #" + id),
      "author" -> "Jim",
      "words" -> words.map{case (k@(d, _), (w, v)) => Json.obj(
        "word" -> w,
        "clue" -> clues(k)._2,
        "x" -> v.x,
        "y" -> v.y,
        "dir" -> d.toString
      )}
    )

  }

  private val _root = "http://www.puzzlesbyjim.com/JS/challenge_0002.js"
  private val _folder = "../data/jim/"

  private def query(id: Int): Boolean = {

    // Do nothing if there already is the JSON file
    val jsonFile = new File(_folder + id + ".json")
    if (jsonFile.exists())
      return false

    // Check if the JS file exists
    val jsFile = new File(_folder + id + ".js")
    if (!jsFile.exists()) {

      // Guarantee that folder is here
      if (!jsFile.getParentFile.isDirectory && !jsFile.getParentFile.mkdirs())
        throw new IOException("failed to create directory for Puzzles by Jim")

      // Download HTML file
      println("downloading " + id + "...")
      val url = "http://www.puzzlesbyjim.com/JS/challenge_" + "%04d".format(id) + ".js"
      val js = try Source.fromURL(url).mkString catch { case e: Exception =>
        System.err.println("failed to wget " + id + " (" + e.getMessage + ")")
        ""
      }

      // Save content
      val out = new FileWriter(jsFile)
      out.write(js)
      out.close()

    }

    // Convert to JSON
    val json = try Json.prettyPrint(parse(Source.fromFile(jsFile).mkString, id)) catch { case e: Exception =>
      System.err.println("failed to parse " + id + " (" + e.getMessage + ")")
      ""
    }

    // Save content
    val out = new FileWriter(jsonFile)
    out.write(json)
    out.close()

    println(id + " successfully exported!")
    true

  }

  def main(args: Array[String]) {

    for (id <- 1 to 222)
      query(id)

  }

}
