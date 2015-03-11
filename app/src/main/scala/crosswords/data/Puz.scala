
package crosswords.data

import java.io._
import crosswords.util.Text
import play.api.libs.json.{Json, JsObject}
import scala.collection.mutable.ArrayBuffer

/**
 * Codec for .PUZ file format, used in some crossword applications.
 * Based on description by Josh Myer and al.
 * (see <a href="https://code.google.com/p/puz/wiki/FileFormat">https://code.google.com/p/puz/wiki/FileFormat</a>)
 *
 * @author Johan Berdat
 */
object Puz {

  private val magic = Seq[Byte](0x41, 0x43, 0x52, 0x4f, 0x53, 0x53, 0x26, 0x44, 0x4f, 0x57, 0x4e, 0x00)

  /**
   * Decode given byte array.
   * @param in .PUZ file bytes
   * @return JSON representation
   */
  def decode(in: Seq[Byte]): JsObject = {

    // Find initial offset using magic constant (should be 2)
    val offset = in.indexOfSlice(magic) - 2

    // Width, height and number of clues of the puzzle
    val width = in(offset + 0x2C) & 0xFF
    val height = in(offset + 0x2D) & 0xFF
    val count = (in(offset + 0x2E) & 0xFF) | ((in(offset + 0x2F) & 0xFF) << 8)

    // Get solution (ASCII 2D array)
    val solution = in.drop(offset + 0x34).take(width * height).map(_.toChar).mkString.grouped(width).toVector

    // Get strings
    val strings = Text.split[Byte](in.drop(offset + 0x33 + 2 * width * height), 0).
      map(s => new String(s.toArray, "ISO-8859-1")).toVector
    val title = strings(0)
    val author = strings(1)
    val copyright = strings(2)
    val clues = (1 to count).map(i => strings(i + 2))

    // Check is a cell is black or out of bounds
    def black(y: Int, x: Int) =
      x < 0 || y < 0 || x >= width || y >= height || solution(y)(x) == '.'

    // Get expected word size at given location
    def span(y: Int, x: Int, dy: Int, dx: Int) =
      if (!black(y - dy, x - dx)) 0
      else (0 to math.max(width, height)).map(i => black(y + i * dy, x + i * dx)).indexOf(true)

    // Rebuild words and generate JSON
    var c = 0
    val words = new ArrayBuffer[JsObject]
    for (y <- 0 until height; x <- 0 until width) {
      val w = span(y, x, 0, 1)
      if (w > 1) {
        val word = (0 until w).map(i => solution(y)(x + i)).mkString
        val clue = clues(c)
        c = c + 1
        words += Json.obj(
          "word" -> word,
          "clue" -> clue,
          "x" -> x,
          "y" -> y,
          "dir" -> "East"
        )
      }
      val h = span(y, x, 1, 0)
      if (h > 1) {
        val word = (0 until h).map(i => solution(y + i)(x)).mkString
        val clue = clues(c)
        c = c + 1
        words += Json.obj(
          "word" -> word,
          "clue" -> clue,
          "x" -> x,
          "y" -> y,
          "dir" -> "South"
        )
      }
    }

    Json.obj(
      "words" -> words,
      "title" -> title,
      "author" -> author
    )

  }

  /**
   * Decode given byte stream.
   * @param in .PUZ file bytes
   * @return JSON representation
   */
  def decode(in: InputStream): JsObject = {
    val array = Iterator.continually(in.read()).takeWhile(_ >= 0).map(_.toByte).toArray
    in.close()
    decode(array)
  }

  def encode(json: JsObject): Array[Byte] = {
    ???
  }

  // TODO encode to .puz?


  def main(args: Array[String]) {

    val json = decode(new FileInputStream("../data/sample/rankfile.puz"))
    println(Json.prettyPrint(json))



  }

}
