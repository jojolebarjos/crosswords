package crosswords.spark

import crosswords.spark.JsonInputFormat._
import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.mllib.feature.{HashingTF, IDF, IDFModel, Normalizer}
import org.apache.spark.mllib.linalg.{SparseVector, Vector}
import org.apache.spark.rdd.RDD

/**
 * Test of graph representation based on TF-IDF for the adjacency matrix and on Vector Space Model for the similarity.
 */
class Graph(val context: SparkContext) {
  /**
   *
   * @param weightedBags A sequence of bags with their respective weights (sum of all weights should be 1)
   * @return The HashingTF, the IDF model and the normalized TF-IDF matrix
   */
  def createModel(weightedBags: Seq[(Float, RDD[(String, String)])]): (HashingTF, IDFModel, RDD[(String, Vector)]) = {
    // Use the stemming module to clean the bags
    //val cleanedBags = weightedBags.map(weightedBag => (weightedBag._1, weightedBag._2.map(bag => (bag._1.toUpperCase, Stem.clean(bag._2)))))
    val cleanedBags = weightedBags.map(weightedBag => (weightedBag._1, weightedBag._2.map(bag => (bag._1.toUpperCase, bag._2.toUpperCase.split("\\s+").filter(!_.equals("")).toSeq))))

    // Foreach weighted bag, if a word has multiple definitions, we regroup them
    val groupedBags = cleanedBags.map(cleaned => (cleaned._1, cleaned._2.reduceByKey((def1, def2) => def1 ++ def2).cache()))

    // Find the size of the vocabulary in order to create the corresponding HashingTF
    val wordsInCorpus = groupedBags.map(_._2.flatMap(_._2)).reduce((rdd1, rdd2) => rdd1 ++ rdd2).distinct().count().toInt
    val hashTF = new HashingTF(wordsInCorpus)

    // Compute each term-frequency (and zip results with words to know which vector defines which word)
    val tfs = groupedBags.map(grouped => (grouped._1, grouped._2.map(_._1).zip(hashTF.transform(grouped._2.map(_._2)))))

    // Aggregate each tf according to its weight
    val weightedTFs = tfs.map(tf => tf._2.map(v => (v._1, SparkUtil.multiplySparse(tf._1, v._2.asInstanceOf[SparseVector]))))
    val aggregatedTF = weightedTFs.reduce((rdd1, rdd2) => rdd1 ++ rdd2).reduceByKey((v1, v2) => SparkUtil.addSparse(v1, v2)).cache()

    // Compute IDF part, then apply it to the TF table
    val idfModel = new IDF().fit(aggregatedTF.map(_._2.asInstanceOf[Vector]))
    val normalizer = new Normalizer()
    val normedTfIdf = idfModel.transform(aggregatedTF.map(_._2.asInstanceOf[Vector])).map(normalizer.transform)
    (hashTF, idfModel, aggregatedTF.map(_._1).zip(normedTfIdf))
  }

  def saveModel(output: String): Unit = {
    ???
  }

  def loadModel(input: String): Unit = {
    ???
  }
}

object Graph {
  def main(args: Array[String]) {
    val context = new SparkContext()

    // Hadoop is buggy on Windows, comment/uncomment the next line of code if it causes trouble
    // See also this: http://qnalist.com/questions/4994960/run-spark-unit-test-on-windows-7
    // System.setProperty("hadoop.home.dir", "C:/winutils/")

    val words = context.jsObjectFile("hdfs:///projects/crosswords/data/definitions/*.bz2").map(_._2).cache()
    val crosswords = context.jsObjectFile("hdfs:///projects/crosswords/data/crosswords/*.bz2").map(_._2).cache()
    // Fix the weights using black magic
    val defs = (0.0f, Bags.definitions(words))
    val examples = (0.0f, Bags.examples(words))
    val equiv = (0.4f, Bags.equivalents(words))
    val asso = (0.3f, Bags.associated(words))
    val clues = (0.3f, Bags.clues(crosswords))

    val model = new Graph(context).createModel(List(defs, examples, equiv, asso, clues))

    // Transform query into query vector and compute its TF-IDF
    val query = "fruit red yellow green skin computer adam eve".toUpperCase.split("\\s+").filter(!_.equals("")).toSeq
    val queryTF = context.parallelize(Array(model._1.transform(query)))
    val queryTfIdf = model._2.transform(queryTF).first()
    val normalizedQuery = new Normalizer().transform(queryTfIdf).asInstanceOf[SparseVector]

    // Compute similarities as dot product (vector space model)
    val sims = model._3.map(indexedVector => (indexedVector._1, SparkUtil.dotProductSparse(normalizedQuery, indexedVector._2.asInstanceOf[SparseVector])))

    // Sorting is performed first locally
    val k = 10
    val partialTopK = sims.mapPartitions(it => {
      val a = it.toArray
      a.sortBy(-_._2).take(k).iterator
    }, true).collect()
    val topK = partialTopK.sortBy(-_._2).take(k)
    context.parallelize(topK).saveAsTextFile("hdfs:///projects/crosswords/apple")

    context.stop()
  }
}
