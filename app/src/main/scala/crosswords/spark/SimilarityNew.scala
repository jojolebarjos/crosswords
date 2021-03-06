package crosswords.spark

import java.io.File

import crosswords.spark.JsonInputFormat._
import org.apache.hadoop.io.compress.BZip2Codec
import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.rdd.RDD
import play.api.libs.json.JsObject

import scala.io.Source

/**
 * @author Matteo Filipponi
 * @author Utku Sirin
 * @author Laurent Valette
 */
object SimilarityNew {
  // TODO: Use black magic to adjust the category weights
  // 0.4 0.5 0.1 1.0 0.6
  private val CLUES_WEIGHT = 0.4f
  private val DEFS_WEIGHT = 0.5f
  private val EXAMPLES_WEIGHT = 0.1f
  private val EQUIV_WEIGHT = 1.0f
  private val ASSO_WEIGHT = 0.6f

  private val CLEANED_PARTITIONS_COUNT = 20

  private val DISTANCE_DAMPING = 0.5f

  def main(args: Array[String]) {
    //System.setProperty("hadoop.home.dir", "C:/winutils/")
    val context = new SparkContext("local[8]", "shell")

    //val crosswords = context.jsObjectFile("../data/crosswords/*.bz2").map(_._2)
    //val words = context.jsObjectFile("../data/definitions/*.bz2").map(_._2)
    //clean(crosswords, words, "../data/cleaned")

    val cleanData = loadCleaned("hdfs:///user/lmvalett/cleaned", context)
    val weightedData = buildWeightedEdges(cleanData)
    val dict = createDictionary(weightedData).cache()
    val indexed = toIndex(weightedData, dict)
    val result = combine(indexed)

    /*val result = context.textFile("../data/matrixNew/adjacency/part-*").map{s =>
      val args = s.split(",")
      (args(0).toLong, args(1).toLong, args(2).toFloat)
    }

    val dict = context.textFile("../data/matrixNew/index2word/part-*").map{s =>
      val args = s.split(",")
      (args(0).toLong, args(1))
    }*/

    def parseCSV(s: String): Seq[Seq[String]] = {
      s.split(";").map(s => s.split(",").toSeq)
    }
    val testData = context.textFile("hdfs:///user/lmvalett/part-00000.bz2").take(50).map(parseCSV).map(t => (t(0).head, t(2)))
    val reverseDict = Dictionary.swap(dict).cache()

    var hits = 0
    testData.foreach { t =>
      val top5 = searchDist2(result, t._2, dict, reverseDict)
      if (top5.contains(t._1)) {
        hits += 1
      }
    }
    context.parallelize(List(hits)).saveAsTextFile("hdfs:///user/lmvalett/out")

    /*val csvResult = result.map(t => t._1 + "," + t._2 + "," + t._3)//.coalesce(CLEANED_PARTITIONS_COUNT)
    csvResult.saveAsTextFile("hdfs:///projects/crosswords/res/complex/adjacency", classOf[BZip2Codec])

    val csvDict = dict.map(t => t._1 + "," + t._2)//.coalesce(CLEANED_PARTITIONS_COUNT)
    csvDict.saveAsTextFile("hdfs:///projects/crosswords/res/complex/index2word", classOf[BZip2Codec])*/

    context.stop()
  }

  /**
   * WARNING: This function takes a lot of time to execute! It also does not support multiple workers due to a limitation
   * of the Stemmer.
   * Clean the crossword and wiktionary definitions and save the results in the specified directory.
   * The results are saved in csv-like format of the type:
   * "<normalized word comma sep>;<stem word comma sep>;<normalized def comma sep>;<stem def comma sep>"
   * @param crosswordEntries A collection of crosswords
   * @param wikiEntries A collection of wiktionary articles
   * @param outputDirectory The path (with no ending file separator) where to write the results
   * @see crosswords.spark.Stem
   */
  def clean(crosswordEntries: RDD[JsObject], wikiEntries: RDD[JsObject], outputDirectory: String): Unit = {
    def cleanToCSV(t: (String, String)): String = {
      val wordsNorm = Stem.normalize(t._1).split("\\s").mkString(",")
      val defsNorm = Stem.normalize(t._2).split("\\s").mkString(",")
      if (wordsNorm.equals("") || defsNorm.equals("")) {
        // Word or def only contains illegal characters
        ""
      } else {
        val wordsStem = Stem.clean(t._1).mkString(",")
        val defsStem = Stem.clean(t._2).mkString(",")
        wordsNorm + ";" + wordsStem + ";" + defsNorm + ";" + defsStem
      }
    }

    val clues = Bags.clues(crosswordEntries)
    val defs = Bags.definitions(wikiEntries)
    val examples = Bags.examples(wikiEntries)
    val equiv = Bags.equivalents(wikiEntries)
    val asso = Bags.associated(wikiEntries)

    // Clean data
    val cleanClues = clues.map(cleanToCSV).filter(s => !s.equals(""))
    val cleanDefs = defs.map(cleanToCSV).filter(s => !s.equals("")).coalesce(CLEANED_PARTITIONS_COUNT)
    val cleanExamples = examples.map(cleanToCSV).filter(s => !s.equals("")).coalesce(CLEANED_PARTITIONS_COUNT)
    val cleanEquiv = equiv.map(cleanToCSV).filter(s => !s.equals("")).coalesce(CLEANED_PARTITIONS_COUNT)
    val cleanAsso = asso.map(cleanToCSV).filter(s => !s.equals("")).coalesce(CLEANED_PARTITIONS_COUNT)

    cleanClues.saveAsTextFile(outputDirectory + File.separator + "clues", classOf[BZip2Codec])
    cleanDefs.saveAsTextFile(outputDirectory + File.separator + "defs", classOf[BZip2Codec])
    cleanExamples.saveAsTextFile(outputDirectory + File.separator + "examples", classOf[BZip2Codec])
    cleanEquiv.saveAsTextFile(outputDirectory + File.separator + "equiv", classOf[BZip2Codec])
    cleanAsso.saveAsTextFile(outputDirectory + File.separator + "asso", classOf[BZip2Codec])
  }

  /**
   * Load the cleaned crosswords and wiktionary entries
   * @param inputDirectory The path (with no ending file separator) where to read the previous results
   * @param context The Spark context
   * @return A sequence of RDD containing the cleaned data from the clues, definitions, examples, equivalents and associated
   */
  def loadCleaned(inputDirectory: String, context: SparkContext): Seq[RDD[Seq[Seq[String]]]] = {
    def parseCSV(s: String): Seq[Seq[String]] = {
      s.split(";").map(s => s.split(",").toSeq)
    }

    val clues = context.textFile(inputDirectory + File.separator + "clues/part-*").map(parseCSV)
    val defs = context.textFile(inputDirectory + File.separator + "defs/part-*").map(parseCSV)
    val examples = context.textFile(inputDirectory + File.separator + "examples/part-*").map(parseCSV)
    val equiv = context.textFile(inputDirectory + File.separator + "equiv/part-*").map(parseCSV)
    val asso = context.textFile(inputDirectory + File.separator + "asso/part-*").map(parseCSV)
    // Tuple5[RDD[Tuple4[Seq[String]]]]
    Seq(clues, defs, examples, equiv, asso)
  }

  /**
   * Weight each category and group all the collections together.
   * @param l A sequence of RDD containing the cleaned data from the clues, definitions, examples, equivalents and associated
   * @return A union of all the collections after adding the category weights.
   */
  def buildWeightedEdges(l: Seq[RDD[Seq[Seq[String]]]]): RDD[(String, String, Float)] = {
    // Edges from normalized to stem
    val norm2stem = l.map(rdd => rdd.flatMap(t => t(0).zip(t(1)) ++ t(2).zip(t(3))))

    // Edges from stem words to stem words
    val stemWords2stemWords = l.map(rdd => rdd.flatMap(t => t(1).flatMap(s1 => t(1).map(s2 => (s1, s2)))))
    // Edges from stem defs to stem defs
    val stemDefs2stemDefs = l.map(rdd => rdd.flatMap(t => t(3).flatMap(s1 => t(3).map(s2 => (s1, s2)))))

    // Edges from stem words to normalized expression (concat of normalized words)
    val stemWords2expr = l.map(rdd => rdd.flatMap { t =>
        val expr = t(0).mkString(" ")
        t(1).map(s => (s, expr))
      }
    )
    // Edges from stem defs to normalized expression (concat of normalized words)
    val stemDefs2expr = l.map(rdd => rdd.flatMap { t =>
      val expr = t(0).mkString(" ")
      t(3).map(s => (s, expr))
    })

    // Weight everything
    val norm2stemWeighted = norm2stem.reduce((rdd1, rdd2) => rdd1 ++ rdd2).map(e => (e._1, e._2, EQUIV_WEIGHT))
    val stem2normWeighted = norm2stemWeighted.map(t => (t._2, t._1, t._3))

    val stemWords2stemWordsWeighted = stemWords2stemWords.reduce((rdd1, rdd2) => rdd1 ++ rdd2).map(e => (e._1, e._2, ASSO_WEIGHT))
    val stemDefs2stemDefsWeighted = stemDefs2stemDefs.reduce((rdd1, rdd2) => rdd1 ++ rdd2).map(e => (e._1, e._2, EXAMPLES_WEIGHT))

    val stemWords2exprWeighted = stemWords2expr.reduce((rdd1, rdd2) => rdd1 ++ rdd2).map(e => (e._1, e._2, ASSO_WEIGHT))
    val expr2stemWordsWeighted = stemWords2exprWeighted.map(t => (t._2, t._1, t._3))

    val stemDefs2exprWeighted = stemDefs2expr.reduce((rdd1, rdd2) => rdd1 ++ rdd2).map(e => (e._1, e._2, EXAMPLES_WEIGHT))

    def weightCategories(edges: RDD[(String, String)], catWeight: Float): RDD[(String, String, Float)] = {
      edges.map(e => (e._2, e._1, catWeight))
    }
    // clues, definitions, examples, equivalents and associated
    val expr2stemDefsWeighted = weightCategories(stemDefs2expr(0), CLUES_WEIGHT) ++
      weightCategories(stemDefs2expr(1), DEFS_WEIGHT) ++
      weightCategories(stemDefs2expr(2), EXAMPLES_WEIGHT) ++
      weightCategories(stemDefs2expr(3), EQUIV_WEIGHT) ++
      weightCategories(stemDefs2expr(4), ASSO_WEIGHT)

    norm2stemWeighted ++ stem2normWeighted ++ stemWords2stemWordsWeighted ++ stemDefs2stemDefsWeighted ++
      stemWords2exprWeighted ++ expr2stemWordsWeighted ++ stemDefs2exprWeighted ++ expr2stemDefsWeighted
  }

  /**
   * Build an index of every word in the vocabulary.
   * @param edges A collection of edges between a word and another word
   * @return An RDD of unique index and word
   * @see crosswords.spark.Dictionary#build
   */
  def createDictionary(edges: RDD[(String, String, Float)]): RDD[(Long, String)] = {
    val flatten = edges.flatMap(t => Array(t._1, t._2))
    Dictionary.build(flatten)
  }

  /**
   * Transform a collection of string-indexed edges to a collection of indexes-indexed edges.
   * @param edges A collection of edges with their weights
   * @param dictionary A collection of all the vocabulary with a unique index
   * @return A collection of edges indexed by indexes
   */
  def toIndex(edges: RDD[(String, String, Float)], dictionary: RDD[(Long, String)]): RDD[((Long, Long), Float)] = {
    val dict = Dictionary.swap(dictionary).collectAsMap()
    edges.map(t => ((dict(t._1), dict(t._2)), t._3))
  }

  /**
   * Compute and normalize the similarity between words. The similarity is between 0 (inclusive) and 1 (inclusive).
   * @param edges A collection of edges with their category weights
   * @return A collection of edges with the similarity between the two words
   */
  def combine(edges: RDD[((Long, Long), Float)]): RDD[(Long, Long, Float)] = {
    val combined = edges.reduceByKey((c1, c2) => Math.max(c1, c2)).map(t => (t._1._1, t._1._2, t._2))
    combined.filter(t => t._1 != t._2)
  }

  def increaseConnectivity(edges: RDD[(Long, Long, Float)]): RDD[(Long, Long, Float)] = {
    val from = edges.map(t => (t._1, (t._2, t._3)))
    val to = edges.map(t => (t._2, (t._1, t._3)))

    // Compute the edges between any word at distance 2
    val newEdges = to.join(from).map{t =>
      val src = t._2._1
      val dest = t._2._2
      (src._1, dest._1, src._2 * dest._2)
    }

    // Recompute the weight between the edges with the increased connections
    val recombine = (edges ++ newEdges).map(t => ((t._1, t._2), t._3)).reduceByKey((w1, w2) => Math.max(w1, w2))
    recombine.map(t => (t._1._1, t._1._2, t._2)).filter(_._3 >= EXAMPLES_WEIGHT)
  }

  def searchDist1(edges: RDD[(Long, Long, Float)], query: Seq[String], dict: RDD[(Long, String)], reverseDict: RDD[(String, Long)]): Seq[(String, Float)] = {
    val queryIndex = query.flatMap(reverseDict.lookup)
    val test = edges.filter(t => queryIndex.contains(t._2)).map(t => (t._1, t._3)).reduceByKey((t1, t2) => t1 + t2)
    test.sortBy(-_._2).take(5).map(t => (dict.lookup(t._1).head, t._2))
  }

  def searchDist2(edges: RDD[(Long, Long, Float)], query: Seq[String], dict: RDD[(Long, String)], reverseDict: RDD[(String, Long)]): Seq[(String, Float)] = {
    val queryIndex = query.flatMap(reverseDict.lookup)

    // Set of nodes at distance 1 of a node of the query
    val dist1 = edges.filter(t => queryIndex.contains(t._2))
    val propagateSearch = dist1.filter(t => t._3 >= 0.999f).map(t => (t._1, (t._2, t._3))).collectAsMap()

    // Set of nodes at distance 2 of a node of the query (represented as node, querynode, path)
    // We damp the weights coming from distance 2 because these are less reliable
    val dist2 = edges.flatMap { t =>
      val node = propagateSearch.get(t._2)
      if (node.nonEmpty) {
        List(((t._1, node.head._1), t._3 * DISTANCE_DAMPING))
      } else {
        Nil
      }
    }
    // Regroup if there are many paths between the two nodes
    val uniquePaths = (dist1.map(t => ((t._1, t._2), t._3)) ++ dist2).reduceByKey((path1, path2) => Math.max(path1, path2))

    val test = uniquePaths.map(t => (t._1._1, t._2)).reduceByKey((weight1, weight2) => weight1 + weight2)
    test.sortBy(-_._2).take(5).map(t => (dict.lookup(t._1).head, t._2))
  }
}
