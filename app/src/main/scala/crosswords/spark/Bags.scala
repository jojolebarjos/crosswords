
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
   * Extract clues from given crosswords.
   */
  def clues(crosswords: RDD[JsObject]): RDD[(String, String)] =
    crosswords.flatMap(c => (c \ "words").as[Seq[JsObject]].map(w => (w \ "word").as[String] -> (w \ "clue").as[String]))

  // internal helper to get all elements from specified item
  private def dictItem(item: String, entries: RDD[JsObject]): RDD[(String, String)] =
    entries.flatMap(e => (e \ item).asOpt[Seq[String]].getOrElse(Seq.empty).map(d => (e \ "word").as[String] -> d))

  /**
   * Extract definitions from given dictionary entries.
   */
  def definitions(entries: RDD[JsObject]): RDD[(String, String)] =
    dictItem("definitions", entries)

  /**
   * Extract definitions from given dictionary entries.
   */
  def examples(entries: RDD[JsObject]): RDD[(String, String)] =
    dictItem("examples", entries)

  /**
   * Extract definitions from given dictionary entries.
   */
  def equivalents(entries: RDD[JsObject]): RDD[(String, String)] =
    dictItem("equivalents", entries)

  /**
   * Extract definitions from given dictionary entries.
   */
  def associated(entries: RDD[JsObject]): RDD[(String, String)] =
    dictItem("associated", entries) ++ dictItem("other", entries)

}
