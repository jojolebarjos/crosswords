
package crosswords.util

import java.io._
import play.api.libs.json.{JsObject, Json}

/**
 * Codec for .PUZ file format, used in some crossword applications.
 * Based on description by Josh Myer and al.
 * (see <a href="https://code.google.com/p/puz/wiki/FileFormat">https://code.google.com/p/puz/wiki/FileFormat</a>)
 *
 * @author Johan Berdat
 */
object Puz {

  /**
   * Decode given byte array.
   * @param in .PUZ file bytes
   * @return JSON representation
   */
  def decode(in: Seq[Byte]): JsObject = {

    // Find initial offset using magic constant (should be 2)
    val magic = Seq[Byte](0x41, 0x43, 0x52, 0x4f, 0x53, 0x53, 0x26, 0x44, 0x4f, 0x57, 0x4e, 0x00)
    val offset = in.indexOfSlice(magic) - 2

    // Width, height and number of clues of the puzzle
    val width = in(offset + 0x2C) & 0xFF
    val height = in(offset + 0x2D) & 0xFF
    val count = (in(offset + 0x2E) & 0xFF) | ((in(offset + 0x2F) & 0xFF) << 8)

    // Get solution (ASCII 2D array)
    val solution = in.drop(offset + 0x34).take(width * height).map(_.toChar).mkString.grouped(width).map(_.toSeq).toVector

    // Get strings
    val strings = Text.split[Byte](in.drop(offset + 0x33 + 2 * width * height), 0).
      map(s => new String(s.toArray, "ISO-8859-1")).toVector
    val title = strings(0)
    val author = strings(1)
    val copyright = strings(2)
    val clues = (1 to count).map(i => strings(i + 2))

    // Convert to JSON
    Json.obj(
      "title" -> title,
      "author" -> author
    ).deepMerge(Grid.toJson(solution -> clues))

  }

  /**
   * Decode given byte stream.
   * @param in .PUZ file bytes
   * @return JSON representation
   */
  def decode(in: InputStream): JsObject = {
    try {
      val array = Iterator.continually(in.read()).takeWhile(_ >= 0).map(_.toByte).toArray
      decode(array)
    } finally {
      try in.close() catch { case _: Exception => /* empty on purpose */ }
    }
  }

  /**
   * Encode given JSON crossword.
   * @param json JSON representation
   * @return .PUZ bytes
   */
  def encode(json: JsObject): Array[Byte] = {

    // Extract relevant data
    val title = (json \ "title").asOpt[String].getOrElse("")
    val author = (json \ "author").asOpt[String].getOrElse("")
    val (grid, clues) = Grid.toGrid(json)
    val width = grid.head.size
    val height = grid.size
    if (width > 255 || height > 255 || clues.size > 65535)
      throw new IllegalArgumentException("crossword too big")

    // Board infos
    val info = Seq[Byte] (
      '1', '.', '2', '\0', // Version string
      0, 0, // Reserved
      0, 0, // Scrambled checksum
      0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 // Reserved
    )
    val board = Seq[Byte](
      width.toByte,
      height.toByte,
      (clues.size & 0xFF).toByte, ((clues.size >> 8) & 0xFF).toByte, // #Clues
      0, 0, // Unknown bitmask
      0, 0 // Scrambled tag
    )

    // Puzzle layout
    val solution = grid.flatMap(_.mkString).mkString.toUpperCase
    if (!solution.forall(c => (c >= 'A' && c <= 'Z') || c == '.'))
      throw new IllegalArgumentException("invalid word character")
    val draft = solution.map(c => if (c == '.') '.' else '-')
    val puzzle = (solution + draft).map(_.toByte)

    // Strings
    val strings = (Seq(title, author, "") ++ clues).map(_.getBytes("ISO-8859-1") ++ Seq(0.toByte))

    // Checksums
    val cib = checksum(board, 0)
    var sum = checksum(puzzle, cib)
    for (string <- strings if string.size > 0)
      sum = checksum(string, sum)

    // Masked checksum bullshit
    val sol = checksum(solution.map(_.toByte), 0)
    val gri = checksum(draft.map(_.toByte), 0)
    var str = 0.toShort
    for (string <- strings if string.size > 0)
      str = checksum(string, str)
    val masked = Seq[Byte](
      (0x49 ^ (cib & 0xFF)).toByte,
      (0x43 ^ (sol & 0xFF)).toByte,
      (0x48 ^ (gri & 0xFF)).toByte,
      (0x45 ^ (str & 0xFF)).toByte,
      (0x41 ^ ((cib >> 8) & 0xFF)).toByte,
      (0x54 ^ ((sol >> 8) & 0xFF)).toByte,
      (0x45 ^ ((gri >> 8) & 0xFF)).toByte,
      (0x44 ^ ((str >> 8) & 0xFF)).toByte
    )

    // Header
    val checksums = Seq[Byte](
      (sum & 0xFF).toByte, ((sum >> 8) & 0xFF).toByte, // Checksum
      0x41, 0x43, 0x52, 0x4f, 0x53, 0x53, 0x26, 0x44, 0x4f, 0x57, 0x4e, 0x00, // File magic (ACROSS&DOWN)
      (cib & 0xFF).toByte, ((cib >> 8) & 0xFF).toByte // CIB Checksum
    ) ++ masked

    // Combine everything
    (checksums ++ info ++ board ++ puzzle ++ strings.flatten).toArray

  }

  // Modified CRC checksum
  private def checksum(bytes: Seq[Byte], sum: Short): Short = {
    var tmp = sum
    for (b <- bytes) {
      if ((tmp & 1) != 0)
        tmp = ((tmp >> 1) + 0x8000).toShort
      else
        tmp = (tmp >> 1).toShort
      tmp = (tmp + b).toShort
    }
    tmp
  }

}
