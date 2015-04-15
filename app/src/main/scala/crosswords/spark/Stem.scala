
package crosswords.spark

import scala.io.Source
import java.text.Normalizer

/**
 * Clean words and find roots.
 *
 * @author Johan Berdat
 * @author Grégory Maitre
 * @author Vincent Mettraux
 * @author Patrick Andrade
 */
object Stem {

  val regex_ASCII = """[^\p{ASCII}]"""

  private val escapes = Map(
    "Æ" -> "AE"
    // TODO more
  )

  /**
   * Convert to uppercase ASCII and remove symbols.
   */
  def toAscii(text: String): String = {
    var tmp = text.toUpperCase
    for ((a, b) <- escapes)
      tmp = tmp.replace(a, b)
    tmp.replaceAll("[_\\W]+", " ").trim
  }

  /**
   * Clean and simplify text.
   */
  def clean(sentence: String): Seq[String] = {
    val ascii = toAscii(sentence)
    ascii.split(" ")
    // TODO improve that A LOT
    sentence
      .toUpperCase()
      .split(" ")
      .map(
        word => Normalizer.normalize(word, Normalizer.Form.NFD)
          .replaceAll(regex_ASCII, ""))
      .map(word => word_stemmer(word))
  }

  def word_stemmer(word : String) : String = {
    "vakavkaf"
  }

  def main(args: Array[String]) {
    for (line <- Source.stdin.getLines()) {
      println(clean(line))
    }
  }

}
