package crosswords.spark

import crosswords.spark.JsonInputFormat._
import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.mllib.feature.{HashingTF, IDF, Normalizer}
import org.apache.spark.mllib.linalg.SparseVector

/**
 * Test of graph representation based on TF-IDF for the adjacency matrix and on Vector Space Model for the similarity.
 */
object Graph {
  def dotProductSparse(v1: SparseVector, v2: SparseVector): Double = {
    val products = for {
      index1 <- v1.indices
      index2 <- v2.indices
      if index1 == index2
    } yield v1(index1) * v2(index2)
    products.sum
  }

  def main(args: Array[String]) {
    // Open context
    val context = new SparkContext()

    // Hadoop is buggy on Windows, comment/uncomment the next line of code if it causes trouble
    // See also this: http://qnalist.com/questions/4994960/run-spark-unit-test-on-windows-7
    // System.setProperty("hadoop.home.dir", "C:/winutils/")

    val words = context.jsObjectFile("hdfs:///projects/crosswords/data/definitions/*.bz2").map(_._2).cache()
    val groupedWords = Bags.definitions(words) ++ Bags.examples(words) ++ Bags.equivalents(words) ++ Bags.associated(words)
    words.unpersist()
    val bags = groupedWords.map(bag => (bag._1.toUpperCase, bag._2.toUpperCase.split("\\s+").filter(!_.equals("")).toSeq))

    // Compute bags as (word: String, any: Seq[String]) in upper case
    // val crosswords = Bags.clues(context.jsObjectFile("hdfs:///projects/crosswords/data/crosswords/*.bz2").map(_._2))
    // val bags = crosswords.map(bag => (bag._1.toUpperCase, bag._2.toUpperCase.split("\\s+").filter(!_.equals("")).toSeq))

    // Group all the definitions for the same word and compute the normalized TF-IDF
    val groupedBags = bags.reduceByKey((def1, def2) => def1 ++ def2).cache()
    val binsCount = groupedBags.flatMap(_._2).distinct().count()
    println("Bin count: " + binsCount)
    val hashTF = new HashingTF(binsCount.toInt)
    val tf = hashTF.transform(groupedBags.map(_._2)).cache()
    val idfModel = new IDF().fit(tf)
    val normalizer = new Normalizer()
    val normedTfIdf = idfModel.transform(tf).map(normalizer.transform)
    tf.unpersist()

    // Keep list of words, so that we can get them from indexes
    val wordsUnique = groupedBags.map(_._1).collect()
    groupedBags.unpersist()

    // Build a query vector from the query
    // We are searching for "apple" if that was not clear
    val query = "fruit red yellow green skin computer adam eve".toUpperCase.split("\\s+").filter(!_.equals("")).toSeq
    val queryTF = context.parallelize(Array(hashTF.transform(query)))
    val queryTfIdf = idfModel.transform(queryTF).first().asInstanceOf[SparseVector]

    // Compute similarities as dot product (vector space model)
    val sims = normedTfIdf.map(v => dotProductSparse(queryTfIdf, v.asInstanceOf[SparseVector])).zipWithIndex()

    // Sorting is performed first locally
    val k = 10
    val partialTopK = sims.mapPartitions(it => {
      val a = it.toArray
      a.sortBy(-_._1).take(k).iterator
    }, true).collect()
    val topK = partialTopK.sortBy(-_._1).take(k)

    val results = topK.map { case (rank, id) => (wordsUnique(id.toInt), rank) }
    context.parallelize(results).saveAsTextFile("hdfs:///projects/crosswords/results/apple")

    context.stop()
  }
}
