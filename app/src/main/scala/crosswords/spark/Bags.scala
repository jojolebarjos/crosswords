
package crosswords.spark

import org.apache.spark.rdd.RDD
import play.api.libs.json.JsObject

/**
 * Helpers to convert JSON objects to intermediate representations, like bags of words.
 *
 * @author Johan Berdat
 * @author Timothée Emery
 */
object Bags {

  /**
   * Extract clues from given crossword.
   */
  def clues(crossword: JsObject): Seq[(String, String)] =
    (crossword \ "words").as[Seq[JsObject]].map(w => (w \ "word").as[String] -> (w \ "clue").as[String])

  /**
   * Extract clues from given crosswords.
   */
  def clues(crosswords: RDD[JsObject]): RDD[(String, String)] =
    crosswords.flatMap(clues)

  private def items(item: String, entry: JsObject): Seq[(String, String)] =
    (entry \ item).asOpt[Seq[String]].getOrElse(Seq.empty).map(d => (entry \ "word").as[String] -> d)

  private def items(item: String, entries: RDD[JsObject]): RDD[(String, String)] =
    entries.flatMap(items(item, _))

  /**
   * Extract definitions from given dictionary entries.
   */
  def definitions(entry: JsObject): Seq[(String, String)] =
    items("definitions", entry)

  /**
   * Extract definitions from given dictionary entries.
   */
  def definitions(entries: RDD[JsObject]): RDD[(String, String)] =
    items("definitions", entries)

  /**
   * Extract definitions from given dictionary entries.
   */
  def examples(entry: JsObject): Seq[(String, String)] =
    items("examples", entry)

  /**
   * Extract definitions from given dictionary entries.
   */
  def examples(entries: RDD[JsObject]): RDD[(String, String)] =
    items("examples", entries)

  /**
   * Extract definitions from given dictionary entries.
   */
  def equivalents(entry: JsObject): Seq[(String, String)] =
    items("equivalents", entry)

  /**
   * Extract definitions from given dictionary entries.
   */
  def equivalents(entries: RDD[JsObject]): RDD[(String, String)] =
    items("equivalents", entries)

  /**
   * Extract definitions from given dictionary entries.
   */
  def associated(entry: JsObject): Seq[(String, String)] =
    items("associated", entry) ++ items("other", entry)

  /**
   * Extract definitions from given dictionary entries.
   */
  def associated(entries: RDD[JsObject]): RDD[(String, String)] =
    items("associated", entries) ++ items("other", entries)

}
