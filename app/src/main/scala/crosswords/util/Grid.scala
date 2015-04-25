
package crosswords.util

import play.api.libs.json.{Json, JsObject}
import scala.collection.mutable.ArrayBuffer

/**
 * Helpers to convert between word-based and grid-based formats.
 * Grid-based format computes clues indices line by line.
 * A dot is used to show black cells.
 *
 * @author Johan Berdat
 */
object Grid {

  /**
   * Convert to a sequence of words.
   * @param json a valid JSON crossword
   * @return a sequence of (word, clue, origin, direction)
   */
  def toWords(json: JsObject): Seq[(String, String, Vec, Direction)] =
    (json \ "words").as[Seq[JsObject]].map(w => (
      (w \ "word").as[String],
      (w \ "clue").as[String],
      Vec((w \ "x").as[Int], (w \ "y").as[Int]),
      Direction((w \ "dir").as[String])
    ))

  /**
   * Convert to a sequence of words.
   * @param grid a valid grid-based crossword
   * @return a sequence of (word, clue, origin, direction)
   */
  def toWords(grid: (Seq[Seq[Char]], Seq[String])): Seq[(String, String, Vec, Direction)] = {

    // Get size
    val width = grid._1.head.size
    val height = grid._1.size

    // Convert grid to map
    val seq = (0 until height).flatMap(y => (0 until width).map(x => Vec(x, y) -> grid._1(y)(x)))
    val map = seq.toMap.withDefaultValue('.')

    // Find word locations
    val locations = seq.filter(_._2 != '.').map(_._1).flatMap(v =>
      List(East, South).filter(d => map(v - d.toVec) == '.' && map(v + d.toVec) != '.').map(_ -> v)
    ).toSeq

    // Safety checks
    if (width == 0 || height == 0 || locations.isEmpty)
      throw new IllegalArgumentException("empty crossword")
    if (locations.size != grid._2.size)
      throw new IllegalArgumentException("invalid number of clues")

    // Rebuild words
    val words = locations.map{case k@(d, v) => k ->
      (0 to width + height).map(i => map(v + d.toVec * i)).takeWhile(_ != '.').mkString
    }.map(_._2)

    // Combine results
    grid._2.zipWithIndex.map{case (c, i) =>
      val (d, v) = locations(i)
      (words(i), c, v, d)
    }

  }

  /**
   * Convert to grid-based representation.
   * @param words a sequence of (word, clue, origin, direction)
   * @return a sequence of lines and a sequence of clues
   */
  def toGrid(words: Seq[(String, String, Vec, Direction)]): (Seq[Seq[Char]], Seq[String]) = {

    // Safety checks
    if (words.isEmpty)
      throw new IllegalArgumentException("empty crossword")
    if (words.exists(_._1.isEmpty))
      throw new IllegalArgumentException("empty word")
    if (words.exists(w => w._4 == West || w._4 == North))
      throw new UnsupportedOperationException("north and west direction not supported")

    // Compute bounds
    val across = words.filter(_._4 == East)
    val down = words.filter(_._4 == South)
    val width = math.max(across.map(w => w._3.x + w._1.size - 1).max, down.map(_._3.x).max) + 1
    val height = math.max(across.map(_._3.y).max, down.map(w => w._3.y + w._1.size - 1).max) + 1

    // Create grid
    val grid = Array.fill(height, width)('.')
    val clues = new ArrayBuffer[String](across.size + down.size)
    for (y <- 0 until height)
      for (x <- 0 until width) {
        val v = Vec(x, y)

        // Check if horizontal word exists
        val across_clue = across.find(_._3 == v)
        if (across_clue.isDefined) {

          // Fill grid
          for ((c, i) <- across_clue.get._1.zipWithIndex) {
            if (grid(y)(x + i) != '.' && grid(y)(x + i) != c)
              throw new IllegalArgumentException("invalid word overlap")
            grid(y)(x + i) = c
          }

          // Add clue
          clues += across_clue.get._2

        }

        // Check if horizontal word exists
        val down_clue = down.find(_._3 == v)
        if (down_clue.isDefined) {

          // Fill grid
          for ((c, i) <- down_clue.get._1.zipWithIndex) {
            if (grid(y + i)(x) != '.' && grid(y + i)(x) != c)
              throw new IllegalArgumentException("invalid word overlap")
            grid(y + i)(x) = c
          }

          // Add clue
          clues += down_clue.get._2

        }

      }

    // Finalize result
    (grid.map(_.toSeq).toSeq, clues)

  }

  /**
   * Convert to grid-based representation.
   * @param json a valid JSON crossword
   * @return a sequence of lines and a sequence of clues
   */
  def toGrid(json: JsObject): (Seq[Seq[Char]], Seq[String]) =
    toGrid(toWords(json))

  /**
   * Convert to a JSON crossword.
   * @param words a valid word sequence
   * @return a JSON crossword
   */
  def toJson(words: Seq[(String, String, Vec, Direction)]): JsObject =
    Json.obj("words" -> words.map(w => Json.obj(
      "word" -> w._1,
      "clue" -> w._2,
      "x" -> w._3.x,
      "y" -> w._3.y,
      "dir" -> w._4.toString
    )))

  /**
   * Convert to a JSON crossword.
   * @param grid a valid grid-based crossword
   * @return a JSON crossword
   */
  def toJson(grid: (Seq[Seq[Char]], Seq[String])): JsObject =
   toJson(toWords(grid))

}
