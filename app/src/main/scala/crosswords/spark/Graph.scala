package crosswords.spark

import crosswords.spark.JsonInputFormat._
import crosswords.util.Vec
import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.mllib.feature.{IDFModel, HashingTF, IDF, Normalizer}
import org.apache.spark.mllib.linalg.SparseVector
import org.apache.spark.mllib.linalg.Vector
import org.apache.spark.rdd.RDD

/**
 * Test of graph representation based on TF-IDF for the adjacency matrix and on Vector Space Model for the similarity.
 */
class Graph(val context: SparkContext) {

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
    val weightedTFs = tfs.map(tf => tf._2.map(v => (v._1, Vec.multiplySparse(tf._1, v._2.asInstanceOf[SparseVector]))))
    val aggregatedTF = weightedTFs.reduce((rdd1, rdd2) => rdd1 ++ rdd2).reduceByKey((v1, v2) => Vec.addSparse(v1, v2)).cache()

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
    // Open context
    val context = new SparkContext()

    // Hadoop is buggy on Windows, comment/uncomment the next line of code if it causes trouble
    // See also this: http://qnalist.com/questions/4994960/run-spark-unit-test-on-windows-7
    // System.setProperty("hadoop.home.dir", "C:/winutils/")

    //val words = context.jsObjectFile("../data/crosswords/*.bz2").map(_._2).cache()
    val words = context.jsObjectFile("../data/crosswords/*.bz2").map(_._2).cache()
    //val equiv = (0.5f, Bags.equivalents(words))
    //val asso = (0.5f, Bags.associated(words))
    val crosswords = (1.0f, Bags.clues(words))

    val model = new Graph(context).createModel(List(crosswords))

    // Transform query into query vector and compute its TF-IDF
    val query = "fruit red yellow green skin computer adam eve".toUpperCase.split("\\s+").filter(!_.equals("")).toSeq
    val queryTF = context.parallelize(Array(model._1.transform(query)))
    val queryTfIdf = model._2.transform(queryTF).first()
    val normalizedQuery = new Normalizer().transform(queryTfIdf).asInstanceOf[SparseVector]

    // Compute similarities as dot product (vector space model)
    val sims = model._3.map(indexedVector => (indexedVector._1, Vec.dotProductSparse(normalizedQuery, indexedVector._2.asInstanceOf[SparseVector])))

    // Sorting is performed first locally
    val k = 10
    val partialTopK = sims.mapPartitions(it => {
      val a = it.toArray
      a.sortBy(-_._2).take(k).iterator
    }, true).collect()
    val topK = partialTopK.sortBy(-_._2).take(k)
    topK.foreach(println)



    /*val groupedWords = Bags.definitions(words) ++ Bags.examples(words) ++ Bags.equivalents(words) ++ Bags.associated(words)
    words.unpersist()
    val bags = groupedWords.map(bag => (bag._1.toUpperCase, bag._2.toUpperCase.split("\\s+").filter(!_.equals("")).toSeq))*/

    // Compute bags as (word: String, any: Seq[String]) in upper case
    //val crosswords = List((1.0f, Bags.clues(context.jsObjectFile("../data./tmp/*.bz2").map(_._2))))
    /*println(crosswords.head._2.count())
    new Graph(context).createModel(crosswords)*/

    // Group all the definitions for the same word and compute the normalized TF-IDF
    /*val groupedBags = bags.reduceByKey((def1, def2) => def1 ++ def2).cache()
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
    val sims = normedTfIdf.map(v => Vec.dotProductSparse(queryTfIdf, v.asInstanceOf[SparseVector])).zipWithIndex()

    // Sorting is performed first locally
    val k = 10
    val partialTopK = sims.mapPartitions(it => {
      val a = it.toArray
      a.sortBy(-_._1).take(k).iterator
    }, true).collect()
    val topK = partialTopK.sortBy(-_._1).take(k)

    val results = topK.map { case (rank, id) => (wordsUnique(id.toInt), rank) }
    val OUTPUT = null
    context.parallelize(results).saveAsTextFile(OUTPUT)*/

    context.stop()
  }
}
