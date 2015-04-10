
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

}
