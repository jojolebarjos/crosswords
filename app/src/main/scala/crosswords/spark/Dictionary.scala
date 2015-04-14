
package crosswords.spark

import org.apache.spark.rdd.RDD
import org.apache.spark.SparkContext.rddToPairRDDFunctions

/**
 * Utilities to build and use a dictionary in Spark.
 *
 * @author Johan Berdat
 * @author Grégory Maitre
 */
object Dictionary {

  /**
   * Swap key and values
   */
  def swap[A, B](rdd: RDD[(A, B)]): RDD[(B, A)] =
    rdd.map(x => x._2 -> x._1)

  /**
   * Create a dictionary for specified words.
   */
  def build(words: RDD[String]): RDD[(Long, String)] =
    swap(words.distinct().sortBy(w => w).zipWithIndex())

  /**
   * Attach ID to words.
   */
  def getID(dictionary: RDD[(Long, String)])(words: RDD[String]): RDD[(String, Long)] =
    words.map(w => (w, ())).join(swap(dictionary)).map(w => (w._1, w._2._2))

  /**
   * Attach word to IDs.
   */
  def getWord(dictionary: RDD[(Long, String)])(ids: RDD[Long]): RDD[(Long, String)] =
    ids.map(w => (w, ())).join(dictionary).map(w => (w._1, w._2._2))

}
