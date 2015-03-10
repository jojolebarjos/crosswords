
package crosswords.util

import scala.collection.mutable.ArrayBuffer

/**
 * @author Johan Berdat
 */
object Text {

  /**
   * Keep content between specified bounds
   * @param text string to trim
   * @param begin lower (exclusive) bound
   * @param end upper (exclusive) bound
   * @return trimmed text
   */
  def bound(text: String, begin: String, end: String) = {
    val b = math.max(text.indexOf(begin), 0)
    val e = text.indexOf(end, b)
    if (e == -1) text.substring(b) else text.substring(b, e)
  }

  /**
   * Split sequence at specified symbol.
   * @param seq sequence to split
   * @param value delimiter
   * @return a sequence of sub sequences
   */
  def split[A](seq: Seq[A], value: A): Seq[Seq[A]] = {
    val buf = new ArrayBuffer[Seq[A]]()
    var i = 0
    var j = seq.indexOf(value)
    while (j >= 0) {
      buf += seq.view(i + 1, j).toVector
      i = j
      j = seq.indexOf(value, i + 1)
    }
    buf += seq.view(i, seq.size).toVector
    buf.toVector
  }

}
